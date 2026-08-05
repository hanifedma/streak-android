package com.hanifedma.streak.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The manifest entry point for the widget.
 *
 * Scheduling is started when the first widget is placed and stopped when the
 * last one is removed, so a user who never adds a widget pays nothing for it.
 */
class HabitWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = HabitWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSync.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetSync.cancel(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Covers the case where work was cancelled while no widget existed and
        // one has since been added back.
        WidgetSync.schedule(context)
        // Load the habits and push them into widget state. Seeding belongs
        // here — outside any Glance composition, so it cannot contend with the
        // session's hold on the state datastore — and it goes through
        // WorkManager rather than goAsync(), which Glance has already consumed
        // for this broadcast. See WidgetSync.refreshSoon.
        WidgetSync.refreshSoon(context)
    }
}
