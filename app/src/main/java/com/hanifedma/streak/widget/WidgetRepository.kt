package com.hanifedma.streak.widget

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Source
import com.hanifedma.streak.BuildConfig
import com.hanifedma.streak.core.Habit
import com.hanifedma.streak.core.Habits
import com.hanifedma.streak.data.CloudStore
import com.hanifedma.streak.data.LocalStore
import kotlinx.coroutines.tasks.await

/**
 * How the widget reads and writes habits.
 *
 * The widget runs in the app's process but usually with no Activity alive, so
 * it cannot lean on the ViewModel. It goes straight to the same two backends:
 *
 *  • signed in  → Firestore. Reads prefer the on-disk cache so the widget
 *    paints instantly and works with no signal, then a server read refreshes
 *    it. Writes go through the normal offline queue.
 *  • signed out → the same habits.json the app uses.
 *
 * Which one is live is decided by whether Firebase has a signed-in user, so the
 * widget always shows the same data as the app.
 */
object WidgetRepository {

    private const val TAG = "WidgetRepository"

    private fun firestore(context: Context): Pair<FirebaseFirestore, String>? {
        if (!BuildConfig.FIREBASE_CONFIGURED) return null
        return try {
            FirebaseApp.initializeApp(context)
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
            val db = FirebaseFirestore.getInstance()
            // Idempotent: settings can only be applied before first use, and
            // the app may already have configured this instance.
            try {
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
            } catch (ignored: IllegalStateException) {
            }
            db to uid
        } catch (e: Exception) {
            Log.e(TAG, "Firebase unavailable to the widget", e)
            null
        }
    }

    /**
     * Today's habits for the widget.
     *
     * @param preferCache read from the local cache only. Used for the first
     *        paint so the widget never shows a spinner or a stale blank while
     *        a network round-trip completes.
     */
    suspend fun loadHabits(context: Context, preferCache: Boolean): List<Habit> {
        val fs = firestore(context)
        if (fs != null) {
            val (db, uid) = fs
            return try {
                val snap = db.collection("users").document(uid).collection("habits")
                    .get(if (preferCache) Source.CACHE else Source.DEFAULT)
                    .await()
                val store = CloudStore(db, uid)
                Habits.sortHabits(snap.documents.mapNotNull { store.habitFromSnapshot(it) })
            } catch (e: Exception) {
                // An empty cache throws rather than returning nothing; fall back
                // to the server so a freshly-added widget still fills in.
                if (preferCache) loadHabits(context, preferCache = false)
                else {
                    Log.e(TAG, "Widget read failed", e)
                    emptyList()
                }
            }
        }
        return LocalStore.get(context).readAll()
    }

    /** Toggle one habit for one day, from the widget. */
    suspend fun toggle(context: Context, habitId: String, dayKey: String) {
        val habits = loadHabits(context, preferCache = true)
        val habit = habits.firstOrNull { it.id == habitId } ?: return
        val done = Habits.statusOf(habit, dayKey).let {
            it == com.hanifedma.streak.core.DayStatus.DONE ||
                it == com.hanifedma.streak.core.DayStatus.SKIP
        }
        val value: Double? = if (done) null else Habits.doneValue(habit)

        val fs = firestore(context)
        if (fs != null) {
            val (db, uid) = fs
            CloudStore(db, uid).setEntry(habitId, dayKey, value)
        } else {
            LocalStore.get(context).setEntry(habitId, dayKey, value)
        }
    }
}
