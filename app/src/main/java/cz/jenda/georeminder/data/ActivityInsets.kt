package cz.jenda.georeminder.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Výšky systémových lišt změřené v hlavním okně aktivity.
 * Dialogová okna (celoobrazovková mapa) je na některých telefonech
 * (Android 15 / Samsung) nedostávají – proto si je půjčují odsud.
 */
object ActivityInsets {
    /** Výška spodní navigační lišty v pixelech. */
    val navigationBottomPx = MutableStateFlow(0)
}
