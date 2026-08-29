package com.hanifedma.streak.data

import com.hanifedma.streak.core.Freq
import com.hanifedma.streak.core.Habit
import com.hanifedma.streak.core.HabitFactory
import com.hanifedma.streak.core.HabitType
import com.hanifedma.streak.core.Habits
import com.hanifedma.streak.core.Polarity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup: the same JSON shape the web app writes, so a file exported on one
 * platform imports cleanly on the other.
 *
 * Uses org.json (part of Android) rather than pulling in a serialization
 * library — the shape is small, fixed, and has to tolerate junk from
 * hand-edited files anyway.
 */
object Backup {

    const val VERSION = 1

    // ------------------------------------------------------------
    //  Habit ⇄ JSON
    // ------------------------------------------------------------

    fun freqToJson(f: Freq): JSONObject = when (f) {
        is Freq.Weekdays -> JSONObject()
            .put("type", "weekdays")
            .put("days", JSONArray(f.days))
        is Freq.Weekly -> JSONObject().put("type", "weekly").put("times", f.times)
        else -> JSONObject().put("type", "daily")
    }

    fun freqFromJson(o: JSONObject?): Freq {
        if (o == null) return Freq.Daily
        return when (o.optString("type")) {
            "weekdays" -> {
                val arr = o.optJSONArray("days")
                val days = ArrayList<Int>()
                if (arr != null) for (i in 0 until arr.length()) days.add(arr.optInt(i, -1))
                Freq.Weekdays(days)
            }
            "weekly" -> Freq.Weekly(o.optInt("times", 3))
            else -> Freq.Daily
        }
    }

    fun logToJson(log: Map<String, Double>): JSONObject {
        val o = JSONObject()
        for ((k, v) in log) o.put(k, v)
        return o
    }

    fun logFromJson(o: JSONObject?): Map<String, Double> {
        if (o == null) return emptyMap()
        val out = HashMap<String, Double>()
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = o.opt(k)
            if (v is Number) out[k] = v.toDouble()
        }
        return out
    }

    fun habitToJson(h: Habit): JSONObject = JSONObject()
        .put("name", h.name)
        .put("color", h.color)
        .put("type", h.type.wire)
        .put("polarity", h.polarity.wire)
        .put("goalDir", h.goalDir.wire)
        .put("target", h.target)
        .put("unit", h.unit)
        .put("freq", freqToJson(h.freq))
        .put("startDate", h.startDate)
        .put("archived", h.archived)
        .put("order", h.order)
        .put("log", logToJson(h.log))

    /**
     * A genuinely absent field, as null.
     *
     * JSONObject.optString returns "" for a missing key, which normalize()
     * would treat as a real (empty) value rather than "not supplied" — so an
     * old backup missing `color` would end up with no colour instead of the
     * default. This distinguishes the two.
     */
    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).ifBlank { null } else null

    fun habitFromJson(o: JSONObject, id: String): Habit = HabitFactory.normalize(
        id = id,
        name = o.optString("name"),
        color = o.stringOrNull("color"),
        type = o.stringOrNull("type"),
        polarity = o.stringOrNull("polarity"),
        goalDir = o.stringOrNull("goalDir"),
        target = if (o.has("target")) o.optDouble("target", 1.0) else null,
        unit = o.stringOrNull("unit"),
        freq = freqFromJson(o.optJSONObject("freq")),
        startDate = o.stringOrNull("startDate"),
        archived = o.optBoolean("archived", false),
        order = o.optInt("order", 0),
        log = logFromJson(o.optJSONObject("log")),
    )

    // ------------------------------------------------------------
    //  Export / import
    // ------------------------------------------------------------

    fun toJson(habits: List<Habit>): String {
        val arr = JSONArray()
        habits.forEach { arr.put(habitToJson(it)) }
        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        return JSONObject()
            .put("app", "streak")
            .put("version", VERSION)
            .put("exportedAt", stamp)
            .put("habits", arr)
            .toString(2)
    }

    /**
     * Read a backup file. Accepts our own export and, defensively, a bare array
     * of habits. Returns null for anything unreadable rather than throwing.
     */
    fun fromJson(text: String): List<Habit>? {
        val arr: JSONArray = try {
            val trimmed = text.trim()
            if (trimmed.startsWith("[")) JSONArray(trimmed)
            else JSONObject(trimmed).optJSONArray("habits") ?: return null
        } catch (e: Exception) {
            return null
        }
        val out = ArrayList<Habit>()
        for (i in 0 until minOf(arr.length(), Habits.MAX_HABITS)) {
            val o = arr.optJSONObject(i) ?: continue
            val h = habitFromJson(o, newId())
            if (h.name.isNotBlank()) out.add(h)
        }
        return out
    }

    /**
     * Merge imported habits into existing ones, matching on name.
     *
     * Existing entries win on a conflict, so importing an older backup can only
     * ever add history — it never silently overwrites something newer.
     */
    fun merge(existing: List<Habit>, incoming: List<Habit>): MergeResult {
        val byName = existing.associateBy { it.name.lowercase(Locale.ROOT) }
        var maxOrder = existing.maxOfOrNull { it.order } ?: -1
        val ops = ArrayList<WriteOp>()
        var added = 0
        var merged = 0

        for (inc in incoming) {
            val match = byName[inc.name.lowercase(Locale.ROOT)]
            if (match != null) {
                val log = HashMap(inc.log)
                log.putAll(match.log) // existing wins
                // The earlier of the two start dates, for the same reason the
                // logs are merged rather than replaced: an import can only
                // ever add history back. It matters most right after "start
                // over" — without this, restoring a backup brought the entries
                // back but left the habit claiming it began today, so its
                // stats read from the wrong date.
                val startDate = minOf(inc.startDate, match.startDate)
                // Nothing new to write — don't spend a write on it.
                if (log.size == match.log.size && startDate == match.startDate) continue
                ops.add(WriteOp.Update(
                    match.id, HabitFactory.withLog(match.copy(startDate = startDate), log)))
                merged++
            } else {
                maxOrder += 1
                val id = newId()
                ops.add(WriteOp.Set(id, inc.copy(id = id, order = maxOrder)))
                added++
            }
        }
        return MergeResult(ops, added, merged)
    }

    /**
     * A spreadsheet-friendly table: one row per habit per RECORDED day.
     *
     * Note what that means for an avoid habit: its kept days have nothing in
     * the log, so the rows you get are the slips (and any day explicitly
     * confirmed). That is the honest export of what was actually recorded \u2014
     * the `kind` column is there so a reader knows which way round to read a
     * sparse habit.
     */
    fun toCsv(habits: List<Habit>): String {
        val sb = StringBuilder()
        // A BOM so Excel opens Korean habit names as UTF-8 instead of mojibake.
        // Written as an escape, not a literal: a raw BOM byte sitting in the
        // middle of a source file is invisible and trips tooling.
        sb.append('\uFEFF')
        sb.append("habit,kind,date,value,status,unit,target\r\n")
        fun esc(s: String): String =
            if (s.any { it == '"' || it == ',' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

        for (h in habits) {
            val avoid = h.polarity == Polarity.AVOID
            for (k in h.log.keys.sorted()) {
                val v = h.log[k]!!
                val skipped = v == Habits.SKIP
                val status = when {
                    skipped -> "skipped"
                    h.type != HabitType.BINARY -> "logged"
                    avoid -> if (v >= 1) "kept" else "broken"
                    else -> "done"
                }
                sb.append(esc(h.name)).append(',')
                    .append(if (avoid) "avoid" else "do").append(',')
                    .append(k).append(',')
                    .append(if (skipped) "" else trimNum(v)).append(',')
                    .append(status).append(',')
                    .append(esc(h.unit)).append(',')
                    .append(trimNum(h.target)).append("\r\n")
            }
        }
        return sb.toString()
    }

    /** 5.0 → "5", 2.5 → "2.5". Keeps exported numbers tidy. */
    private fun trimNum(v: Double): String =
        if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()

    /** Short, collision-safe enough for a per-user collection, and readable. */
    fun newId(): String =
        java.lang.Long.toString(System.currentTimeMillis(), 36) +
            (100000..999999).random().toString(36)
}

data class MergeResult(val ops: List<WriteOp>, val added: Int, val merged: Int)

/** A batched change. Both stores understand the same set. */
sealed class WriteOp {
    data class Set(val id: String, val habit: Habit) : WriteOp()
    data class Update(val id: String, val habit: Habit) : WriteOp()
    data class Order(val id: String, val order: Int) : WriteOp()
    data class Delete(val id: String) : WriteOp()
}
