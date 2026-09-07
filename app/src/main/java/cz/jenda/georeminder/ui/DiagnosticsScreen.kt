package cz.jenda.georeminder.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cz.jenda.georeminder.R
import cz.jenda.georeminder.data.DiagnosticEvent
import cz.jenda.georeminder.data.DiagnosticEventType
import cz.jenda.georeminder.data.DiagnosticSnapshot
import cz.jenda.georeminder.data.DiagnosticStore
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.data.SystemAccess
import cz.jenda.georeminder.model.CzechFormat
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.ui.components.CardDivider
import cz.jenda.georeminder.ui.components.InsetCard
import cz.jenda.georeminder.ui.components.SectionHeader
import cz.jenda.georeminder.ui.components.SheetHeader
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType

@Composable
fun DiagnosticsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = GeoTheme.colors
    val diagnostics = remember(context) { DiagnosticStore.get(context) }
    var snapshot by remember { mutableStateOf(diagnostics.snapshot()) }

    fun refresh() {
        snapshot = diagnostics.snapshot()
    }

    DisposableEffect(lifecycleOwner, diagnostics) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        SheetHeader(
            title = stringResource(R.string.diagnostics_title),
            leftText = stringResource(R.string.action_done),
            onLeft = onClose,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DiagnosticAccessSection(snapshot)
            DiagnosticDeliverySection(snapshot)

            Column {
                SectionHeader(stringResource(R.string.settings_reliability))
                InsetCard {
                    DiagnosticActionRow(stringResource(R.string.diagnostics_fix_permissions)) {
                        SystemAccess.openAppDetails(context)
                    }
                    CardDivider()
                    DiagnosticActionRow(stringResource(R.string.diagnostics_resync)) {
                        runCatching { ReminderStore.get(context).resyncAll() }
                        refresh()
                    }
                    CardDivider()
                    DiagnosticActionRow(stringResource(R.string.diagnostics_test_minute)) {
                        ReminderStore.get(context).add(
                            Reminder(
                                title = context.getString(R.string.diagnostics_test_title),
                                kind = ReminderKind.TIME,
                                dueDate = System.currentTimeMillis() + 60_000L,
                                timeRepeat = TimeRepeat.NEVER,
                            ),
                        )
                        Toast.makeText(
                            context,
                            R.string.diagnostics_test_created,
                            Toast.LENGTH_SHORT,
                        ).show()
                        refresh()
                    }
                    CardDivider()
                    DiagnosticActionRow(stringResource(R.string.diagnostics_copy)) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                context.getString(R.string.diagnostics_title),
                                diagnostics.technicalReport(snapshot),
                            ),
                        )
                        Toast.makeText(
                            context,
                            R.string.diagnostics_copied,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                Text(
                    text = stringResource(R.string.diagnostics_privacy_note),
                    style = GeoType.caption2,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                )
            }

            DiagnosticHistorySection(snapshot.recentEvents)
        }
    }
}

@Composable
private fun DiagnosticAccessSection(snapshot: DiagnosticSnapshot) {
    Column {
        SectionHeader(stringResource(R.string.diagnostics_access))
        InsetCard {
            DiagnosticStatusRow(
                stringResource(R.string.diagnostics_notifications),
                snapshot.notificationsEnabled,
            )
            CardDivider()
            DiagnosticStatusRow(
                stringResource(R.string.diagnostics_fine_location),
                snapshot.fineLocationGranted,
            )
            CardDivider()
            DiagnosticStatusRow(
                stringResource(R.string.diagnostics_background_location),
                snapshot.backgroundLocationGranted,
            )
            CardDivider()
            DiagnosticStatusRow(
                stringResource(R.string.diagnostics_system_location),
                snapshot.systemLocationEnabled,
            )
            CardDivider()
            DiagnosticStatusRow(
                stringResource(R.string.diagnostics_exact_alarms),
                snapshot.exactAlarmsAllowed,
                falseLabel = stringResource(R.string.diagnostics_not_allowed),
            )
            CardDivider()
            DiagnosticStatusRow(
                stringResource(R.string.diagnostics_battery),
                snapshot.batteryUnrestricted,
                falseLabel = stringResource(R.string.diagnostics_restricted),
            )
        }
    }
}

@Composable
private fun DiagnosticDeliverySection(snapshot: DiagnosticSnapshot) {
    Column {
        SectionHeader(stringResource(R.string.diagnostics_delivery))
        InsetCard {
            DiagnosticValueRow(
                stringResource(R.string.diagnostics_active_geo),
                snapshot.activeGeoReminders.toString(),
            )
            CardDivider()
            DiagnosticValueRow(
                stringResource(R.string.diagnostics_geofences),
                stringResource(
                    R.string.diagnostics_geofence_counts,
                    snapshot.registeredGeofences,
                    snapshot.failedGeofences,
                ),
            )
            CardDivider()
            DiagnosticValueRow(
                stringResource(R.string.diagnostics_limit),
                stringResource(R.string.diagnostics_limit_value, snapshot.geofenceLimitUsed),
            )
            CardDivider()
            DiagnosticValueRow(
                stringResource(R.string.diagnostics_active_time),
                snapshot.activeTimeReminders.toString(),
            )
            CardDivider()
            DiagnosticValueRow(
                stringResource(R.string.diagnostics_nearest_alarm),
                snapshot.nearestTimeAlarm?.let(CzechFormat::dateTime)
                    ?: stringResource(R.string.diagnostics_none),
            )
            CardDivider()
            DiagnosticValueRow(
                stringResource(R.string.diagnostics_snoozed),
                snapshot.snoozedReminders.toString(),
            )
            CardDivider()
            DiagnosticValueRow(
                stringResource(R.string.diagnostics_last_resync),
                snapshot.lastSuccessfulResync?.let(CzechFormat::dateTime)
                    ?: stringResource(R.string.diagnostics_unknown),
            )
            CardDivider()
            DiagnosticValueRow(
                stringResource(R.string.diagnostics_last_error),
                snapshot.lastRegistrationError ?: stringResource(R.string.diagnostics_none),
            )
            CardDivider()
            DiagnosticValueRow(
                stringResource(R.string.diagnostics_data_state),
                when (snapshot.dataIntegrityState) {
                    ReminderStore.DataIntegrityState.OK -> stringResource(R.string.diagnostics_data_ok)
                    ReminderStore.DataIntegrityState.PARTIAL_RECOVERED ->
                        stringResource(R.string.diagnostics_data_partial)
                    ReminderStore.DataIntegrityState.CORRUPTED ->
                        stringResource(R.string.diagnostics_data_corrupted)
                },
            )
        }
    }
}

@Composable
private fun DiagnosticHistorySection(events: List<DiagnosticEvent>) {
    val colors = GeoTheme.colors
    Column {
        SectionHeader(stringResource(R.string.diagnostics_history))
        InsetCard {
            if (events.isEmpty()) {
                Text(
                    text = stringResource(R.string.diagnostics_event_empty),
                    style = GeoType.body,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            } else {
                events.forEachIndexed { index, event ->
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = diagnosticEventLabel(event.type),
                            style = GeoType.body,
                            color = colors.label,
                        )
                        Text(
                            text = buildString {
                                append(CzechFormat.dateTime(event.timestamp))
                                event.detail?.let { append(" · ").append(it) }
                            },
                            style = GeoType.caption,
                            color = colors.secondaryLabel,
                        )
                    }
                    if (index != events.lastIndex) CardDivider()
                }
            }
        }
    }
}

@Composable
private fun diagnosticEventLabel(type: DiagnosticEventType): String = when (type) {
    DiagnosticEventType.RESYNC_OK -> stringResource(R.string.diagnostics_event_resync_ok)
    DiagnosticEventType.RESYNC_FAILED -> stringResource(R.string.diagnostics_event_resync_failed)
    DiagnosticEventType.GEOFENCE_REGISTER_OK -> stringResource(R.string.diagnostics_event_geofence_ok)
    DiagnosticEventType.GEOFENCE_REGISTER_FAIL -> stringResource(R.string.diagnostics_event_geofence_failed)
    DiagnosticEventType.ALARM_SCHEDULED -> stringResource(R.string.diagnostics_event_alarm_scheduled)
    DiagnosticEventType.ALARM_FIRED -> stringResource(R.string.diagnostics_event_alarm_fired)
    DiagnosticEventType.SNOOZE_SET -> stringResource(R.string.diagnostics_event_snooze_set)
    DiagnosticEventType.DATA_PARTIAL_RECOVERY -> stringResource(R.string.diagnostics_event_data_partial)
}

@Composable
private fun DiagnosticStatusRow(
    label: String,
    ok: Boolean,
    falseLabel: String = stringResource(R.string.diagnostics_problem),
) {
    val colors = GeoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = GeoType.body,
            color = colors.label,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (ok) stringResource(R.string.diagnostics_ok) else falseLabel,
            style = GeoType.subheadline,
            color = if (ok) colors.secondaryLabel else colors.red,
        )
    }
}

@Composable
private fun DiagnosticValueRow(label: String, value: String) {
    val colors = GeoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = GeoType.body,
            color = colors.label,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = value,
            style = GeoType.subheadline,
            color = colors.secondaryLabel,
        )
    }
}

@Composable
private fun DiagnosticActionRow(label: String, onClick: () -> Unit) {
    val colors = GeoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .iosClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = GeoType.body,
            color = colors.accent,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.tertiaryLabel,
            modifier = Modifier.size(20.dp),
        )
    }
}
