package cz.jenda.georeminder.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Barvy podle DESIGN_TOKENS.md – Světlý, Tmavý a Neutrální vzhled.
 */
data class GeoColors(
    val accent: Color,
    val background: Color,
    val card: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val teal: Color,
    val purple: Color,
    val segmentTrack: Color,
    val segmentThumb: Color,
    val sliderTrack: Color,
    val switchTrackOff: Color,
    val tabBarBackground: Color,
    val tabActiveBubble: Color,
    val glass: Color,
    val shadow: Color,
    val mapBg: Color,
    val road: Color,
    val widgetBg: Color,
    val isDark: Boolean,
)

val LightGeoColors = GeoColors(
    accent = Color(0xFF0066CC),
    background = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0xC23C3C43),   // 76 % (WCAG AA > 4.5:1 na bílé)
    tertiaryLabel = Color(0xA63C3C43),    // 65 % (WCAG AA > 4.5:1 na bílé)
    separator = Color(0x4A3C3C43),        // 29 %
    green = Color(0xFF198754),
    red = Color(0xFFC62828),
    orange = Color(0xFFAD6200),
    yellow = Color(0xFF806000),
    teal = Color(0xFF087C8C),
    purple = Color(0xFF7E3AA8),
    segmentTrack = Color(0x1F767680),     // 12 %
    segmentThumb = Color(0xFFFFFFFF),
    sliderTrack = Color(0x33787880),      // 20 %
    switchTrackOff = Color(0x52787880),   // 32 %
    tabBarBackground = Color(0xF2F7F7F9),
    tabActiveBubble = Color(0xFFFFFFFF),
    glass = Color(0xEBFFFFFF),
    shadow = Color(0x24000000),
    mapBg = Color(0xFFE7ECE4),
    road = Color(0xB8FFFFFF),
    widgetBg = Color(0xFFEFEFF4),
    isDark = false,
)

val DarkGeoColors = GeoColors(
    accent = Color(0xFF0A84FF),
    background = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xB3EBEBF5),   // 70 % (WCAG AA)
    tertiaryLabel = Color(0x99EBEBF5),    // 60 % (WCAG AA)
    separator = Color(0xA6545458),        // 65 %
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    yellow = Color(0xFFFFD60A),
    teal = Color(0xFF40C8E0),
    purple = Color(0xFFBF5AF2),
    segmentTrack = Color(0x3D767680),     // 24 %
    segmentThumb = Color(0xFF636366),
    sliderTrack = Color(0x5C787880),      // 36 %
    switchTrackOff = Color(0x52787880),
    tabBarBackground = Color(0xF21C1C1E),
    tabActiveBubble = Color(0xFF3A3A3C),
    glass = Color(0xEB1C1C1E),
    shadow = Color(0x99000000),
    mapBg = Color(0xFF23272B),
    road = Color(0x1FFFFFFF),
    widgetBg = Color(0xFF2C2C2E),
    isDark = true,
)

val NeutralGeoColors = GeoColors(
    accent = Color(0xFF4C5FD0),
    background = Color(0xFFECE6DA),
    card = Color(0xFFFBF8F2),
    label = Color(0xFF2A2621),
    secondaryLabel = Color(0xD03A342B),   // WCAG AA
    tertiaryLabel = Color(0xB53A342B),    // WCAG AA
    separator = Color(0x243A342B),
    green = Color(0xFF2FA36B),
    red = Color(0xFFD9694E),
    orange = Color(0xFFC8862B),
    yellow = Color(0xFFD9A63C),
    teal = Color(0xFF2E9CA6),
    purple = Color(0xFF8A63C8),
    segmentTrack = Color(0x143A342B),
    segmentThumb = Color(0xFFFFFFFF),
    sliderTrack = Color(0x2E3A342B),
    switchTrackOff = Color(0x3D3A342B),
    tabBarBackground = Color(0xF5FBF8F2),
    tabActiveBubble = Color(0xFFFFFFFF),
    glass = Color(0xEBFBF8F2),
    shadow = Color(0x2E503714),
    mapBg = Color(0xFFE4DECF),
    road = Color(0x9EFFFFFF),
    widgetBg = Color(0xFFF0EADB),
    isDark = false,
)

val LocalGeoColors = staticCompositionLocalOf { LightGeoColors }
