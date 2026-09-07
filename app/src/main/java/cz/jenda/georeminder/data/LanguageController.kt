package cz.jenda.georeminder.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Řízení volby jazyka aplikace přes standardní Android/AppCompat per-app locale. */
object LanguageController {

    const val LANG_SYSTEM = "SYSTEM"
    const val LANG_CS = "CS"
    const val LANG_EN = "EN"

    private const val KEY_LOCALE_MIGRATED = "appLocaleMigratedToAppCompatV1"

    fun setAppLanguage(context: Context, langCode: String) {
        // Starou preference ještě aktualizujeme kvůli jednorázové migraci ze starších verzí.
        // Zdroj pravdy pro běžný provoz je už AppCompatDelegate.
        FeatureSettings.setAppLanguage(context, langCode)
        AppCompatDelegate.setApplicationLocales(localeListFor(langCode))
    }

    /**
     * Jednorázově předá starou vlastní preference AppCompatu. Volat před
     * AppCompatActivity.onCreate(), aby Android 12 a starší dostaly locale včas.
     */
    fun migrateLegacyPreferenceIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LOCALE_MIGRATED, false)) return

        val legacy = prefs.getString("appLanguage", LANG_SYSTEM) ?: LANG_SYSTEM
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

    fun localeListFor(langCode: String): LocaleListCompat = when (langCode) {
        LANG_CS -> LocaleListCompat.forLanguageTags("cs-CZ")
        LANG_EN -> LocaleListCompat.forLanguageTags("en-US")
        else -> LocaleListCompat.getEmptyLocaleList()
    }

    fun getLocale(langCode: String): Locale = when (langCode) {
        LANG_CS -> Locale.forLanguageTag("cs-CZ")
        LANG_EN -> Locale.US
        else -> Locale.getDefault()
    }
}
