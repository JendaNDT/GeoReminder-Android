package cz.jenda.georeminder.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Barvy 1:1 podle DESIGN_SPEC §1 – systémové barvy iOS pro světlý a tmavý režim.
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
    val segmentTrack: Color,
    val segmentThumb: Color,
    val sliderTrack: Color,
    val switchTrackOff: Color,
    val tabBarBackground: Color,
    val tabActiveBubble: Color,
    val glass: Color,
    val isDark: Boolean,
)

val LightGeoColors = GeoColors(
    accent = Color(0xFF007AFF),
    background = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),   // 60 %
    tertiaryLabel = Color(0x4D3C3C43),    // 30 %
    separator = Color(0x4A3C3C43),        // 29 %
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    yellow = Color(0xFFFFCC00),
    segmentTrack = Color(0x1F767680),     // 12 %
    segmentThumb = Color(0xFFFFFFFF),
    sliderTrack = Color(0x33787880),      // 20 %
    switchTrackOff = Color(0x52787880),   // 32 %
    tabBarBackground = Color(0xF2F7F7F9),
    tabActiveBubble = Color(0xFFFFFFFF),
    glass = Color(0xEBFFFFFF),
    isDark = false,
)

val DarkGeoColors = GeoColors(
    accent = Color(0xFF0A84FF),
    background = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),   // 60 %
    tertiaryLabel = Color(0x4DEBEBF5),    // 30 %
    separator = Color(0xA6545458),        // 65 %
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    yellow = Color(0xFFFFD60A),
    segmentTrack = Color(0x3D767680),     // 24 %
    segmentThumb = Color(0xFF636366),
    sliderTrack = Color(0x5C787880),      // 36 %
    switchTrackOff = Color(0x52787880),
    tabBarBackground = Color(0xF21C1C1E),
    tabActiveBubble = Color(0xFF3A3A3C),
    glass = Color(0xEB1C1C1E),
    isDark = true,
)

val LocalGeoColors = staticCompositionLocalOf { LightGeoColors }
