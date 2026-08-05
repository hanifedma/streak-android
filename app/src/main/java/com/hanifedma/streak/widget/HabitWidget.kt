package com.hanifedma.streak.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.Preferences
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.hanifedma.streak.MainActivity
import com.hanifedma.streak.core.DayStatus
import com.hanifedma.streak.core.Habit
import com.hanifedma.streak.core.HabitType
import com.hanifedma.streak.core.Habits
import com.hanifedma.streak.data.Prefs
import com.hanifedma.streak.i18n.Strings.t
import com.hanifedma.streak.ui.theme.DarkStreakColors
import com.hanifedma.streak.ui.theme.LightStreakColors

/**
 * The home screen widget: today's habits, each tickable without opening the app.
 *
 * Rendered with Glance rather than hand-written RemoteViews, so it shares the
 * app's colours and the same domain logic for what "done" means.
 */
class HabitWidget : GlanceAppWidget() {

    // Responsive: the widget re-composes for whatever size the user resizes it
    // to, rather than assuming one shape.
    override val sizeMode = SizeMode.Exact

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Nothing but provideContent here, deliberately.
        //
        // Writing widget state from inside provideGlance deadlocks: Glance
        // holds the state datastore open for the session, so updateAppWidgetState
        // waits on a lock this coroutine will never release — the widget then
        // hangs on the launcher's placeholder with no exception to show for it.
        // Seeding happens in HabitWidgetReceiver.onUpdate instead.
        provideContent {
            // Reading from currentState is what makes this reactive: any
            // writeState + update repaints the widget, from here or from the
            // app's live Firestore listener.
            val prefs = currentState<Preferences>()
            val json = prefs[WidgetSync.HABITS_JSON]
            val habits = remember(json) { WidgetData.decode(json) }

            // Belt and braces: if this widget was bound without onUpdate ever
            // seeding it, ask for a refresh once. refreshNow runs outside this
            // composition, so there is no lock to contend with.
            LaunchedEffect(json == null) {
                if (json == null) WidgetSync.refreshNow(context)
            }

            WidgetBody(context, habits, loaded = json != null)
        }
    }

    @Composable
    private fun WidgetBody(context: Context, habits: List<Habit>, loaded: Boolean) {
        val prefs = Prefs(context)
        val colors = if (prefs.dark) DarkStreakColors else LightStreakColors
        val lang = prefs.lang
        val today = Habits.todayKey()
        val due = Habits.dueOn(habits.filter { !it.archived }, today)
        val progress = Habits.dayProgress(habits, today)
        val height = LocalSize.current.height

        GlanceTheme {
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .background(colors.bg)
                    .cornerRadius(18.dp)
                    .padding(12.dp)
            ) {
                // Header — tapping it opens the app.
                Row(
                    GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        t(lang, "app.name"),
                        style = TextStyle(
                            color = ColorProvider(colors.text),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        ),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Text(
                        if (progress.due == 0) "" else "${progress.done}/${progress.due}",
                        style = TextStyle(
                            color = ColorProvider(colors.accent),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                Spacer(GlanceModifier.height(8.dp))

                when {
                    // "No habits yet" is only true once the state has actually
                    // loaded. Before that, say nothing rather than tell the
                    // user something false for a frame.
                    !loaded -> Spacer(GlanceModifier.fillMaxSize())
                    due.isEmpty() && habits.isEmpty() -> WidgetMessage(t(lang, "today.empty"), colors.muted)
                    due.isEmpty() -> WidgetMessage(t(lang, "today.nothingDue"), colors.muted)
                    else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(due, itemId = { it.id.hashCode().toLong() }) { habit ->
                            WidgetRow(habit, today, colors, compact = height < 180.dp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetMessage(text: String, color: Color) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = TextStyle(
                    color = ColorProvider(Color(color.value)),
                    fontSize = 13.sp,
                ),
            )
        }
    }

    @Composable
    private fun WidgetRow(
        habit: Habit,
        today: String,
        colors: com.hanifedma.streak.ui.theme.StreakColors,
        compact: Boolean,
    ) {
        val status = Habits.statusOf(habit, today)
        val done = status == DayStatus.DONE
        val skipped = status == DayStatus.SKIP
        val hc = colors.habit(habit.color)

        Row(
            GlanceModifier
                .fillMaxWidth()
                .padding(vertical = if (compact) 3.dp else 5.dp)
                // The whole row is the target: a 20dp checkbox alone is a
                // miserable thing to hit on a home screen.
                .clickable(
                    actionRunCallback<ToggleHabitAction>(
                        actionParametersOf(
                            ToggleHabitAction.habitIdKey to habit.id,
                            ToggleHabitAction.dayKey to today,
                        )
                    )
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                GlanceModifier
                    .size(22.dp)
                    .cornerRadius(7.dp)
                    .background(
                        if (done) hc else colors.surface2
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (done) "✔" else if (skipped) "–" else "",
                    style = TextStyle(
                        color = ColorProvider(
                            if (done) colors.bg else colors.muted
                        ),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(GlanceModifier.width(10.dp))
            Text(
                habit.name,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(hc),
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            // Measurable habits show progress rather than a bare tick, since
            // one tap can't express "1,200 of 2,000 ml".
            if (habit.type == HabitType.MEASURABLE) {
                val v = Habits.entryOf(habit, today)
                val shown = if (v == null || v == Habits.SKIP) "–" else trim(v)
                Text(
                    "$shown/${trim(habit.target)}",
                    style = TextStyle(
                        color = ColorProvider(colors.muted),
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }

    private fun trim(v: Double): String =
        if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
}

/** Tapping a row ticks or unticks it, then refreshes every widget. */
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val habitId = parameters[habitIdKey] ?: return
        val day = parameters[dayKey] ?: Habits.todayKey()
        WidgetRepository.toggle(context, habitId, day)
        WidgetSync.refreshNow(context)
    }

    companion object {
        val habitIdKey = ActionParameters.Key<String>("habitId")
        val dayKey = ActionParameters.Key<String>("day")
    }
}
