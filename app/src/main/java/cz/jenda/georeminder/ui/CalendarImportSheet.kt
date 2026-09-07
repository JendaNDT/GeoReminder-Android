package cz.jenda.georeminder.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.jenda.georeminder.R
import cz.jenda.georeminder.data.CalendarEventItem
import cz.jenda.georeminder.data.CalendarImporter
import cz.jenda.georeminder.data.CalendarLoadResult
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.model.CzechFormat
import cz.jenda.georeminder.ui.components.CardDivider
import cz.jenda.georeminder.ui.components.EmptyState
import cz.jenda.georeminder.ui.components.InsetCard
import cz.jenda.georeminder.ui.components.SectionHeader
import cz.jenda.georeminder.ui.components.SheetHeader
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import kotlinx.coroutines.launch

private sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    data class Content(val events: List<CalendarEventItem>) : CalendarUiState
    data object Empty : CalendarUiState
    data object PermissionDenied : CalendarUiState
    data class Error(val reason: String) : CalendarUiState
}

@Composable
fun CalendarImportSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val store = remember { ReminderStore.get(context) }
    val reminders by store.reminders.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var refreshToken by remember { mutableStateOf(0) }
    var uiState by remember {
        mutableStateOf<CalendarUiState>(
            if (permissionGranted) CalendarUiState.Loading else CalendarUiState.PermissionDenied
        )
    }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importing by remember { mutableStateOf(false) }

    val importedKeys = reminders.mapNotNull { it.calendarSourceKey }.toSet()
    val currentEvents = (uiState as? CalendarUiState.Content)?.events.orEmpty()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        selectedKeys = emptySet()
        if (granted) refreshToken++ else uiState = CalendarUiState.PermissionDenied
    }

    LaunchedEffect(permissionGranted, refreshToken) {
        if (!permissionGranted) {
            uiState = CalendarUiState.PermissionDenied
            return@LaunchedEffect
        }
        uiState = CalendarUiState.Loading
        uiState = when (val result = CalendarImporter.getUpcomingEvents(context)) {
            is CalendarLoadResult.Success -> CalendarUiState.Content(result.events)
            CalendarLoadResult.Empty -> CalendarUiState.Empty
            CalendarLoadResult.PermissionDenied -> {
                permissionGranted = false
                CalendarUiState.PermissionDenied
            }
            is CalendarLoadResult.Error -> CalendarUiState.Error(result.reason)
        }
    }

    fun importSelected() {
        if (selectedKeys.isEmpty() || importing) return
        importing = true
        scope.launch {
            try {
                val snapshot = store.snapshotAfterPendingIo()
                val alreadyImported = snapshot.mapNotNull { it.calendarSourceKey }.toSet()
                val additions = currentEvents
                    .filter { it.instanceKey in selectedKeys && it.instanceKey !in alreadyImported }
                    .map(CalendarImporter::toReminder)

                val success = additions.isEmpty() || store.replaceAllFromImport(snapshot + additions)
                if (success) {
                    selectedKeys = emptySet()
                    Toast.makeText(
                        context,
                        if (additions.isEmpty()) {
                            "Vybrané události už byly importované"
                        } else {
                            "Naimportováno ${additions.size} událostí z kalendáře"
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(context, "Import z kalendáře selhal", Toast.LENGTH_SHORT).show()
                }
            } finally {
                importing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        SheetHeader(
            title = androidx.compose.ui.res.stringResource(R.string.calendar_import_title),
            leftText = androidx.compose.ui.res.stringResource(R.string.action_cancel),
            onLeft = onClose,
            rightText = if (selectedKeys.isNotEmpty()) {
                androidx.compose.ui.res.stringResource(R.string.calendar_import_button, selectedKeys.size)
            } else {
                ""
            },
            rightEnabled = selectedKeys.isNotEmpty() && !importing,
            onRight = { importSelected() },
        )

        when (val state = uiState) {
            CalendarUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 90.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }

            CalendarUiState.PermissionDenied -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(
                            icon = Icons.Filled.CalendarMonth,
                            title = "Přístup ke kalendáři",
                            text = "GeoReminder potřebuje oprávnění číst kalendář, aby mohl nabídnout nadcházející události k importu.",
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.calendar_grant_button),
                                style = GeoType.footnoteBold,
                            )
                        }
                    }
                }
            }

            CalendarUiState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 90.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Filled.CalendarMonth,
                        title = "Žádné události",
                        text = "V systémovém kalendáři na příštích 30 dní nejsou žádné budoucí výskyty událostí.",
                    )
                }
            }

            is CalendarUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 70.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(
                            icon = Icons.Filled.CalendarMonth,
                            title = "Kalendář se nepodařilo načíst",
                            text = "Systémový kalendář vrátil chybu. Události nebyly změněny.",
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { refreshToken++ },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text("Zkusit znovu", color = Color.White)
                        }
                    }
                }
            }

            is CalendarUiState.Content -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp, bottom = 40.dp),
                ) {
                    SectionHeader("Nadcházející události (30 dní)")
                    InsetCard {
                        state.events.forEachIndexed { index, event ->
                            val alreadyImported = event.instanceKey in importedKeys
                            val selected = event.instanceKey in selectedKeys
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .iosClickable(enabled = !alreadyImported && !importing) {
                                        selectedKeys = if (selected) {
                                            selectedKeys - event.instanceKey
                                        } else {
                                            selectedKeys + event.instanceKey
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = event.title,
                                        style = GeoType.body,
                                        color = if (alreadyImported) colors.secondaryLabel else colors.label,
                                    )
                                    Text(
                                        text = CzechFormat.dateTime(event.startTimeMillis) +
                                            if (!event.location.isNullOrEmpty()) " • ${event.location}" else "",
                                        style = GeoType.caption,
                                        color = colors.secondaryLabel,
                                    )
                                    if (alreadyImported) {
                                        Text(
                                            text = "Již importováno",
                                            style = GeoType.caption,
                                            color = colors.green,
                                        )
                                    }
                                }
                                if (selected || alreadyImported) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = if (alreadyImported) "Již importováno" else "Vybráno",
                                        tint = if (alreadyImported) colors.green else colors.accent,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            if (index != state.events.lastIndex) CardDivider()
                        }
                    }
                }
            }
        }
    }
}
