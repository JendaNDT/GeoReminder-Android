package cz.jenda.georeminder.ui.theme

import android.content.Context
import cz.jenda.georeminder.data.SharedStorage
import kotlinx.coroutines.flow.MutableStateFlow

/** Režim vzhledu aplikace. Uživatelské názvy jsou v lokalizovaných resources. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK, NEUTRAL, GLASS,
}

/** Drží zvolený vzhled a ukládá ho mezi spuštěními. */
object ThemeController {
    private const val KEY = "appearanceMode"

    val mode = MutableStateFlow(ThemeMode.SYSTEM)

    fun init(context: Context) {
        val raw = context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        mode.value = when (raw) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            "neutral" -> ThemeMode.NEUTRAL
            "glass" -> ThemeMode.GLASS
            else -> ThemeMode.SYSTEM
        }
    }

    fun set(context: Context, newMode: ThemeMode) {
        mode.value = newMode
        val raw = when (newMode) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.NEUTRAL -> "neutral"
            ThemeMode.GLASS -> "glass"
            ThemeMode.SYSTEM -> "system"
        }
        context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, raw)
            .apply()
    }
}
