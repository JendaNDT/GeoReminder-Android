package cz.jenda.georeminder.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cz.jenda.georeminder.data.CalendarEventItem
import cz.jenda.georeminder.data.CalendarImporter
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.model.CzechFormat
import cz.jenda.georeminder.ui.components.CardDivider
import cz.jenda.georeminder.ui.components.EmptyState
import cz.jenda.georeminder.ui.components.GlassCircleButton
import cz.jenda.georeminder.ui.components.InsetCard
import cz.jenda.georeminder.ui.components.SectionHeader
import cz.jenda.georeminder.ui.components.SheetHeader
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType

@Composable
fun CalendarImportSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val store = remember { ReminderStore.get(context) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var events by remember { mutableStateOf<List<CalendarEventItem>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var importedCount by remember { mutableStateOf<Int?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            events = CalendarImporter.getUpcomingEvents(context)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            events = CalendarImporter.getUpcomingEvents(context)
        }
    }

    fun importSelected() {
        val selectedEvents = events.filter { it.id in selectedIds }
        selectedEvents.forEach { ev ->
            store.add(CalendarImporter.toReminder(ev))
        }
        val count = selectedEvents.size
        if (count > 0) {
            android.widget.Toast.makeText(
                context,
                "Naimportováno $count událostí z kalendáře",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        SheetHeader(
            title = stringResource(cz.jenda.georeminder.R.string.calendar_import_title),
            leftText = stringResource(cz.jenda.georeminder.R.string.action_cancel),
            onLeft = onClose,
            rightText = if (selectedIds.isNotEmpty()) stringResource(cz.jenda.georeminder.R.string.calendar_import_button, selectedIds.size) else "",
            rightEnabled = selectedIds.isNotEmpty(),
            onRight = {
                importSelected()
                onClose()
            },
        )

        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 60.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        icon = Icons.Filled.CalendarMonth,
                        title = stringResource(cz.jenda.georeminder.R.string.calendar_import_title),
                        text = stringResource(cz.jenda.georeminder.R.string.permission_banner_notifications),
                    )
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(cz.jenda.georeminder.R.string.calendar_grant_button), style = GeoType.footnoteBold)
                    }
                }
            }
        } else if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 90.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Filled.CalendarMonth,
                    title = "Žádné události",
                    text = "V systémovém kalendáři na příštích 30 dní nebyly nalezeny žádné události.",
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 40.dp),
            ) {
                SectionHeader("Nadcházející události (30 dní)")
                InsetCard {
                    events.forEachIndexed { index, event ->
                        val isSelected = event.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .iosClickable {
                                    selectedIds = if (isSelected) {
                                        selectedIds - event.id
                                    } else {
                                        selectedIds + event.id
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.title,
                                    style = GeoType.body,
                                    color = colors.label,
                                )
                                Text(
                                    text = CzechFormat.dateTime(event.startTimeMillis) +
                                            if (!event.location.isNullOrEmpty()) " • ${event.location}" else "",
                                    style = GeoType.caption,
                                    color = colors.secondaryLabel,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Vybráno",
                                    tint = colors.accent,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        if (index != events.lastIndex) CardDivider()
                    }
                }
            }
        }
    }
}
