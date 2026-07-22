package cz.jenda.georeminder

import android.app.Application
import cz.jenda.georeminder.notify.NotificationHelper
import cz.jenda.georeminder.ui.theme.ThemeController

class GeoReminderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        ThemeController.init(this)
        cz.jenda.georeminder.data.FeatureSettings.init(this)
    }
}
