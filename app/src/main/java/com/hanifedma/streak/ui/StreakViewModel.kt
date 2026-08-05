package com.hanifedma.streak.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.hanifedma.streak.BuildConfig
import com.hanifedma.streak.auth.AuthManager
import com.hanifedma.streak.core.Habit
import com.hanifedma.streak.core.HabitFactory
import com.hanifedma.streak.core.Habits
import com.hanifedma.streak.data.Backup
import com.hanifedma.streak.data.CloudStore
import com.hanifedma.streak.data.HabitStore
import com.hanifedma.streak.data.LocalStore
import com.hanifedma.streak.data.Prefs
import com.hanifedma.streak.data.WriteOp
import com.hanifedma.streak.i18n.DateNames
import com.hanifedma.streak.i18n.Lang
import com.hanifedma.streak.i18n.Strings
import com.hanifedma.streak.widget.WidgetSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/** Which screen the app is on. */
enum class Screen { LOADING, LOGIN, APP }

/** The two main tabs. */
enum class Tab { TODAY, HABITS }

data class UiState(
    val screen: Screen = Screen.LOADING,
    val tab: Tab = Tab.TODAY,
    val habits: List<Habit> = emptyList(),
    val today: String = Habits.todayKey(),
    val dark: Boolean = true,
    val lang: Lang = Lang.DEFAULT,
    val weekStart: Int = 0,
    val search: String = "",
    val gridDays: Int = 60,
    val mode: String? = null,          // "cloud" | "local"
    val userName: String? = null,
    val userPhoto: String? = null,
    val offline: Boolean = false,
    val firebaseAvailable: Boolean = BuildConfig.FIREBASE_CONFIGURED,
) {
    val active: List<Habit> get() = habits.filter { !it.archived }
    val archived: List<Habit> get() = habits.filter { it.archived }
}

/** A one-shot message for the snackbar. */
data class Toast(
    val id: Long,
    val messageKey: String,
    val params: List<Pair<String, Any>> = emptyList(),
    val actionKey: String? = null,
    val action: (() -> Unit)? = null,
    val isError: Boolean = false,
)

class StreakViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val localStore = LocalStore.get(app)

    private var store: HabitStore? = null
    private var collectJob: Job? = null

    private var auth: FirebaseAuth? = null
    private var db: FirebaseFirestore? = null
    private var authManager: AuthManager? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null

    private val _state = MutableStateFlow(
        UiState(
            dark = prefs.dark,
            lang = prefs.lang,
            weekStart = prefs.weekStart,
            tab = if (prefs.view == "habits") Tab.HABITS else Tab.TODAY,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _toasts = MutableStateFlow<Toast?>(null)
    val toasts: StateFlow<Toast?> = _toasts.asStateFlow()

    var names: DateNames = DateNames(prefs.lang)
        private set

    init { boot() }

    // ------------------------------------------------------------
    //  Boot
    // ------------------------------------------------------------

    private fun boot() {
        val app = getApplication<Application>()
        if (!BuildConfig.FIREBASE_CONFIGURED) {
            // No Firebase in this build — the app still works fully, on-device.
            enterLocalMode()
            return
        }
        try {
            FirebaseApp.initializeApp(app)
            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance().also { it.firestoreSettings = offlineSettings() }
            authManager = AuthManager(auth!!, webClientId(app))
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed — falling back to local mode", e)
            enterLocalMode()
            return
        }

        // Auth answers from its own cache, so this normally fires immediately —
        // including offline. If the user was signed in, we go straight in.
        authListener = authManager!!.addAuthListener { user ->
            if (user != null) enterCloudMode(user)
            else if (prefs.localMode) enterLocalMode()
            else _state.update { it.copy(screen = Screen.LOGIN, mode = null) }
        }
    }

    /**
     * The OAuth web client id, which the google-services plugin generates as a
     * string resource.
     *
     * Looked up by name rather than as R.string.default_web_client_id on
     * purpose: that symbol only exists once google-services.json is present, so
     * referencing it directly would stop the project compiling without one —
     * exactly the state the conditional plugin in build.gradle.kts exists to
     * support.
     */
    @SuppressLint("DiscouragedApi") // deliberate; see the note above
    private fun webClientId(context: Context): String {
        val id = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName,
        )
        return if (id != 0) context.getString(id) else ""
    }

    /** Offline-first: habits are cached on disk, so a cold launch shows data
     *  instantly and every write is queued durably until there is a signal. */
    private fun offlineSettings(): FirebaseFirestoreSettings =
        FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()

    override fun onCleared() {
        authListener?.let { l -> authManager?.removeAuthListener(l) }
        collectJob?.cancel()
        super.onCleared()
    }

    // ------------------------------------------------------------
    //  Modes
    // ------------------------------------------------------------

    fun enterLocalMode() {
        prefs.localMode = true
        switchStore(localStore)
        _state.update { it.copy(screen = Screen.APP, mode = "local", userName = null, userPhoto = null) }
    }

    private fun enterCloudMode(user: FirebaseUser) {
        val database = db ?: return
        prefs.localMode = false
        viewModelScope.launch {
            migrateLocalIfAny(user.uid, database)
            switchStore(CloudStore(database, user.uid) { key -> toast(key, isError = true) })
            _state.update {
                it.copy(
                    screen = Screen.APP, mode = "cloud",
                    userName = user.displayName ?: user.email,
                    userPhoto = user.photoUrl?.toString(),
                )
            }
        }
    }

    private fun switchStore(next: HabitStore) {
        collectJob?.cancel()
        store = next
        collectJob = viewModelScope.launch {
            next.habits().collect { snap ->
                _state.update {
                    it.copy(habits = snap.habits, offline = snap.fromCache && snap.habits.isNotEmpty())
                }
                // While this process is alive the widget is genuinely real
                // time: the snapshot that just updated the screen updates the
                // home screen too. Covers ticks, edits AND deletions, because
                // it redraws from whatever the listener reports.
                refreshWidget()
            }
        }
    }

    /** Redraw any placed widget. No-op when the user has never added one. */
    private fun refreshWidget() {
        viewModelScope.launch {
            try {
                if (WidgetSync.hasWidgets(getApplication())) {
                    WidgetSync.refreshNow(getApplication())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Widget refresh skipped", e)
            }
        }
    }

    /** Move habits saved on this device into the account, once, after sign-in. */
    private suspend fun migrateLocalIfAny(uid: String, database: FirebaseFirestore) {
        try {
            val local = localStore.readAll()
            if (local.isEmpty()) return
            val cloud = CloudStore(database, uid)
            val existing = cloud.readAll()
            val result = Backup.merge(existing, local)
            if (result.ops.isNotEmpty()) cloud.writeMany(result.ops)
            localStore.clearAll()
            if (result.added + result.merged > 0) toast("toast.moved")
        } catch (e: Exception) {
            // Never block sign-in on a migration problem — the local copy is
            // left untouched so nothing is lost and it retries next time.
            Log.e(TAG, "Local migration failed", e)
        }
    }

    // ------------------------------------------------------------
    //  Auth
    // ------------------------------------------------------------

    fun signIn(context: Context) {
        val manager = authManager ?: return
        viewModelScope.launch {
            val err = manager.signIn(context)
            if (err != null && err != "err.auth.cancelled") toast(err, isError = true)
        }
    }

    fun signOut(context: Context) {
        val manager = authManager ?: return
        viewModelScope.launch {
            try {
                collectJob?.cancel()
                store = null
                _state.update { it.copy(habits = emptyList()) }
                manager.signOut(context)
                prefs.localMode = false
                _state.update { it.copy(screen = Screen.LOGIN, mode = null, userName = null, userPhoto = null) }
            } catch (e: Exception) {
                Log.e(TAG, "Sign-out failed", e)
                toast("err.signout", isError = true)
            }
        }
    }

    // ------------------------------------------------------------
    //  Settings
    // ------------------------------------------------------------

    fun setDark(v: Boolean) { prefs.dark = v; _state.update { it.copy(dark = v) } }

    fun setLang(v: Lang) {
        prefs.lang = v
        names = DateNames(v)
        _state.update { it.copy(lang = v) }
    }

    fun toggleLang() = setLang(if (_state.value.lang == Lang.KO) Lang.EN else Lang.KO)

    fun setWeekStart(v: Int) { prefs.weekStart = v; _state.update { it.copy(weekStart = v) } }

    fun setTab(t: Tab) {
        prefs.view = if (t == Tab.HABITS) "habits" else "today"
        _state.update { it.copy(tab = t) }
    }

    fun setSearch(q: String) = _state.update { it.copy(search = q) }

    fun extendGrid() = _state.update {
        it.copy(gridDays = minOf(5 * 365, it.gridDays + 60))
    }

    /** Roll over if the app is left open past midnight. */
    fun refreshToday() {
        val now = Habits.todayKey()
        if (now != _state.value.today) _state.update { it.copy(today = now) }
    }

    // ------------------------------------------------------------
    //  Entries
    // ------------------------------------------------------------

    fun setEntry(habitId: String, dayKey: String, value: Double?) {
        val s = _state.value
        // The future is not something you can have already done.
        if (Habits.diffDays(dayKey, s.today) > 0) return
        val v = value?.coerceIn(if (value == Habits.SKIP) Habits.SKIP else 0.0, Habits.MAX_VALUE)
        store?.setEntry(habitId, dayKey, v)
    }

    fun toggleEntry(habitId: String, dayKey: String) {
        val h = habitById(habitId) ?: return
        when (Habits.statusOf(h, dayKey)) {
            com.hanifedma.streak.core.DayStatus.DONE,
            com.hanifedma.streak.core.DayStatus.SKIP -> setEntry(habitId, dayKey, null)
            else -> setEntry(habitId, dayKey, Habits.doneValue(h))
        }
    }

    fun bumpValue(habitId: String, dayKey: String, delta: Int) {
        val h = habitById(habitId) ?: return
        val has = Habits.hasEntry(h, dayKey)
        val base = if (has) Habits.entryOf(h, dayKey)!! else 0.0

        // Pressing "−" on a blank day is meaningless for an "at least" goal,
        // but for an "at most" goal it is how you record a clean zero — which
        // is a success.
        val isAtMost = h.type == com.hanifedma.streak.core.HabitType.MEASURABLE &&
            h.goalDir == com.hanifedma.streak.core.GoalDir.AT_MOST
        if (!has && delta < 0 && !isAtMost) return

        val next = maxOf(0.0, base + delta * Habits.stepOf(h))
        if (has && next == base) return
        setEntry(habitId, dayKey, next)
    }

    // ------------------------------------------------------------
    //  Habits
    // ------------------------------------------------------------

    fun habitById(id: String?): Habit? = _state.value.habits.firstOrNull { it.id == id }

    fun saveHabit(edited: Habit, isNew: Boolean) {
        val s = _state.value
        if (isNew) {
            if (s.active.size >= Habits.MAX_HABITS) {
                toast("editor.limit", listOf("n" to Habits.MAX_HABITS), isError = true)
                return
            }
            val order = (s.habits.maxOfOrNull { it.order } ?: -1) + 1
            store?.create(edited.copy(order = order))
        } else {
            store?.update(edited)
        }
        toast("toast.saved")
    }

    fun setArchived(habit: Habit, archived: Boolean) {
        store?.update(habit.copy(archived = archived))
        toast(if (archived) "toast.archived" else "toast.unarchived", listOf("name" to habit.name))
    }

    fun deleteHabit(habit: Habit) {
        // Keep a copy so Undo can restore it exactly, minus createdAt — a
        // server timestamp shouldn't be resurrected as an old value.
        val snapshot = habit.copy(createdAt = null)
        store?.remove(habit.id)
        toast(
            "toast.deleted", listOf("name" to habit.name),
            actionKey = "toast.undo", action = { store?.create(snapshot) },
        )
    }

    /**
     * Persist a new order from a list of ids.
     *
     * Rewrites every active habit's `order` to 0…n-1 rather than nudging single
     * values, so ties and the gaps an import can leave never end up with two
     * habits fighting over the same slot.
     */
    fun commitOrder(ids: List<String>) {
        val byId = _state.value.habits.associateBy { it.id }
        val ordered = LinkedHashSet<Habit>()
        ids.forEach { id -> byId[id]?.takeIf { !it.archived }?.let { ordered.add(it) } }
        _state.value.active.forEach { ordered.add(it) }

        val ops = ordered.mapIndexedNotNull { i, h ->
            if (h.order != i) WriteOp.Order(h.id, i) else null
        }
        if (ops.isEmpty()) return // nothing actually moved
        viewModelScope.launch {
            try { store?.writeMany(ops) } catch (e: Exception) {
                Log.e(TAG, "Reorder failed", e); toast("err.save", isError = true)
            }
        }
    }

    fun moveHabit(id: String, delta: Int) {
        val list = _state.value.active
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        val ids = list.map { it.id }.toMutableList()
        ids[i] = ids[j].also { ids[j] = ids[i] }
        commitOrder(ids)
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                store?.writeMany(_state.value.habits.map { WriteOp.Delete(it.id) })
                toast("toast.cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Clear failed", e); toast("err.delete", isError = true)
            }
        }
    }

    // ------------------------------------------------------------
    //  Backup
    // ------------------------------------------------------------

    fun exportTo(uri: Uri, csv: Boolean) {
        viewModelScope.launch {
            try {
                val habits = _state.value.habits
                val text = if (csv) Backup.toCsv(habits) else Backup.toJson(habits)
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("no output stream")
                toast("toast.exported")
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e); toast("err.save", isError = true)
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("no input stream")
                val incoming = Backup.fromJson(text)
                if (incoming == null) { toast("err.import", isError = true); return@launch }
                if (incoming.isEmpty()) { toast("err.importEmpty", isError = true); return@launch }
                val result = Backup.merge(_state.value.habits, incoming)
                if (result.ops.isEmpty()) { toast("toast.imported", listOf("n" to 0)); return@launch }
                store?.writeMany(result.ops)
                val n = result.added + result.merged
                toast(Strings.countKey("toast.imported", n), listOf("n" to n))
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e); toast("err.import", isError = true)
            }
        }
    }

    fun suggestedFileName(csv: Boolean): String =
        "streak-${if (csv) "" else "backup-"}${_state.value.today}.${if (csv) "csv" else "json"}"

    // ------------------------------------------------------------
    //  Toasts
    // ------------------------------------------------------------

    fun toast(
        key: String,
        params: List<Pair<String, Any>> = emptyList(),
        actionKey: String? = null,
        action: (() -> Unit)? = null,
        isError: Boolean = false,
    ) {
        _toasts.value = Toast(System.nanoTime(), key, params, actionKey, action, isError)
    }

    fun toastShown(t: Toast) {
        _toasts.compareAndSet(t, null)
    }

    /**
     * Ask the launcher to pin the widget.
     *
     * Widgets are notoriously undiscoverable — most people never long-press
     * their home screen — so the app offers to place one directly. Not every
     * launcher supports pinning, hence [canPinWidget].
     */
    fun requestPinWidget(context: Context) {
        // Pinning arrived in API 26; below that the only route is the
        // launcher's own widget picker, so canPinWidget() hides the button.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            val provider = android.content.ComponentName(
                context, com.hanifedma.streak.widget.HabitWidgetReceiver::class.java,
            )
            if (manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(provider, null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't request widget pin", e)
        }
    }

    fun canPinWidget(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            android.appwidget.AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
        } catch (e: Exception) {
            false
        }
    }

    /** A fresh habit pre-filled for the editor. */
    fun blankHabit(): Habit {
        val s = _state.value
        return HabitFactory.normalize(
            id = Backup.newId(),
            name = "",
            color = Habits.COLORS[s.active.size % Habits.COLORS.size],
            startDate = s.today,
        )
    }

    /** Current month, for the stats calendar. */
    fun currentMonth(): Pair<Int, Int> {
        val c = Habits.parseKey(_state.value.today)
        return c.get(Calendar.YEAR) to c.get(Calendar.MONTH)
    }

    private companion object { const val TAG = "StreakViewModel" }
}
