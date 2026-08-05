package com.hanifedma.streak.data

import com.hanifedma.streak.core.Habit
import kotlinx.coroutines.flow.Flow

/**
 * One interface, two backends — exactly as on the web:
 *
 *  • [CloudStore] → Firestore, one document per habit under
 *    /users/{uid}/habits/{id}. Real-time: a tick made on your laptop appears
 *    here about a second later. Writes hit the local cache first, so the UI
 *    never waits for the network.
 *  • [LocalStore] → this device only. Needs no account, no Firebase project
 *    and no internet; the app is fully usable before you set anything up.
 */
interface HabitStore {

    /** "cloud" or "local" — surfaced in Settings so the user knows where their data lives. */
    val mode: String

    /** Habits, newest state first. Emits again on every change, local or remote. */
    fun habits(): Flow<StoreSnapshot>

    fun create(habit: Habit)

    /** Replace a habit's editable fields. Never touches `log`, so a concurrent
     *  tick from another device can't be clobbered by an edit. */
    fun update(habit: Habit)

    /** Set or clear one day's entry. `value == null` clears it. */
    fun setEntry(habitId: String, dayKey: String, value: Double?)

    fun remove(id: String)

    /** Bulk change, used by reorder, import and "delete everything". */
    suspend fun writeMany(ops: List<WriteOp>)

    /** One-shot read, used by the sign-in migration. */
    suspend fun readAll(): List<Habit>
}

/**
 * @param habits the current list
 * @param fromCache true when Firestore answered from its offline cache, which
 *        is how the UI knows to show the "offline" badge
 */
data class StoreSnapshot(val habits: List<Habit>, val fromCache: Boolean = false)
