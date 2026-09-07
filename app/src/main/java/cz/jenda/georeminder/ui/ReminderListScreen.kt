package cz.jenda.georeminder.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings as SettingsGear
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.LatLng
import cz.jenda.georeminder.MainActivity
import cz.jenda.georeminder.R
import cz.jenda.georeminder.data.FavoritesStore
import cz.jenda.georeminder.data.LocationHolder
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.notify.NotificationHelper
import cz.jenda.georeminder.ui.components.CardDivider
import cz.jenda.georeminder.ui.components.EmptyState
import cz.jenda.georeminder.ui.components.GlassCircleButton
import cz.jenda.georeminder.ui.components.InsetCard
import cz.jenda.georeminder.ui.components.PermissionBanner
import cz.jenda.georeminder.ui.components.QuickActionSheet
import cz.jenda.georeminder.ui.components.SectionHeader
import cz.jenda.georeminder.ui.components.SwipeReminderRow
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.components.nextMorningMillis
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import cz.jenda.georeminder.ui.viewmodel.ReminderListViewModel
import kotlinx.coroutines.launch

/** Hlavní seznam připomínek, oprávnění a vstupní body do editoru. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(
    viewModel: ReminderListViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val store = remember { ReminderStore.get(context) }
    remember { FavoritesStore.get(context) }

    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val active by viewModel.activeReminders.collectAsStateWithLifecycle()
    val done by viewModel.doneReminders.collectAsStateWithLifecycle()
    val geofenceFailed by viewModel.geofenceFailed.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var showNewSheet by rememberSaveable { mutableStateOf(false) }
    var newSheetKind by rememberSaveable { mutableStateOf<String?>(null) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCalendarImport by rememberSaveable { mutableStateOf(false) }
    var longPressedReminder by remember { mutableStateOf<Reminder?>(null) }
    var pendingNotificationReminderId by rememberSaveable { mutableStateOf<String?>(null) }

    var notificationsDenied by remember { mutableStateOf(false) }
    var locationDenied by remember { mutableStateOf(false) }
    var backgroundMissing by remember { mutableStateOf(false) }
    var batteryRestricted by remember { mutableStateOf(false) }
    var sharedPrefill by remember { mutableStateOf<Pair<String, LatLng>?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun refreshPermissionState() {
        notificationsDenied = !NotificationManagerCompat.from(context).areNotificationsEnabled()
        locationDenied = !LocationHolder.hasFineLocation(context)
        backgroundMissing = LocationHolder.hasFineLocation(context) &&
            !LocationHolder.hasBackgroundLocation(context)
        val powerManager = context.getSystemService(PowerManager::class.java)
        batteryRestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false
    }

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

    LaunchedEffect(Unit) {
        MainActivity.notificationReminderRequest.collect { reminderId ->
            if (!reminderId.isNullOrBlank()) {
                pendingNotificationReminderId = reminderId
                if (MainActivity.notificationReminderRequest.value == reminderId) {
                    MainActivity.notificationReminderRequest.value = null
                }
            }
        }
    }

    LaunchedEffect(
        pendingNotificationReminderId,
        showNewSheet,
        editingReminder,
        showFavorites,
        showSettings,
        showCalendarImport,
        longPressedReminder,
    ) {
        val reminderId = pendingNotificationReminderId ?: return@LaunchedEffect
        val hasBlockingSheet = showNewSheet || editingReminder != null || showFavorites ||
            showSettings || showCalendarImport || longPressedReminder != null
        if (hasBlockingSheet) return@LaunchedEffect

        val loadResult = store.reloadAndWait()
        if (loadResult == ReminderStore.ReloadResult.ERROR) {
            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_reminder_load_failed))
            pendingNotificationReminderId = null
            return@LaunchedEffect
        }

        val target = store.reminders.value.firstOrNull { it.id == reminderId }
        if (target == null) {
            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_reminder_missing))
        } else {
            newSheetKind = null
            sharedPrefill = null
            editingReminder = target
        }
        pendingNotificationReminderId = null
    }

    fun requestDelete(reminder: Reminder) {
        viewModel.markPendingDelete(reminder)
        scope.launch {
            val res = snackbarHostState.showSnackbar(
                message = context.getString(R.string.snackbar_reminder_deleted),
                actionLabel = context.getString(R.string.action_undo),
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
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    GlassCircleButton(Icons.Filled.StarBorder, stringResource(R.string.top_favorites)) {
                        showFavorites = true
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassCircleButton(Icons.Filled.CalendarMonth, stringResource(R.string.top_calendar_import)) {
                        showCalendarImport = true
                    }
                    Spacer(Modifier.weight(1f))
                    GlassCircleButton(Icons.Filled.SettingsGear, stringResource(R.string.top_settings)) {
                        showSettings = true
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassCircleButton(Icons.Filled.Add, stringResource(R.string.top_new_reminder)) {
                        newSheetKind = null
                        editingReminder = null
                        showNewSheet = true
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.app_name),
                    style = GeoType.largeTitle,
                    color = colors.label,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 12.dp),
                )
            }

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
                                    .size(18.dp)
                                    .iosClickable { viewModel.updateSearchQuery("") },
                            )
                        }
                    }
                }
            }

            if (notificationsDenied || locationDenied || backgroundMissing || batteryRestricted || geofenceFailed) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        if (notificationsDenied) {
                            PermissionBanner(
                                icon = Icons.Filled.NotificationsOff,
                                message = stringResource(R.string.permission_notifications_disabled),
                            )
                        }
                        if (locationDenied) {
                            PermissionBanner(
                                icon = Icons.Filled.LocationOff,
                                message = stringResource(R.string.permission_location_denied),
                            )
                        } else if (backgroundMissing) {
                            PermissionBanner(
                                icon = Icons.Filled.LocationOff,
                                message = stringResource(R.string.permission_background_missing),
                            )
                        }
                        if (batteryRestricted) {
                            PermissionBanner(
                                icon = Icons.Filled.BatteryAlert,
                                message = stringResource(R.string.permission_battery_restricted),
                                actionLabel = stringResource(R.string.action_allow),
                                onAction = {
                                    val direct = Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}"),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try {
                                        context.startActivity(direct)
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(
                                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        } catch (_: Exception) {
                                        }
                                    }
                                },
                            )
                        }
                        if (geofenceFailed) {
                            PermissionBanner(
                                icon = Icons.Filled.LocationOff,
                                message = stringResource(R.string.permission_geofence_failed),
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

    if (showNewSheet || editingReminder != null) {
        key(editingReminder?.id ?: "new", sharedPrefill) {
            ReminderEditorModal(
                existing = editingReminder,
                initialKind = if (newSheetKind == "time") ReminderKind.TIME else ReminderKind.LOCATION,
                initialPlaceName = sharedPrefill?.first ?: "",
                initialCoordinate = sharedPrefill?.second,
                onClose = {
                    showNewSheet = false
                    editingReminder = null
                    newSheetKind = null
                    sharedPrefill = null
                },
            )
        }
    }

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
                    store.snoozeAt(target, nextMorningMillis())
                    NotificationHelper.cancel(context, target.id)
                },
                onNavigate = if (target.kind == ReminderKind.LOCATION) {
                    {
                        val uri = Uri.parse(
                            "geo:${target.latitude},${target.longitude}?q=${target.latitude},${target.longitude}" +
                                "(${Uri.encode(target.placeName.ifEmpty { target.title })})"
                        )
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_no_navigation_app),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                } else null,
                onShare = {
                    val shareText = buildString {
                        append(context.getString(R.string.share_reminder_line, target.title))
                        if (target.placeName.isNotEmpty()) {
                            append("\n").append(context.getString(R.string.share_place_line, target.placeName))
                        }
                        if (target.kind == ReminderKind.LOCATION) {
                            append("\nhttps://maps.google.com/?q=")
                                .append(target.latitude)
                                .append(",")
                                .append(target.longitude)
                        }
                    }
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(
                        Intent.createChooser(
                            sendIntent,
                            context.getString(R.string.share_chooser_title),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                onDelete = { requestDelete(target) },
            )
        }
    }
}
