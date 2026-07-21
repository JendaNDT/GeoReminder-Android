package cz.jenda.georeminder.ui

import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings as SettingsGear
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
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
import cz.jenda.georeminder.model.TriggerType
import cz.jenda.georeminder.ui.components.CardDivider
import cz.jenda.georeminder.ui.components.EmptyState
import cz.jenda.georeminder.ui.components.GlassCircleButton
import cz.jenda.georeminder.ui.components.InsetCard
import cz.jenda.georeminder.ui.components.PermissionBanner
import cz.jenda.georeminder.ui.components.SectionHeader
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType

/**
 * Hlavní obrazovka: velký titulek GeoReminder, bannery oprávnění a seznam
 * rozdělený na Aktivní a Hotové, u geo-připomínek se vzdáleností.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen() {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val store = remember { ReminderStore.get(context) }
    remember { FavoritesStore.get(context) } // zahřátí (čipy ve formuláři)

    val reminders by store.reminders.collectAsStateWithLifecycle()
    val userLocation by LocationHolder.location.collectAsStateWithLifecycle()

    var showNewSheet by rememberSaveable { mutableStateOf(false) }
    var newSheetKind by rememberSaveable { mutableStateOf<String?>(null) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    var notificationsDenied by remember { mutableStateOf(false) }
    var locationDenied by remember { mutableStateOf(false) }
    var backgroundMissing by remember { mutableStateOf(false) }
    var batteryRestricted by remember { mutableStateOf(false) }
    var sharedPrefill by remember { mutableStateOf<Pair<String, LatLng>?>(null) }

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
                sharedPrefill = PlaceLinkResolver.resolve(text)
                newSheetKind = "location"
                editingReminder = null
                showNewSheet = true
            }
        }
    }

    val active = remember(reminders) {
        reminders.filter { !it.isDone }.sortedByDescending { it.createdAt }
    }
    val done = remember(reminders) {
        reminders.filter { it.isDone }.sortedByDescending { it.createdAt }
    }

    fun distanceText(reminder: Reminder): String? {
        if (reminder.kind != ReminderKind.LOCATION) return null
        val user = userLocation ?: return null
        val result = FloatArray(1)
        Location.distanceBetween(
            user.latitude, user.longitude,
            reminder.latitude, reminder.longitude, result,
        )
        return CzechFormat.distance(result[0])
    }

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
                GlassCircleButton(Icons.Filled.StarBorder, "Oblíbená místa") {
                    showFavorites = true
                }
                Spacer(Modifier.weight(1f))
                GlassCircleButton(Icons.Filled.SettingsGear, "Nastavení") {
                    showSettings = true
                }
                Spacer(Modifier.width(12.dp))
                GlassCircleButton(Icons.Filled.Add, "Nová připomínka") {
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
                modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 18.dp),
            )
        }
        // Bannery oprávnění a spolehlivosti
        if (notificationsDenied || locationDenied || backgroundMissing || batteryRestricted) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    if (notificationsDenied) {
                        PermissionBanner(
                            icon = Icons.Filled.NotificationsOff,
                            message = "Notifikace jsou vypnuté – appka ti nemůže nic připomenout.",
                        )
                    }
                    if (locationDenied) {
                        PermissionBanner(
                            icon = Icons.Filled.LocationOff,
                            message = "Přístup k poloze je zakázaný – připomínky na místa nebudou fungovat.",
                        )
                    } else if (backgroundMissing) {
                        PermissionBanner(
                            icon = Icons.Filled.LocationOff,
                            message = "Poloha není povolená „Vždy“ – připomínky na místa nepřijdou se zavřenou appkou.",
                        )
                    }
                    if (batteryRestricted) {
                        PermissionBanner(
                            icon = Icons.Filled.BatteryAlert,
                            message = "Telefon může kvůli šetření baterie blokovat připomínky na pozadí.",
                            actionLabel = "Povolit",
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
                        title = "Zatím žádné připomínky",
                        text = "Ťukni na + a vytvoř první připomínku na místo nebo na čas.",
                    )
                }
            }
        } else {
            if (active.isNotEmpty()) {
                item { SectionHeader("Aktivní") }
                item {
                    InsetCard {
                        active.forEachIndexed { index, reminder ->
                            key(reminder.id) {
                                SwipeReminderRow(
                                    reminder = reminder,
                                    distance = distanceText(reminder),
                                    onTap = { editingReminder = reminder },
                                    onToggleDone = { store.toggleDone(reminder) },
                                    onDelete = { store.delete(reminder) },
                                )
                            }
                            if (index != active.lastIndex) CardDivider(startIndent = 60.dp)
                        }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
            if (done.isNotEmpty()) {
                item { SectionHeader("Hotové") }
                item {
                    InsetCard {
                        done.forEachIndexed { index, reminder ->
                            key(reminder.id) {
                                SwipeReminderRow(
                                    reminder = reminder,
                                    distance = distanceText(reminder),
                                    onTap = { editingReminder = reminder },
                                    onToggleDone = { store.toggleDone(reminder) },
                                    onDelete = { store.delete(reminder) },
                                )
                            }
                            if (index != done.lastIndex) CardDivider(startIndent = 60.dp)
                        }
                    }
                }
            }
        }
    }

    // Formulář (nová / úprava)
    if (showNewSheet || editingReminder != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showNewSheet = false
                editingReminder = null
                newSheetKind = null
                sharedPrefill = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = null,
        ) {
            // key() zajistí čerstvý formulář, když se změní cíl (úprava vs. nová,
            // nebo přijde sdílené místo, zatímco je formulář otevřený)
            key(editingReminder?.id ?: "new", sharedPrefill) {
                EditReminderSheet(
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

    // Nastavení (vzhled)
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
}

/** Ikona typu připomínky (DESIGN_SPEC §4). */
fun reminderIcon(reminder: Reminder): ImageVector = when {
    reminder.kind == ReminderKind.TIME -> Icons.Filled.Schedule
    reminder.trigger == TriggerType.LEAVE -> Icons.AutoMirrored.Filled.DirectionsWalk
    else -> Icons.Filled.LocationOn
}

/** Řádek se swipe akcemi: doprava Hotovo/Vrátit (zelená), doleva Smazat (červená). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeReminderRow(
    reminder: Reminder,
    distance: String?,
    onTap: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = GeoTheme.colors
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggleDone()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false
                }
                else -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.4f },
    )

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.green)
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (reminder.isDone) Icons.AutoMirrored.Filled.Undo else Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (reminder.isDone) "Vrátit" else "Hotovo",
                            style = GeoType.footnoteBold,
                            color = Color.White,
                        )
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.red)
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Icon(Icons.Filled.Delete, null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Smazat", style = GeoType.footnoteBold, color = Color.White)
                    }
                }
                else -> {}
            }
        },
    ) {
        ReminderRow(reminder = reminder, distance = distance, onTap = onTap)
    }
}

/** Jeden řádek seznamu: ikona typu, titulek a podtitulek se vzdáleností. */
@Composable
private fun ReminderRow(
    reminder: Reminder,
    distance: String?,
    onTap: () -> Unit,
) {
    val colors = GeoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card)
            .iosClickable(onClick = onTap)
            .defaultMinSize(minHeight = 60.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = reminderIcon(reminder),
                contentDescription = null,
                tint = if (reminder.isDone) colors.secondaryLabel else colors.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = reminder.title,
                style = GeoType.body,
                color = if (reminder.isDone) colors.secondaryLabel else colors.label,
                textDecoration = if (reminder.isDone) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reminder.subtitle + (distance?.let { " • $it" } ?: ""),
                style = GeoType.caption,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
