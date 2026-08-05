package com.hanifedma.streak.core

import java.util.Calendar
import java.util.Locale

/**
 * Streak — domain logic. A direct port of the web app's habits.js, so both
 * clients agree exactly on what a streak is.
 *
 * Pure functions only: no Android types, no I/O. That keeps it unit-testable on
 * the JVM (see HabitsTest) and keeps the rules in one readable place.
 *
 * TWO RULES THE WHOLE APP DEPENDS ON:
 *
 *  1. A "day" is always a LOCAL calendar day, written "YYYY-MM-DD". We never
 *     derive day keys from UTC — someone in Seoul (UTC+9) ticking a habit at
 *     08:00 would otherwise have it land on the previous day. All date maths
 *     goes through Calendar field arithmetic, which is immune to DST jumps
 *     (adding 86_400_000 ms is not).
 *
 *  2. A log value is a NUMBER and 0 is meaningful, so every check tests for
 *     null and never for "falsiness". Values are:
 *        null      → no entry at all
 *        SKIP (-1) → deliberately skipped (doesn't count, doesn't break)
 *        0 … n     → the recorded amount (1 = "done" for yes/no habits)
 */
object Habits {

    const val SKIP = -1.0

    /** Habit colours. Each name maps to a colour pair in the theme. */
    val COLORS = listOf(
        "green", "teal", "sky", "blue", "indigo",
        "purple", "pink", "red", "amber", "lime",
    )

    const val DEFAULT_COLOR = "green"

    /** Hard limits — keep one Firestore document comfortably under 1 MB. */
    const val MAX_HABITS = 100
    const val MAX_NAME_LEN = 60
    const val MAX_UNIT_LEN = 12
    const val MAX_VALUE = 1_000_000.0

    // ------------------------------------------------------------
    //  Dates
    // ------------------------------------------------------------

    private fun pad2(n: Int) = if (n < 10) "0$n" else n.toString()

    /** Calendar → "YYYY-MM-DD" using the device's LOCAL calendar. */
    fun dateKey(c: Calendar): String =
        "${c.get(Calendar.YEAR)}-${pad2(c.get(Calendar.MONTH) + 1)}-${pad2(c.get(Calendar.DAY_OF_MONTH))}"

    /** Today's local day key. `now` is injectable so tests can pin the clock. */
    fun todayKey(now: Calendar = Calendar.getInstance()): String = dateKey(now)

    /** "YYYY-MM-DD" → Calendar at local midnight. */
    fun parseKey(key: String): Calendar {
        val p = key.split("-")
        val c = Calendar.getInstance()
        c.clear()
        c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
        return c
    }

    private val KEY_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun isKey(key: String?): Boolean = key != null && KEY_RE.matches(key)

    /** Add n days to a day key. Field arithmetic, so DST never shifts it. */
    fun shiftKey(key: String, n: Int): String {
        val c = parseKey(key)
        c.add(Calendar.DAY_OF_MONTH, n)
        return dateKey(c)
    }

    /**
     * Whole days from b to a (a - b).
     *
     * Converts each date to a day number using a pure proleptic-Gregorian
     * formula rather than subtracting milliseconds, so a 23- or 25-hour DST day
     * still counts as exactly one day.
     */
    fun diffDays(aKey: String, bKey: String): Int = epochDay(aKey) - epochDay(bKey)

    /** Days since 1970-01-01, computed from the calendar fields alone. */
    private fun epochDay(key: String): Int {
        val p = key.split("-")
        return epochDay(p[0].toInt(), p[1].toInt(), p[2].toInt())
    }

    private fun epochDay(year: Int, month: Int, day: Int): Int {
        // Howard Hinnant's days-from-civil algorithm.
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    /** 0 = Sunday … 6 = Saturday, for a day key. */
    fun dowOf(key: String): Int {
        // Calendar.DAY_OF_WEEK is 1-based from Sunday.
        return parseKey(key).get(Calendar.DAY_OF_WEEK) - 1
    }

    /** First day of the week containing `key`, honouring the week-start setting. */
    fun weekStartKey(key: String, weekStart: Int): String {
        val ws = if (weekStart == 1) 1 else 0
        val back = (dowOf(key) - ws + 7) % 7
        return shiftKey(key, -back)
    }

    /** Inclusive list of day keys from `fromKey` to `toKey`, oldest first. */
    fun rangeKeys(fromKey: String, toKey: String): List<String> {
        val n = diffDays(toKey, fromKey)
        if (n < 0) return emptyList()
        val out = ArrayList<String>(n + 1)
        val c = parseKey(fromKey)
        for (i in 0..n) {
            out.add(dateKey(c))
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return out
    }

    /** Weekday indices in display order for the given week start. */
    fun weekdayOrder(weekStart: Int): List<Int> {
        val ws = if (weekStart == 1) 1 else 0
        return (0..6).map { (it + ws) % 7 }
    }

    /**
     * A month laid out as weeks of 7 day keys (null = padding outside the
     * month), so the caller can render a plain grid with no date maths.
     */
    fun monthGrid(year: Int, month: Int /* 0-11 */, weekStart: Int): List<List<String?>> {
        val ws = if (weekStart == 1) 1 else 0
        val first = Calendar.getInstance()
        first.clear()
        first.set(year, month, 1)
        val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val lead = ((first.get(Calendar.DAY_OF_WEEK) - 1) - ws + 7) % 7

        val cells = ArrayList<String?>()
        repeat(lead) { cells.add(null) }
        for (d in 1..daysInMonth) {
            val c = Calendar.getInstance()
            c.clear()
            c.set(year, month, d)
            cells.add(dateKey(c))
        }
        while (cells.size % 7 != 0) cells.add(null)
        return cells.chunked(7)
    }

    // ------------------------------------------------------------
    //  Per-day status
    // ------------------------------------------------------------

    /** Is this habit *supposed* to be done on this day?
     *  Weekly-count habits have no fixed days — any day counts, so they are
     *  "scheduled" every day and judged per week instead. */
    fun isScheduled(habit: Habit, key: String): Boolean = when (val f = habit.freq) {
        is Freq.Weekdays -> f.days.contains(dowOf(key))
        else -> true
    }

    fun entryOf(habit: Habit, key: String): Double? = habit.log[key]

    fun statusOf(habit: Habit, key: String): DayStatus {
        val v = entryOf(habit, key)
        if (v == SKIP) return DayStatus.SKIP
        // Days before the habit began are never held against it — but that
        // means its EFFECTIVE start (see Habit.firstDay), not its start date,
        // or gaps in back-filled history would silently vanish from streaks.
        if (v == null && diffDays(key, habit.firstDay) < 0) return DayStatus.PRESTART
        if (v == null) return if (isScheduled(habit, key)) DayStatus.NONE else DayStatus.UNSCHEDULED

        if (habit.type == HabitType.BINARY) return if (v >= 1) DayStatus.DONE else DayStatus.NONE
        if (habit.goalDir == GoalDir.AT_MOST) {
            return if (v <= habit.target) DayStatus.DONE else DayStatus.MISS
        }
        if (v >= habit.target) return DayStatus.DONE
        return if (v > 0) DayStatus.PARTIAL else DayStatus.NONE
    }

    /** 0…1 — how far through the day's goal. Drives partial fills. */
    fun progressOf(habit: Habit, key: String): Float {
        val v = entryOf(habit, key) ?: return 0f
        if (v == SKIP) return 0f
        if (habit.type == HabitType.BINARY) return if (v >= 1) 1f else 0f
        if (habit.goalDir == GoalDir.AT_MOST) return if (v <= habit.target) 1f else 0f
        if (habit.target <= 0) return 1f
        return (v / habit.target).coerceIn(0.0, 1.0).toFloat()
    }

    /** The value a single tap should record. */
    fun doneValue(habit: Habit): Double =
        if (habit.type == HabitType.BINARY) 1.0 else habit.target

    /** Step size for the +/- buttons on a measurable habit. */
    fun stepOf(habit: Habit): Double =
        maxOf(1.0, Math.round(habit.target / 10.0).toDouble())

    /** Has this day been explicitly recorded (as opposed to left blank)? */
    fun hasEntry(habit: Habit, key: String): Boolean {
        val v = entryOf(habit, key)
        return v != null && v != SKIP
    }

    // ------------------------------------------------------------
    //  Streaks
    // ------------------------------------------------------------

    /**
     * Current and best streak.
     *
     * For daily / weekday habits the unit is DAYS:
     *   done                          → extends the streak
     *   skip / unscheduled / prestart → transparent (neither extends nor breaks)
     *   none / partial / miss         → breaks it
     *   …except TODAY, which is still in progress: an unfinished today does not
     *   break the streak (an explicit "miss" on an at-most habit does, because
     *   the limit has already been blown).
     *
     * For weekly-count habits the unit is WEEKS: a week counts once it has the
     * required number of done days, and the current week is likewise still in
     * progress until it either fills up or ends.
     */
    fun computeStreaks(habit: Habit, todayK: String, weekStart: Int = 0): Streaks {
        if (habit.freq is Freq.Weekly) return weeklyStreaks(habit, todayK, weekStart)

        val startK = habit.firstDay
        if (diffDays(todayK, startK) < 0) return Streaks(0, 0, StreakUnit.DAY)

        val keys = rangeKeys(startK, todayK) // oldest → newest

        var best = 0
        var run = 0
        for (key in keys) {
            when (statusOf(habit, key)) {
                DayStatus.DONE -> { run++; if (run > best) best = run }
                DayStatus.SKIP, DayStatus.UNSCHEDULED, DayStatus.PRESTART -> Unit // transparent
                else -> run = 0
            }
        }

        var current = 0
        for (i in keys.indices.reversed()) {
            val key = keys[i]
            val st = statusOf(habit, key)
            if (st == DayStatus.DONE) { current++; continue }
            if (st == DayStatus.SKIP || st == DayStatus.UNSCHEDULED || st == DayStatus.PRESTART) continue
            // "none"/"partial" on today = still in progress, not a break.
            if (key == todayK && st != DayStatus.MISS) continue
            break
        }
        if (current > best) best = current // an in-progress run can be the best
        return Streaks(current, best, StreakUnit.DAY)
    }

    private fun weeklyStreaks(habit: Habit, todayK: String, weekStart: Int): Streaks {
        val times = (habit.freq as Freq.Weekly).times
        val startK = habit.firstDay
        if (diffDays(todayK, startK) < 0) return Streaks(0, 0, StreakUnit.WEEK)

        val firstWeek = weekStartKey(startK, weekStart)
        val thisWeek = weekStartKey(todayK, weekStart)
        val weeks = diffDays(thisWeek, firstWeek) / 7

        val filled = ArrayList<Boolean>(weeks + 1)
        for (w in 0..weeks) {
            val wk = shiftKey(firstWeek, w * 7)
            var done = 0
            for (d in 0..6) if (statusOf(habit, shiftKey(wk, d)) == DayStatus.DONE) done++
            filled.add(done >= times)
        }

        var best = 0
        var run = 0
        for (f in filled) {
            if (f) { run++; if (run > best) best = run } else run = 0
        }

        var current = 0
        for (i in filled.indices.reversed()) {
            if (filled[i]) { current++; continue }
            // The current week is still open — it just hasn't filled up yet.
            if (i == filled.size - 1) continue
            break
        }
        if (current > best) best = current
        return Streaks(current, best, StreakUnit.WEEK)
    }

    // ------------------------------------------------------------
    //  Rates and scores
    // ------------------------------------------------------------

    /**
     * Completion over an inclusive range.
     *   done     → numerator
     *   expected → scheduled days, minus skips, minus days before the start
     * Weekly-count habits are measured against times × whole-weeks-in-range.
     */
    fun completion(habit: Habit, fromKey: String, toKey: String): Completion {
        // Effective start, so back-filled history is measured rather than ignored.
        val startK = habit.firstDay
        val from = if (diffDays(fromKey, startK) < 0) startK else fromKey
        if (diffDays(toKey, from) < 0) return Completion(0, 0, 0f)

        val keys = rangeKeys(from, toKey)
        var done = 0
        var expected = 0
        var skipped = 0

        for (key in keys) {
            when (statusOf(habit, key)) {
                DayStatus.DONE -> { done++; expected++ }
                DayStatus.SKIP -> skipped++
                DayStatus.PRESTART, DayStatus.UNSCHEDULED -> Unit
                else -> expected++
            }
        }

        val f = habit.freq
        if (f is Freq.Weekly) {
            // Skipped days can't count towards the weekly quota either, so
            // shrink the expectation proportionally rather than by whole weeks.
            val days = keys.size - skipped
            expected = Math.round(days / 7.0 * f.times).toInt()
        }
        // A "done" on an unscheduled day is a bonus; never report over 100%.
        val rate = if (expected > 0) minOf(1f, done.toFloat() / expected) else 0f
        return Completion(done, expected, rate)
    }

    /** Rolling score used for the ring beside each habit name. */
    fun score(habit: Habit, todayK: String, days: Int = 30): Float =
        completion(habit, shiftKey(todayK, -(days - 1)), todayK).rate

    /** Total number of days this habit was completed, over its whole history. */
    fun totalDone(habit: Habit): Int =
        habit.log.keys.count { statusOf(habit, it) == DayStatus.DONE }

    /** Sum of recorded amounts over an inclusive range. */
    fun totalValue(habit: Habit, fromKey: String?, toKey: String?): Double {
        var sum = 0.0
        for ((k, v) in habit.log) {
            if (v == SKIP) continue
            if (fromKey != null && diffDays(k, fromKey) < 0) continue
            if (toKey != null && diffDays(k, toKey) > 0) continue
            sum += v
        }
        return sum
    }

    /** Per-weekday completion, index 0 = Sunday. */
    fun byWeekday(habit: Habit, fromKey: String, toKey: String): WeekdayStats {
        val done = IntArray(7)
        val expected = IntArray(7)
        val start = habit.firstDay
        val from = if (diffDays(fromKey, start) < 0) start else fromKey
        if (diffDays(toKey, from) < 0) return WeekdayStats(done, expected)

        for (key in rangeKeys(from, toKey)) {
            val st = statusOf(habit, key)
            val d = dowOf(key)
            if (st == DayStatus.DONE) done[d]++
            if (st == DayStatus.SKIP || st == DayStatus.PRESTART || st == DayStatus.UNSCHEDULED) continue
            expected[d]++
        }
        return WeekdayStats(done, expected)
    }

    // ------------------------------------------------------------
    //  Aggregates across all habits
    // ------------------------------------------------------------

    /** How many habits are due on `key`, and how many of those are done. */
    fun dayProgress(habits: List<Habit>, key: String): DayProgress {
        var due = 0
        var done = 0
        for (h in habits) {
            if (h.archived) continue
            when (statusOf(h, key)) {
                DayStatus.DONE -> { due++; done++ }
                DayStatus.NONE, DayStatus.PARTIAL, DayStatus.MISS -> due++
                else -> Unit
            }
        }
        return DayProgress(due, done, if (due > 0) done.toFloat() / due else 0f)
    }

    /** Habits due on `key`, in display order (skipped and done still listed). */
    fun dueOn(habits: List<Habit>, key: String): List<Habit> = habits.filter {
        if (it.archived) return@filter false
        val st = statusOf(it, key)
        st != DayStatus.PRESTART && st != DayStatus.UNSCHEDULED
    }

    fun sortHabits(habits: List<Habit>): List<Habit> =
        habits.sortedWith(compareBy({ it.order }, { it.name.lowercase(Locale.ROOT) }))
}
