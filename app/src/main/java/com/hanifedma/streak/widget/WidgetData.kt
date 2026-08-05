package com.hanifedma.streak.widget

import com.hanifedma.streak.core.Habit
import com.hanifedma.streak.data.Backup
import org.json.JSONArray

/**
 * The habit list as the widget stores it.
 *
 * Glance keeps each widget's state in its own datastore, and that — not a
 * variable captured while composing — is what a widget must render from. It
 * survives process death, so a widget the launcher re-binds hours later still
 * paints real content instead of a placeholder.
 *
 * The `id` is written and read back deliberately: tapping a row has to identify
 * exactly which habit to toggle, so the ids that Backup normally regenerates on
 * import must be preserved here.
 */
object WidgetData {

    fun encode(habits: List<Habit>): String {
        val arr = JSONArray()
        habits.forEach { h -> arr.put(Backup.habitToJson(h).put("id", h.id)) }
        return arr.toString()
    }

    fun decode(json: String?): List<Habit> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isBlank()) null else Backup.habitFromJson(o, id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
