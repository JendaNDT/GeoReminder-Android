package cz.jenda.georeminder.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Řízení volby jazyka aplikace přes standardní Android/AppCompat per-app locale. */
object LanguageController {

    const val LANG_SYSTEM = "SYSTEM"
    const val LANG_CS = "CS"
    const val LANG_EN = "EN"

    private const val KEY_LEGACY_APP_LANGUAGE = "appLanguage"
    private const val KEY_LOCALE_MIGRATED = "appLocaleMigratedToAppCompatV1"

    fun setAppLanguage(@Suppress("UNUSED_PARAMETER") context: Context, langCode: String) {
        AppCompatDelegate.setApplicationLocales(localeListFor(langCode))
    }

    /**
     * Jednorázově předá starou vlastní preference AppCompatu. Volat před
     * AppCompatActivity.onCreate(), aby Android 12 a starší dostaly locale včas.
     */
    fun migrateLegacyPreferenceIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LOCALE_MIGRATED, false)) return

        val legacy = prefs.getString(KEY_LEGACY_APP_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
        if (AppCompatDelegate.getApplicationLocales().isEmpty && legacy != LANG_SYSTEM) {
            AppCompatDelegate.setApplicationLocales(localeListFor(legacy))
        }
        prefs.edit().putBoolean(KEY_LOCALE_MIGRATED, true).apply()
    }

    /** Aktuální uživatelská volba. Prázdný seznam znamená skutečně „podle systému“. */
    fun currentLanguageCode(): String {
        val locale = AppCompatDelegate.getApplicationLocales().get(0) ?: return LANG_SYSTEM
        return when (locale.language.lowercase(Locale.ROOT)) {
            "cs" -> LANG_CS
            "en" -> LANG_EN
            else -> LANG_SYSTEM
        }
    }

    /**
     * Efektivní podporovaný jazyk. Aplikace má jen české výchozí resources a
     * values-en, takže jiný systémový jazyk spadne stejně jako resources do češtiny.
     */
    fun effectiveLanguageCode(systemLocale: Locale = systemLocale()): String =
        when (val selected = currentLanguageCode()) {
            LANG_CS, LANG_EN -> selected
            else -> if (systemLocale.language.equals("en", ignoreCase = true)) LANG_EN else LANG_CS
        }

    fun effectiveLocale(systemLocale: Locale = systemLocale()): Locale =
        when (effectiveLanguageCode(systemLocale)) {
            LANG_EN -> Locale.US
            else -> Locale.forLanguageTag("cs-CZ")
        }

    /**
     * Context pro komponenty, které mohou běžet bez AppCompatActivity
     * (receiver, widget, TTS). Neprovádí globální updateConfiguration; vytvoří
     * pouze lokální konfiguraci pro čtení správných string resources.
     */
    fun localizedContext(context: Context): Context {
        if (currentLanguageCode() == LANG_SYSTEM) return context
        val locale = effectiveLocale()
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }

    fun localeListFor(langCode: String): LocaleListCompat = when (langCode) {
        LANG_CS -> LocaleListCompat.forLanguageTags("cs-CZ")
        LANG_EN -> LocaleListCompat.forLanguageTags("en-US")
        else -> LocaleListCompat.getEmptyLocaleList()
    }

    fun getLocale(langCode: String): Locale = when (langCode) {
        LANG_CS -> Locale.forLanguageTag("cs-CZ")
        LANG_EN -> Locale.US
        else -> effectiveLocale()
    }

    private fun systemLocale(): Locale {
        val locales = Resources.getSystem().configuration.locales
        return if (!locales.isEmpty) locales[0] else Locale.US
    }
}
