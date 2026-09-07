package cz.jenda.georeminder.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.LatLng
import cz.jenda.georeminder.R
import cz.jenda.georeminder.data.AttachmentHelper
import cz.jenda.georeminder.data.FavoritesStore
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.model.AlertStyle
import cz.jenda.georeminder.model.CzechFormat
import cz.jenda.georeminder.model.DEFAULT_RADIUS
import cz.jenda.georeminder.model.FavoritePlace
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.ReminderText
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.model.TriggerType
import cz.jenda.georeminder.notify.ReminderScheduler
import cz.jenda.georeminder.ui.components.CardDivider
import cz.jenda.georeminder.ui.components.IOSDiscardDialog
import cz.jenda.georeminder.ui.components.IOSSwitch
import cz.jenda.georeminder.ui.components.InsetCard
import cz.jenda.georeminder.ui.components.RadiusSlider
import cz.jenda.georeminder.ui.components.SectionHeader
import cz.jenda.georeminder.ui.components.SegmentedControl
import cz.jenda.georeminder.ui.components.SheetHeader
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import java.util.Calendar
import java.util.TimeZone

/**
 * Formulář pro vytvoření nebo úpravu připomínky (na místě i na čas).
 * Rozložení 1:1 podle DESIGN_SPEC §5.3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReminderSheet(
    existing: Reminder?,
    initialKind: ReminderKind = ReminderKind.LOCATION,
    initialPlaceName: String = "",
    initialCoordinate: LatLng? = null,
    onClose: () -> Unit,
    dismissRequestNonce: Int = 0,
    onDirtyChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val store = remember { ReminderStore.get(context) }
    val favoritesStore = remember { FavoritesStore.get(context) }
    val favorites by favoritesStore.favorites.collectAsStateWithLifecycle()

    val editorNow = remember(existing?.id) { System.currentTimeMillis() }
    val initialDueDate = remember(existing?.id, existing?.dueDate, existing?.timeRepeat, editorNow) {
        editorInitialDueDate(existing, editorNow)
    }

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: initialKind) }
    var trigger by remember { mutableStateOf(existing?.trigger ?: TriggerType.ARRIVE) }
    var repeats by remember { mutableStateOf(existing?.repeats ?: false) }
    var radius by remember { mutableStateOf(existing?.radius ?: DEFAULT_RADIUS) }
    var placeName by remember {
        mutableStateOf(existing?.placeName ?: initialPlaceName)
    }
    var coordinate by remember {
        mutableStateOf(
            if (existing != null && existing.kind == ReminderKind.LOCATION) {
                LatLng(existing.latitude, existing.longitude)
            } else {
                initialCoordinate
            }
        )
    }
    var dueDate by remember { mutableStateOf(initialDueDate) }
    var timeRepeat by remember { mutableStateOf(existing?.timeRepeat ?: TimeRepeat.NEVER) }
    var weekdaysSel by remember {
        mutableStateOf(
            existing?.weekdays?.takeIf { it.isNotEmpty() }?.toSet()
                ?: setOf(
                    ReminderScheduler.isoWeekday(
                        existing?.dueDate ?: initialDueDate
                    )
                )
        )
    }

    var alertStyle by remember { mutableStateOf(existing?.alertStyle ?: AlertStyle.DEFAULT) }
    var nagging by remember { mutableStateOf(existing?.nagging ?: false) }
    var attachmentPath by remember { mutableStateOf<String?>(existing?.attachmentPath) }

    val attachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val copied = AttachmentHelper.copyToInternal(context, uri)
            if (copied != null) {
                if (attachmentPath != null && attachmentPath != existing?.attachmentPath) {
                    AttachmentHelper.deleteAttachment(context, attachmentPath)
                }
                attachmentPath = copied
            } else {
                Toast.makeText(context, context.getString(R.string.attachment_invalid), Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showPicker by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var repeatMenuOpen by remember { mutableStateOf(false) }
    var alertMenuOpen by remember { mutableStateOf(false) }

    val timeInPast = kind == ReminderKind.TIME &&
            timeRepeat == TimeRepeat.NEVER &&
            dueDate <= System.currentTimeMillis()
    val canSave = title.trim().isNotEmpty() &&
            (kind == ReminderKind.TIME || coordinate != null) &&
            !timeInPast

    fun save() {
        val cleanTitle = title.trim()
        val cleanPlace = placeName.ifEmpty { context.getString(R.string.edit_selected_place) }

        if (existing != null) {
            val updated = if (kind == ReminderKind.LOCATION) {
                val coord = coordinate ?: return
                existing.copy(
                    title = cleanTitle,
                    kind = kind,
                    placeName = cleanPlace,
                    latitude = coord.latitude,
                    longitude = coord.longitude,
                    radius = radius,
                    trigger = trigger,
                    repeats = repeats,
                    dueDate = null,
                    weekdays = null,
                    alertStyle = alertStyle,
                    nagging = nagging,
                    attachmentPath = attachmentPath,
                )
            } else {
                existing.copy(
                    title = cleanTitle,
                    kind = kind,
                    dueDate = dueDate,
                    timeRepeat = timeRepeat,
                    repeats = false,
                    weekdays = if (timeRepeat == TimeRepeat.WEEKLY) {
                        weekdaysSel.sorted()
                    } else null,
                    alertStyle = alertStyle,
                    nagging = nagging,
                    attachmentPath = attachmentPath,
                )
            }
            store.update(updated)
        } else {
            if (kind == ReminderKind.LOCATION) {
                val coord = coordinate ?: return
                store.add(
                    Reminder(
                        title = cleanTitle,
                        kind = ReminderKind.LOCATION,
                        placeName = cleanPlace,
                        latitude = coord.latitude,
                        longitude = coord.longitude,
                        radius = radius,
                        trigger = trigger,
                        repeats = repeats,
                        alertStyle = alertStyle,
                        nagging = nagging,
                        attachmentPath = attachmentPath,
                    )
                )
            } else {
                store.add(
                    Reminder(
                        title = cleanTitle,
                        kind = ReminderKind.TIME,
                        dueDate = dueDate,
                        timeRepeat = timeRepeat,
                        weekdays = if (timeRepeat == TimeRepeat.WEEKLY) {
                            weekdaysSel.sorted()
                        } else null,
                        alertStyle = alertStyle,
                        nagging = nagging,
                        attachmentPath = attachmentPath,
                    )
                )
            }
        }
        onClose()
    }

    val initialTitle = existing?.title ?: ""
    val initialKindVal = existing?.kind ?: initialKind
    val initialTriggerVal = existing?.trigger ?: TriggerType.ARRIVE
    val initialRepeatsVal = existing?.repeats ?: false
    val initialRadiusVal = existing?.radius ?: DEFAULT_RADIUS
    val initialPlaceVal = existing?.placeName ?: initialPlaceName
    val initialCoordVal = if (existing != null && existing.kind == ReminderKind.LOCATION) {
        LatLng(existing.latitude, existing.longitude)
    } else {
        initialCoordinate
    }
    val initialDueDateVal = initialDueDate
    val initialTimeRepeatVal = existing?.timeRepeat ?: TimeRepeat.NEVER
    val initialWeekdaysVal = existing?.weekdays?.takeIf { it.isNotEmpty() }?.toSet()
        ?: setOf(ReminderScheduler.isoWeekday(initialDueDateVal))
    val initialAlertStyleVal = existing?.alertStyle ?: AlertStyle.DEFAULT
    val initialNaggingVal = existing?.nagging ?: false
    val initialAttachmentVal = existing?.attachmentPath

    val isDirty = title.trim() != initialTitle ||
            kind != initialKindVal ||
            trigger != initialTriggerVal ||
            repeats != initialRepeatsVal ||
            radius != initialRadiusVal ||
            placeName != initialPlaceVal ||
            coordinate != initialCoordVal ||
            dueDate != initialDueDateVal ||
            timeRepeat != initialTimeRepeatVal ||
            weekdaysSel != initialWeekdaysVal ||
            alertStyle != initialAlertStyleVal ||
            nagging != initialNaggingVal ||
            attachmentPath != initialAttachmentVal

    var showDiscardDialog by remember { mutableStateOf(false) }

    fun cleanupUnsavedAttachment() {
        if (attachmentPath != null && attachmentPath != existing?.attachmentPath) {
            AttachmentHelper.deleteAttachment(context, attachmentPath)
        }
    }

    fun handleClose() {
        if (isDirty) {
            showDiscardDialog = true
        } else {
            cleanupUnsavedAttachment()
            onClose()
        }
    }

    LaunchedEffect(isDirty) {
        onDirtyChange(isDirty)
    }
    LaunchedEffect(dismissRequestNonce) {
        if (dismissRequestNonce > 0) handleClose()
    }

    androidx.activity.compose.BackHandler(enabled = isDirty) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        IOSDiscardDialog(
            onConfirm = {
                showDiscardDialog = false
                cleanupUnsavedAttachment()
                onClose()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        SheetHeader(
            title = stringResource(
                if (existing == null) R.string.title_new_reminder else R.string.title_edit_reminder
            ),
            leftText = stringResource(R.string.action_cancel),
            onLeft = { handleClose() },
            rightText = stringResource(R.string.action_save),
            rightEnabled = canSave,
            onRight = { save() },
        )

        if (timeInPast) {
            Text(
                text = stringResource(R.string.edit_time_past),
                style = GeoType.caption,
                color = colors.red,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            SectionHeader(stringResource(R.string.edit_section_what), Modifier.padding(top = 8.dp))
            InsetCard {
                FormTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = stringResource(R.string.edit_title_example),
                )
            }

            Spacer(Modifier.height(20.dp))
            InsetCard {
                SegmentedControl(
                    options = ReminderKind.entries.map { ReminderText.kindLabel(context, it) },
                    selectedIndex = ReminderKind.entries.indexOf(kind),
                    modifier = Modifier.padding(10.dp),
                ) { kind = ReminderKind.entries[it] }
            }

            Spacer(Modifier.height(24.dp))

            if (kind == ReminderKind.LOCATION) {
                SectionHeader(stringResource(R.string.edit_section_where))
                InsetCard {
                    if (favorites.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            favorites.forEach { place ->
                                FavoriteChip(place) {
                                    coordinate = LatLng(place.latitude, place.longitude)
                                    placeName = place.name
                                    radius = place.radius
                                }
                            }
                        }
                        CardDivider()
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosClickable { showPicker = true }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Map, null,
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (coordinate == null) {
                                stringResource(R.string.edit_select_place_map)
                            } else {
                                placeName.ifEmpty { stringResource(R.string.edit_selected_place) }
                            },
                            style = GeoType.body,
                            color = colors.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Filled.ChevronRight, null,
                            tint = colors.secondaryLabel,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    if (coordinate != null) {
                        CardDivider()
                        SegmentedControl(
                            options = TriggerType.entries.map { ReminderText.triggerLabel(context, it) },
                            selectedIndex = TriggerType.entries.indexOf(trigger),
                            modifier = Modifier.padding(10.dp),
                        ) { trigger = TriggerType.entries[it] }

                        CardDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.edit_radius_format, radius.toInt()),
                                style = GeoType.subheadline,
                                color = colors.label,
                            )
                            RadiusSlider(
                                radius = radius,
                                onRadiusChange = { radius = it },
                            )
                            Text(
                                text = stringResource(R.string.edit_radius_hint),
                                style = GeoType.caption2,
                                color = colors.secondaryLabel,
                            )
                        }

                        CardDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = ReminderText.triggerRepeatLabel(context, trigger),
                                style = GeoType.body,
                                color = colors.label,
                                modifier = Modifier.weight(1f),
                            )
                            IOSSwitch(checked = repeats) { repeats = it }
                        }
                    }
                }
            } else {
                SectionHeader(stringResource(R.string.edit_section_when))
                InsetCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.edit_date_time),
                            style = GeoType.body,
                            color = colors.label,
                            modifier = Modifier.weight(1f),
                        )
                        DateCapsule(CzechFormat.date(dueDate)) { showDateDialog = true }
                        Spacer(Modifier.width(8.dp))
                        DateCapsule(CzechFormat.time(dueDate)) { showTimeDialog = true }
                    }

                    CardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.edit_repetition),
                            style = GeoType.body,
                            color = colors.label,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            Row(
                                modifier = Modifier.iosClickable { repeatMenuOpen = true },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = ReminderText.timeRepeatLabel(context, timeRepeat),
                                    style = GeoType.body,
                                    color = colors.secondaryLabel,
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Filled.UnfoldMore, null,
                                    tint = colors.secondaryLabel,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = repeatMenuOpen,
                                onDismissRequest = { repeatMenuOpen = false },
                            ) {
                                TimeRepeat.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(ReminderText.timeRepeatLabel(context, option), style = GeoType.body)
                                        },
                                        trailingIcon = {
                                            if (option == timeRepeat) {
                                                Icon(
                                                    Icons.Filled.Check, null,
                                                    tint = colors.accent,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                        onClick = {
                                            timeRepeat = option
                                            repeatMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (timeRepeat == TimeRepeat.WEEKLY) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 2.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            val dayLabels = listOf(
                                stringResource(R.string.day_mon),
                                stringResource(R.string.day_tue),
                                stringResource(R.string.day_wed),
                                stringResource(R.string.day_thu),
                                stringResource(R.string.day_fri),
                                stringResource(R.string.day_sat),
                                stringResource(R.string.day_sun),
                            )
                            val dayFull = listOf(
                                stringResource(R.string.day_mon_full),
                                stringResource(R.string.day_tue_full),
                                stringResource(R.string.day_wed_full),
                                stringResource(R.string.day_thu_full),
                                stringResource(R.string.day_fri_full),
                                stringResource(R.string.day_sat_full),
                                stringResource(R.string.day_sun_full),
                            )
                            for (day in 1..7) {
                                val selected = day in weekdaysSel
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .toggleable(
                                            value = selected,
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            role = Role.Checkbox,
                                            onValueChange = {
                                                weekdaysSel = if (selected) {
                                                    if (weekdaysSel.size > 1) weekdaysSel - day else weekdaysSel
                                                } else {
                                                    weekdaysSel + day
                                                }
                                            },
                                        )
                                        .semantics { contentDescription = dayFull[day - 1] },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) colors.accent else colors.segmentTrack
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = dayLabels[day - 1],
                                            style = GeoType.footnote,
                                            color = if (selected) Color.White else colors.label,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (timeRepeat != TimeRepeat.NEVER) {
                        Text(
                            text = stringResource(
                                if (timeRepeat == TimeRepeat.DAILY) {
                                    R.string.edit_daily_help
                                } else {
                                    R.string.edit_weekly_help
                                }
                            ),
                            style = GeoType.caption2,
                            color = colors.secondaryLabel,
                            modifier = Modifier.padding(
                                start = 16.dp, end = 16.dp, bottom = 12.dp,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader(stringResource(R.string.edit_alerts_section))
            InsetCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.edit_alert_kind),
                        style = GeoType.body,
                        color = colors.label,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        Row(
                            modifier = Modifier.iosClickable { alertMenuOpen = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = ReminderText.alertStyleLabel(context, alertStyle),
                                style = GeoType.body,
                                color = colors.secondaryLabel,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.UnfoldMore, null,
                                tint = colors.secondaryLabel,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = alertMenuOpen,
                            onDismissRequest = { alertMenuOpen = false },
                        ) {
                            AlertStyle.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(ReminderText.alertStyleLabel(context, option), style = GeoType.body)
                                    },
                                    trailingIcon = {
                                        if (option == alertStyle) {
                                            Icon(
                                                Icons.Filled.Check, null,
                                                tint = colors.accent,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        alertStyle = option
                                        alertMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }

                CardDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.alert_nagging),
                        style = GeoType.body,
                        color = colors.label,
                        modifier = Modifier.weight(1f),
                    )
                    IOSSwitch(checked = nagging) { nagging = it }
                }

                if (alertStyle == AlertStyle.URGENT || nagging) {
                    Text(
                        text = buildString {
                            if (alertStyle == AlertStyle.URGENT) {
                                append(context.getString(R.string.edit_urgent_help))
                            }
                            if (nagging) {
                                if (isNotEmpty()) append(" ")
                                append(context.getString(R.string.edit_nagging_help))
                            }
                        },
                        style = GeoType.caption2,
                        color = colors.secondaryLabel,
                        modifier = Modifier.padding(
                            start = 16.dp, end = 16.dp, bottom = 12.dp,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader(stringResource(R.string.attachment_header))
            InsetCard {
                if (attachmentPath == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosClickable { attachmentLauncher.launch(AttachmentHelper.SUPPORTED_MIME_TYPES) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.attachment_attach),
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
                } else {
                    val fileName = attachmentPath?.substringAfterLast('/')
                        ?: stringResource(R.string.attachment_default_name)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = fileName,
                            style = GeoType.body,
                            color = colors.label,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.attachment_open),
                            tint = colors.accent,
                            modifier = Modifier
                                .size(22.dp)
                                .iosClickable { AttachmentHelper.openAttachment(context, attachmentPath!!) },
                        )
                        Spacer(Modifier.width(14.dp))
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.attachment_delete),
                            tint = colors.red,
                            modifier = Modifier
                                .size(22.dp)
                                .iosClickable {
                                    val toDelete = attachmentPath
                                    attachmentPath = null
                                    if (toDelete != null && toDelete != existing?.attachmentPath) {
                                        AttachmentHelper.deleteAttachment(context, toDelete)
                                    }
                                },
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        Dialog(
            onDismissRequest = { showPicker = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
            ),
        ) {
            LocationPickerSheet(
                initialName = placeName,
                initialCoordinate = coordinate,
                initialRadius = radius,
                onCancel = { showPicker = false },
                onConfirm = { name, coord, newRadius ->
                    placeName = name
                    coordinate = coord
                    radius = newRadius
                    showPicker = false
                },
            )
        }
    }

    if (showDateDialog) {
        val todayStartUtc = remember {
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                val local = Calendar.getInstance()
                clear()
                set(
                    local.get(Calendar.YEAR),
                    local.get(Calendar.MONTH),
                    local.get(Calendar.DAY_OF_MONTH),
                )
            }.timeInMillis
        }
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate + TimeZone.getDefault().getOffset(dueDate),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis >= todayStartUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDateDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { selected ->
                        dueDate = mergeSelectedDate(dueDate, selected)
                    }
                    showDateDialog = false
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showDateDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = dateState, showModeToggle = false)
        }
    }

    if (showTimeDialog) {
        val cal = Calendar.getInstance().apply { timeInMillis = dueDate }
        val timeState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true,
        )
        Dialog(onDismissRequest = { showTimeDialog = false }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.card)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = timeState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showTimeDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(onClick = {
                        dueDate = mergeSelectedTime(dueDate, timeState.hour, timeState.minute)
                        showTimeDialog = false
                    }) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            }
        }
    }
}

internal fun editorInitialDueDate(existing: Reminder?, now: Long): Long {
    val reminder = existing ?: return now + 3_600_000L
    val due = reminder.dueDate ?: return now + 3_600_000L
    return if (reminder.timeRepeat == TimeRepeat.NEVER) {
        due.coerceAtLeast(now)
    } else {
        due
    }
}

/** Kapsle s datem/časem (kompaktní DatePicker jako na iOS). */
@Composable
private fun DateCapsule(text: String, onClick: () -> Unit) {
    val colors = GeoTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(colors.segmentTrack)
            .iosClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = text, style = GeoType.body, color = colors.label)
    }
}

/** Čip oblíbeného místa (hvězdička + název, modré 12% pozadí). */
@Composable
fun FavoriteChip(place: FavoritePlace, onClick: () -> Unit) {
    val colors = GeoTheme.colors
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = 0.12f))
            .iosClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Star, null,
            tint = colors.yellow,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(text = place.name, style = GeoType.footnote, color = colors.label)
    }
}

/** Textové pole uvnitř karty (bez orámování, jako iOS Form). */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val colors = GeoTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = GeoType.body.copy(color = colors.label),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = GeoType.body,
                        color = colors.tertiaryLabel,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** Sloučí vybrané datum (UTC půlnoc z kalendáře) se stávajícím časem. */
private fun mergeSelectedDate(currentMillis: Long, selectedUtcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = selectedUtcMillis
    }
    return Calendar.getInstance().apply {
        timeInMillis = currentMillis
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** Sloučí vybraný čas se stávajícím datem. */
private fun mergeSelectedTime(currentMillis: Long, hour: Int, minute: Int): Long {
    return Calendar.getInstance().apply {
        timeInMillis = currentMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
