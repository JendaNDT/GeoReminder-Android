package cz.jenda.georeminder.ui

import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.res.stringResource
import cz.jenda.georeminder.R
import cz.jenda.georeminder.ui.components.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import java.util.Calendar

import androidx.compose.material.icons.filled.Settings as SettingsGear
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.LatLng
import cz.jenda.georeminder.MainActivity
import cz.jenda.georeminder.data.FavoritesStore
import cz.jenda.georeminder.data.LocationHolder
import cz.jenda.georeminder.data.PlaceLinkResolver
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.model.CzechFormat
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.model.TriggerType
import cz.jenda.georeminder.ui.components.CardDivider
import cz.jenda.georeminder.ui.components.EmptyState
import cz.jenda.georeminder.ui.components.GlassCircleButton
import cz.jenda.georeminder.ui.components.InsetCard
import cz.jenda.georeminder.ui.components.PermissionBanner
import cz.jenda.georeminder.ui.components.SectionHeader
import cz.jenda.georeminder.ui.components.SheetHeader
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import kotlinx.coroutines.launch

import androidx.lifecycle.viewmodel.compose.viewModel
import cz.jenda.georeminder.ui.viewmodel.ReminderListViewModel

/**
 * Hlavní obrazovka: velký titulek GeoReminder, bannery oprávnění a seznam
 * rozdělený na Aktivní a Hotové, u geo-připomínek se vzdáleností.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(
    viewModel: ReminderListViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val store = remember { ReminderStore.get(context) }
    remember { FavoritesStore.get(context) } // zahřátí (čipy ve formuláři)

    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val active by viewModel.activeReminders.collectAsStateWithLifecycle()
    val done by viewModel.doneReminders.collectAsStateWithLifecycle()
    val geofenceFailed by viewModel.geofenceFailed.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val storageError by store.storageError.collectAsStateWithLifecycle()
    val deletedMessage = stringResource(R.string.reminder_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    var showNewSheet by rememberSaveable { mutableStateOf(false) }
    var newSheetKind by rememberSaveable { mutableStateOf<String?>(null) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var editorDismissRequest by remember { mutableIntStateOf(0) }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCalendarImport by rememberSaveable { mutableStateOf(false) }
    var longPressedReminder by remember { mutableStateOf<Reminder?>(null) }

    var notificationsDenied by remember { mutableStateOf(false) }
    var locationDenied by remember { mutableStateOf(false) }
    var backgroundMissing by remember { mutableStateOf(false) }
    var batteryRestricted by remember { mutableStateOf(false) }
    var sharedPrefill by remember { mutableStateOf<Pair<String, LatLng>?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun refreshPermissionState() {
        notificationsDenied =
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        locationDenied = !LocationHolder.hasFineLocation(context)
        backgroundMissing = LocationHolder.hasFineLocation(context) &&
                !LocationHolder.hasBackgroundLocation(context)
        val powerManager = context.getSystemService(PowerManager::class.java)
        batteryRestricted =
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false
    }

    // Stav oprávnění se přehodnocuje při každém návratu do appky
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionState()
                LocationHolder.refresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { refreshPermissionState() }

    // Zástupce z plochy (podržení ikony appky) → rovnou otevřít formulář
    LaunchedEffect(Unit) {
        MainActivity.shortcutRequest.collect { kind ->
            if (kind != null) {
                newSheetKind = kind
                editingReminder = null
                showNewSheet = true
                MainActivity.shortcutRequest.value = null
            }
        }
    }

    // Sdílené místo z Map Google → rozluštit polohu a otevřít předvyplněný formulář
    LaunchedEffect(Unit) {
        MainActivity.sharedPlaceText.collect { text ->
            if (text != null) {
                MainActivity.sharedPlaceText.value = null
                sharedPrefill = viewModel.resolveSharedPlace(text)
                newSheetKind = "location"
                editingReminder = null
                showNewSheet = true
            }
        }
    }

    // Mazání s možností „Vrátit zpět" přes ViewModel
    fun requestDelete(reminder: Reminder) {
        viewModel.markPendingDelete(reminder)
        scope.launch {
            val res = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (res == SnackbarResult.ActionPerformed) {
                viewModel.cancelPendingDelete(reminder)
            } else {
                viewModel.confirmDelete(reminder)
            }
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 130.dp),
    ) {
        // Kruhová „skleněná" tlačítka nahoře
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                GlassCircleButton(Icons.Filled.StarBorder, stringResource(R.string.favorites_button)) {
                    showFavorites = true
                }
                Spacer(Modifier.width(10.dp))
                GlassCircleButton(Icons.Filled.CalendarMonth, stringResource(R.string.calendar_import_button_description)) {
                    showCalendarImport = true
                }
                Spacer(Modifier.weight(1f))
                GlassCircleButton(Icons.Filled.SettingsGear, stringResource(R.string.settings_title)) {
                    showSettings = true
                }
                Spacer(Modifier.width(10.dp))
                GlassCircleButton(Icons.Filled.Add, stringResource(R.string.new_reminder_description)) {
                    newSheetKind = null
                    editingReminder = null
                    showNewSheet = true
                }
            }
        }
        // Velký titulek
        item {
            Text(
                text = "GeoReminder",
                style = GeoType.largeTitle,
                color = colors.label,
                modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 12.dp),
            )
        }
        // Vyhledávací pole (Search bar)
        item {
            InsetCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.tertiaryLabel,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                style = GeoType.body,
                                color = colors.tertiaryLabel,
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            textStyle = GeoType.body.copy(color = colors.label),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_clear),
                            tint = colors.tertiaryLabel,
                            modifier = Modifier
                                .size(48.dp)
                                .iosClickable { viewModel.updateSearchQuery("") }
                                .padding(14.dp),
                        )
                    }
                }
            }
        }
        // Bannery oprávnění a spolehlivosti
        if (notificationsDenied || locationDenied || backgroundMissing || batteryRestricted || geofenceFailed || storageError) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    if (notificationsDenied) {
                        PermissionBanner(
                            icon = Icons.Filled.NotificationsOff,
                            message = stringResource(R.string.notifications_disabled),
                        )
                    }
                    if (locationDenied) {
                        PermissionBanner(
                            icon = Icons.Filled.LocationOff,
                            message = stringResource(R.string.location_disabled),
                        )
                    } else if (backgroundMissing) {
                        PermissionBanner(
                            icon = Icons.Filled.LocationOff,
                            message = stringResource(R.string.background_location_missing),
                        )
                    }
                    if (batteryRestricted) {
                        PermissionBanner(
                            icon = Icons.Filled.BatteryAlert,
                            message = stringResource(R.string.battery_restricted),
                            actionLabel = stringResource(R.string.permission_banner_grant),
                            onAction = {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {}
                            },
                        )
                    }
                    if (geofenceFailed) {
                        PermissionBanner(
                            icon = Icons.Filled.LocationOff,
                            message = stringResource(R.string.geofence_failed),
                        )
                    }
                    if (storageError) {
                        PermissionBanner(
                            icon = Icons.Filled.Autorenew,
                            message = stringResource(R.string.storage_error),
                        )
                    }
                }
            }
        }

        if (reminders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxHeight(0.6f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Filled.PinDrop,
                        title = stringResource(R.string.empty_reminders_title),
                        text = stringResource(R.string.empty_reminders_text),
                    )
                }
            }
        } else if (active.isEmpty() && done.isEmpty() && searchQuery.isNotBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxHeight(0.5f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = stringResource(R.string.search_empty_title),
                        text = stringResource(R.string.search_empty_text, searchQuery),
                    )
                }
            }
        } else {
            if (active.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.section_active)) }
                item {
                    InsetCard {
                        active.forEachIndexed { index, reminder ->
                            key(reminder.id) {
                                SwipeReminderRow(
                                    reminder = reminder,
                                    distance = viewModel.distanceText(reminder),
                                    onTap = { editingReminder = reminder },
                                    onLongTap = { longPressedReminder = reminder },
                                    onToggleDone = { viewModel.toggleDone(reminder) },
                                    onDelete = { requestDelete(reminder) },
                                )
                            }
                            if (index != active.lastIndex) CardDivider(startIndent = 60.dp)
                        }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
            if (done.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.section_done)) }
                item {
                    InsetCard {
                        done.forEachIndexed { index, reminder ->
                            key(reminder.id) {
                                SwipeReminderRow(
                                    reminder = reminder,
                                    distance = viewModel.distanceText(reminder),
                                    onTap = { editingReminder = reminder },
                                    onLongTap = { longPressedReminder = reminder },
                                    onToggleDone = { viewModel.toggleDone(reminder) },
                                    onDelete = { requestDelete(reminder) },
                                )
                            }
                            if (index != done.lastIndex) CardDivider(startIndent = 60.dp)
                        }
                    }
                }
            }
        }
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        )
    }

    // Formulář (nová / úprava)
    if (showNewSheet || editingReminder != null) {
        // Compose Dialog zůstane přítomný, dokud guard formuláře výslovně
        // nezavolá onClose. Bottom sheet se při systémovém Zpět schoval ještě
        // před volbou „Pokračovat v úpravách“.
        Dialog(
            onDismissRequest = { editorDismissRequest++ },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
        ) {
            // key() zajistí čerstvý formulář, když se změní cíl (úprava vs. nová,
            // nebo přijde sdílené místo, zatímco je formulář otevřený)
            key(editingReminder?.id ?: "new", sharedPrefill) {
                EditReminderSheet(
                    existing = editingReminder,
                    initialKind = if (newSheetKind == "time") ReminderKind.TIME else ReminderKind.LOCATION,
                    initialPlaceName = sharedPrefill?.first ?: "",
                    initialCoordinate = sharedPrefill?.second,
                    dismissRequest = editorDismissRequest,
                    onClose = {
                        showNewSheet = false
                        editingReminder = null
                        newSheetKind = null
                        sharedPrefill = null
                    },
                )
            }
        }
    }

    // Oblíbená místa
    if (showFavorites) {
        ModalBottomSheet(
            onDismissRequest = { showFavorites = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = null,
        ) {
            FavoritesSheet(onClose = { showFavorites = false })
        }
    }

    // Nastavení (vzhled & zálohy)
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = null,
        ) {
            SettingsSheet(onClose = { showSettings = false })
        }
    }

    // Import z Google Kalendáře
    if (showCalendarImport) {
        ModalBottomSheet(
            onDismissRequest = { showCalendarImport = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = null,
        ) {
            CalendarImportSheet(onClose = { showCalendarImport = false })
        }
    }

    // Kontextová nabídka rychlých akcí po dlouhém stisku
    if (longPressedReminder != null) {
        val target = longPressedReminder!!
        ModalBottomSheet(
            onDismissRequest = { longPressedReminder = null },
            containerColor = colors.background,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = null,
        ) {
            QuickActionSheet(
                reminder = target,
                onClose = { longPressedReminder = null },
                onEdit = { editingReminder = target },
                onSnoozeTomorrow = {
                    val updated = target.copy(
                        dueDate = nextMorningMillis(),
                        isDone = false,
                    )
                    store.update(updated)
                },
                onNavigate = if (target.kind == ReminderKind.LOCATION) {
                    {
                        val uri = "geo:${target.latitude},${target.longitude}?q=${target.latitude},${target.longitude}(${Uri.encode(target.placeName.ifEmpty { target.title })})".toUri()
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, context.getString(R.string.toast_no_navigation_app), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } else null,
                onShare = {
                    val shareText = buildString {
                        append(context.getString(R.string.share_reminder_prefix, target.title))
                        if (target.placeName.isNotEmpty()) {
                            append("\n").append(context.getString(R.string.share_place_prefix, target.placeName))
                            append("\nhttps://maps.google.com/?q=").append(target.latitude).append(",").append(target.longitude)
                        }
                    }
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                onDelete = { requestDelete(target) },
            )
        }
    }
}
