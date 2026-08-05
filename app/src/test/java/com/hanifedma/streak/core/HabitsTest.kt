package com.hanifedma.streak.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * A port of the web app's tests.js, assertion for assertion, so the Android and
 * web clients provably agree on what a streak is.
 *
 * Each of these guards a rule the interface silently depends on: local-day keys
 * (never UTC), DST-safe arithmetic, skip transparency, an unfinished "today"
 * not breaking a streak, and back-filled history being measured rather than
 * ignored.
 */
class HabitsTest {

    /** Build a habit for tests, with sane defaults. */
    private fun mk(
        name: String = "Test",
        color: String? = null,
        type: String? = null,
        goalDir: String? = null,
        target: Double? = null,
        unit: String? = null,
        freq: Freq? = null,
        startDate: String? = "2026-01-01",
        archived: Boolean = false,
        order: Int = 0,
        log: Map<String, Double> = emptyMap(),
    ) = HabitFactory.normalize(
        id = "t", name = name, color = color, type = type, goalDir = goalDir,
        target = target, unit = unit, freq = freq, startDate = startDate,
        archived = archived, order = order, log = log,
    )

    private fun cal(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Calendar {
        val c = Calendar.getInstance()
        c.clear()
        c.set(y, m, d, h, min)
        return c
    }

    // ----------------------------------------------------------
    //  date keys
    // ----------------------------------------------------------

    @Test fun dateKeyPadsMonthAndDay() =
        assertEquals("2026-01-05", Habits.dateKey(cal(2026, 0, 5)))

    @Test fun dateKeyEndOfYear() =
        assertEquals("2026-12-31", Habits.dateKey(cal(2026, 11, 31)))

    @Test fun parseKeyRoundTrips() =
        assertEquals("2026-08-04", Habits.dateKey(Habits.parseKey("2026-08-04")))

    @Test fun isKeyAcceptsAndRejects() {
        assertTrue(Habits.isKey("2026-08-04"))
        assertTrue(!Habits.isKey("2026-8-4"))
        assertTrue(!Habits.isKey(""))
        assertTrue(!Habits.isKey(null))
    }

    // A day key must come from the LOCAL calendar. In any timezone ahead of
    // UTC, 08:00 local is still the previous day in UTC — deriving keys from
    // UTC would put the tick on the wrong day.
    @Test fun dateKeyUsesLocalTimeNotUtc() =
        assertEquals("2026-08-04", Habits.dateKey(cal(2026, 7, 4, 8, 0)))

    @Test fun dateKeyLateEveningStaysOnSameDay() =
        assertEquals("2026-08-04", Habits.dateKey(cal(2026, 7, 4, 23, 59)))

    @Test fun shiftKeyForwardAndBack() {
        assertEquals("2026-08-05", Habits.shiftKey("2026-08-04", 1))
        assertEquals("2026-08-03", Habits.shiftKey("2026-08-04", -1))
    }

    @Test fun shiftKeyAcrossMonthAndYearEnds() {
        assertEquals("2026-02-01", Habits.shiftKey("2026-01-31", 1))
        assertEquals("2027-01-01", Habits.shiftKey("2026-12-31", 1))
    }

    @Test fun shiftKeyHandlesLeapDay() {
        assertEquals("2028-02-29", Habits.shiftKey("2028-02-28", 1))
        assertEquals("2026-03-01", Habits.shiftKey("2026-02-28", 1))
    }

    @Test fun diffDaysBasics() {
        assertEquals(0, Habits.diffDays("2026-08-04", "2026-08-04"))
        assertEquals(1, Habits.diffDays("2026-08-05", "2026-08-04"))
        assertEquals(-1, Habits.diffDays("2026-08-03", "2026-08-04"))
    }

    @Test fun diffDaysAcrossYears() {
        assertEquals(365, Habits.diffDays("2027-01-01", "2026-01-01"))
        assertEquals(366, Habits.diffDays("2029-01-01", "2028-01-01"))
    }

    // DST: in US/EU zones a spring-forward day is 23 h and autumn 25 h long.
    // Field-based maths must still report exactly one day.
    @Test fun diffDaysAcrossDstChanges() {
        assertEquals(1, Habits.diffDays("2026-03-09", "2026-03-08")) // US spring
        assertEquals(1, Habits.diffDays("2026-11-02", "2026-11-01")) // US autumn
        assertEquals(1, Habits.diffDays("2026-03-30", "2026-03-29")) // EU spring
        assertEquals("2026-03-09", Habits.shiftKey("2026-03-08", 1))
    }

    @Test fun rangeKeysSpansDstCleanly() = assertEquals(
        listOf("2026-03-07", "2026-03-08", "2026-03-09", "2026-03-10"),
        Habits.rangeKeys("2026-03-07", "2026-03-10"),
    )

    @Test fun rangeKeysInclusiveAndReversed() {
        assertEquals(listOf("2026-08-04", "2026-08-05", "2026-08-06"),
            Habits.rangeKeys("2026-08-04", "2026-08-06"))
        assertEquals(1, Habits.rangeKeys("2026-08-04", "2026-08-04").size)
        assertEquals(0, Habits.rangeKeys("2026-08-06", "2026-08-04").size)
    }

    // 2026-08-04 is a Tuesday.
    @Test fun dowOfTuesday() = assertEquals(2, Habits.dowOf("2026-08-04"))

    @Test fun weekStartKeyBothStarts() {
        assertEquals("2026-08-02", Habits.weekStartKey("2026-08-04", 0))
        assertEquals("2026-08-03", Habits.weekStartKey("2026-08-04", 1))
        assertEquals("2026-08-02", Habits.weekStartKey("2026-08-02", 0))
        assertEquals("2026-07-27", Habits.weekStartKey("2026-08-02", 1))
    }

    @Test fun weekdayOrderFollowsWeekStart() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), Habits.weekdayOrder(0))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 0), Habits.weekdayOrder(1))
    }

    // ----------------------------------------------------------
    //  normalising
    // ----------------------------------------------------------

    @Test fun defaults() {
        val d = mk()
        assertEquals(HabitType.BINARY, d.type)
        assertEquals(GoalDir.AT_LEAST, d.goalDir)
        assertEquals(Freq.Daily, d.freq)
        assertEquals(Habits.DEFAULT_COLOR, d.color)
    }

    @Test fun binaryTargetForcedToOne() =
        assertEquals(1.0, mk(type = "binary", target = 9.0).target, 0.0)

    @Test fun unknownColourFallsBack() =
        assertEquals(Habits.DEFAULT_COLOR, mk(color = "chartreuse").color)

    @Test fun knownColourKept() = assertEquals("sky", mk(color = "sky").color)

    @Test fun nameIsClipped() =
        assertEquals(Habits.MAX_NAME_LEN, mk(name = "x".repeat(200)).name.length)

    @Test fun badStartDateFallsBackToToday() =
        assertTrue(Habits.isKey(mk(startDate = "nope").startDate))

    @Test fun atLeastTargetOfZeroIsLifted() =
        assertEquals(1.0, mk(type = "measurable", goalDir = "at_least", target = 0.0).target, 0.0)

    @Test fun atMostTargetOfZeroIsAllowed() =
        assertEquals(0.0, mk(type = "measurable", goalDir = "at_most", target = 0.0).target, 0.0)

    @Test fun negativeTargetRejected() =
        assertEquals(1.0, mk(type = "measurable", target = -5.0).target, 0.0)

    @Test fun weekdaysSortedAndDeduped() = assertEquals(
        Freq.Weekdays(listOf(1, 3, 5)),
        mk(freq = Freq.Weekdays(listOf(5, 1, 1, 3))).freq,
    )

    @Test fun emptyWeekdaysFallsBackToDaily() =
        assertEquals(Freq.Daily, mk(freq = Freq.Weekdays(emptyList())).freq)

    @Test fun outOfRangeWeekdaysDropped() =
        assertEquals(Freq.Weekdays(listOf(2)), mk(freq = Freq.Weekdays(listOf(9, 2))).freq)

    @Test fun weeklyTimesClamped() {
        assertEquals(Freq.Weekly(7), mk(freq = Freq.Weekly(99)).freq)
        assertEquals(Freq.Weekly(1), mk(freq = Freq.Weekly(0)).freq)
    }

    @Test fun sanitizeLogKeepsOnlyValidPairs() {
        val cleaned = mk(log = mapOf(
            "2026-01-01" to 1.0, "bad-key" to 1.0, "2026-01-03" to -4.0,
            "2026-01-04" to Habits.SKIP, "2026-01-05" to 0.0,
            "2026-01-06" to Double.NaN,
        )).log
        assertEquals(setOf("2026-01-01", "2026-01-04", "2026-01-05"), cleaned.keys)
        assertEquals(Habits.SKIP, cleaned["2026-01-04"]!!, 0.0)
        assertEquals(0.0, cleaned["2026-01-05"]!!, 0.0) // a literal 0 survives
    }

    // ----------------------------------------------------------
    //  day status
    // ----------------------------------------------------------

    private val bin = mk(log = mapOf("2026-01-02" to 1.0, "2026-01-03" to Habits.SKIP))

    @Test fun binaryStatuses() {
        assertEquals(DayStatus.DONE, Habits.statusOf(bin, "2026-01-02"))
        assertEquals(DayStatus.SKIP, Habits.statusOf(bin, "2026-01-03"))
        assertEquals(DayStatus.NONE, Habits.statusOf(bin, "2026-01-04"))
        assertEquals(DayStatus.PRESTART, Habits.statusOf(bin, "2025-12-31"))
    }

    @Test fun anEntryBeforeTheStartDateStillCounts() =
        assertEquals(DayStatus.DONE, Habits.statusOf(mk(log = mapOf("2025-12-25" to 1.0)), "2025-12-25"))

    // 2026-01-05 is a Monday, 2026-01-06 a Tuesday.
    private val wd = mk(freq = Freq.Weekdays(listOf(1, 3, 5)))

    @Test fun weekdayScheduling() {
        assertTrue(Habits.isScheduled(wd, "2026-01-05"))
        assertTrue(!Habits.isScheduled(wd, "2026-01-06"))
        assertEquals(DayStatus.UNSCHEDULED, Habits.statusOf(wd, "2026-01-06"))
        assertEquals(DayStatus.NONE, Habits.statusOf(wd, "2026-01-05"))
    }

    private val meas = mk(
        type = "measurable", target = 5.0, unit = "km",
        log = mapOf("2026-01-02" to 5.0, "2026-01-03" to 2.0,
            "2026-01-04" to 7.0, "2026-01-05" to 0.0),
    )

    @Test fun measurableStatuses() {
        assertEquals(DayStatus.DONE, Habits.statusOf(meas, "2026-01-02"))
        assertEquals(DayStatus.PARTIAL, Habits.statusOf(meas, "2026-01-03"))
        assertEquals(DayStatus.DONE, Habits.statusOf(meas, "2026-01-04"))
        assertEquals(DayStatus.NONE, Habits.statusOf(meas, "2026-01-05"))
    }

    @Test fun progressIsFractionalAndCapped() {
        assertEquals(0.4f, Habits.progressOf(meas, "2026-01-03"), 0.0001f)
        assertEquals(1f, Habits.progressOf(meas, "2026-01-04"), 0.0001f)
    }

    private val most = mk(
        type = "measurable", goalDir = "at_most", target = 0.0,
        log = mapOf("2026-01-02" to 0.0, "2026-01-03" to 3.0),
    )

    @Test fun atMostStatuses() {
        assertEquals(DayStatus.DONE, Habits.statusOf(most, "2026-01-02"))
        assertEquals(DayStatus.MISS, Habits.statusOf(most, "2026-01-03"))
        assertEquals(DayStatus.NONE, Habits.statusOf(most, "2026-01-04"))
    }

    @Test fun doneValueByType() {
        assertEquals(1.0, Habits.doneValue(bin), 0.0)
        assertEquals(5.0, Habits.doneValue(meas), 0.0)
    }

    // ----------------------------------------------------------
    //  streaks — daily
    // ----------------------------------------------------------

    private val today = "2026-01-10"

    @Test fun threeDaysIncludingToday() = assertEquals(
        3,
        Habits.computeStreaks(mk(log = mapOf(
            "2026-01-08" to 1.0, "2026-01-09" to 1.0, "2026-01-10" to 1.0)), today).current,
    )

    @Test fun todayUnfinishedDoesNotBreakTheStreak() = assertEquals(
        2,
        Habits.computeStreaks(mk(log = mapOf("2026-01-08" to 1.0, "2026-01-09" to 1.0)), today).current,
    )

    @Test fun aMissedYesterdayBreaksItButBestIsRemembered() {
        val h = mk(log = mapOf("2026-01-07" to 1.0, "2026-01-08" to 1.0))
        assertEquals(0, Habits.computeStreaks(h, today).current)
        assertEquals(2, Habits.computeStreaks(h, today).best)
    }

    @Test fun aSkipIsTransparent() = assertEquals(
        2,
        Habits.computeStreaks(mk(log = mapOf(
            "2026-01-08" to 1.0, "2026-01-09" to Habits.SKIP, "2026-01-10" to 1.0)), today).current,
    )

    @Test fun emptyHabitHasNoStreak() {
        val s = Habits.computeStreaks(mk(), today)
        assertEquals(0, s.current)
        assertEquals(0, s.best)
        assertEquals(StreakUnit.DAY, s.unit)
    }

    @Test fun prestartDaysAreTransparent() = assertEquals(
        2,
        Habits.computeStreaks(
            mk(startDate = "2026-01-09", log = mapOf("2026-01-09" to 1.0, "2026-01-10" to 1.0)),
            today,
        ).current,
    )

    // Jan 2026: 5th Mon, 7th Wed, 9th Fri, 10th Sat.
    @Test fun weekdayHabitIgnoresItsOffDays() = assertEquals(
        3,
        Habits.computeStreaks(mk(
            freq = Freq.Weekdays(listOf(1, 3, 5)),
            log = mapOf("2026-01-05" to 1.0, "2026-01-07" to 1.0, "2026-01-09" to 1.0),
        ), today).current,
    )

    @Test fun missingAScheduledWeekdayBreaksIt() = assertEquals(
        1,
        Habits.computeStreaks(mk(
            freq = Freq.Weekdays(listOf(1, 3, 5)),
            log = mapOf("2026-01-05" to 1.0, "2026-01-09" to 1.0),
        ), today).current,
    )

    @Test fun anExplicitMissTodayDoesBreakTheStreak() = assertEquals(
        0,
        Habits.computeStreaks(mk(
            type = "measurable", goalDir = "at_most", target = 0.0,
            log = mapOf("2026-01-09" to 0.0, "2026-01-10" to 2.0),
        ), today).current,
    )

    @Test fun bestStreakFoundInTheMiddle() {
        val h = mk(log = mapOf(
            "2026-01-01" to 1.0, "2026-01-02" to 1.0, "2026-01-03" to 1.0,
            "2026-01-04" to 1.0, "2026-01-05" to 1.0,
            "2026-01-09" to 1.0, "2026-01-10" to 1.0,
        ))
        assertEquals(5, Habits.computeStreaks(h, today).best)
        assertEquals(2, Habits.computeStreaks(h, today).current)
    }

    // ----------------------------------------------------------
    //  back-filled history
    // ----------------------------------------------------------
    // A habit created today whose past was filled in by hand. Started on the
    // 10th; ticked on the 5th, 6th, 8th, 9th and 10th — the 7th left empty.
    // Every one of these days is before the start date, so if "before the
    // start" were judged by startDate alone they would all be transparent and
    // five scattered ticks would report as a five-day streak.
    private val backfilled = mk(startDate = "2026-01-10", log = mapOf(
        "2026-01-05" to 1.0, "2026-01-06" to 1.0,
        "2026-01-08" to 1.0, "2026-01-09" to 1.0, "2026-01-10" to 1.0,
    ))

    @Test fun theEmptyDayIsAMissNotBeforeItStarted() =
        assertEquals(DayStatus.NONE, Habits.statusOf(backfilled, "2026-01-07"))

    @Test fun daysBeforeTheOldestEntryAreStillPrestart() =
        assertEquals(DayStatus.PRESTART, Habits.statusOf(backfilled, "2026-01-04"))

    @Test fun aGapInBackFilledHistoryBreaksTheStreak() {
        assertEquals(3, Habits.computeStreaks(backfilled, "2026-01-10").current)
        assertEquals(3, Habits.computeStreaks(backfilled, "2026-01-10").best)
    }

    @Test fun completionMeasuresBackFilledHistory() {
        val c = Habits.completion(backfilled, Habits.shiftKey("2026-01-10", -29), "2026-01-10")
        assertEquals(6, c.expected)
        assertEquals(5, c.done)
        assertTrue("rate should not be a misleading 100%", c.rate < 1f)
    }

    @Test fun weekdayBreakdownCoversTheBackFilledRange() {
        val wdStats = Habits.byWeekday(backfilled, Habits.shiftKey("2026-01-10", -29), "2026-01-10")
        assertEquals(6, wdStats.expected.count { it > 0 })
        assertEquals(5, wdStats.done.count { it > 0 })
    }

    @Test fun aNormalHabitStillIgnoresDaysBeforeItsStart() {
        val plain = mk(startDate = "2026-01-08", log = mapOf("2026-01-09" to 1.0, "2026-01-10" to 1.0))
        assertEquals(DayStatus.PRESTART, Habits.statusOf(plain, "2026-01-05"))
        assertEquals(2, Habits.computeStreaks(plain, "2026-01-10").current)
    }

    // ----------------------------------------------------------
    //  streaks — weekly count
    // ----------------------------------------------------------
    // Sunday-start weeks. 2026-01-04 is a Sunday, so the week of the 4th runs
    // 4th–10th and the week before is 2025-12-28 – 2026-01-03.

    @Test fun twoFullWeeks() {
        val h = mk(startDate = "2025-12-28", freq = Freq.Weekly(3), log = mapOf(
            "2025-12-29" to 1.0, "2025-12-31" to 1.0, "2026-01-02" to 1.0,
            "2026-01-05" to 1.0, "2026-01-06" to 1.0, "2026-01-07" to 1.0,
        ))
        val s = Habits.computeStreaks(h, today, 0)
        assertEquals(2, s.current)
        assertEquals(StreakUnit.WEEK, s.unit)
    }

    @Test fun theCurrentWeekIsStillOpen() = assertEquals(
        1,
        Habits.computeStreaks(mk(
            startDate = "2025-12-28", freq = Freq.Weekly(3),
            log = mapOf("2025-12-29" to 1.0, "2025-12-31" to 1.0,
                "2026-01-02" to 1.0, "2026-01-05" to 1.0),
        ), today, 0).current,
    )

    @Test fun aShortWeekBreaksTheWeeklyStreak() = assertEquals(
        1,
        Habits.computeStreaks(mk(
            startDate = "2025-12-21", freq = Freq.Weekly(3),
            log = mapOf("2025-12-22" to 1.0, "2026-01-05" to 1.0,
                "2026-01-06" to 1.0, "2026-01-07" to 1.0),
        ), today, 0).current,
    )

    // ----------------------------------------------------------
    //  completion
    // ----------------------------------------------------------

    @Test fun completionBasics() {
        val c = Habits.completion(mk(log = mapOf(
            "2026-01-01" to 1.0, "2026-01-02" to 1.0,
            "2026-01-03" to 0.0, "2026-01-04" to 1.0,
        )), "2026-01-01", "2026-01-04")
        assertEquals(3, c.done)
        assertEquals(4, c.expected)
        assertEquals(0.75f, c.rate, 0.0001f)
    }

    @Test fun aSkipLowersTheExpectationNotTheRate() {
        val c = Habits.completion(mk(log = mapOf(
            "2026-01-01" to 1.0, "2026-01-02" to Habits.SKIP, "2026-01-03" to 1.0,
        )), "2026-01-01", "2026-01-03")
        assertEquals(2, c.expected)
        assertEquals(1f, c.rate, 0.0001f)
    }

    @Test fun daysBeforeTheStartAreNotExpected() {
        val c = Habits.completion(
            mk(startDate = "2026-01-03", log = mapOf("2026-01-03" to 1.0)),
            "2026-01-01", "2026-01-03",
        )
        assertEquals(1, c.expected)
        assertEquals(1f, c.rate, 0.0001f)
    }

    @Test fun onlyScheduledWeekdaysAreExpected() = assertEquals(
        1,
        Habits.completion(
            mk(freq = Freq.Weekdays(listOf(1)), log = mapOf("2026-01-05" to 1.0)),
            "2026-01-05", "2026-01-11",
        ).expected,
    )

    @Test fun emptyRangeExpectsNothingAndIsNotNaN() {
        val c = Habits.completion(mk(), "2026-01-05", "2026-01-01")
        assertEquals(0, c.expected)
        assertEquals(0f, c.rate, 0.0001f)
    }

    @Test fun rateNeverExceedsOne() = assertTrue(
        Habits.completion(mk(
            freq = Freq.Weekdays(listOf(1)),
            log = mapOf("2026-01-05" to 1.0, "2026-01-06" to 1.0, "2026-01-07" to 1.0),
        ), "2026-01-05", "2026-01-11").rate <= 1f,
    )

    @Test fun totals() {
        assertEquals(3, Habits.totalDone(mk(log = mapOf(
            "2026-01-01" to 1.0, "2026-01-02" to 1.0,
            "2026-01-03" to 0.0, "2026-01-04" to 1.0))))
        assertEquals(14.0, Habits.totalValue(meas, "2026-01-01", "2026-01-31"), 0.0001)
        assertEquals(2.0, Habits.totalValue(mk(log = mapOf(
            "2026-01-01" to 1.0, "2026-01-02" to Habits.SKIP, "2026-01-03" to 1.0,
        )), "2026-01-01", "2026-01-31"), 0.0001)
    }

    // ----------------------------------------------------------
    //  aggregates
    // ----------------------------------------------------------

    @Test fun dayProgressExcludesArchivedAndUnscheduled() {
        // 2026-01-10 is a Saturday.
        val list = listOf(
            mk(name = "a", log = mapOf("2026-01-10" to 1.0)),
            mk(name = "b"),
            mk(name = "c", archived = true),
            mk(name = "d", freq = Freq.Weekdays(listOf(0))), // Sunday only
        )
        val dp = Habits.dayProgress(list, today)
        assertEquals(2, dp.due)
        assertEquals(1, dp.done)
        assertEquals(0.5f, dp.rate, 0.0001f)
        assertEquals(2, Habits.dueOn(list, today).size)
        assertEquals(0f, Habits.dayProgress(emptyList(), today).rate, 0.0001f)
    }

    @Test fun sortHabitsByOrderThenName() = assertEquals(
        listOf("A", "C", "B"),
        Habits.sortHabits(listOf(
            mk(name = "B", order = 1), mk(name = "A", order = 0), mk(name = "C", order = 0),
        )).map { it.name },
    )

    // ----------------------------------------------------------
    //  calendar grid
    // ----------------------------------------------------------

    @Test fun monthGridJan2026() {
        // Jan 2026 starts on a Thursday and has 31 days.
        val g = Habits.monthGrid(2026, 0, 0)
        assertTrue(g.all { it.size == 7 })
        assertEquals(4, g[0].count { it == null })
        assertEquals("2026-01-01", g[0][4])
        assertEquals(31, g.flatten().count { it != null })
    }

    @Test fun monthGridMondayStartShiftsPadding() =
        assertEquals(3, Habits.monthGrid(2026, 0, 1)[0].count { it == null })

    @Test fun februaryLeapAndNormal() {
        assertEquals(29, Habits.monthGrid(2028, 1, 0).flatten().count { it != null })
        assertEquals(28, Habits.monthGrid(2026, 1, 0).flatten().count { it != null })
    }
}
