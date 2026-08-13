package com.hanifedma.streak.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.hanifedma.streak.core.Freq
import com.hanifedma.streak.core.Habit
import com.hanifedma.streak.core.HabitFactory
import com.hanifedma.streak.core.Habits
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed storage, reading and writing the very same documents as the
 * web app: /users/{uid}/habits/{habitId}, with each habit's daily entries in a
 * `log` map on its own document.
 *
 * Real-time by construction: [habits] is a snapshot listener, so a tick made on
 * another device shows up here about a second later with no refresh.
 *
 * @param onError reported to the UI as a message; the store never throws at
 *        the caller for a write that failed in the background.
 */
class CloudStore(
    private val db: FirebaseFirestore,
    private val uid: String,
    private val onError: (String) -> Unit = {},
) : HabitStore {

    override val mode = "cloud"

    private fun col() = db.collection("users").document(uid).collection("habits")
    private fun ref(id: String) = col().document(id)

    override fun habits(): Flow<StoreSnapshot> = callbackFlow {
        // Deliberately unordered. A Firestore orderBy silently DROPS documents
        // that lack the field, so a habit written without `order` would simply
        // vanish from the list. Sorting happens client-side, where a missing
        // value is just a default.
        //
        // MetadataChanges.INCLUDE so the UI learns when a result came from the
        // offline cache and can say so.
        val reg = col().addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
            if (err != null) {
                Log.e(TAG, "Firestore listen failed", err)
                onError("err.load")
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            val list = snap.documents.mapNotNull { toHabit(it) }
            trySend(StoreSnapshot(Habits.sortHabits(list), snap.metadata.isFromCache))
        }
        awaitClose { reg.remove() }
    }

    // Firestore writes are deliberately NOT awaited. The Task they return only
    // completes once the *server* has acknowledged the write, so awaiting it
    // hangs indefinitely while offline — the tick would appear to do nothing
    // even though it is already saved locally and queued. Firestore applies
    // every write to its cache immediately and replays the queue when the
    // connection returns; we only surface an error if one actually arrives.
    override fun create(habit: Habit) {
        ref(habit.id).set(toDoc(habit)).addOnFailureListener { fail("err.save", it) }
    }

    override fun update(habit: Habit) {
        // No `log` here on purpose: an edit must not clobber a tick that
        // arrived from another device while the editor was open.
        ref(habit.id).update(toEditableDoc(habit)).addOnFailureListener { fail("err.save", it) }
    }

    override fun setEntry(habitId: String, dayKey: String, value: Double?) {
        // The field path MUST be built with FieldPath.of("log", key) rather
        // than the string "log.2026-08-04". Firestore parses a string as dot
        // notation, and a segment starting with a digit or containing "-" is
        // not a legal unquoted identifier — the string form throws.
        val path = FieldPath.of("log", dayKey)
        val v: Any = value ?: FieldValue.delete()
        ref(habitId).update(path, v).addOnFailureListener { fail("err.save", it) }
    }

    override fun remove(id: String) {
        ref(id).delete().addOnFailureListener { fail("err.delete", it) }
    }

    override suspend fun writeMany(ops: List<WriteOp>) {
        // Firestore caps a batch at 500 operations; chunk to stay safely under.
        for (chunk in ops.chunked(400)) {
            val batch = db.batch()
            for (op in chunk) {
                when (op) {
                    is WriteOp.Set -> batch.set(ref(op.id), toDoc(op.habit))
                    is WriteOp.Update -> batch.update(ref(op.id), toEditableDoc(op.habit) + ("log" to op.habit.log))
                    is WriteOp.Order -> batch.update(ref(op.id), "order", op.order)
                    is WriteOp.Delete -> batch.delete(ref(op.id))
                }
            }
            batch.commit().await()
        }
    }

    override suspend fun readAll(): List<Habit> =
        col().get().await().documents.mapNotNull { toHabit(it) }

    private fun fail(key: String, e: Exception) {
        Log.e(TAG, "Firestore write failed", e)
        onError(key)
    }

    // ------------------------------------------------------------
    //  Document ⇄ Habit
    // ------------------------------------------------------------

    /** Public so the widget can reuse this parsing rather than duplicating it. */
    fun habitFromSnapshot(d: DocumentSnapshot): Habit? = toHabit(d)

    private fun toHabit(d: DocumentSnapshot): Habit? {
        if (!d.exists()) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val rawLog = d.get("log") as? Map<String, Any?> ?: emptyMap()
            val log = HashMap<String, Double>(rawLog.size)
            for ((k, v) in rawLog) if (v is Number) log[k] = v.toDouble()

            @Suppress("UNCHECKED_CAST")
            val rawFreq = d.get("freq") as? Map<String, Any?>
            val freq: Freq = when (rawFreq?.get("type")) {
                "weekdays" -> {
                    val days = (rawFreq["days"] as? List<*>)
                        ?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
                    Freq.Weekdays(days)
                }
                "weekly" -> Freq.Weekly((rawFreq["times"] as? Number)?.toInt() ?: 3)
                else -> Freq.Daily
            }

            HabitFactory.normalize(
                id = d.id,
                name = d.getString("name"),
                color = d.getString("color"),
                type = d.getString("type"),
                polarity = d.getString("polarity"),
                goalDir = d.getString("goalDir"),
                target = d.getDouble("target"),
                unit = d.getString("unit"),
                freq = freq,
                startDate = d.getString("startDate"),
                archived = d.getBoolean("archived") ?: false,
                order = (d.get("order") as? Number)?.toInt() ?: 0,
                createdAt = d.getTimestamp("createdAt")?.toDate()?.time,
                log = log,
            )
        } catch (e: Exception) {
            // One malformed document must not take out the whole list.
            Log.e(TAG, "Skipping unreadable habit ${d.id}", e)
            null
        }
    }

    /** The fields an edit may change. `id` is never stored in the body — it is
     *  the document's own key — and `log` is handled separately. */
    private fun toEditableDoc(h: Habit): Map<String, Any> = mapOf(
        "name" to h.name,
        "color" to h.color,
        "type" to h.type.wire,
        "polarity" to h.polarity.wire,
        "goalDir" to h.goalDir.wire,
        "target" to h.target,
        "unit" to h.unit,
        "freq" to freqMap(h.freq),
        "startDate" to h.startDate,
        "archived" to h.archived,
        "order" to h.order,
    )

    private fun toDoc(h: Habit): Map<String, Any> = toEditableDoc(h) + mapOf(
        "log" to h.log,
        "createdAt" to (h.createdAt?.let { com.google.firebase.Timestamp(java.util.Date(it)) }
            ?: FieldValue.serverTimestamp()),
    )

    private fun freqMap(f: Freq): Map<String, Any> = when (f) {
        is Freq.Weekdays -> mapOf("type" to "weekdays", "days" to f.days)
        is Freq.Weekly -> mapOf("type" to "weekly", "times" to f.times)
        else -> mapOf("type" to "daily")
    }

    private companion object { const val TAG = "CloudStore" }
}
