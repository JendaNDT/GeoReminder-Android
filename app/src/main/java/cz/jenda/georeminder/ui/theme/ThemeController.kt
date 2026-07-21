package cz.jenda.georeminder.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** Režim vzhledu aplikace (rozšíření Android verze – iOS se řídí jen systémem). */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    val label: String
        get() = when (this) {
            SYSTEM -> "Podle systému"
            LIGHT -> "Světlý"
            DARK -> "Tmavý"
        }
}

/** Drží zvolený vzhled a ukládá ho mezi spuštěními. */
object ThemeController {
    private const val PREFS = "georeminder"
    private const val KEY = "appearanceMode"

    val mode = MutableStateFlow(ThemeMode.SYSTEM)

    fun init(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        mode.value = when (raw) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    fun set(context: Context, newMode: ThemeMode) {
        mode.value = newMode
        val raw = when (newMode) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.SYSTEM -> "system"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, raw)
            .apply()
    }
}
