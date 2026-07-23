package cz.jenda.georeminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import cz.jenda.georeminder.R
import cz.jenda.georeminder.data.LocationHolder
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import cz.jenda.georeminder.ui.components.EmptyState
import cz.jenda.georeminder.ui.components.GlassCircleButton
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import cz.jenda.georeminder.ui.theme.MapStyles
import cz.jenda.georeminder.ui.theme.ThemeController
import kotlinx.coroutines.launch

/**
 * Druhá záložka: všechny aktivní geo-připomínky na jedné mapě.
 * Ťuknutím na špendlík se otevře úprava připomínky (DESIGN_SPEC §5.6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapOverviewScreen() {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val density = LocalDensity.current
    val store = remember { ReminderStore.get(context) }
    val reminders by store.reminders.collectAsStateWithLifecycle()
    val currentLocation by LocationHolder.location.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var editorDismissRequest by remember { mutableIntStateOf(0) }

    val locationReminders = remember(reminders) {
        reminders.filter { it.kind == ReminderKind.LOCATION && !it.isDone }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (locationReminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Filled.Map,
                    title = stringResource(R.string.map_empty_title),
                    text = stringResource(R.string.map_empty_text),
                )
            }
        } else {
            val cameraPositionState = rememberCameraPositionState {
                val first = locationReminders.first()
                position = CameraPosition.fromLatLngZoom(
                    LatLng(first.latitude, first.longitude),
                    zoomForSpan(2000.0, first.latitude),
                )
            }
            val hasFine = remember { LocationHolder.hasFineLocation(context) }

            // Kamera tak, aby byly vidět všechny připomínky najednou
            LaunchedEffect(locationReminders.map { it.id }) {
                runCatching {
                    if (locationReminders.size == 1) {
                        val only = locationReminders.first()
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngBounds(
                                boundsAround(LatLng(only.latitude, only.longitude), 2000.0),
                                40,
                            )
                        )
                    } else {
                        val builder = LatLngBounds.builder()
                        locationReminders.forEach {
                            builder.include(LatLng(it.latitude, it.longitude))
                        }
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngBounds(builder.build(), 140)
                        )
                    }
                }
            }

            val currentThemeMode by ThemeController.mode.collectAsStateWithLifecycle()
            val infiniteTransition = rememberInfiniteTransition(label = "pulseCircle")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.28f,
                targetValue = 0.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )

            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = PaddingValues(top = 60.dp, bottom = 96.dp),
                properties = MapProperties(
                    isMyLocationEnabled = hasFine,
                    mapStyleOptions = MapStyles.getMapStyle(currentThemeMode, isSystemDark),
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false,
                ),
            ) {
                locationReminders.forEach { reminder ->
                    Marker(
                        state = MarkerState(
                            position = LatLng(reminder.latitude, reminder.longitude)
                        ),
                        title = reminder.title,
                        onClick = {
                            editingReminder = reminder
                            true
                        },
                    )
                    Circle(
                        center = LatLng(reminder.latitude, reminder.longitude),
                        radius = reminder.radius * pulseScale,
                        fillColor = colors.accent.copy(alpha = pulseAlpha),
                        strokeColor = colors.accent.copy(alpha = (pulseAlpha * 2f).coerceAtMost(0.8f)),
                        strokeWidth = with(density) { 2.dp.toPx() },
                    )
                }
            }

            currentLocation?.let { location ->
                GlassCircleButton(
                    icon = Icons.Filled.MyLocation,
                    contentDescription = stringResource(R.string.map_center_on_me),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 112.dp),
                ) {
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(location.latitude, location.longitude),
                                15f,
                            )
                        )
                    }
                }
            }
        }

        // Horní lišta s inline titulkem (poloprůhledný „materiál")
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(colors.glass)
                .statusBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.map_title),
                style = GeoType.headline,
                color = colors.label,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(vertical = 12.dp),
            )
        }
    }

    if (editingReminder != null) {
        Dialog(
            onDismissRequest = { editorDismissRequest++ },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
        ) {
            EditReminderSheet(
                existing = editingReminder,
                dismissRequest = editorDismissRequest,
                onClose = { editingReminder = null },
            )
        }
    }
}
