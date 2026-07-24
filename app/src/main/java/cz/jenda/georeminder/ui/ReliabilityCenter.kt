package cz.jenda.georeminder.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import cz.jenda.georeminder.R
import cz.jenda.georeminder.data.GeofenceRegistrationState
import cz.jenda.georeminder.data.LocationHolder
import cz.jenda.georeminder.data.ReliabilityEvent
import cz.jenda.georeminder.data.ReliabilityEventType
import cz.jenda.georeminder.data.ReliabilityHistory
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.notify.ReminderScheduler
import cz.jenda.georeminder.ui.components.CardDivider
import cz.jenda.georeminder.ui.components.InsetCard
import cz.jenda.georeminder.ui.components.SectionHeader
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import java.text.DateFormat
import java.util.Date

@Composable
fun ReliabilityCenter() {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val events by ReliabilityHistory.events.collectAsStateWithLifecycle()
    val reminders by remember { ReminderStore.get(context) }
        .reminders.collectAsStateWithLifecycle()
    val geofenceStates by LocationHolder.geofenceStates.collectAsStateWithLifecycle()
    var refreshKey by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsOk = remember(refreshKey) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val fineLocationOk = remember(refreshKey) {
        LocationHolder.hasFineLocation(context)
    }
    val backgroundLocationOk = remember(refreshKey, fineLocationOk) {
        LocationHolder.hasBackgroundLocation(context)
    }
    val exactAlarmsOk = remember(refreshKey) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java)
                    ?.canScheduleExactAlarms() == true
    }
    val batteryOk = remember(refreshKey) {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) != false
    }
    val activeLocationIds = reminders
        .filter { !it.isDone && it.kind == ReminderKind.LOCATION }
        .map { it.id }
    val activeGeofences = activeLocationIds.count {
        geofenceStates[it] == GeofenceRegistrationState.ACTIVE
    }
    val failedGeofences = activeLocationIds.count {
        geofenceStates[it] == GeofenceRegistrationState.FAILED
    }

    Column {
        SectionHeader(stringResource(R.string.reliability_center_title))
        InsetCard {
            ReliabilityStatusRow(
                label = stringResource(R.string.reliability_notifications),
                detail = if (notificationsOk) {
                    stringResource(R.string.reliability_status_ready)
                } else {
                    stringResource(R.string.reliability_status_blocked)
                },
                ok = notificationsOk,
                icon = Icons.Filled.NotificationsActive,
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
            )
            CardDivider()
            ReliabilityStatusRow(
                label = stringResource(R.string.reliability_location),
                detail = when {
                    !fineLocationOk ->
                        stringResource(R.string.reliability_status_location_missing)
                    !backgroundLocationOk ->
                        stringResource(R.string.reliability_status_background_missing)
                    else -> stringResource(R.string.reliability_status_ready)
                },
                ok = fineLocationOk && backgroundLocationOk,
                icon = Icons.Filled.LocationOn,
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
            )
            CardDivider()
            ReliabilityStatusRow(
                label = stringResource(R.string.reliability_exact_alarms),
                detail = if (exactAlarmsOk) {
                    stringResource(R.string.reliability_status_exact)
                } else {
                    stringResource(R.string.reliability_status_approximate)
                },
                ok = exactAlarmsOk,
                icon = Icons.Filled.Alarm,
                onClick = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}"),
                        )
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${context.packageName}"))
                    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
            )
            CardDivider()
            ReliabilityStatusRow(
                label = stringResource(R.string.reliability_battery),
                detail = if (batteryOk) {
                    stringResource(R.string.reliability_status_ready)
                } else {
                    stringResource(R.string.reliability_status_battery_restricted)
                },
                ok = batteryOk,
                icon = Icons.Filled.BatteryFull,
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
            )
            CardDivider()
            ReliabilityStatusRow(
                label = stringResource(R.string.reliability_geofences),
                detail = when {
                    activeLocationIds.isEmpty() ->
                        stringResource(R.string.reliability_status_no_geofences)
                    failedGeofences > 0 ->
                        stringResource(R.string.reliability_status_geofence_failed, failedGeofences)
                    else ->
                        stringResource(
                            R.string.reliability_status_geofence_active,
                            activeGeofences,
                            activeLocationIds.size,
                        )
                },
                ok = when {
                    activeLocationIds.isEmpty() -> null
                    failedGeofences > 0 -> false
                    else -> activeGeofences == activeLocationIds.size
                },
                icon = Icons.Filled.LocationOn,
            )
            CardDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .iosClickable {
                        val exact = ReminderScheduler.get(context)
                            .scheduleReliabilityTest()
                        val message = if (exact) {
                            context.getString(R.string.reliability_test_scheduled)
                        } else {
                            context.getString(R.string.reliability_test_scheduled_approximate)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Science,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.reliability_test_action),
                        style = GeoType.body,
                        color = colors.accent,
                    )
                    Text(
                        stringResource(R.string.reliability_test_explanation),
                        style = GeoType.caption,
                        color = colors.secondaryLabel,
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = colors.tertiaryLabel,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Text(
            text = stringResource(R.string.reliability_privacy_note),
            style = GeoType.caption2,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )

        SectionHeader(
            stringResource(R.string.reliability_history_title),
            Modifier.padding(top = 12.dp),
        )
        InsetCard {
            if (events.isEmpty()) {
                Text(
                    text = stringResource(R.string.reliability_history_empty),
                    style = GeoType.subheadline,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            } else {
                events.take(5).forEachIndexed { index, event ->
                    ReliabilityEventRow(event)
                    if (index != events.take(5).lastIndex) CardDivider()
                }
                CardDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .iosClickable { ReliabilityHistory.clear(context) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        tint = colors.red,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        stringResource(R.string.reliability_history_clear),
                        style = GeoType.body,
                        color = colors.red,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReliabilityStatusRow(
    label: String,
    detail: String,
    ok: Boolean?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null,
) {
    val colors = GeoTheme.colors
    val clickModifier = if (onClick != null) Modifier.iosClickable(onClick = onClick) else Modifier
    Row(
        modifier = clickModifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.size(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = GeoType.body, color = colors.label)
            Text(detail, style = GeoType.caption, color = colors.secondaryLabel)
        }
        Icon(
            imageVector = when (ok) {
                true -> Icons.Filled.CheckCircle
                false -> Icons.Filled.Warning
                null -> Icons.Filled.ChevronRight
            },
            contentDescription = null,
            tint = when (ok) {
                true -> colors.green
                false -> colors.orange
                null -> colors.tertiaryLabel
            },
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun ReliabilityEventRow(event: ReliabilityEvent) {
    val colors = GeoTheme.colors
    val title = reliabilityEventTitle(event)
    val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(event.timestamp))
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = if (event.reminderTitle.isNullOrBlank()) {
                title
            } else {
                "$title — ${event.reminderTitle}"
            },
            style = GeoType.subheadline,
            color = colors.label,
        )
        Text(
            text = time,
            style = GeoType.caption2,
            color = colors.secondaryLabel,
        )
    }
}

@Composable
private fun reliabilityEventTitle(event: ReliabilityEvent): String =
    when (event.type) {
        ReliabilityEventType.SCHEDULED_EXACT ->
            stringResource(R.string.reliability_event_scheduled_exact)
        ReliabilityEventType.SCHEDULED_APPROXIMATE ->
            stringResource(R.string.reliability_event_scheduled_approximate)
        ReliabilityEventType.GEOFENCE_REGISTERED ->
            stringResource(R.string.reliability_event_geofence_registered)
        ReliabilityEventType.GEOFENCE_FAILED ->
            stringResource(R.string.reliability_event_geofence_failed)
        ReliabilityEventType.TRIGGERED_TIME ->
            stringResource(R.string.reliability_event_triggered_time)
        ReliabilityEventType.TRIGGERED_LOCATION ->
            stringResource(R.string.reliability_event_triggered_location)
        ReliabilityEventType.NOTIFICATION_SHOWN ->
            stringResource(R.string.reliability_event_notification_shown)
        ReliabilityEventType.NOTIFICATION_BLOCKED ->
            stringResource(R.string.reliability_event_notification_blocked)
        ReliabilityEventType.SNOOZED ->
            stringResource(R.string.reliability_event_snoozed)
        ReliabilityEventType.COMPLETED ->
            stringResource(R.string.reliability_event_completed)
        ReliabilityEventType.REACTIVATED ->
            stringResource(R.string.reliability_event_reactivated)
        ReliabilityEventType.DELETED ->
            stringResource(R.string.reliability_event_deleted)
        ReliabilityEventType.TEST_SCHEDULED ->
            stringResource(R.string.reliability_event_test_scheduled)
        ReliabilityEventType.TEST_TRIGGERED ->
            stringResource(R.string.reliability_event_test_triggered)
    }
