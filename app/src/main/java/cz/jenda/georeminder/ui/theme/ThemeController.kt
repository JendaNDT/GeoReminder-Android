package cz.jenda.georeminder.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import cz.jenda.georeminder.R
import cz.jenda.georeminder.data.SharedStorage
import kotlinx.coroutines.flow.MutableStateFlow

/** Režim vzhledu aplikace (rozšíření Android verze – iOS se řídí jen systémem). */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK, NEUTRAL, GLASS;

    @get:StringRes
    val labelRes: Int
        get() = when (this) {
            SYSTEM -> R.string.theme_system
            LIGHT -> R.string.theme_light
            DARK -> R.string.theme_dark
            NEUTRAL -> R.string.theme_neutral
            GLASS -> R.string.theme_glass
        }
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
        context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY, raw)
        }
    }
}
