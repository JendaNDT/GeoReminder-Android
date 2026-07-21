package cz.jenda.georeminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import cz.jenda.georeminder.ui.components.EmptyState
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType

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

    var editingReminder by remember { mutableStateOf<Reminder?>(null) }

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
                    title = "Žádná místa k zobrazení",
                    text = "Aktivní připomínky na místa se ukážou tady na mapě.",
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

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = PaddingValues(top = 60.dp, bottom = 96.dp),
                properties = MapProperties(
                    isMyLocationEnabled = hasFine,
                    mapStyleOptions = if (colors.isDark) {
                        MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
                    } else null,
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
                        radius = reminder.radius,
                        fillColor = colors.accent.copy(alpha = 0.12f),
                        strokeColor = colors.accent.copy(alpha = 0.6f),
                        strokeWidth = with(density) { 1.5.dp.toPx() },
                    )
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
                text = "Mapa připomínek",
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
        ModalBottomSheet(
            onDismissRequest = { editingReminder = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = null,
        ) {
            EditReminderSheet(
                existing = editingReminder,
                onClose = { editingReminder = null },
            )
        }
    }
}
