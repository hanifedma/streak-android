package com.hanifedma.streak.i18n

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Interface translations, Korean ⇄ English.
 *
 * Korean is the default and the choice is remembered per device, exactly as on
 * the web. This lives in Kotlin rather than res/values-ko/ because the language
 * is switched *inside* the app, independently of the device locale — using
 * resource qualifiers would tie it to system settings instead.
 *
 * Only the interface is translated; habit names you type are never touched.
 */
enum class Lang(val code: String, val locale: Locale) {
    KO("ko", Locale.KOREA),
    EN("en", Locale.US);

    companion object {
        val DEFAULT = KO
        fun from(code: String?): Lang = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

object Strings {

    private val ko: Map<String, String> = mapOf(
        "app.name" to "Streak",

        // --- chrome ---
        "tab.today" to "오늘",
        "tab.habits" to "습관",
        "theme.light" to "밝게",
        "theme.dark" to "어둡게",
        "theme.title" to "밝은 화면 / 어두운 화면 전환",
        "lang.title" to "언어 변경",
        "menu.title" to "메뉴",
        "menu.settings" to "설정",
        "menu.archived" to "보관함",
        "menu.about" to "정보",
        "signin.short" to "로그인",
        "signout" to "로그아웃",
        "chip.local" to "이 기기",
        "chip.local.title" to "이 기기에만 저장됩니다. 로그인하면 모든 기기에서 동기화됩니다.",

        // --- login ---
        "login.h1" to "습관을 이어가세요.",
        "login.sub" to "매일 체크하고, 연속 기록을 쌓고, 모든 기기에서 바로 이어서 보세요.",
        "login.google" to "Google로 계속하기",
        "login.local" to "로그인 없이 이 기기에서만 사용하기",
        "login.privacy" to "기록은 본인만 볼 수 있습니다.",
        "setup.h1" to "설정이 한 번 필요합니다",
        "setup.p1" to "아직 Firebase에 연결되지 않았습니다. app/google-services.json 파일을 넣고 다시 빌드하세요.",
        "setup.p2" to "자세한 방법은 README.md에 있습니다. 약 5분이면 되고 무료입니다.",

        // --- today ---
        "today.progress" to "{done} / {due} 완료",
        "today.allDone" to "오늘 할 일을 모두 마쳤습니다 🎉",
        "today.none" to "오늘 예정된 습관이 없습니다.",
        "today.empty" to "아직 습관이 없습니다.",
        "today.emptySub" to "작은 것 하나부터 시작해 보세요.",
        "today.nothingDue" to "오늘은 쉬는 날입니다.",
        "today.nothingDueSub" to "오늘 예정된 습관이 없습니다.",

        // --- habits / grid ---
        "habits.title" to "습관",
        "habits.add" to "새 습관",
        "habits.empty" to "아직 습관이 없습니다.",
        "habits.emptySub" to "‘새 습관’을 눌러 시작해 보세요.",
        "habits.search" to "습관 검색…",
        "habits.count" to "습관 {n}개",
        "habits.count.one" to "습관 {n}개",
        "habits.jumpToday" to "오늘로",
        "habits.reorder" to "순서 바꾸기",
        "habits.scrollHint" to "옆으로 넘기면 지난 기록을 볼 수 있습니다",
        "reorder.hint" to "손잡이를 끌어서 순서를 바꾸세요.",
        "reorder.up" to "위로",
        "reorder.down" to "아래로",
        "reorder.done" to "완료",

        // --- cells ---
        "cell.done" to "완료",
        "cell.skip" to "건너뜀",
        "cell.none" to "미완료",
        "cell.partial" to "진행 중",
        "cell.miss" to "초과",
        "cell.unscheduled" to "예정 없음",
        "cell.prestart" to "시작 전",
        "cell.setValue" to "기록",
        "cell.markDone" to "완료로 표시",
        "cell.markSkip" to "건너뛰기",
        "cell.clear" to "지우기",
        "cell.save" to "저장",

        // --- editor ---
        "editor.new" to "새 습관",
        "editor.edit" to "습관 수정",
        "editor.name" to "이름",
        "editor.namePh" to "예: 물 2리터 마시기",
        "editor.color" to "색상",
        "editor.type" to "유형",
        "editor.type.binary" to "예 / 아니오",
        "editor.type.measurable" to "측정형",
        "editor.goal" to "목표",
        "editor.goal.at_least" to "이상",
        "editor.goal.at_most" to "이하",
        "editor.target" to "목표값",
        "editor.unit" to "단위",
        "editor.unitPh" to "예: 쪽, km, 분",
        "editor.freq" to "반복",
        "editor.freq.daily" to "매일",
        "editor.freq.weekdays" to "요일 선택",
        "editor.freq.weekly" to "주 {n}회",
        "editor.freq.weeklyLabel" to "주 몇 회",
        "editor.pickDays" to "요일을 하나 이상 선택하세요.",
        "editor.start" to "시작일",
        "editor.startHint" to "시작일 이전 날짜는 미완료로 계산되지 않습니다.",
        "editor.save" to "저장",
        "editor.cancel" to "취소",
        "editor.delete" to "삭제",
        "editor.archive" to "보관",
        "editor.unarchive" to "보관 해제",
        "editor.nameRequired" to "이름을 입력하세요.",
        "editor.limit" to "습관은 최대 {n}개까지 만들 수 있습니다.",

        // --- stats ---
        "stats.title" to "통계",
        "stats.current" to "현재 연속",
        "stats.best" to "최고 연속",
        "stats.rate30" to "최근 30일",
        "stats.total" to "총 완료",
        "stats.unit.day" to "일",
        "stats.unit.week" to "주",
        "stats.unit.times" to "회",
        "stats.byWeekday" to "요일별 달성률",
        "stats.prevMonth" to "이전 달",
        "stats.nextMonth" to "다음 달",
        "stats.since" to "{date}부터",
        "stats.noData" to "아직 기록이 없습니다.",
        "stats.edit" to "수정",
        "stats.sum" to "합계",

        // --- settings ---
        "settings.title" to "설정",
        "settings.weekStart" to "주 시작 요일",
        "settings.weekStart.0" to "일요일",
        "settings.weekStart.1" to "월요일",
        "settings.display" to "표시",
        "settings.language" to "언어",
        "settings.theme" to "테마",
        "settings.data" to "데이터",
        "settings.export" to "백업 내보내기 (JSON)",
        "settings.exportCsv" to "표로 내보내기 (CSV)",
        "settings.import" to "백업 가져오기",
        "settings.importHint" to "같은 이름의 습관은 기록이 합쳐집니다.",
        "settings.danger" to "위험",
        "settings.clear" to "모든 습관 삭제",
        "settings.clearConfirm" to "모든 습관과 기록을 삭제할까요? 되돌릴 수 없습니다.",
        "settings.done" to "완료",
        "settings.addWidget" to "홈 화면에 위젯 추가",
        "settings.addWidgetHint" to "홈 화면에서 바로 오늘의 습관을 체크할 수 있습니다.",
        "settings.storage" to "저장 위치",
        "settings.storage.cloud" to "Google 계정에 동기화 중",
        "settings.storage.local" to "이 기기에만 저장",

        // --- archived / about ---
        "archived.title" to "보관함",
        "archived.empty" to "보관된 습관이 없습니다.",
        "archived.hint" to "보관한 습관은 목록에서 숨겨지지만 기록은 그대로 남습니다.",
        "about.title" to "정보",
        "about.p1" to "Streak은 가볍고 빠른 습관 추적기입니다.",
        "about.p2" to "기록은 Google 계정에 안전하게 저장되며 본인만 볼 수 있습니다. 오프라인에서도 사용할 수 있고, 인터넷이 연결되면 자동으로 동기화됩니다.",
        "about.version" to "버전",
        "about.web" to "웹 버전",

        // --- toasts / errors ---
        "toast.saved" to "저장했습니다",
        "toast.deleted" to "‘{name}’을(를) 삭제했습니다",
        "toast.archived" to "‘{name}’을(를) 보관했습니다",
        "toast.unarchived" to "‘{name}’을(를) 보관 해제했습니다",
        "toast.undo" to "실행 취소",
        "toast.imported" to "습관 {n}개를 가져왔습니다",
        "toast.imported.one" to "습관 {n}개를 가져왔습니다",
        "toast.exported" to "백업을 내보냈습니다",
        "toast.cleared" to "모두 삭제했습니다",
        "toast.offline" to "오프라인 · 저장된 기록을 보는 중",
        "toast.moved" to "이 기기의 기록을 계정으로 옮겼습니다",

        "err.load" to "기록을 불러오지 못했습니다.",
        "err.save" to "저장하지 못했습니다. 연결을 확인해 주세요.",
        "err.delete" to "삭제하지 못했습니다.",
        "err.import" to "백업 파일을 읽을 수 없습니다.",
        "err.importEmpty" to "파일에 습관이 없습니다.",
        "err.auth.cancelled" to "로그인을 취소했습니다.",
        "err.auth.network" to "인터넷에 연결되어 있지 않습니다. 로그인 없이 이 기기에서 계속 사용할 수 있습니다.",
        "err.auth.noAccount" to "이 기기에 Google 계정이 없습니다. 기기 설정에서 계정을 추가해 주세요.",
        "err.auth.generic" to "로그인하지 못했습니다. 다시 시도해 주세요.",
        "err.signout" to "로그아웃하지 못했습니다.",

        // --- common ---
        "confirm.delete" to "‘{name}’을(를) 삭제할까요? 기록도 함께 사라집니다.",
        "confirm.yes" to "삭제",
        "confirm.no" to "취소",
        "common.ok" to "확인",
        "common.cancel" to "취소",
        "common.close" to "닫기",
        "common.back" to "뒤로",
        "common.today" to "오늘",
    )

    private val en: Map<String, String> = mapOf(
        "app.name" to "Streak",

        "tab.today" to "Today",
        "tab.habits" to "Habits",
        "theme.light" to "Light",
        "theme.dark" to "Dark",
        "theme.title" to "Toggle light / dark mode",
        "lang.title" to "Change language",
        "menu.title" to "Menu",
        "menu.settings" to "Settings",
        "menu.archived" to "Archived",
        "menu.about" to "About",
        "signin.short" to "Sign in",
        "signout" to "Sign out",
        "chip.local" to "This device",
        "chip.local.title" to "Saved on this device only. Sign in to sync across all your devices.",

        "login.h1" to "Keep the streak going.",
        "login.sub" to "Check in daily, build streaks, and pick up right where you left off on any device.",
        "login.google" to "Continue with Google",
        "login.local" to "or use on this device without an account",
        "login.privacy" to "Only you can see your habits.",
        "setup.h1" to "One-time setup needed",
        "setup.p1" to "This build isn't connected to Firebase yet. Drop app/google-services.json in and rebuild.",
        "setup.p2" to "Step-by-step instructions are in README.md. It takes about 5 minutes and is free.",

        "today.progress" to "{done} of {due} done",
        "today.allDone" to "Everything done for today 🎉",
        "today.none" to "Nothing scheduled today.",
        "today.empty" to "No habits yet.",
        "today.emptySub" to "Start with one small thing.",
        "today.nothingDue" to "A rest day.",
        "today.nothingDueSub" to "Nothing is scheduled for today.",

        "habits.title" to "Habits",
        "habits.add" to "New habit",
        "habits.empty" to "No habits yet.",
        "habits.emptySub" to "Press “New habit” to get started.",
        "habits.search" to "Search habits…",
        "habits.count" to "{n} habits",
        "habits.count.one" to "{n} habit",
        "habits.jumpToday" to "Today",
        "habits.reorder" to "Reorder",
        "habits.scrollHint" to "Scroll sideways to see earlier days",
        "reorder.hint" to "Drag the handle to reorder.",
        "reorder.up" to "Move up",
        "reorder.down" to "Move down",
        "reorder.done" to "Done",

        "cell.done" to "Done",
        "cell.skip" to "Skipped",
        "cell.none" to "Not done",
        "cell.partial" to "In progress",
        "cell.miss" to "Over the limit",
        "cell.unscheduled" to "Not scheduled",
        "cell.prestart" to "Before it started",
        "cell.setValue" to "Amount",
        "cell.markDone" to "Mark done",
        "cell.markSkip" to "Skip this day",
        "cell.clear" to "Clear",
        "cell.save" to "Save",

        "editor.new" to "New habit",
        "editor.edit" to "Edit habit",
        "editor.name" to "Name",
        "editor.namePh" to "e.g. Drink 2 litres of water",
        "editor.color" to "Colour",
        "editor.type" to "Type",
        "editor.type.binary" to "Yes / No",
        "editor.type.measurable" to "Measurable",
        "editor.goal" to "Goal",
        "editor.goal.at_least" to "at least",
        "editor.goal.at_most" to "at most",
        "editor.target" to "Target",
        "editor.unit" to "Unit",
        "editor.unitPh" to "e.g. pages, km, min",
        "editor.freq" to "Repeat",
        "editor.freq.daily" to "Every day",
        "editor.freq.weekdays" to "Certain days",
        "editor.freq.weekly" to "{n}× per week",
        "editor.freq.weeklyLabel" to "Times per week",
        "editor.pickDays" to "Pick at least one day.",
        "editor.start" to "Start date",
        "editor.startHint" to "Days before this are never counted as missed.",
        "editor.save" to "Save",
        "editor.cancel" to "Cancel",
        "editor.delete" to "Delete",
        "editor.archive" to "Archive",
        "editor.unarchive" to "Unarchive",
        "editor.nameRequired" to "Please enter a name.",
        "editor.limit" to "You can have at most {n} habits.",

        "stats.title" to "Stats",
        "stats.current" to "Current streak",
        "stats.best" to "Best streak",
        "stats.rate30" to "Last 30 days",
        "stats.total" to "Total done",
        "stats.unit.day" to "days",
        "stats.unit.week" to "weeks",
        "stats.unit.times" to "times",
        "stats.byWeekday" to "By weekday",
        "stats.prevMonth" to "Previous month",
        "stats.nextMonth" to "Next month",
        "stats.since" to "since {date}",
        "stats.noData" to "Nothing recorded yet.",
        "stats.edit" to "Edit",
        "stats.sum" to "Total",

        "settings.title" to "Settings",
        "settings.weekStart" to "Week starts on",
        "settings.weekStart.0" to "Sunday",
        "settings.weekStart.1" to "Monday",
        "settings.display" to "Display",
        "settings.language" to "Language",
        "settings.theme" to "Theme",
        "settings.data" to "Data",
        "settings.export" to "Export backup (JSON)",
        "settings.exportCsv" to "Export as a table (CSV)",
        "settings.import" to "Import a backup",
        "settings.importHint" to "Habits with the same name have their history merged.",
        "settings.danger" to "Danger zone",
        "settings.clear" to "Delete all habits",
        "settings.clearConfirm" to "Delete every habit and all of its history? This cannot be undone.",
        "settings.done" to "Done",
        "settings.addWidget" to "Add widget to home screen",
        "settings.addWidgetHint" to "Tick today's habits straight from your home screen.",
        "settings.storage" to "Stored in",
        "settings.storage.cloud" to "Synced to your Google account",
        "settings.storage.local" to "This device only",

        "archived.title" to "Archived",
        "archived.empty" to "Nothing archived.",
        "archived.hint" to "Archived habits are hidden from your lists, but their history is kept.",
        "about.title" to "About",
        "about.p1" to "Streak is a small, fast habit tracker.",
        "about.p2" to "Your habits are stored privately in your own Google account and only you can read them. It works offline and syncs automatically when you're back online.",
        "about.version" to "Version",
        "about.web" to "Web version",

        "toast.saved" to "Saved",
        "toast.deleted" to "Deleted “{name}”",
        "toast.archived" to "Archived “{name}”",
        "toast.unarchived" to "Restored “{name}”",
        "toast.undo" to "Undo",
        "toast.imported" to "Imported {n} habits",
        "toast.imported.one" to "Imported {n} habit",
        "toast.exported" to "Backup exported",
        "toast.cleared" to "Everything deleted",
        "toast.offline" to "Offline · showing your saved copy",
        "toast.moved" to "Moved this device's habits into your account",

        "err.load" to "Couldn't load your habits.",
        "err.save" to "Couldn't save. Please check your connection.",
        "err.delete" to "Couldn't delete that.",
        "err.import" to "That backup file couldn't be read.",
        "err.importEmpty" to "There are no habits in that file.",
        "err.auth.cancelled" to "Sign-in was cancelled.",
        "err.auth.network" to "No internet connection. You can keep using this device without an account.",
        "err.auth.noAccount" to "No Google account on this device. Add one in your device settings.",
        "err.auth.generic" to "Couldn't sign in. Please try again.",
        "err.signout" to "Couldn't sign out.",

        "confirm.delete" to "Delete “{name}”? Its history goes too.",
        "confirm.yes" to "Delete",
        "confirm.no" to "Cancel",
        "common.ok" to "OK",
        "common.cancel" to "Cancel",
        "common.close" to "Close",
        "common.back" to "Back",
        "common.today" to "Today",
    )

    private fun table(lang: Lang) = if (lang == Lang.EN) en else ko

    /** Translate. `t(lang, "today.progress", "done" to 2, "due" to 5)`. */
    fun t(lang: Lang, key: String, vararg params: Pair<String, Any>): String {
        // Fall back through the default language, then the key itself, so a
        // missing string is visible in review but never crashes or shows null.
        var s = table(lang)[key] ?: table(Lang.DEFAULT)[key] ?: key
        for ((name, value) in params) s = s.replace("{$name}", value.toString())
        return s
    }

    /**
     * A string that counts something: "1 habit" vs "2 habits".
     *
     * English needs a singular form; without one the habits toolbar read
     * "1 habits", which is the very first thing anyone sees after adding their
     * first habit. Korean has no plural, so both keys hold the same text there
     * — defined rather than left to fall back, because the fallback would
     * reach the English table and print English inside a Korean UI.
     */
    fun tCount(lang: Lang, key: String, n: Int): String = t(lang, countKey(key, n), "n" to n)

    /** The key a counted string resolves to, for callers that render later. */
    fun countKey(key: String, n: Int): String = if (n == 1) "$key.one" else key
}

/**
 * Localised date names. Built once per language rather than per call — the grid
 * asks for weekday names hundreds of times while scrolling.
 */
class DateNames(val lang: Lang) {
    private val loc = lang.locale

    /** Short weekday names, index 0 = Sunday. */
    val short: List<String> = buildWeekdays("EEE")

    /** Single-letter weekday names, index 0 = Sunday. */
    val narrow: List<String> = buildWeekdays(if (lang == Lang.KO) "EEEEE" else "EEEEE")

    /** Full weekday names, index 0 = Sunday. */
    val full: List<String> = buildWeekdays("EEEE")

    private fun buildWeekdays(pattern: String): List<String> {
        val fmt = SimpleDateFormat(pattern, loc)
        // 2024-01-07 was a Sunday — a stable anchor for generating names.
        return (0..6).map { i ->
            val c = Calendar.getInstance()
            c.clear()
            c.set(2024, 0, 7 + i)
            fmt.format(c.time)
        }
    }

    /** "2026년 8월" / "August 2026". */
    fun monthTitle(year: Int, month: Int): String {
        val c = Calendar.getInstance()
        c.clear()
        c.set(year, month, 1)
        val pattern = if (lang == Lang.KO) "yyyy년 M월" else "MMMM yyyy"
        return SimpleDateFormat(pattern, loc).format(c.time)
    }

    /** A full, readable date for headings. */
    fun longDate(key: String): String {
        val c = com.hanifedma.streak.core.Habits.parseKey(key)
        val pattern = if (lang == Lang.KO) "yyyy년 M월 d일 EEEE" else "EEEE, MMMM d, yyyy"
        return SimpleDateFormat(pattern, loc).format(c.time)
    }

    /** Format a number with the locale's own grouping. */
    fun num(v: Double): String {
        val nf = NumberFormat.getNumberInstance(loc)
        nf.maximumFractionDigits = 2
        return nf.format(v)
    }

    /** A number that fits in a narrow grid cell: 1200 → "1.2k". */
    fun compact(v: Double): String = when {
        v < 1000 -> num(Math.round(v * 100) / 100.0)
        v < 10000 -> "${Math.round(v / 100) / 10.0}k"
        v < 1000000 -> "${Math.round(v / 1000)}k"
        else -> "${Math.round(v / 100000) / 10.0}M"
    }

    /** Streak/measure units. English needs a singular; Korean does not. */
    fun unitText(lang: Lang, unitKey: String, n: Int): String {
        val word = Strings.t(lang, "stats.unit.$unitKey")
        return if (lang == Lang.EN && n == 1) word.removeSuffix("s") else word
    }
}
