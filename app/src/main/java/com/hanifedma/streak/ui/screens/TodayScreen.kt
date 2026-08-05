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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import com.hanifedma.streak.ui.UiState
import com.hanifedma.streak.ui.components.EmptyState
import com.hanifedma.streak.ui.components.HabitDot
import com.hanifedma.streak.ui.components.ProgressRing
import com.hanifedma.streak.ui.components.StreakCard
import com.hanifedma.streak.ui.components.ThinProgress
import com.hanifedma.streak.ui.theme.Streak

/**
 * Today: a checklist of what is due, with a progress ring. The screen people
 * open most, so it is the launch tab and everything on it is one tap away.
 */
@Composable
fun TodayScreen(
    state: UiState,
    names: DateNames,
    contentPadding: PaddingValues,
    onToggle: (String) -> Unit,
    onBump: (String, Int) -> Unit,
    onOpenStats: (String) -> Unit,
    onEditValue: (Habit) -> Unit,
    onNewHabit: () -> Unit,
) {
    val c = Streak.colors
    val lang = state.lang
    val key = state.today
    val all = state.active
    val due = Habits.dueOn(all, key)
    val progress = Habits.dayProgress(all, key)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "head") {
            StreakCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            names.longDate(key),
                            color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                all.isEmpty() -> t(lang, "today.empty")
                                progress.due == 0 -> t(lang, "today.none")
                                progress.done == progress.due -> t(lang, "today.allDone")
                                else -> t(lang, "today.progress",
                                    "done" to progress.done, "due" to progress.due)
                            },
                            color = c.muted, fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    ProgressRing(
                        progress = progress.rate,
                        label = "${(progress.rate * 100).toInt()}%",
                    )
                }
            }
        }

        if (all.isEmpty() || due.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    emoji = if (all.isEmpty()) "🌱" else "🛌",
                    title = if (all.isEmpty()) t(lang, "today.empty") else t(lang, "today.nothingDue"),
                    subtitle = if (all.isEmpty()) t(lang, "today.emptySub")
                        else t(lang, "today.nothingDueSub"),
                ) {
                    Button(
                        onClick = onNewHabit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.accent, contentColor = c.accentContrast,
                        ),
                    ) { Text("＋  " + t(lang, "habits.add"), fontWeight = FontWeight.SemiBold) }
                }
            }
        } else {
            items(due, key = { it.id }) { habit ->
                TodayRow(
                    habit = habit, dayKey = key, lang = lang, names = names,
                    todayKey = state.today, weekStart = state.weekStart,
                    onToggle = { onToggle(habit.id) },
                    onBump = { d -> onBump(habit.id, d) },
                    onOpenStats = { onOpenStats(habit.id) },
                    onEditValue = { onEditValue(habit) },
                )
            }
            item(key = "add") {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onNewHabit,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.accent, contentColor = c.accentContrast,
                    ),
                ) { Text("＋  " + t(lang, "habits.add"), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun TodayRow(
    habit: Habit,
    dayKey: String,
    lang: Lang,
    names: DateNames,
    todayKey: String,
    weekStart: Int,
    onToggle: () -> Unit,
    onBump: (Int) -> Unit,
    onOpenStats: () -> Unit,
    onEditValue: () -> Unit,
) {
    val c = Streak.colors
    val hc = c.habit(habit.color)
    val status = Habits.statusOf(habit, dayKey)
    val done = status == DayStatus.DONE
    val skipped = status == DayStatus.SKIP
    val streak = Habits.computeStreaks(habit, todayKey, weekStart)
    val recorded = Habits.hasEntry(habit, dayKey)
    val value = Habits.entryOf(habit, dayKey)

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (done) c.surface2 else c.surface, RoundedCornerShape(14.dp))
            .border(1.dp, if (done) hc else c.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenStats)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HabitDot(hc)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                habit.name,
                color = hc, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sub = when {
                    skipped -> t(lang, "cell.skip")
                    habit.type == HabitType.MEASURABLE -> {
                        // An unrecorded day shows "–", never "0". For an
                        // at-most habit a recorded 0 is a success and a blank
                        // day is not — they must never look the same.
                        val shown = if (recorded) names.num(value!!) else "–"
                        "$shown / ${names.num(habit.target)}" +
                            if (habit.unit.isNotBlank()) " ${habit.unit}" else ""
                    }
                    else -> scheduleText(habit, lang, names)
                }
                Text(sub, color = c.muted, fontSize = 12.5.sp)
                if (streak.current > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "🔥 ${streak.current} " + names.unitText(
                            lang,
                            if (streak.unit == StreakUnit.WEEK) "week" else "day",
                            streak.current,
                        ),
                        color = c.muted, fontSize = 12.5.sp,
                    )
                }
            }
            if (habit.type == HabitType.MEASURABLE && !skipped) {
                Spacer(Modifier.height(6.dp))
                ThinProgress(Habits.progressOf(habit, dayKey), hc)
            }
        }
        Spacer(Modifier.width(10.dp))

        if (habit.type == HabitType.MEASURABLE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−") { onBump(-1) }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        // Minimums, not fixed: a four-digit value at a large
                        // font scale needs more than 58.dp, and clipping the
                        // number would misreport the day's progress.
                        .widthIn(min = 58.dp).heightIn(min = 36.dp)
                        .background(c.surface, RoundedCornerShape(9.dp))
                        .border(1.dp, c.border, RoundedCornerShape(9.dp))
                        .clickable(onClick = onEditValue)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (recorded) names.num(value!!) else "–",
                        color = hc, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(4.dp))
                StepButton("+") { onBump(1) }
            }
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .background(if (done) hc else c.surface2, RoundedCornerShape(12.dp))
                    .border(2.dp, if (done) hc else c.borderStrong, RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (skipped) "–" else "✔",
                    color = when {
                        done -> c.bg
                        skipped -> c.muted
                        else -> Streak.colors.cellEmpty
                    },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val c = Streak.colors
    Box(
        Modifier
            .size(36.dp)
            .background(c.surface2, RoundedCornerShape(9.dp))
            .border(1.dp, c.borderStrong, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = c.text, fontSize = 17.sp) }
}

/** Human description of a habit's schedule. */
fun scheduleText(habit: Habit, lang: Lang, names: DateNames): String = when (val f = habit.freq) {
    is com.hanifedma.streak.core.Freq.Weekly -> t(lang, "editor.freq.weekly", "n" to f.times)
    is com.hanifedma.streak.core.Freq.Weekdays -> f.days.joinToString(", ") { names.short[it] }
    else -> t(lang, "editor.freq.daily")
}
