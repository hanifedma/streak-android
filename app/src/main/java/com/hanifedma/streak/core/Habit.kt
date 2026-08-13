package com.hanifedma.streak.core

/**
 * The habit model, matching the web app's shape exactly so both clients can
 * read and write the same Firestore documents.
 *
 * Stored at /users/{uid}/habits/{id}:
 *
 *   { name, color, type, polarity, goalDir, target, unit, freq, startDate,
 *     archived, order, log: { "2026-08-04": 1, "2026-08-03": -1 } }
 *
 * Keeping the daily entries in a `log` map on the habit's own document means
 * a year of history costs one read per habit rather than one per day — which
 * is what keeps this inside Firestore's free tier and quick on a slow link.
 */
data class Habit(
    val id: String,
    val name: String,
    val color: String = Habits.DEFAULT_COLOR,
    val type: HabitType = HabitType.BINARY,
    /**
     * Which way round the habit works, and therefore what an empty day means.
     *
     * [Polarity.DO] is the ordinary kind: nothing recorded = not done.
     * [Polarity.AVOID] is "no sweets", "no cigarettes": nothing recorded =
     * KEPT, and the thing you record is the day you slipped.
     */
    val polarity: Polarity = Polarity.DO,
    val goalDir: GoalDir = GoalDir.AT_LEAST,
    val target: Double = 1.0,
    val unit: String = "",
    val freq: Freq = Freq.Daily,
    val startDate: String,
    /**
     * The day this habit effectively begins: its start date, or its oldest
     * entry when history was back-filled before it.
     *
     * This matters a lot. "Before the start" days are transparent to streaks,
     * so if the start date were the only authority, a habit created today with
     * back-filled history would have EVERY gap in that history ignored — five
     * scattered ticks would report as a five-day streak. Anchoring to the
     * oldest real entry means a day skipped between two ticks breaks the
     * streak, which is what the calendar plainly shows.
     *
     * Precomputed because statusOf() consults it constantly while drawing the
     * grid and must stay O(1).
     */
    val firstDay: String,
    val archived: Boolean = false,
    val order: Int = 0,
    val createdAt: Long? = null,
    val log: Map<String, Double> = emptyMap(),
)

enum class HabitType { BINARY, MEASURABLE;
    val wire: String get() = if (this == BINARY) "binary" else "measurable"
    companion object {
        fun from(s: String?) = if (s == "measurable") MEASURABLE else BINARY
    }
}

enum class GoalDir { AT_LEAST, AT_MOST;
    val wire: String get() = if (this == AT_LEAST) "at_least" else "at_most"
    companion object {
        fun from(s: String?) = if (s == "at_most") AT_MOST else AT_LEAST
    }
}

/** Build a habit, or break one. See [Habit.polarity]. */
enum class Polarity { DO, AVOID;
    val wire: String get() = if (this == AVOID) "avoid" else "do"
    companion object {
        // Anything that isn't exactly "avoid" is an ordinary habit, so a
        // document written before this feature existed reads back unchanged.
        fun from(s: String?) = if (s == "avoid") AVOID else DO
    }
}

/** How often a habit is due. */
sealed class Freq {
    object Daily : Freq()
    /** Specific weekdays, 0 = Sunday. Always sorted and deduplicated. */
    data class Weekdays(val days: List<Int>) : Freq()
    /** N times per week, on any days. */
    data class Weekly(val times: Int) : Freq()
}

enum class DayStatus {
    /** Before the habit existed; never a miss. */
    PRESTART,
    /** Deliberately skipped; transparent to streaks. */
    SKIP,
    /** Goal met — for an avoid habit, the day was kept. */
    DONE,
    /** Some progress, goal not met (measurable, at-least). */
    PARTIAL,
    /** An explicit failure: over an at-most limit, or the day an avoid habit
     *  was broken. */
    MISS,
    /** Not due today; transparent to streaks. */
    UNSCHEDULED,
    /** Due, but nothing recorded. */
    NONE,
}

enum class StreakUnit { DAY, WEEK }

data class Streaks(val current: Int, val best: Int, val unit: StreakUnit)
data class Completion(val done: Int, val expected: Int, val rate: Float)
data class DayProgress(val due: Int, val done: Int, val rate: Float)
data class WeekdayStats(val done: IntArray, val expected: IntArray) {
    // Arrays don't get sensible equals/hashCode from the data class.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WeekdayStats) return false
        return done.contentEquals(other.done) && expected.contentEquals(other.expected)
    }
    override fun hashCode(): Int = done.contentHashCode() * 31 + expected.contentHashCode()
}

/**
 * Build a valid Habit from loose input.
 *
 * Deliberately forgiving: habits arrive from Firestore documents written by
 * another client, from a JSON backup, or from an older version of either app.
 * Anything unrecognised falls back to a sane default rather than throwing.
 */
object HabitFactory {

    fun normalize(
        id: String,
        name: String?,
        color: String? = null,
        type: String? = null,
        polarity: String? = null,
        goalDir: String? = null,
        target: Double? = null,
        unit: String? = null,
        freq: Freq? = null,
        startDate: String? = null,
        archived: Boolean = false,
        order: Int = 0,
        createdAt: Long? = null,
        log: Map<String, Double>? = null,
    ): Habit {
        val t = HabitType.from(type)
        val pol = Polarity.from(polarity)

        // "Avoid at least 30 minutes of screen time" is a contradiction: an
        // avoid habit succeeds by staying under something, never by reaching
        // it. Binary habits ignore goalDir, so only measurable ones are pinned.
        var dir = GoalDir.from(goalDir)
        if (pol == Polarity.AVOID && t == HabitType.MEASURABLE) dir = GoalDir.AT_MOST

        var tgt = target ?: 1.0
        if (!tgt.isFinite() || tgt < 0) tgt = 1.0
        if (t == HabitType.BINARY) tgt = 1.0
        // "at least 0" would be satisfied by doing nothing, which makes streaks
        // meaningless — lift it to the smallest goal that can actually be missed.
        if (t == HabitType.MEASURABLE && dir == GoalDir.AT_LEAST && tgt <= 0) tgt = 1.0

        // "3 times a week" counts the occasions you DID something. An avoid
        // habit has none to count — every scheduled day is kept or broken — so
        // a weekly quota would be met by every week that ever existed.
        var frequency = normalizeFreq(freq)
        if (pol == Polarity.AVOID && frequency is Freq.Weekly) frequency = Freq.Daily

        val cleanLog = sanitizeLog(log)
        val start = if (Habits.isKey(startDate)) startDate!! else Habits.todayKey()

        // Day keys are zero-padded YYYY-MM-DD, so plain string comparison is
        // already chronological — no parsing needed for the minimum.
        var firstDay = start
        for (k in cleanLog.keys) if (k < firstDay) firstDay = k

        return Habit(
            id = id,
            name = (name ?: "").take(Habits.MAX_NAME_LEN),
            color = if (color != null && Habits.COLORS.contains(color)) color else Habits.DEFAULT_COLOR,
            type = t,
            polarity = pol,
            goalDir = dir,
            target = tgt,
            unit = (unit ?: "").take(Habits.MAX_UNIT_LEN),
            freq = frequency,
            startDate = start,
            firstDay = firstDay,
            archived = archived,
            order = order,
            createdAt = createdAt,
            log = cleanLog,
        )
    }

    /** Re-derive firstDay after the log or start date changes. */
    fun withLog(habit: Habit, log: Map<String, Double>): Habit {
        val clean = sanitizeLog(log)
        var firstDay = habit.startDate
        for (k in clean.keys) if (k < firstDay) firstDay = k
        return habit.copy(log = clean, firstDay = firstDay)
    }

    fun normalizeFreq(f: Freq?): Freq = when (f) {
        is Freq.Weekdays -> {
            val days = f.days.filter { it in 0..6 }.distinct().sorted()
            // A weekday habit with no days can never be done — certainly not
            // what was meant, so fall back to daily.
            if (days.isEmpty()) Freq.Daily else Freq.Weekdays(days)
        }
        is Freq.Weekly -> Freq.Weekly(f.times.coerceIn(1, 7))
        else -> Freq.Daily
    }

    /** Drop anything that isn't a "YYYY-MM-DD" → finite number pair. */
    fun sanitizeLog(log: Map<String, Double>?): Map<String, Double> {
        if (log == null) return emptyMap()
        val out = LinkedHashMap<String, Double>(log.size)
        for ((k, raw) in log) {
            if (!Habits.isKey(k)) continue
            if (!raw.isFinite()) continue
            if (raw == Habits.SKIP) { out[k] = Habits.SKIP; continue }
            if (raw < 0) continue
            out[k] = minOf(raw, Habits.MAX_VALUE)
        }
        return out
    }
}
