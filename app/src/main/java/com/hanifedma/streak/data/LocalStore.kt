package com.hanifedma.streak.data

import android.content.Context
import android.util.Log
import com.hanifedma.streak.core.Habit
import com.hanifedma.streak.core.HabitFactory
import com.hanifedma.streak.core.Habits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Device-only storage: a single JSON file in the app's private directory.
 *
 * Chosen over Room deliberately. The whole dataset is a small list of habits
 * that is always read and written as a unit, and it has to round-trip through
 * the exact same JSON shape as the web app's backups — a database schema and
 * migrations would be pure overhead for that.
 */
class LocalStore private constructor(context: Context) : HabitStore {

    companion object {
        @Volatile private var instance: LocalStore? = null

        /**
         * The one instance for this process.
         *
         * This MUST be a singleton. The app and the widget both reach for
         * local storage, and each instance keeps the habit list in memory to
         * emit from — two of them would hold divergent copies and silently
         * overwrite each other's writes (tick from the widget, then edit in
         * the app, and the tick is gone).
         */
        fun get(context: Context): LocalStore =
            instance ?: synchronized(this) {
                instance ?: LocalStore(context.applicationContext).also { instance = it }
            }

        const val TAG = "LocalStore"
    }

    private val file = File(context.filesDir, "habits.json")
    private val state = MutableStateFlow(read())

    override val mode = "local"

    override fun habits(): Flow<StoreSnapshot> = state.map { StoreSnapshot(it) }

    private fun read(): List<Habit> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            val out = ArrayList<Habit>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").ifBlank { Backup.newId() }
                out.add(Backup.habitFromJson(o, id))
            }
            Habits.sortHabits(out)
        } catch (e: Exception) {
            // A corrupt file must not brick the app. Better an empty list the
            // user can rebuild than a crash loop on every launch.
            Log.e(TAG, "Couldn't read local habits", e)
            emptyList()
        }
    }

    private fun write(list: List<Habit>) {
        try {
            val arr = JSONArray()
            list.forEach { h -> arr.put(Backup.habitToJson(h).put("id", h.id)) }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't save local habits", e)
        }
        state.value = Habits.sortHabits(list)
    }

    private fun current() = state.value

    override fun create(habit: Habit) {
        write(current() + habit)
    }

    override fun update(habit: Habit) {
        // Keep the stored log: callers hand us an edited habit whose log may be
        // a stale copy, and the same rule holds here as in the cloud store.
        write(current().map { if (it.id == habit.id) HabitFactory.withLog(habit, it.log) else it })
    }

    override fun setEntry(habitId: String, dayKey: String, value: Double?) {
        write(current().map { h ->
            if (h.id != habitId) h else {
                val log = HashMap(h.log)
                if (value == null) log.remove(dayKey) else log[dayKey] = value
                HabitFactory.withLog(h, log)
            }
        })
    }

    override fun remove(id: String) {
        write(current().filterNot { it.id == id })
    }

    override suspend fun writeMany(ops: List<WriteOp>) {
        var list = current()
        for (op in ops) {
            list = when (op) {
                is WriteOp.Set ->
                    if (list.any { it.id == op.id }) list.map { if (it.id == op.id) op.habit else it }
                    else list + op.habit
                is WriteOp.Update ->
                    list.map { if (it.id == op.id) op.habit else it }
                is WriteOp.Order ->
                    list.map { if (it.id == op.id) it.copy(order = op.order) else it }
                is WriteOp.Delete ->
                    list.filterNot { it.id == op.id }
            }
        }
        write(list)
    }

    override suspend fun readAll(): List<Habit> = current()

    /** Only the local store needs this — used after migrating to an account. */
    fun clearAll() {
        try { file.delete() } catch (e: Exception) { Log.e(TAG, "Couldn't clear local habits", e) }
        state.value = emptyList()
    }

}
