package com.hanifedma.streak.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.streak.core.DayStatus
import com.hanifedma.streak.core.Habit
import com.hanifedma.streak.core.HabitType
import com.hanifedma.streak.core.Habits
import com.hanifedma.streak.i18n.DateNames
import com.hanifedma.streak.i18n.Lang
import com.hanifedma.streak.i18n.Strings.t
import com.hanifedma.streak.ui.UiState
import com.hanifedma.streak.ui.components.EmptyState
import com.hanifedma.streak.ui.components.ProgressRing
import com.hanifedma.streak.ui.theme.Streak

/**
 * The habit grid: habits down the side, days across the top, newest first.
 *
 * The name column is pinned and the day columns scroll horizontally, with one
 * shared scroll state so every row moves together with the header.
 *
 * Only the day columns actually on screen are composed (see [visibleWindow]).
 * A naive implementation composes every cell — 60 days × 15 rows is 900 —
 * which is exactly the kind of thing that makes a cheap phone stutter.
 */
@Composable
fun HabitsGridScreen(
    state: UiState,
    names: DateNames,
    contentPadding: PaddingValues,
    onToggle: (String, String) -> Unit,
    onCellLongPress: (Habit, String) -> Unit,
    onOpenStats: (String) -> Unit,
    onNeedMoreDays: () -> Unit,
    compact: Boolean,
) {
    val c = Streak.colors
    val lang = state.lang

    val habits = remember(state.habits, state.search) {
        val q = state.search.trim().lowercase()
        state.active.filter { q.isEmpty() || it.name.lowercase().contains(q) }
    }

    if (habits.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
            if (state.active.isEmpty()) {
                EmptyState("🌱", t(lang, "habits.empty"), t(lang, "habits.emptySub"))
            } else {
                EmptyState("🔍", t(lang, "stats.noData"), "")
            }
        }
        return
    }

    val cellW: Dp = if (compact) 32.dp else 36.dp
    val rowH: Dp = if (compact) 40.dp else 44.dp
    val nameW: Dp = if (compact) 140.dp else 200.dp

    val days = remember(state.today, state.gridDays) {
        List(state.gridDays) { Habits.shiftKey(state.today, -it) }
    }

    val hScroll = rememberScrollState()
    val vScroll = rememberLazyListState()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().padding(contentPadding)) {
        val cellPx = with(density) { cellW.toPx() }
        val viewportPx = with(density) { (maxWidth - nameW).toPx() }

        // Which day columns are actually visible, plus a small overscan so a
        // fling doesn't reveal blank space before the next frame composes.
        val window by remember(days.size, cellPx, viewportPx) {
            derivedStateOf { visibleWindow(hScroll.value, viewportPx, cellPx, days.size) }
        }

        // Load older days as the grid nears its right-hand end.
        LaunchedEffect(hScroll, days.size) {
            snapshotFlow { hScroll.value to hScroll.maxValue }
                .collect { (value, max) ->
                    if (max > 0 && value > max - viewportPx / 2) onNeedMoreDays()
                }
        }

        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(c.surface, RoundedCornerShape(14.dp))
                    .border(1.dp, c.border, RoundedCornerShape(14.dp))
            ) {
                Column {
                    // ---- header row ----
                    // heightIn, not height: the weekday and the day number are
                    // both sized in sp, so they grow with the system font
                    // setting. At a fixed 38.dp a large font scale pushed the
                    // second line past the bottom edge and sliced the day
                    // numbers in half.
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 38.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(nameW))
                        Row(
                            Modifier
                                .weight(1f)
                                .horizontalScroll(hScroll, enabled = false)
                        ) {
                            Spacer(Modifier.width(with(density) { (window.first * cellPx).toDp() }))
                            for (i in window) {
                                val key = days[i]
                                DayHeader(key, cellW, names, isToday = i == 0)
                            }
                            Spacer(Modifier.width(with(density) {
                                ((days.size - window.last - 1) * cellPx).toDp()
                            }))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))

                    // ---- rows ----
                    LazyColumn(state = vScroll, modifier = Modifier.fillMaxWidth()) {
                        items(habits, key = { it.id }) { habit ->
                            GridRow(
                                habit = habit, days = days, window = window,
                                cellW = cellW, rowH = rowH, nameW = nameW,
                                cellPx = cellPx, hScroll = hScroll,
                                today = state.today, weekStart = state.weekStart,
                                names = names,
                                onToggle = { key -> onToggle(habit.id, key) },
                                onLongPress = { key -> onCellLongPress(habit, key) },
                                onOpenStats = { onOpenStats(habit.id) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                t(lang, "habits.scrollHint"),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center, color = c.muted, fontSize = 12.sp,
            )
        }
    }
}

/** Column indices to compose, with one column of overscan on each side. */
private fun visibleWindow(scroll: Int, viewportPx: Float, cellPx: Float, count: Int): IntRange {
    if (cellPx <= 0f || count == 0) return 0..0
    val first = ((scroll / cellPx).toInt() - 1).coerceAtLeast(0)
    val visible = (viewportPx / cellPx).toInt() + 3
    val last = (first + visible).coerceAtMost(count - 1)
    return first..last
}

@Composable
private fun DayHeader(key: String, cellW: Dp, names: DateNames, isToday: Boolean) {
    val c = Streak.colors
    val dow = Habits.dowOf(key)
    val day = key.substring(8).trimStart('0')
    // Wraps its content rather than filling a fixed height, so the row above
    // can grow with it when the system font is scaled up.
    Column(
        Modifier.width(cellW).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            names.short[dow],
            fontSize = 10.sp,
            color = if (isToday) c.accent else if (dow == 0 || dow == 6) c.faint else c.muted,
        )
        Text(
            day,
            fontSize = 12.sp,
            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (isToday) c.accent else c.text,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridRow(
    habit: Habit,
    days: List<String>,
    window: IntRange,
    cellW: Dp,
    rowH: Dp,
    nameW: Dp,
    cellPx: Float,
    hScroll: androidx.compose.foundation.ScrollState,
    today: String,
    weekStart: Int,
    names: DateNames,
    onToggle: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onOpenStats: () -> Unit,
) {
    val c = Streak.colors
    val hc = c.habit(habit.color)
    val density = LocalDensity.current
    val streak = remember(habit, today, weekStart) {
        Habits.computeStreaks(habit, today, weekStart)
    }
    val score = remember(habit, today) { Habits.score(habit, today, 30) }

    Row(Modifier.fillMaxWidth().height(rowH), verticalAlignment = Alignment.CenterVertically) {
        // Pinned name column.
        Row(
            Modifier
                .width(nameW)
                .fillMaxSize()
                .background(c.surface)
                .clickable(onClick = onOpenStats)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(score, size = 20.dp, stroke = 3.dp, color = hc)
            Spacer(Modifier.width(8.dp))
            Text(
                habit.name,
                modifier = Modifier.weight(1f),
                color = hc, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (streak.current > 0) {
                Text(
                    streak.current.toString(),
                    color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Box(Modifier.width(1.dp).fillMaxSize().background(c.border))

        Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
            Spacer(Modifier.width(with(density) { (window.first * cellPx).toDp() }))
            for (i in window) {
                val key = days[i]
                GridCell(habit, key, cellW, rowH, hc, names, onToggle, onLongPress)
            }
            Spacer(Modifier.width(with(density) {
                ((days.size - window.last - 1) * cellPx).toDp()
            }))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCell(
    habit: Habit,
    key: String,
    cellW: Dp,
    rowH: Dp,
    hc: androidx.compose.ui.graphics.Color,
    names: DateNames,
    onToggle: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    val c = Streak.colors
    val status = Habits.statusOf(habit, key)
    val value = Habits.entryOf(habit, key)

    val (glyph, color, weight) = when (status) {
        DayStatus.DONE -> Triple("✔", hc, FontWeight.Bold)
        DayStatus.SKIP -> Triple("–", c.faint, FontWeight.Normal)
        DayStatus.PARTIAL -> Triple(names.compact(value ?: 0.0), hc, FontWeight.Bold)
        DayStatus.MISS -> Triple("✕", c.danger, FontWeight.Bold)
        DayStatus.UNSCHEDULED, DayStatus.PRESTART -> Triple("·", c.cellOff, FontWeight.Normal)
        else -> Triple("✕", c.cellEmpty, FontWeight.Normal)
    }

    Box(
        Modifier
            .width(cellW).height(rowH)
            .combinedClickable(
                onClick = {
                    // Measurable habits need a number, so a plain tap opens the
                    // sheet instead of guessing an amount.
                    if (habit.type == HabitType.MEASURABLE) onLongPress(key) else onToggle(key)
                },
                onLongClick = { onLongPress(key) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = color,
            fontWeight = weight,
            fontSize = when (status) {
                DayStatus.DONE -> 17.sp
                DayStatus.PARTIAL -> 11.sp
                else -> 15.sp
            },
            maxLines = 1,
        )
    }
}
