package cz.jenda.georeminder.data

import android.content.Context
import cz.jenda.georeminder.model.AlertStyle
import cz.jenda.georeminder.model.DEFAULT_RADIUS
import kotlinx.coroutines.flow.MutableStateFlow

/** Rozšiřující nastavení funkcí aplikace GeoReminder. */
object FeatureSettings {
    private const val KEY_TTS_ENABLED = "ttsEnabled"
    private const val KEY_TTS_FULL_TEXT = "ttsReadFullText"
    private const val KEY_GROUP_BY_PLACE = "groupByPlace"
    private const val KEY_DEFAULT_RADIUS = "defaultRadius"
    private const val KEY_DEFAULT_ALERT = "defaultAlertStyle"

    private const val KEY_ADAPTIVE_POWER_SAVER = "adaptivePowerSaver"

    val ttsEnabled = MutableStateFlow(false)
    val ttsReadFullText = MutableStateFlow(false)
    val groupByPlace = MutableStateFlow(false)
    val defaultRadius = MutableStateFlow(DEFAULT_RADIUS)
    val defaultAlertStyle = MutableStateFlow(AlertStyle.DEFAULT)
    val adaptivePowerSaver = MutableStateFlow(true)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
        ttsEnabled.value = prefs.getBoolean(KEY_TTS_ENABLED, false)
        ttsReadFullText.value = prefs.getBoolean(KEY_TTS_FULL_TEXT, false)
        groupByPlace.value = prefs.getBoolean(KEY_GROUP_BY_PLACE, false)
        defaultRadius.value = prefs.getFloat(KEY_DEFAULT_RADIUS, DEFAULT_RADIUS.toFloat()).toDouble()
        adaptivePowerSaver.value = prefs.getBoolean(KEY_ADAPTIVE_POWER_SAVER, true)
        val alertRaw = prefs.getString(KEY_DEFAULT_ALERT, AlertStyle.DEFAULT.name)
        defaultAlertStyle.value = try {
            AlertStyle.valueOf(alertRaw ?: AlertStyle.DEFAULT.name)
        } catch (_: Exception) {
            AlertStyle.DEFAULT
        }
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

    fun setDefaultRadius(context: Context, radius: Double) {
        defaultRadius.value = radius
        context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_DEFAULT_RADIUS, radius.toFloat())
            .apply()
    }

    fun setDefaultAlertStyle(context: Context, style: AlertStyle) {
        defaultAlertStyle.value = style
        context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEFAULT_ALERT, style.name)
            .apply()
    }

    fun setAdaptivePowerSaver(context: Context, enabled: Boolean) {
        adaptivePowerSaver.value = enabled
        save(context, KEY_ADAPTIVE_POWER_SAVER, enabled)
    }

    private fun save(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }
}
