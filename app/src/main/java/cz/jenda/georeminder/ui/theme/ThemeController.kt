package cz.jenda.georeminder.ui.theme

import android.content.Context
import cz.jenda.georeminder.data.SharedStorage
import kotlinx.coroutines.flow.MutableStateFlow

/** Režim vzhledu aplikace (rozšíření Android verze – iOS se řídí jen systémem). */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK, NEUTRAL;

    val label: String
        get() = when (this) {
            SYSTEM -> "Podle systému"
            LIGHT -> "Světlý"
            DARK -> "Tmavý"
            NEUTRAL -> "Neutrální (teplý)"
        }
}

/** Drží zvolený vzhled a ukládá ho mezi spuštěními. */
object ThemeController {
    private const val KEY = "appearanceMode"

    val mode = MutableStateFlow(ThemeMode.SYSTEM)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        mode.value = when (raw) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            "neutral" -> ThemeMode.NEUTRAL
            else -> ThemeMode.SYSTEM
        }
        // Migrace odstraněného Glass režimu: uživatele vrátit na systémový
        // vzhled a zároveň přepsat starou hodnotu, aby se nevracela.
        if (raw == "glass") {
            prefs.edit().putString(KEY, "system").apply()
        }
    }

    fun set(context: Context, newMode: ThemeMode) {
        mode.value = newMode
        val raw = when (newMode) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.NEUTRAL -> "neutral"
            ThemeMode.SYSTEM -> "system"
        }
        context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, raw)
            .apply()
    }
}
