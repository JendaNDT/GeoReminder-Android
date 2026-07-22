package cz.jenda.georeminder.ui.theme

import com.google.android.gms.maps.model.MapStyleOptions

/**
/ Vlastní JSON vektory pro stylování Map Google podle zvoleného vzhledu.
 */
object MapStyles {
    /** Tmavý grafitový styl mapy. */
    private const val DARK_STYLE = """
    [
      {"elementType": "geometry", "stylers": [{"color": "#242f3e"}]},
      {"elementType": "labels.text.stroke", "stylers": [{"color": "#242f3e"}]},
      {"elementType": "labels.text.fill", "stylers": [{"color": "#746855"}]},
      {"featureType": "administrative.locality", "elementType": "labels.text.fill", "stylers": [{"color": "#d59563"}]},
      {"featureType": "poi", "elementType": "labels.text.fill", "stylers": [{"color": "#d59563"}]},
      {"featureType": "poi.park", "elementType": "geometry", "stylers": [{"color": "#263c3f"}]},
      {"featureType": "road", "elementType": "geometry", "stylers": [{"color": "#38414e"}]},
      {"featureType": "road", "elementType": "geometry.stroke", "stylers": [{"color": "#212a37"}]},
      {"featureType": "road", "elementType": "labels.text.fill", "stylers": [{"color": "#9ca5b3"}]},
      {"featureType": "road.highway", "elementType": "geometry", "stylers": [{"color": "#746855"}]},
      {"featureType": "road.highway", "elementType": "geometry.stroke", "stylers": [{"color": "#1f2835"}]},
      {"featureType": "transit", "elementType": "geometry", "stylers": [{"color": "#2f3948"}]},
      {"featureType": "water", "elementType": "geometry", "stylers": [{"color": "#17263c"}]}
    ]
    """

    /** Teplý krémový styl mapy pro Neutrální vzhled. */
    private const val NEUTRAL_STYLE = """
    [
      {"elementType": "geometry", "stylers": [{"color": "#f5f1e8"}]},
      {"elementType": "labels.text.fill", "stylers": [{"color": "#616161"}]},
      {"elementType": "labels.text.stroke", "stylers": [{"color": "#f5f1e8"}]},
      {"featureType": "park", "elementType": "geometry", "stylers": [{"color": "#e2ebd9"}]},
      {"featureType": "road", "elementType": "geometry", "stylers": [{"color": "#ffffff"}]},
      {"featureType": "water", "elementType": "geometry", "stylers": [{"color": "#c9dbf2"}]}
    ]
    """

    /** Vlajková Indigo stylizovaná mapa pro Glass režim. */
    private const val GLASS_STYLE = """
    [
      {"elementType": "geometry", "stylers": [{"color": "#1d1d36"}]},
      {"elementType": "labels.text.fill", "stylers": [{"color": "#8f9bb3"}]},
      {"featureType": "park", "elementType": "geometry", "stylers": [{"color": "#142c33"}]},
      {"featureType": "road", "elementType": "geometry", "stylers": [{"color": "#2d2d54"}]},
      {"featureType": "water", "elementType": "geometry", "stylers": [{"color": "#0d1b2a"}]}
    ]
    """

    fun getMapStyle(mode: ThemeMode): MapStyleOptions? {
        return when (mode) {
            ThemeMode.DARK -> MapStyleOptions(DARK_STYLE)
            ThemeMode.NEUTRAL -> MapStyleOptions(NEUTRAL_STYLE)
            ThemeMode.GLASS -> MapStyleOptions(GLASS_STYLE)
            else -> null // Výchozí světlá mapa pro SYSTEM / LIGHT
        }
    }
}
