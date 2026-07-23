package cz.jenda.georeminder.data

import android.content.Context
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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

    /** AppCompat zajistí okamžitou rekreaci Activity i kompatibilitu před Androidem 13. */
    fun applyLanguage(context: Context, langCode: String) {
        val locales = when (langCode) {
            LANG_CS -> LocaleListCompat.forLanguageTags("cs-CZ")
            LANG_EN -> LocaleListCompat.forLanguageTags("en-US")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun getLocale(langCode: String): Locale {
        return when (langCode) {
            LANG_CS -> Locale.forLanguageTag("cs-CZ")
            LANG_EN -> Locale.forLanguageTag("en-US")
            else -> Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
        }
    }
}
