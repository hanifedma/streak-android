package com.hanifedma.streak.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        // Load the habits and push them into widget state. This is the right
        // place for it — outside any Glance composition, so it cannot contend
        // with the session's hold on the state datastore.
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetSync.refreshNow(appContext)
            } catch (e: Exception) {
                Log.e("HabitWidgetReceiver", "Seeding widget failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
