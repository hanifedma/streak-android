package com.hanifedma.streak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.streak.core.DayStatus
import com.hanifedma.streak.core.Habit
import com.hanifedma.streak.core.HabitType
import com.hanifedma.streak.core.Habits
import com.hanifedma.streak.core.StreakUnit
import com.hanifedma.streak.i18n.DateNames
import com.hanifedma.streak.i18n.Lang
import com.hanifedma.streak.i18n.Strings.t
import com.hanifedma.streak.ui.components.EmptyState
import com.hanifedma.streak.ui.components.HabitDot
import com.hanifedma.streak.ui.components.WeekdayBars
import com.hanifedma.streak.ui.theme.Streak
import java.util.Calendar

/**
 * Stats for one habit: streaks, 30-day completion, a month heatmap and a
 * by-weekday breakdown.
 *
 * The same composable serves both layouts — a side pane on a tablet and a
 * full-screen sheet on a phone — so the two can never drift apart.
 */
@Composable
fun StatsPane(
    habit: Habit?,
    today: String,
    weekStart: Int,
    lang: Lang,
    names: DateNames,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onEdit: (Habit) -> Unit,
) {
    val c = Streak.colors
    if (habit == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("📈", t(lang, "stats.title"), t(lang, "stats.noData"))
        }
        return
    }

    val hc = c.habit(habit.color)
    val start = remember(today) { Habits.parseKey(today) }
    var year by remember(habit.id) { mutableStateOf(start.get(Calendar.YEAR)) }
    var month by remember(habit.id) { mutableStateOf(start.get(Calendar.MONTH)) }

    val avoid = Habits.isAvoid(habit)
    val streak = remember(habit, today, weekStart) { Habits.computeStreaks(habit, today, weekStart) }
    val c30 = remember(habit, today) {
        Habits.completion(habit, Habits.shiftKey(today, -29), today, today)
    }
    // For an avoid habit almost every day is a success, so counting them says
    // very little — how many times it was broken is the number worth a tile.
    val total = remember(habit, today) {
        if (avoid) Habits.totalMissed(habit, today) else Habits.totalDone(habit, today)
    }
    val wd = remember(habit, today) {
        Habits.byWeekday(habit, Habits.shiftKey(today, -83), today, today) // 12 weeks
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HabitDot(hc, size = 12.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                habit.name,
                modifier = Modifier.weight(1f, fill = false),
                color = hc, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            // Says which way round to read the panel: on an avoid habit a full
            // month of colour means nothing went wrong, not that something was
            // done 31 times.
            if (avoid) {
                Spacer(Modifier.width(8.dp))
                Text(
                    t(lang, "badge.avoid"),
                    color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(c.surface2, RoundedCornerShape(999.dp))
                        .border(1.dp, c.border, RoundedCornerShape(999.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            scheduleText(habit, lang, names) + " · " +
                t(lang, "stats.since", "date" to habit.startDate),
            color = c.muted, fontSize = 13.sp,
        )
        if (habit.type == HabitType.MEASURABLE) {
            Spacer(Modifier.height(4.dp))
            Text(
                t(lang, "stats.sum") + ": " +
                    names.num(Habits.totalValue(habit, habit.firstDay, today)) +
                    if (habit.unit.isNotBlank()) " ${habit.unit}" else "",
                color = c.muted, fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(18.dp))

        // ---- tiles ----
        val unitKey = if (streak.unit == StreakUnit.WEEK) "week" else "day"
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("🔥", t(lang, "stats.current"), streak.current.toString(),
                names.unitText(lang, unitKey, streak.current), Modifier.weight(1f))
            Tile("🏆", t(lang, "stats.best"), streak.best.toString(),
                names.unitText(lang, unitKey, streak.best), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("📈", t(lang, "stats.rate30"), "${(c30.rate * 100).toInt()}", "%", Modifier.weight(1f))
            Tile(
                if (avoid) "✕" else "✔",
                t(lang, if (avoid) "stats.slips" else "stats.total"),
                total.toString(),
                names.unitText(lang, "day", total), Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(22.dp))

        // ---- calendar ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconSquare("‹") {
                val cal = Calendar.getInstance().apply { clear(); set(year, month, 1); add(Calendar.MONTH, -1) }
                year = cal.get(Calendar.YEAR); month = cal.get(Calendar.MONTH)
            }
            Text(
                names.monthTitle(year, month),
                modifier = Modifier.weight(1f),
                color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconSquare("›") {
                val cal = Calendar.getInstance().apply { clear(); set(year, month, 1); add(Calendar.MONTH, 1) }
                year = cal.get(Calendar.YEAR); month = cal.get(Calendar.MONTH)
            }
        }
        Spacer(Modifier.height(12.dp))

        val order = Habits.weekdayOrder(weekStart)
        Row(Modifier.fillMaxWidth()) {
            order.forEach { d ->
                Text(
                    names.narrow[d],
                    modifier = Modifier.weight(1f),
                    color = c.muted, fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val weeks = remember(year, month, weekStart) { Habits.monthGrid(year, month, weekStart) }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { key ->
                    Box(Modifier.weight(1f).padding(vertical = 2.dp)) {
                        if (key != null) CalendarCell(habit, key, today, hc, names)
                        else Spacer(Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---- weekday breakdown ----
        Text(t(lang, "stats.byWeekday"), color = c.muted, fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        WeekdayBars(
            labels = order.map { names.narrow[it] },
            percents = order.map { d ->
                if (wd.expected[d] > 0) (wd.done[d] * 100f / wd.expected[d]).toInt() else 0
            },
            color = hc,
        )

        Spacer(Modifier.height(22.dp))
        OutlinedButton(onClick = { onEdit(habit) }, modifier = Modifier.fillMaxWidth()) {
            Text(t(lang, "stats.edit"))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Tile(icon: String, label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val c = Streak.colors
    Column(
        modifier
            .background(c.surface, RoundedCornerShape(14.dp))
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(icon, fontSize = 15.sp)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(4.dp))
            Text(unit, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(label, color = c.muted, fontSize = 12.sp)
    }
}

@Composable
private fun CalendarCell(habit: Habit, key: String, today: String, hc: Color, names: DateNames) {
    val c = Streak.colors
    val future = Habits.diffDays(key, today) > 0
    val status = if (future) null else Habits.statusOf(habit, key, today)
    val isToday = key == today

    val bg = when (status) {
        DayStatus.DONE -> hc
        DayStatus.PARTIAL -> hc.copy(alpha = 0.5f)
        DayStatus.SKIP -> c.surface3
        DayStatus.MISS -> c.danger.copy(alpha = 0.18f)
        DayStatus.NONE -> c.surface2
        else -> Color.Transparent
    }
    val fg = when (status) {
        DayStatus.DONE, DayStatus.PARTIAL -> c.bg
        DayStatus.MISS -> c.danger
        DayStatus.NONE -> c.muted
        else -> c.faint
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(bg, RoundedCornerShape(7.dp))
            .then(
                if (isToday) Modifier.border(2.dp, c.accent, RoundedCornerShape(7.dp))
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            key.substring(8).trimStart('0'),
            color = fg, fontSize = 12.sp,
            fontWeight = if (status == DayStatus.DONE) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun IconSquare(label: String, onClick: () -> Unit) {
    val c = Streak.colors
    Box(
        Modifier
            .size(40.dp)
            .background(c.surface2, RoundedCornerShape(11.dp))
            .border(1.dp, c.border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = c.text, fontSize = 16.sp) }
}
