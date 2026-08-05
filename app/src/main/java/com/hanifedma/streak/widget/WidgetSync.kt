package com.hanifedma.streak.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.hanifedma.streak.core.Habit
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Keeping the widget current.
 *
 * How fresh the widget is depends on whether the app's process is alive, and it
 * is worth being precise about that:
 *
 *  1. APP OPEN — genuinely real time. The ViewModel already holds a Firestore
 *     snapshot listener, and [refreshNow] is called on every change, so a tick
 *     made in the browser reaches the widget in about a second.
 *
 *  2. YOU TICK FROM THE WIDGET — instant. The write goes through Firestore's
 *     offline queue and the widget redraws immediately.
 *
 *  3. APP CLOSED — periodic, not instant. Android will not keep a socket open
 *     for a closed app, so a change made elsewhere is picked up by the checks
 *     below rather than pushed. In practice that means: every ~15 minutes
 *     (WorkManager's floor), whenever the screen is unlocked, and whenever the
 *     widget is tapped or resized.
 *
 * Case 3 cannot be made instant for free: it needs a server push (Cloud
 * Functions → FCM), which requires Firebase's paid Blaze plan. See README.
 */
object WidgetSync {

    private const val TAG = "WidgetSync"
    private const val WORK_NAME = "streak-widget-refresh"
    private const val SEED_WORK_NAME = "streak-widget-seed"

    /** Where each widget keeps its habit list. */
    val HABITS_JSON = stringPreferencesKey("habits_json")

    suspend fun readState(context: Context, id: GlanceId): String =
        try {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[HABITS_JSON] ?: ""
        } catch (e: Exception) {
            ""
        }

    suspend fun writeState(context: Context, id: GlanceId, habits: List<Habit>) {
        // The two-arg overload hands back a MutablePreferences; the one that
        // takes an explicit state definition expects the lambda to *return* a
        // new Preferences instead.
        updateAppWidgetState(context, id) { prefs ->
            prefs[HABITS_JSON] = WidgetData.encode(habits)
        }
    }

    /**
     * Reload the data and redraw every placed widget.
     *
     * Writing the state *then* calling update is the order that matters: the
     * composition renders from state, so updating without writing first would
     * just redraw the same thing.
     */
    suspend fun refreshNow(context: Context) {
        try {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(HabitWidget::class.java)
            if (ids.isEmpty()) return
            val habits = WidgetRepository.loadHabits(context, preferCache = false)
            ids.forEach { writeState(context, it, habits) }
            HabitWidget().updateAll(context)
        } catch (e: Exception) {
            Log.e(TAG, "Widget refresh failed", e)
        }
    }

    /**
     * Refresh once, very soon, from somewhere that must not block.
     *
     * A BroadcastReceiver cannot simply launch a coroutine and hope: the
     * process may be killed the moment onReceive returns. The usual answer is
     * goAsync(), but that is unavailable inside a GlanceAppWidgetReceiver —
     * goAsync() may be called only ONCE per broadcast, and Glance's own
     * onReceive has already claimed it. A second call returns null, which
     * Kotlin sees as a non-null platform type, so `pending.finish()` throws
     * NullPointerException on a background thread and takes the process with
     * it — leaving a permanently blank widget and a crash far from the cause.
     *
     * Handing the work to WorkManager sidesteps that entirely: it keeps the
     * process alive on its own terms and survives the receiver returning.
     */
    fun refreshSoon(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SEED_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            // Deliberately unconstrained, unlike the periodic refresh: a
            // signed-out user has no network to wait for, and a signed-in one
            // can still paint from the Firestore disk cache.
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build(),
        )
    }

    /** True when at least one widget is on a home screen — so the app can skip
     *  the work entirely when nobody has placed one. */
    suspend fun hasWidgets(context: Context): Boolean = try {
        GlanceAppWidgetManager(context).getGlanceIds(HabitWidget::class.java).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    /**
     * Schedule the background refresh.
     *
     * 15 minutes is WorkManager's minimum period; asking for less is silently
     * rounded up, so there is nothing to gain by pretending otherwise.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    // No point waking up to re-read the server with no network;
                    // local-only users are refreshed by the app itself.
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

/** The periodic refresh itself. */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            if (!WidgetSync.hasWidgets(applicationContext)) {
                // Nobody has a widget placed; stop burning the user's battery.
                WidgetSync.cancel(applicationContext)
                return Result.success()
            }
            WidgetSync.refreshNow(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Refresh on unlock.
 *
 * The moment someone looks at their home screen is exactly when the widget
 * needs to be right, and it costs nothing to catch up then rather than waiting
 * for the next periodic run. Registered at runtime because ACTION_USER_PRESENT
 * cannot be declared in the manifest.
 */
class ScreenUnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_USER_PRESENT) return
        // goAsync keeps the broadcast alive past onReceive; finish() must run
        // on every path or the system logs a leak. Safe to call here — this is
        // a plain receiver, so nothing has claimed the pending result already.
        // Declared nullable anyway: goAsync() is a platform type that really
        // can return null, and treating it as non-null is what crashed
        // HabitWidgetReceiver. See WidgetSync.refreshSoon.
        val pending: PendingResult? = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetSync.refreshNow(appContext)
            } catch (e: Exception) {
                Log.e("WidgetSync", "Unlock refresh failed", e)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        fun register(context: Context): ScreenUnlockReceiver {
            val receiver = ScreenUnlockReceiver()
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            return receiver
        }
    }
}
