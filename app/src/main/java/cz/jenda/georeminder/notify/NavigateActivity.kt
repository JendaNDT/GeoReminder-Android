package cz.jenda.georeminder.notify

import android.app.Activity
import android.os.Bundle

/**
 * Neviditelná „přestupní" aktivita pro tlačítko Navigovat na notifikaci.
 *
 * Od Androidu 12 (targetSdk 31+) nesmí BroadcastReceiver ani Service spuštěný
 * z notifikace nastartovat aktivitu (tzv. „notification trampoline" – systém to
 * zablokuje). Proto akce „Navigovat" míří přímo sem přes
 * `PendingIntent.getActivity` a teprve tahle aktivita otevře mapovou aplikaci a
 * okamžitě se ukončí. Spuštění mapy z aktivity je povolené.
 */
class NavigateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        if (!lat.isNaN() && !lng.isNaN()) {
            NavigationLauncher.open(this, lat, lng, name)
        }
        finish()
    }

    companion object {
        const val EXTRA_LAT = "nav_lat"
        const val EXTRA_LNG = "nav_lng"
        const val EXTRA_NAME = "nav_name"
    }
}
