package cz.jenda.georeminder.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Volitelné funkce aplikace (rozšíření Android verze – iOS je nemá).
 * Drží přepínače a ukládá je mezi spuštěními do stejných SharedPreferences
 * jako zbytek nastavení. Vzor: [cz.jenda.georeminder.ui.theme.ThemeController].
 *
 * Výchozí stav všech přepínačů je VYPNUTO – funkce nesmí měnit chování
 * připomínek, dokud si je uživatel sám nezapne.
 */
object FeatureSettings {
    private const val KEY_READ_ALOUD = "readAloud"
    private const val KEY_READ_ALOUD_FULL = "readAloudFullText"

    /** Číst připomínku nahlas po jejím spuštění (systémové TTS, lokálně). */
    val readAloud = MutableStateFlow(false)

    /** Číst i celý text připomínky (jinak jen název). Platí, když je [readAloud]. */
    val readAloudFullText = MutableStateFlow(false)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
        readAloud.value = prefs.getBoolean(KEY_READ_ALOUD, false)
        readAloudFullText.value = prefs.getBoolean(KEY_READ_ALOUD_FULL, false)
    }

    fun setReadAloud(context: Context, value: Boolean) {
        readAloud.value = value
        write(context, KEY_READ_ALOUD, value)
    }

    fun setReadAloudFullText(context: Context, value: Boolean) {
        readAloudFullText.value = value
        write(context, KEY_READ_ALOUD_FULL, value)
    }

    private fun write(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }
}
