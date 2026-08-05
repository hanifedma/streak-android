package com.hanifedma.streak.data

import android.content.Context
import androidx.core.content.edit
import com.hanifedma.streak.i18n.Lang

/**
 * Device-level preferences: theme, language, week start, last view.
 *
 * These stay on the device rather than syncing, matching the web app — they
 * describe how *this* screen should look, not what the habits are.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)

    var dark: Boolean
        get() = sp.getBoolean(KEY_DARK, true) // dark by default, as on the web
        set(v) = sp.edit { putBoolean(KEY_DARK, v) }

    var lang: Lang
        get() = Lang.from(sp.getString(KEY_LANG, Lang.DEFAULT.code))
        set(v) = sp.edit { putString(KEY_LANG, v.code) }

    /** 0 = Sunday, 1 = Monday. */
    var weekStart: Int
        get() = if (sp.getInt(KEY_WEEK_START, 0) == 1) 1 else 0
        set(v) = sp.edit { putInt(KEY_WEEK_START, if (v == 1) 1 else 0) }

    /** "today" or "habits". */
    var view: String
        get() = sp.getString(KEY_VIEW, "today") ?: "today"
        set(v) = sp.edit { putString(KEY_VIEW, v) }

    /** Set once the user has chosen to continue without an account, so we
     *  don't shove the sign-in screen at them again on every launch. */
    var localMode: Boolean
        get() = sp.getBoolean(KEY_LOCAL_MODE, false)
        set(v) = sp.edit { putBoolean(KEY_LOCAL_MODE, v) }

    private companion object {
        const val KEY_DARK = "dark"
        const val KEY_LANG = "lang"
        const val KEY_WEEK_START = "weekStart"
        const val KEY_VIEW = "view"
        const val KEY_LOCAL_MODE = "localMode"
    }
}
