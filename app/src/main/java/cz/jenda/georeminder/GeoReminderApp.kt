package cz.jenda.georeminder

import android.app.Application
import cz.jenda.georeminder.ui.theme.ThemeController

class GeoReminderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeController.init(this)
        cz.jenda.georeminder.data.FeatureSettings.init(this)
        cz.jenda.georeminder.data.LanguageController.applyLanguage(this, cz.jenda.georeminder.data.FeatureSettings.appLanguage.value)
    }
}
