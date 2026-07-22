package cz.jenda.georeminder.notify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Otevře navigaci do zadaného bodu ve vnější mapové aplikaci.
 *
 * Nepoužívá vlastní Google Maps API klíč aplikace – je to jen systémový intent,
 * takže nespotřebovává žádnou kvótu mapového klíče. Primárně spustí navigaci
 * přímo v Google Maps; když nejsou k dispozici, nabídne výběr jiné mapové
 * aplikace přes `geo:` odkaz.
 */
object NavigationLauncher {

    fun open(context: Context, latitude: Double, longitude: Double, placeName: String) {
        // 1) Přímo navigace v Google Maps
        val gmaps = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=$latitude,$longitude"),
        ).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (gmaps.resolveActivity(context.packageManager) != null) {
            context.startActivity(gmaps)
            return
        }

        // 2) Záloha: jakákoli mapová aplikace přes geo: odkaz (výběr)
        val label = Uri.encode(placeName.ifBlank { "Cíl" })
        val geo = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)"),
        )
        val chooser = Intent.createChooser(geo, "Navigovat přes…").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            // Toast musí běžet na hlavním vlákně (pro jistotu i kdyby se open()
            // někdy zavolalo z pozadí).
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Není nainstalovaná žádná mapová aplikace",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
