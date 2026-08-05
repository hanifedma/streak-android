package com.hanifedma.streak.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hanifedma.streak.BuildConfig
import com.hanifedma.streak.core.Habit
import com.hanifedma.streak.i18n.Lang
import com.hanifedma.streak.i18n.Strings.t
import com.hanifedma.streak.ui.screens.AboutSheet
import com.hanifedma.streak.ui.screens.ArchivedSheet
import com.hanifedma.streak.ui.screens.CellSheet
import com.hanifedma.streak.ui.screens.EditorSheet
import com.hanifedma.streak.ui.screens.HabitsGridScreen
import com.hanifedma.streak.ui.screens.LoginScreen
import com.hanifedma.streak.ui.screens.ReorderSheet
import com.hanifedma.streak.ui.screens.SettingsSheet
import com.hanifedma.streak.ui.screens.StatsPane
import com.hanifedma.streak.ui.screens.TodayScreen
import com.hanifedma.streak.ui.theme.Streak
import com.hanifedma.streak.ui.theme.StreakTheme

/** Which overlay is open, if any. */
private sealed class Sheet {
    data class Editor(val habit: Habit, val isNew: Boolean) : Sheet()
    data class Cell(val habit: Habit, val dayKey: String) : Sheet()
    data class Stats(val habitId: String) : Sheet()
    data class Confirm(
        val message: String, val confirmLabel: String, val onConfirm: () -> Unit,
    ) : Sheet()
    object Settings : Sheet()
    object Archived : Sheet()
    object About : Sheet()
    object Reorder : Sheet()
}

/**
 * The app shell.
 *
 * Layout adapts to the window rather than to "phone vs tablet": below 600dp a
 * bottom bar, above it a navigation rail, and above 840dp a two-pane list +
 * stats so a tablet's width is actually used instead of stretching one column.
 * Driven by the window's own width, so it is also correct for a phone in
 * landscape, a foldable, and a freeform/split-screen window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakApp(vm: StreakViewModel, widthDp: Int) {
    val state by vm.state.collectAsStateWithLifecycle()
    val toast by vm.toasts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    StreakTheme(darkTheme = state.dark) {
        val c = Streak.colors
        val lang = state.lang
        val snackbar = remember { SnackbarHostState() }

        var sheet by remember { mutableStateOf<Sheet?>(null) }
        var selectedHabitId by remember { mutableStateOf<String?>(null) }

        val compact = widthDp < 600
        val expanded = widthDp >= 840

        // On a wide window the stats pane is always visible, so give it
        // something to show rather than an empty column.
        LaunchedEffect(expanded, state.active.firstOrNull()?.id) {
            if (expanded && selectedHabitId == null) {
                selectedHabitId = state.active.firstOrNull()?.id
            }
        }

        // Roll the day over if the app is left open past midnight.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val obs = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) vm.refreshToday()
            }
            lifecycleOwner.lifecycle.addObserver(obs)
            onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
        }

        // Backup file pickers.
        val exportJson = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri -> uri?.let { vm.exportTo(it, csv = false) } }
        val exportCsv = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv")
        ) { uri -> uri?.let { vm.exportTo(it, csv = true) } }
        val importFile = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> uri?.let { vm.importFrom(it) } }

        // Toasts → snackbar, with the Undo action wired through.
        LaunchedEffect(toast) {
            val tt = toast ?: return@LaunchedEffect
            val result = snackbar.showSnackbar(
                message = t(lang, tt.messageKey, *tt.params.toTypedArray()),
                actionLabel = tt.actionKey?.let { t(lang, it) },
                duration = if (tt.actionKey != null) SnackbarDuration.Long else SnackbarDuration.Short,
                withDismissAction = false,
            )
            if (result == SnackbarResult.ActionPerformed) tt.action?.invoke()
            vm.toastShown(tt)
        }

        when (state.screen) {
            Screen.LOADING -> Box(
                Modifier.fillMaxSize().background(c.bg),
                contentAlignment = Alignment.Center,
            ) { Text("✔", color = c.accent, fontSize = 40.sp) }

            Screen.LOGIN -> LoginScreen(
                lang = lang,
                firebaseAvailable = state.firebaseAvailable,
                onGoogle = { vm.signIn(context) },
                onLocal = { vm.enterLocalMode() },
            )

            Screen.APP -> Scaffold(
                containerColor = c.bg,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (compact) {
                        NavigationBar(containerColor = c.surface) {
                            NavigationBarItem(
                                selected = state.tab == Tab.TODAY,
                                onClick = { vm.setTab(Tab.TODAY) },
                                icon = { Text("✔", fontSize = 18.sp) },
                                label = { Text(t(lang, "tab.today")) },
                                colors = navItemColors(),
                            )
                            NavigationBarItem(
                                selected = state.tab == Tab.HABITS,
                                onClick = { vm.setTab(Tab.HABITS) },
                                icon = { Text("▦", fontSize = 18.sp) },
                                label = { Text(t(lang, "tab.habits")) },
                                colors = navItemColors(),
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (state.tab == Tab.TODAY && state.active.isNotEmpty()) return@Scaffold
                    if (state.tab == Tab.HABITS) {
                        FloatingActionButton(
                            onClick = { sheet = Sheet.Editor(vm.blankHabit(), true) },
                            containerColor = c.accent,
                            contentColor = c.accentContrast,
                        ) { Text("＋", fontSize = 24.sp) }
                    }
                },
            ) { inner ->
                Row(Modifier.fillMaxSize()) {
                    if (!compact) {
                        NavigationRail(containerColor = c.surface) {
                            Spacer(Modifier.height(8.dp))
                            NavigationRailItem(
                                selected = state.tab == Tab.TODAY,
                                onClick = { vm.setTab(Tab.TODAY) },
                                icon = { Text("✔", fontSize = 18.sp) },
                                label = { Text(t(lang, "tab.today")) },
                                colors = railItemColors(),
                            )
                            NavigationRailItem(
                                selected = state.tab == Tab.HABITS,
                                onClick = { vm.setTab(Tab.HABITS) },
                                icon = { Text("▦", fontSize = 18.sp) },
                                label = { Text(t(lang, "tab.habits")) },
                                colors = railItemColors(),
                            )
                        }
                    }

                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        TopBar(
                            state = state,
                            lang = lang,
                            compact = compact,
                            onToggleLang = { vm.toggleLang() },
                            onToggleTheme = { vm.setDark(!state.dark) },
                            onSignIn = { vm.signIn(context) },
                            onSignOut = { vm.signOut(context) },
                            onSettings = { sheet = Sheet.Settings },
                            onArchived = { sheet = Sheet.Archived },
                            onAbout = { sheet = Sheet.About },
                        )

                        if (state.tab == Tab.HABITS) {
                            HabitsToolbar(
                                state = state, lang = lang,
                                onSearch = { vm.setSearch(it) },
                                onReorder = { sheet = Sheet.Reorder },
                                onNew = { sheet = Sheet.Editor(vm.blankHabit(), true) },
                            )
                        }

                        Box(Modifier.weight(1f)) {
                            val padding = PaddingValues(
                                start = 12.dp, end = 12.dp, top = 8.dp,
                                bottom = inner.calculateBottomPadding() + 88.dp,
                            )
                            when (state.tab) {
                                Tab.TODAY -> TodayScreen(
                                    state = state, names = vm.names, contentPadding = padding,
                                    onToggle = { id -> vm.toggleEntry(id, state.today) },
                                    onBump = { id, d -> vm.bumpValue(id, state.today, d) },
                                    onOpenStats = { id ->
                                        if (expanded) selectedHabitId = id else sheet = Sheet.Stats(id)
                                    },
                                    onEditValue = { h -> sheet = Sheet.Cell(h, state.today) },
                                    onNewHabit = { sheet = Sheet.Editor(vm.blankHabit(), true) },
                                )
                                Tab.HABITS -> HabitsGridScreen(
                                    state = state, names = vm.names, contentPadding = padding,
                                    onToggle = { id, key -> vm.toggleEntry(id, key) },
                                    onCellLongPress = { h, key -> sheet = Sheet.Cell(h, key) },
                                    onOpenStats = { id ->
                                        if (expanded) selectedHabitId = id else sheet = Sheet.Stats(id)
                                    },
                                    onNeedMoreDays = { vm.extendGrid() },
                                    compact = compact,
                                )
                            }
                        }
                    }

                    // Two-pane on a wide window: the list keeps its place while
                    // stats sit alongside, rather than covering everything.
                    if (expanded) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
                        Box(Modifier.width(400.dp).fillMaxHeight().background(c.bg)) {
                            StatsPane(
                                habit = vm.habitById(selectedHabitId),
                                today = state.today, weekStart = state.weekStart,
                                lang = lang, names = vm.names,
                                contentPadding = PaddingValues(top = 20.dp),
                                onEdit = { h -> sheet = Sheet.Editor(h, false) },
                            )
                        }
                    }
                }
            }
        }

        // ---------------- overlays ----------------
        val current = sheet
        if (current != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { sheet = null },
                sheetState = sheetState,
                containerColor = c.elevated,
                // Sheets can be tall (the editor, stats); let them use the
                // full height on a small screen instead of scrolling in a
                // letterbox.
                modifier = Modifier.fillMaxWidth(),
            ) {
                when (current) {
                    is Sheet.Editor -> EditorSheet(
                        initial = current.habit, isNew = current.isNew,
                        lang = lang, names = vm.names,
                        today = state.today, weekStart = state.weekStart,
                        onSave = { h -> vm.saveHabit(h, current.isNew); sheet = null },
                        onDelete = { h ->
                            sheet = Sheet.Confirm(
                                t(lang, "confirm.delete", "name" to h.name),
                                t(lang, "confirm.yes"),
                            ) { vm.deleteHabit(h); selectedHabitId = null }
                        },
                        onArchive = { h, on -> vm.setArchived(h, on); sheet = null },
                        onDismiss = { sheet = null },
                    )
                    is Sheet.Cell -> CellSheet(
                        habit = current.habit, dayKey = current.dayKey,
                        lang = lang, names = vm.names,
                        onSet = { v -> vm.setEntry(current.habit.id, current.dayKey, v); sheet = null },
                        onDismiss = { sheet = null },
                    )
                    is Sheet.Stats -> Box(Modifier.fillMaxSize()) {
                        StatsPane(
                            habit = vm.habitById(current.habitId),
                            today = state.today, weekStart = state.weekStart,
                            lang = lang, names = vm.names,
                            onEdit = { h -> sheet = Sheet.Editor(h, false) },
                        )
                    }
                    is Sheet.Confirm -> ConfirmSheet(
                        message = current.message,
                        confirmLabel = current.confirmLabel,
                        cancelLabel = t(lang, "confirm.no"),
                        onConfirm = { current.onConfirm(); sheet = null },
                        onDismiss = { sheet = null },
                    )
                    Sheet.Settings -> SettingsSheet(
                        state = state, lang = lang,
                        onDark = { vm.setDark(it) },
                        onLang = { vm.setLang(it) },
                        onWeekStart = { vm.setWeekStart(it) },
                        onExportJson = { exportJson.launch(vm.suggestedFileName(false)) },
                        onExportCsv = { exportCsv.launch(vm.suggestedFileName(true)) },
                        onImport = { importFile.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        onAddWidget = if (vm.canPinWidget(context)) {
                            { vm.requestPinWidget(context); sheet = null }
                        } else null,
                        onClearAll = {
                            sheet = Sheet.Confirm(
                                t(lang, "settings.clearConfirm"), t(lang, "confirm.yes"),
                            ) { vm.clearAll(); selectedHabitId = null }
                        },
                        onDismiss = { sheet = null },
                    )
                    Sheet.Archived -> ArchivedSheet(
                        archived = state.archived, lang = lang,
                        onUnarchive = { h -> vm.setArchived(h, false); sheet = null },
                        onDelete = { h ->
                            sheet = Sheet.Confirm(
                                t(lang, "confirm.delete", "name" to h.name),
                                t(lang, "confirm.yes"),
                            ) { vm.deleteHabit(h) }
                        },
                        onDismiss = { sheet = null },
                    )
                    Sheet.About -> AboutSheet(
                        lang = lang, version = BuildConfig.VERSION_NAME,
                        onDismiss = { sheet = null },
                    )
                    Sheet.Reorder -> ReorderSheet(
                        habits = state.active, lang = lang,
                        onMove = { id, d -> vm.moveHabit(id, d) },
                        onDismiss = { sheet = null },
                    )
                }
            }
        }
    }
}

/** Navigation colours: Streak's accent, not Material's default purple. */
@Composable
private fun navItemColors() = androidx.compose.material3.NavigationBarItemDefaults.colors(
    selectedIconColor = Streak.colors.accentContrast,
    selectedTextColor = Streak.colors.accent,
    indicatorColor = Streak.colors.accent,
    unselectedIconColor = Streak.colors.muted,
    unselectedTextColor = Streak.colors.muted,
)

@Composable
private fun railItemColors() = androidx.compose.material3.NavigationRailItemDefaults.colors(
    selectedIconColor = Streak.colors.accentContrast,
    selectedTextColor = Streak.colors.accent,
    indicatorColor = Streak.colors.accent,
    unselectedIconColor = Streak.colors.muted,
    unselectedTextColor = Streak.colors.muted,
)

@Composable
private fun ConfirmSheet(
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Streak.colors
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(message, color = c.text, fontSize = 16.sp)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(cancelLabel, color = c.muted)
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = c.danger, contentColor = androidx.compose.ui.graphics.Color.White,
                ),
            ) { Text(confirmLabel) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Header: identity, language, theme and the overflow menu. */
@Composable
private fun TopBar(
    state: UiState,
    lang: Lang,
    compact: Boolean,
    onToggleLang: () -> Unit,
    onToggleTheme: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSettings: () -> Unit,
    onArchived: () -> Unit,
    onAbout: () -> Unit,
) {
    val c = Streak.colors
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            // Edge-to-edge draws behind the status bar; without this the title
            // and the pills sit underneath the clock.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            t(lang, "app.name"),
            color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )

        PillButton(if (lang == Lang.KO) "한" else "EN", onToggleLang)
        Spacer(Modifier.width(7.dp))
        PillButton(if (state.dark) "🌙" else "☀️", onToggleTheme)
        Spacer(Modifier.width(7.dp))

        if (state.mode == "cloud") {
            // Avatar only — the name is the first thing to go when space is
            // tight, and the account is still reachable from the menu.
            Box(
                Modifier
                    .size(34.dp)
                    .background(c.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (state.userName?.trim()?.firstOrNull() ?: '?').uppercase(),
                    color = c.accentContrast, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
            }
            if (!compact) {
                Spacer(Modifier.width(8.dp))
                Text(
                    state.userName ?: "",
                    color = c.muted, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(120.dp),
                )
            }
        } else if (state.firebaseAvailable) {
            PillButton(t(lang, "signin.short"), onSignIn, accent = true)
        }

        Spacer(Modifier.width(7.dp))
        Box {
            PillButton("⋮", { menu = true })
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = c.elevated,
            ) {
                DropdownMenuItem(
                    text = { Text(t(lang, "menu.settings"), color = c.text) },
                    onClick = { menu = false; onSettings() },
                )
                DropdownMenuItem(
                    text = { Text(t(lang, "menu.archived"), color = c.text) },
                    onClick = { menu = false; onArchived() },
                )
                DropdownMenuItem(
                    text = { Text(t(lang, "menu.about"), color = c.text) },
                    onClick = { menu = false; onAbout() },
                )
                if (state.mode == "cloud") {
                    DropdownMenuItem(
                        text = { Text(t(lang, "signout"), color = c.text) },
                        onClick = { menu = false; onSignOut() },
                    )
                }
            }
        }
    }
    if (state.offline) {
        Text(
            t(lang, "toast.offline"),
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface2)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            color = c.muted, fontSize = 11.sp,
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit, accent: Boolean = false) {
    val c = Streak.colors
    Box(
        Modifier
            .height(38.dp)
            .background(if (accent) c.accent else c.surface2, RoundedCornerShape(11.dp))
            .border(1.dp, if (accent) c.accent else c.border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (accent) c.accentContrast else c.text,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The Habits tab's own toolbar: search and reorder. */
@Composable
private fun HabitsToolbar(
    state: UiState,
    lang: Lang,
    onSearch: (String) -> Unit,
    onReorder: () -> Unit,
    onNew: () -> Unit,
) {
    val c = Streak.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.search,
                onValueChange = onSearch,
                placeholder = { Text(t(lang, "habits.search"), color = c.faint, fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).height(52.dp),
            )
            Spacer(Modifier.width(8.dp))
            PillButton("⇅ " + t(lang, "habits.reorder"), onReorder)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            t(lang, "habits.count", "n" to state.active.size),
            color = c.muted, fontSize = 12.sp,
        )
    }
}
