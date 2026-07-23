package cz.jenda.georeminder.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.ui.res.stringResource
import cz.jenda.georeminder.R
import cz.jenda.georeminder.BuildConfig
import cz.jenda.georeminder.data.LanguageController
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import cz.jenda.georeminder.data.BackupManager
import cz.jenda.georeminder.data.FeatureSettings
import cz.jenda.georeminder.notify.TtsSpeaker
import cz.jenda.georeminder.ui.components.CardDivider
import cz.jenda.georeminder.ui.components.IOSSwitch
import cz.jenda.georeminder.ui.components.InsetCard
import cz.jenda.georeminder.ui.components.SectionHeader
import cz.jenda.georeminder.ui.components.SheetHeader
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import cz.jenda.georeminder.ui.theme.ThemeController
import cz.jenda.georeminder.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val coroutineScope = rememberCoroutineScope()
    val currentMode by ThemeController.mode.collectAsStateWithLifecycle()

    val ttsEnabled by FeatureSettings.ttsEnabled.collectAsStateWithLifecycle()
    val ttsReadFullText by FeatureSettings.ttsReadFullText.collectAsStateWithLifecycle()
    val groupByPlace by FeatureSettings.groupByPlace.collectAsStateWithLifecycle()
    val ttsTestMessage = stringResource(R.string.tts_test_message)

    var showCalendarSheet by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val success = BackupManager.exportBackup(context, uri)
                Toast.makeText(
                    context,
                    context.getString(
                        if (success) R.string.toast_backup_export_success
                        else R.string.backup_export_failed
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val success = BackupManager.importBackup(context, uri)
                Toast.makeText(
                    context,
                    context.getString(
                        if (success) R.string.toast_backup_import_success
                        else R.string.backup_import_failed
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        SheetHeader(
            title = stringResource(R.string.settings_title),
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
            // --- SEKCIE 1: VZHLAD ---
            Column {
                SectionHeader(stringResource(R.string.settings_appearance))
                InsetCard {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = mode == currentMode,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.RadioButton,
                                    onClick = { ThemeController.set(context, mode) },
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(mode.labelRes),
                                style = GeoType.body,
                                color = colors.label,
                                modifier = Modifier.weight(1f),
                            )
                            if (mode == currentMode) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (index != ThemeMode.entries.lastIndex) {
                            CardDivider()
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_widget_system),
                    style = GeoType.caption2,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                )
            }

            // --- SEKCIE 1B: JAZYK ---
            val currentLang by FeatureSettings.appLanguage.collectAsState()
            Column {
                SectionHeader(stringResource(R.string.settings_language))
                InsetCard {
                    listOf(
                        LanguageController.LANG_SYSTEM to stringResource(R.string.lang_system),
                        LanguageController.LANG_CS to stringResource(R.string.lang_cs),
                        LanguageController.LANG_EN to stringResource(R.string.lang_en),
                    ).forEachIndexed { index, (langCode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = langCode == currentLang,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.RadioButton,
                                    onClick = { LanguageController.setAppLanguage(context, langCode) },
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = GeoType.body,
                                color = colors.label,
                                modifier = Modifier.weight(1f),
                            )
                            if (langCode == currentLang) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (index != 2) CardDivider()
                    }
                }
            }

            // --- SEKCIE 2: FUNKCE & TTS ---
            Column {
                SectionHeader(stringResource(R.string.settings_features))
                InsetCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_tts_enable),
                                style = GeoType.body,
                                color = colors.label,
                            )
                            Text(
                                text = stringResource(R.string.settings_tts_enable_description),
                                style = GeoType.caption,
                                color = colors.secondaryLabel,
                            )
                        }
                        IOSSwitch(
                            checked = ttsEnabled,
                            label = stringResource(R.string.settings_tts_enable),
                            onCheckedChange = { FeatureSettings.setTtsEnabled(context, it) },
                        )
                    }

                    if (ttsEnabled) {
                        CardDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_tts_full),
                                    style = GeoType.body,
                                    color = colors.label,
                                )
                                Text(
                                    text = stringResource(R.string.settings_tts_full_description),
                                    style = GeoType.caption,
                                    color = colors.secondaryLabel,
                                )
                            }
                            IOSSwitch(
                                checked = ttsReadFullText,
                                label = stringResource(R.string.settings_tts_full),
                                onCheckedChange = { FeatureSettings.setTtsReadFullText(context, it) },
                            )
                        }
                        CardDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .iosClickable {
                                    TtsSpeaker.speakText(context, ttsTestMessage)
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                text = stringResource(R.string.settings_tts_test),
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

                    CardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_group_by_place),
                                style = GeoType.body,
                                color = colors.label,
                            )
                            Text(
                                text = stringResource(R.string.settings_group_description),
                                style = GeoType.caption,
                                color = colors.secondaryLabel,
                            )
                        }
                        IOSSwitch(
                            checked = groupByPlace,
                            label = stringResource(R.string.settings_group_by_place),
                            onCheckedChange = { FeatureSettings.setGroupByPlace(context, it) },
                        )
                    }
                }
            }

            // --- SEKCIE 3: ZÁLOHOVÁNÍ & IMPORT ---
            Column {
                SectionHeader(stringResource(R.string.settings_backup))
                InsetCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosClickable { showCalendarSheet = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(10.dp))
                        Text(stringResource(R.string.settings_import_calendar), style = GeoType.body, color = colors.accent, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(20.dp))
                    }
                    CardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosClickable { exportLauncher.launch("georeminder_backup.json") }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Upload, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_export_backup), style = GeoType.body, color = colors.accent)
                            Text(stringResource(R.string.settings_export_backup_description), style = GeoType.caption, color = colors.secondaryLabel)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(20.dp))
                    }
                    CardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosClickable { importLauncher.launch(arrayOf("application/json")) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.FileDownload, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_import_backup), style = GeoType.body, color = colors.accent)
                            Text(stringResource(R.string.settings_import_backup_description), style = GeoType.caption, color = colors.secondaryLabel)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // --- SEKCIE 4: SPOLEHLIVOST & SYSTÉM ---
            Column {
                SectionHeader(stringResource(R.string.settings_permissions))
                InsetCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosClickable {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_notification_system),
                            style = GeoType.body,
                            color = colors.label,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = colors.tertiaryLabel,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    CardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosClickable {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_battery_optimization),
                            style = GeoType.body,
                            color = colors.label,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = colors.tertiaryLabel,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    CardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosClickable {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_all_permissions),
                            style = GeoType.body,
                            color = colors.label,
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
            }

            // --- SEKCIE 5: O APLIKACI ---
            Column {
                SectionHeader(stringResource(R.string.settings_app_info))
                InsetCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_version),
                            style = GeoType.body,
                            color = colors.label,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.settings_version_value, BuildConfig.VERSION_NAME),
                            style = GeoType.subheadline,
                            color = colors.secondaryLabel,
                        )
                    }
                    CardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosClickable {
                                val intent = Intent(Intent.ACTION_VIEW, "https://github.com/JendaNDT/GeoReminder-Android".toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_github),
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
            }
        }
    }

    if (showCalendarSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCalendarSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = null,
        ) {
            CalendarImportSheet(onClose = { showCalendarSheet = false })
        }
    }
}
