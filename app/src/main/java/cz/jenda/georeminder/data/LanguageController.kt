package cz.jenda.georeminder.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Řízení volby jazyka aplikace (Podle systému / Čeština / English).
 */
object LanguageController {

    const val LANG_SYSTEM = "SYSTEM"
    const val LANG_CS = "CS"
    const val LANG_EN = "EN"

    fun setAppLanguage(context: Context, langCode: String) {
        FeatureSettings.setAppLanguage(context, langCode)
        applyLanguage(context, langCode)
    }

    fun applyLanguage(context: Context, langCode: String) {
        val locale = getLocale(langCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun getLocale(langCode: String): Locale {
        return when (langCode) {
            LANG_CS -> Locale("cs", "CZ")
            LANG_EN -> Locale("en", "US")
            else -> Locale.getDefault()
        }
    }
}
