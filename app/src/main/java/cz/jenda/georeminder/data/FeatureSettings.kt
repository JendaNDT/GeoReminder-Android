package cz.jenda.georeminder.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** Rozšiřující nastavení funkcí aplikace GeoReminder. */
object FeatureSettings {
    private const val KEY_TTS_ENABLED = "ttsEnabled"
    private const val KEY_TTS_FULL_TEXT = "ttsReadFullText"
    private const val KEY_GROUP_BY_PLACE = "groupByPlace"

    val ttsEnabled = MutableStateFlow(false)
    val ttsReadFullText = MutableStateFlow(false)
    val groupByPlace = MutableStateFlow(false)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
        ttsEnabled.value = prefs.getBoolean(KEY_TTS_ENABLED, false)
        ttsReadFullText.value = prefs.getBoolean(KEY_TTS_FULL_TEXT, false)
        groupByPlace.value = prefs.getBoolean(KEY_GROUP_BY_PLACE, false)
    }

    fun setTtsEnabled(context: Context, enabled: Boolean) {
        ttsEnabled.value = enabled
        save(context, KEY_TTS_ENABLED, enabled)
    }

    fun setTtsReadFullText(context: Context, readFull: Boolean) {
        ttsReadFullText.value = readFull
        save(context, KEY_TTS_FULL_TEXT, readFull)
    }

    fun setGroupByPlace(context: Context, group: Boolean) {
        groupByPlace.value = group
        save(context, KEY_GROUP_BY_PLACE, group)
    }

    private fun save(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }
}
