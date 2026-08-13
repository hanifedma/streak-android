package com.hanifedma.streak.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.hanifedma.streak.core.Habit
import com.hanifedma.streak.core.HabitType
import com.hanifedma.streak.core.Habits
import com.hanifedma.streak.i18n.DateNames
import com.hanifedma.streak.i18n.Lang
import com.hanifedma.streak.i18n.Strings.t
import com.hanifedma.streak.ui.UiState
import com.hanifedma.streak.ui.components.HabitDot
import com.hanifedma.streak.ui.components.SectionLabel
import com.hanifedma.streak.ui.theme.Streak
import kotlinx.coroutines.isActive

/**
 * The sheet a grid cell opens: record an amount, mark done, skip, or clear.
 *
 * Skip is deliberately first-class rather than hidden — it is what keeps a
 * streak honest through illness and holidays.
 */
@Composable
fun CellSheet(
    habit: Habit,
    dayKey: String,
    lang: Lang,
    names: DateNames,
    onSet: (Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Streak.colors
    val existing = Habits.entryOf(habit, dayKey)
    var amount by remember(habit.id, dayKey) {
        mutableStateOf(
            if (existing == null || existing == Habits.SKIP) ""
            else trimAmount(existing)
        )
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HabitDot(c.habit(habit.color))
            Spacer(Modifier.width(10.dp))
            Text(
                habit.name,
                modifier = Modifier.weight(1f),
                color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(names.longDate(dayKey), color = c.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))

        if (habit.type == HabitType.MEASURABLE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { s -> amount = s.filter { it.isDigit() || it == '.' } },
                    label = { Text(t(lang, "cell.setValue")) },
                    suffix = if (habit.unit.isNotBlank()) {
                        { Text(habit.unit, color = c.muted) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        // Clearing the field means "no entry", not "zero".
                        onSet(if (amount.isBlank()) null else amount.toDoubleOrNull())
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.accent, contentColor = c.accentContrast,
                    ),
                ) { Text(t(lang, "cell.save")) }
            }
            Spacer(Modifier.height(12.dp))
        }

        val avoid = Habits.isAvoid(habit)
        SheetAction("✔  " + t(lang, if (avoid) "cell.markKept" else "cell.markDone")) {
            onSet(Habits.doneValue(habit))
        }
        // Only a yes/no avoid habit gets a one-tap "I slipped": a measurable
        // one has no single number that counts as a breach, so it asks for the
        // amount in the field above instead.
        if (avoid && habit.type == HabitType.BINARY) {
            SheetAction("✕  " + t(lang, "cell.markBroke"), danger = true) { onSet(Habits.BROKE) }
        }
        SheetAction("–  " + t(lang, "cell.markSkip")) { onSet(Habits.SKIP) }
        // On an avoid habit, clearing a day hands it back to "kept" — the good
        // outcome — so it is neither dangerous nor another ✕ beside the slip.
        SheetAction(
            (if (avoid) "↺  " else "✕  ") + t(lang, "cell.clear"),
            danger = !avoid,
        ) { onSet(null) }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(t(lang, "common.cancel"), color = c.muted)
        }
    }
}

@Composable
private fun SheetAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = Streak.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Text(label, color = if (danger) c.danger else c.text, fontSize = 15.sp)
    }
}

/** Settings: display preferences and the data tools. */
@Composable
fun SettingsSheet(
    state: UiState,
    lang: Lang,
    onDark: (Boolean) -> Unit,
    onLang: (Lang) -> Unit,
    onWeekStart: (Int) -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImport: () -> Unit,
    onClearAll: () -> Unit,
    onAddWidget: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val c = Streak.colors
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(t(lang, "settings.title"), color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))

        SectionLabel(t(lang, "settings.display"))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t(lang, "settings.theme"), color = c.text, modifier = Modifier.weight(1f))
            Text(
                t(lang, if (state.dark) "theme.dark" else "theme.light"),
                color = c.muted, fontSize = 13.sp,
            )
            Spacer(Modifier.width(8.dp))
            Switch(checked = state.dark, onCheckedChange = onDark)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t(lang, "settings.language"), color = c.text, modifier = Modifier.weight(1f))
            PillGroup(
                options = listOf(Lang.KO to "한국어", Lang.EN to "English"),
                selected = lang,
                onSelect = onLang,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(t(lang, "settings.weekStart"), color = c.text)
        Spacer(Modifier.height(8.dp))
        PillGroup(
            options = listOf(0 to t(lang, "settings.weekStart.0"), 1 to t(lang, "settings.weekStart.1")),
            selected = state.weekStart,
            onSelect = onWeekStart,
            fill = true,
        )

        Spacer(Modifier.height(22.dp))
        SectionLabel(t(lang, "settings.data"))
        Text(
            t(lang, "settings.storage") + ": " +
                t(lang, if (state.mode == "cloud") "settings.storage.cloud" else "settings.storage.local"),
            color = c.muted, fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onExportJson, modifier = Modifier.fillMaxWidth()) {
            Text(t(lang, "settings.export"))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
            Text(t(lang, "settings.exportCsv"))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text(t(lang, "settings.import"))
        }
        Spacer(Modifier.height(6.dp))
        Text(t(lang, "settings.importHint"), color = c.muted, fontSize = 12.sp)

        Spacer(Modifier.height(22.dp))
        if (onAddWidget != null) {
            SectionLabel(t(lang, "settings.addWidget"))
            OutlinedButton(onClick = onAddWidget, modifier = Modifier.fillMaxWidth()) {
                Text("▦  " + t(lang, "settings.addWidget"))
            }
            Spacer(Modifier.height(6.dp))
            Text(t(lang, "settings.addWidgetHint"), color = c.muted, fontSize = 12.sp)
            Spacer(Modifier.height(22.dp))
        }
        SectionLabel(t(lang, "settings.danger"))
        TextButton(onClick = onClearAll) {
            Text(t(lang, "settings.clear"), color = c.danger)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.accent, contentColor = c.accentContrast,
            ),
        ) { Text(t(lang, "settings.done")) }
    }
}

@Composable
private fun <T> PillGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    fill: Boolean = false,
) {
    val c = Streak.colors
    Row(
        (if (fill) Modifier.fillMaxWidth() else Modifier).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                (if (fill) Modifier.weight(1f) else Modifier)
                    .fillMaxHeight()
                    // A floor, not a fixed height: labels are in sp and grow
                    // with the system font setting.
                    .heightIn(min = 38.dp)
                    .background(if (on) c.accent else c.surface2, RoundedCornerShape(10.dp))
                    .border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(10.dp))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) c.accentContrast else c.muted,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Archived habits: restore or delete for good. */
@Composable
fun ArchivedSheet(
    archived: List<Habit>,
    lang: Lang,
    onUnarchive: (Habit) -> Unit,
    onDelete: (Habit) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Streak.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text(t(lang, "archived.title"), color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(t(lang, "archived.hint"), color = c.muted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        if (archived.isEmpty()) {
            Text(t(lang, "archived.empty"), color = c.muted)
        } else {
            LazyColumn(
                Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(archived, key = { it.id }) { h ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(c.surface, RoundedCornerShape(11.dp))
                            .border(1.dp, c.border, RoundedCornerShape(11.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HabitDot(c.habit(h.color))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            h.name,
                            modifier = Modifier.weight(1f),
                            color = c.habit(h.color), fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { onUnarchive(h) }) {
                            Text(t(lang, "editor.unarchive"), fontSize = 13.sp)
                        }
                        TextButton(onClick = { onDelete(h) }) {
                            Text(t(lang, "editor.delete"), color = c.danger, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.accent, contentColor = c.accentContrast,
            ),
        ) { Text(t(lang, "settings.done")) }
    }
}

/** About. */
@Composable
fun AboutSheet(lang: Lang, version: String, onDismiss: () -> Unit) {
    val c = Streak.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text(t(lang, "about.title"), color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(t(lang, "about.p1"), color = c.muted, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Text(t(lang, "about.p2"), color = c.muted, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Text("${t(lang, "about.version")}: $version", color = c.muted, fontSize = 13.sp)
        Text("${t(lang, "about.web")}: hanifedma.com/streak/", color = c.muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.accent, contentColor = c.accentContrast,
            ),
        ) { Text(t(lang, "common.ok")) }
    }
}

/**
 * Reorder: press and hold a row and drag it anywhere, or nudge it with the
 * arrows.
 *
 * The whole row is the grab area — there is no separate handle to hunt for.
 *
 * Dragging works on a local copy of the list rather than on the store. A write
 * has to travel through the repository and, when signed in, bounce off
 * Firestore before it comes back as state; a finger is far quicker than that,
 * so driving the rows straight from shared state would leave them lagging
 * behind the drag. The copy is resynced from the store whenever the *set* of
 * habits changes, but never merely because an update echoed back the order we
 * just wrote — that would snap the rows to a stale position for a frame.
 */
@Composable
fun ReorderSheet(
    habits: List<Habit>,
    lang: Lang,
    onCommit: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Streak.colors
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    var order by remember { mutableStateOf(habits) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var autoScroll by remember { mutableFloatStateOf(0f) }

    // Adopt store updates only between drags, so a snapshot arriving mid-drag
    // cannot pull the row out from under the finger.
    LaunchedEffect(habits, draggingId) {
        if (draggingId != null) return@LaunchedEffect
        val next = if (order.map { it.id }.toSet() == habits.map { it.id }.toSet()) {
            val byId = habits.associateBy { it.id }
            order.mapNotNull { byId[it.id] } // same habits: keep what is on screen
        } else {
            habits // added, deleted or archived elsewhere: take the store's word
        }
        if (next != order) order = next
    }

    /**
     * Re-read where the dragged row now sits: how hard to auto-scroll, and
     * whether it has travelled far enough to change places with a neighbour.
     */
    fun track() {
        val id = draggingId ?: return
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo

        // A swap changes `order` now but is only laid out on the next frame, and
        // both the drag callback and the scroll loop can run in between. Measure
        // against geometry that still describes the previous order and the row
        // looks a whole slot out of place, which swaps it straight back. So wait
        // until the rows on screen agree with the order being dragged.
        val onScreen = visible.map { it.key }
        if (onScreen != order.map { it.id }.filter { it in onScreen }) return

        val self = visible.firstOrNull { it.key == id }
        if (self == null) {
            autoScroll = 0f // lost the row: better still than scrolling blind
            return
        }
        val top = self.offset + dragOffset
        val bottom = top + self.size

        autoScroll = when {
            top < info.viewportStartOffset ->
                (top - info.viewportStartOffset).coerceAtLeast(-AUTO_SCROLL_MAX)
            bottom > info.viewportEndOffset ->
                (bottom - info.viewportEndOffset).coerceAtMost(AUTO_SCROLL_MAX)
            else -> 0f
        }

        // Swap once the row's middle is over a neighbour, then take that
        // neighbour's slot out of the offset so the row does not jump.
        val middle = top + self.size / 2f
        val target = info.visibleItemsInfo.firstOrNull {
            it.key != id && middle >= it.offset && middle <= it.offset + it.size
        } ?: return
        val from = order.indexOfFirst { it.id == id }
        val to = order.indexOfFirst { it.id == target.key }
        if (from >= 0 && to >= 0 && from != to) {
            order = order.toMutableList().apply { add(to, removeAt(from)) }
            dragOffset += self.offset - target.offset
        }
    }

    // Held near an edge, the list keeps scrolling under the row — and keeps
    // reordering, so a still finger at the edge is not a dead one. Every pixel
    // the list moves is added back to the offset to keep the row on the finger.
    LaunchedEffect(draggingId) {
        if (draggingId == null) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { } // layoutInfo is settled at the top of the frame
            track()
            val step = autoScroll
            if (step != 0f) dragOffset += listState.scrollBy(step)
        }
    }

    fun endDrag() {
        draggingId = null
        dragOffset = 0f
        autoScroll = 0f
        onCommit(order.map { it.id })
    }

    fun move(id: String, delta: Int) {
        val from = order.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to < 0 || to > order.lastIndex) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        onCommit(order.map { it.id })
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text(t(lang, "habits.reorder"), color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(t(lang, "reorder.hint"), color = c.muted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(order, key = { _, h -> h.id }) { index, h ->
                val dragging = h.id == draggingId
                Row(
                    Modifier
                        // The row being dragged is placed by hand; letting the
                        // animation fight the finger would make it rubber-band.
                        .animateItem(
                            fadeInSpec = null,
                            placementSpec = if (dragging) null else tween(180),
                            fadeOutSpec = null,
                        )
                        .zIndex(if (dragging) 1f else 0f)
                        // Read inside the layer block, so following the finger
                        // costs a redraw and not a recomposition.
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .fillMaxWidth()
                        .background(
                            if (dragging) c.surface3 else c.surface,
                            RoundedCornerShape(11.dp),
                        )
                        .border(
                            1.dp,
                            if (dragging) c.borderStrong else c.border,
                            RoundedCornerShape(11.dp),
                        )
                        .pointerInput(h.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = h.id
                                    dragOffset = 0f
                                    autoScroll = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = { endDrag() },
                                // A cancelled gesture still leaves the rows in
                                // their new places, so save that, rather than
                                // showing an order the store does not have.
                                onDragCancel = { endDrag() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    track()
                                },
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HabitDot(c.habit(h.color))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        h.name,
                        modifier = Modifier.weight(1f),
                        color = c.habit(h.color), fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    ArrowButton("↑", t(lang, "reorder.up"), enabled = index > 0) {
                        move(h.id, -1)
                    }
                    Spacer(Modifier.width(6.dp))
                    ArrowButton("↓", t(lang, "reorder.down"), enabled = index < order.size - 1) {
                        move(h.id, 1)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.accent, contentColor = c.accentContrast,
            ),
        ) { Text(t(lang, "reorder.done")) }
    }
}

/** Pixels per frame, the ceiling for edge auto-scroll while dragging. */
private const val AUTO_SCROLL_MAX = 18f

@Composable
private fun ArrowButton(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = Streak.colors
    Box(
        Modifier
            // Min, not fixed: the glyph is sp and has to be free to grow with
            // the user's font scale instead of being clipped.
            .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
            .background(c.surface2, RoundedCornerShape(9.dp))
            .border(1.dp, c.border, RoundedCornerShape(9.dp))
            .then(
                if (enabled) {
                    Modifier.clickable(onClickLabel = description, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) c.text else c.faint, fontSize = 15.sp)
    }
}

private fun trimAmount(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
