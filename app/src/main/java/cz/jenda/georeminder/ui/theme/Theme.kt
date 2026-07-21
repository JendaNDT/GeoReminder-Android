package cz.jenda.georeminder.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

object GeoTheme {
    val colors: GeoColors
        @Composable get() = LocalGeoColors.current
}

private fun Typography.withFontFamily(ff: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = ff),
    displayMedium = displayMedium.copy(fontFamily = ff),
    displaySmall = displaySmall.copy(fontFamily = ff),
    headlineLarge = headlineLarge.copy(fontFamily = ff),
    headlineMedium = headlineMedium.copy(fontFamily = ff),
    headlineSmall = headlineSmall.copy(fontFamily = ff),
    titleLarge = titleLarge.copy(fontFamily = ff),
    titleMedium = titleMedium.copy(fontFamily = ff),
    titleSmall = titleSmall.copy(fontFamily = ff),
    bodyLarge = bodyLarge.copy(fontFamily = ff),
    bodyMedium = bodyMedium.copy(fontFamily = ff),
    bodySmall = bodySmall.copy(fontFamily = ff),
    labelLarge = labelLarge.copy(fontFamily = ff),
    labelMedium = labelMedium.copy(fontFamily = ff),
    labelSmall = labelSmall.copy(fontFamily = ff),
)

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun GeoReminderTheme(content: @Composable () -> Unit) {
    val mode by ThemeController.mode.collectAsState()
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkGeoColors else LightGeoColors

    // Barva ikon ve stavovém řádku podle zvoleného vzhledu (ne jen podle systému)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    // Materiálové schéma jen kvůli systémovým dialogům (kalendář, čas) –
    // vlastní UI používá GeoTheme.colors.
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            background = colors.background,
            surface = colors.card,
            surfaceContainer = colors.card,
            surfaceContainerHigh = colors.card,
            surfaceContainerHighest = colors.card,
            onSurface = colors.label,
            onBackground = colors.label,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            background = colors.background,
            surface = colors.card,
            surfaceContainer = colors.card,
            surfaceContainerHigh = colors.card,
            surfaceContainerHighest = colors.card,
            onSurface = colors.label,
            onBackground = colors.label,
        )
    }

    CompositionLocalProvider(LocalGeoColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography().withFontFamily(InterFamily),
            content = content,
        )
    }
}
