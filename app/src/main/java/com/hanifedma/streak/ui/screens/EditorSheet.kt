package com.hanifedma.streak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.streak.core.Freq
import com.hanifedma.streak.core.GoalDir
import com.hanifedma.streak.core.Habit
import com.hanifedma.streak.core.HabitFactory
import com.hanifedma.streak.core.HabitType
import com.hanifedma.streak.core.Habits
import com.hanifedma.streak.core.Polarity
import com.hanifedma.streak.i18n.DateNames
import com.hanifedma.streak.i18n.Lang
import com.hanifedma.streak.i18n.Strings.t
import com.hanifedma.streak.ui.components.SectionLabel
import com.hanifedma.streak.ui.theme.Streak

/**
 * Create or edit a habit: name, colour, type, goal, schedule, start date.
 *
 * Everything is held in local state and only committed on Save, so backing out
 * of a half-finished edit leaves the stored habit untouched.
 */
@Composable
fun EditorSheet(
    initial: Habit,
    isNew: Boolean,
    lang: Lang,
    names: DateNames,
    today: String,
    weekStart: Int,
    onSave: (Habit) -> Unit,
    onDelete: (Habit) -> Unit,
    onArchive: (Habit, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Streak.colors

    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableStateOf(initial.color) }
    var type by remember { mutableStateOf(initial.type) }
    var polarity by remember { mutableStateOf(initial.polarity) }
    var goalDir by remember { mutableStateOf(initial.goalDir) }
    var target by remember { mutableStateOf(trimNumber(initial.target)) }
    var unit by remember { mutableStateOf(initial.unit) }
    var freqKind by remember {
        mutableStateOf(
            when (initial.freq) {
                is Freq.Weekdays -> "weekdays"
                is Freq.Weekly -> "weekly"
                else -> "daily"
            }
        )
    }
    var days by remember {
        mutableStateOf((initial.freq as? Freq.Weekdays)?.days?.toSet() ?: emptySet())
    }
    var times by remember {
        mutableStateOf(((initial.freq as? Freq.Weekly)?.times ?: 3).toString())
    }
    var startDate by remember { mutableStateOf(initial.startDate) }
    var error by remember { mutableStateOf<String?>(null) }

    val avoid = polarity == Polarity.AVOID

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            t(lang, if (isNew) "editor.new" else "editor.edit"),
            color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))

        SectionLabel(t(lang, "editor.name"))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= Habits.MAX_NAME_LEN) name = it },
            placeholder = {
                Text(t(lang, if (avoid) "editor.namePhAvoid" else "editor.namePh"), color = c.faint)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        SectionLabel(t(lang, "editor.color"))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Habits.COLORS.forEach { name0 ->
                val hc = c.habit(name0)
                Box(
                    Modifier
                        .weight(1f)
                        .size(28.dp)
                        .background(hc, CircleShape)
                        .border(
                            width = if (color == name0) 3.dp else 1.dp,
                            color = if (color == name0) c.text else c.border,
                            shape = CircleShape,
                        )
                        .clickable { color = name0 }
                )
            }
        }

        // Which way round the habit works comes before how it is measured: it
        // changes what every control below it means.
        Spacer(Modifier.height(16.dp))
        SectionLabel(t(lang, "editor.polarity"))
        SegmentedRow(
            options = listOf(
                Polarity.DO to t(lang, "editor.polarity.do"),
                Polarity.AVOID to t(lang, "editor.polarity.avoid"),
            ),
            selected = polarity,
            onSelect = { picked ->
                polarity = picked
                // Keep the form out of any state the domain layer would
                // silently rewrite on save: an avoid habit is always a
                // ceiling, and never a weekly quota.
                if (picked == Polarity.AVOID) {
                    goalDir = GoalDir.AT_MOST
                    if (freqKind == "weekly") freqKind = "daily"
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            t(lang, if (avoid) "editor.polarity.avoidHint" else "editor.polarity.doHint"),
            color = c.muted, fontSize = 12.sp, lineHeight = 16.sp,
        )

        Spacer(Modifier.height(16.dp))
        SectionLabel(t(lang, "editor.type"))
        SegmentedRow(
            options = listOf(
                HabitType.BINARY to t(lang, "editor.type.binary"),
                HabitType.MEASURABLE to t(lang, "editor.type.measurable"),
            ),
            selected = type,
            onSelect = { type = it },
        )

        if (type == HabitType.MEASURABLE) {
            Spacer(Modifier.height(16.dp))
            SectionLabel(t(lang, "editor.goal"))
            if (avoid) {
                // Shown, not offered — there is no other direction an avoid
                // habit could have, and a dead segmented control invites taps
                // that do nothing.
                Text(
                    t(lang, "editor.goal.at_most"),
                    color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surface2, RoundedCornerShape(11.dp))
                        .border(1.dp, c.border, RoundedCornerShape(11.dp))
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                SegmentedRow(
                    options = listOf(
                        GoalDir.AT_LEAST to t(lang, "editor.goal.at_least"),
                        GoalDir.AT_MOST to t(lang, "editor.goal.at_most"),
                    ),
                    selected = goalDir,
                    onSelect = { goalDir = it },
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { s -> target = s.filter { it.isDigit() || it == '.' } },
                    label = { Text(t(lang, "editor.target")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { if (it.length <= Habits.MAX_UNIT_LEN) unit = it },
                    label = { Text(t(lang, "editor.unit")) },
                    placeholder = { Text(t(lang, "editor.unitPh"), color = c.faint) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel(t(lang, "editor.freq"))
        SegmentedRow(
            // "3 times a week" counts occasions you did something, and an
            // avoid habit has none to count — so it isn't offered one.
            options = buildList {
                add("daily" to t(lang, "editor.freq.daily"))
                add("weekdays" to t(lang, "editor.freq.weekdays"))
                if (!avoid) add("weekly" to t(lang, "editor.freq.weeklyLabel"))
            },
            selected = freqKind,
            onSelect = { freqKind = it },
        )

        if (freqKind == "weekdays") {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Habits.weekdayOrder(weekStart).forEach { d ->
                    val on = days.contains(d)
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .background(
                                if (on) c.accent else c.surface2,
                                RoundedCornerShape(11.dp),
                            )
                            .border(
                                1.dp,
                                if (on) c.accent else c.border,
                                RoundedCornerShape(11.dp),
                            )
                            .clickable { days = if (on) days - d else days + d },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            names.narrow[d],
                            color = if (on) c.accentContrast else c.muted,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (freqKind == "weekly") {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = times,
                    onValueChange = { s -> times = s.filter { it.isDigit() }.take(1) },
                    label = { Text(t(lang, "editor.freq.weeklyLabel")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    t(lang, "stats.unit.times") + " / " + t(lang, "stats.unit.week"),
                    color = c.muted, fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel(t(lang, "editor.start"))
        DatePickerRow(
            value = startDate,
            max = today,
            names = names,
            onChange = { startDate = it },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            t(lang, if (avoid) "editor.startHintAvoid" else "editor.startHint"),
            color = c.muted, fontSize = 12.sp, lineHeight = 16.sp,
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                error!!,
                color = c.danger, fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.danger.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isNew) {
                TextButton(onClick = { onDelete(initial) }) {
                    Text(t(lang, "editor.delete"), color = c.danger)
                }
                TextButton(onClick = { onArchive(initial, !initial.archived) }) {
                    Text(
                        t(lang, if (initial.archived) "editor.unarchive" else "editor.archive"),
                        color = c.muted,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(t(lang, "editor.cancel"), color = c.muted) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        error = t(lang, "editor.nameRequired"); return@Button
                    }
                    val freq: Freq = when (freqKind) {
                        "weekdays" -> {
                            if (days.isEmpty()) {
                                error = t(lang, "editor.pickDays"); return@Button
                            }
                            Freq.Weekdays(days.toList().sorted())
                        }
                        "weekly" -> Freq.Weekly(times.toIntOrNull() ?: 3)
                        else -> Freq.Daily
                    }
                    onSave(
                        HabitFactory.normalize(
                            id = initial.id,
                            name = trimmed,
                            color = color,
                            type = type.wire,
                            polarity = polarity.wire,
                            goalDir = goalDir.wire,
                            target = if (type == HabitType.MEASURABLE)
                                target.toDoubleOrNull() ?: 1.0 else 1.0,
                            unit = if (type == HabitType.MEASURABLE) unit.trim() else "",
                            freq = freq,
                            startDate = startDate,
                            archived = initial.archived,
                            order = initial.order,
                            createdAt = initial.createdAt,
                            log = initial.log,
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.accent, contentColor = c.accentContrast,
                ),
            ) { Text(t(lang, "editor.save"), fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** A single-choice row of pills. */
@Composable
private fun <T> SegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val c = Streak.colors
    // IntrinsicSize.Min + fillMaxHeight keeps every segment the same height when
    // one of them wraps — otherwise a two-line label makes its own button taller
    // than its neighbours.
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // A minimum, not a fixed height. Labels are sized in sp, so
                    // they grow with the system font setting: "Times per week"
                    // does not fit one line across a third of a narrow phone,
                    // and a fixed 44.dp silently clipped it to "Times per".
                    .heightIn(min = 44.dp)
                    .background(if (on) c.accent else c.surface2, RoundedCornerShape(11.dp))
                    .border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(11.dp))
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
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

/**
 * Start-date stepper.
 *
 * A plain ± row rather than a date-picker dialog: the start date is nearly
 * always today or a few days back, and this keeps the whole editor on one
 * screen. It can never be set into the future, which the domain layer would
 * reject anyway.
 */
@Composable
private fun DatePickerRow(value: String, max: String, names: DateNames, onChange: (String) -> Unit) {
    val c = Streak.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepSquare("−") { onChange(Habits.shiftKey(value, -1)) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                // "Wednesday, August 5, 2026" is long, and longer still at a
                // large font scale — wrapping is much better than losing the
                // year off the end.
                .heightIn(min = 44.dp)
                .background(c.surface2, RoundedCornerShape(11.dp))
                .border(1.dp, c.border, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                names.longDate(value),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                color = c.text, fontSize = 13.sp, lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        StepSquare("+") {
            val next = Habits.shiftKey(value, 1)
            if (Habits.diffDays(next, max) <= 0) onChange(next)
        }
    }
}

@Composable
private fun StepSquare(label: String, onClick: () -> Unit) {
    val c = Streak.colors
    Box(
        Modifier
            .size(44.dp)
            .background(c.surface2, RoundedCornerShape(11.dp))
            .border(1.dp, c.border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = c.text, fontSize = 17.sp) }
}

/** 5.0 → "5", 2.5 → "2.5" — the editor should not show "5.0". */
private fun trimNumber(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
