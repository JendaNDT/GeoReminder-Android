package cz.jenda.georeminder.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.jenda.georeminder.R
import cz.jenda.georeminder.data.BackupManager
import cz.jenda.georeminder.data.FeatureSettings
import cz.jenda.georeminder.data.LanguageController
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val currentMode by ThemeController.mode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val ttsEnabled by FeatureSettings.ttsEnabled.collectAsStateWithLifecycle()
    val ttsReadFullText by FeatureSettings.ttsReadFullText.collectAsStateWithLifecycle()
    val groupByPlace by FeatureSettings.groupByPlace.collectAsStateWithLifecycle()
    val currentLang = LanguageController.currentLanguageCode()
    val versionName = remember(context.packageName) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }

    var showCalendarSheet by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    BackupManager.exportBackup(context, uri)
                }
                Toast.makeText(
                    context,
                    context.getString(
                        if (success) R.string.toast_backup_export_success
                        else R.string.toast_backup_export_failed
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    BackupManager.importBackup(context, uri)
                }
                Toast.makeText(
                    context,
                    context.getString(
                        if (success) R.string.toast_backup_import_success
                        else R.string.toast_backup_import_failed
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val themeOptions = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
        ThemeMode.NEUTRAL to stringResource(R.string.theme_neutral),
        ThemeMode.GLASS to stringResource(R.string.theme_glass),
    )

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
            Column {
                SectionHeader(stringResource(R.string.settings_appearance))
                InsetCard {
                    themeOptions.forEachIndexed { index, (mode, label) ->
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
                                text = label,
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
                        if (index != themeOptions.lastIndex) CardDivider()
                    }
                }
                Text(
                    text = stringResource(R.string.widget_appearance_system_note),
                    style = GeoType.caption2,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                )
            }

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
                                text = stringResource(R.string.settings_tts_enable_desc),
                                style = GeoType.caption,
                                color = colors.secondaryLabel,
                            )
                        }
                        IOSSwitch(
                            checked = ttsEnabled,
                            onCheckedChange = { enabled ->
                                FeatureSettings.setTtsEnabled(context, enabled)
                                if (!enabled) TtsSpeaker.shutdown()
                            },
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
                                    text = stringResource(R.string.settings_tts_full_desc),
                                    style = GeoType.caption,
                                    color = colors.secondaryLabel,
                                )
                            }
                            IOSSwitch(
                                checked = ttsReadFullText,
                                onCheckedChange = { FeatureSettings.setTtsReadFullText(context, it) },
                            )
                        }
                        CardDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .iosClickable {
                                    TtsSpeaker.speakText(context, context.getString(R.string.tts_test_message))
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
                                text = stringResource(R.string.settings_group_by_place_desc),
                                style = GeoType.caption,
                                color = colors.secondaryLabel,
                            )
                        }
                        IOSSwitch(
                            checked = groupByPlace,
                            onCheckedChange = { FeatureSettings.setGroupByPlace(context, it) },
                        )
                    }
                }
            }

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
                        Text(
                            stringResource(R.string.settings_import_calendar),
                            style = GeoType.body,
                            color = colors.accent,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(20.dp))
                    }
                    CardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosClickable { exportLauncher.launch("georeminder_backup.zip") }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Upload, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_export_backup),
                                style = GeoType.body,
                                color = colors.accent,
                            )
                            Text(
                                stringResource(R.string.settings_export_backup_desc),
                                style = GeoType.caption,
                                color = colors.secondaryLabel,
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(20.dp))
                    }
                    CardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosClickable {
                                importLauncher.launch(
                                    arrayOf("application/zip", "application/json", "application/octet-stream")
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.FileDownload, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_import_backup),
                                style = GeoType.body,
                                color = colors.accent,
                            )
                            Text(
                                stringResource(R.string.settings_import_backup_desc),
                                style = GeoType.caption,
                                color = colors.secondaryLabel,
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Column {
                SectionHeader(stringResource(R.string.settings_reliability))
                InsetCard {
                    SettingsLinkRow(stringResource(R.string.settings_phone_notifications)) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                    CardDivider()
                    SettingsLinkRow(stringResource(R.string.settings_battery_optimization)) {
                        val powerManager = context.getSystemService(PowerManager::class.java)
                        val intent = if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false) {
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        } else {
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                    CardDivider()
                    SettingsLinkRow(stringResource(R.string.settings_all_permissions)) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                }
            }

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
                            text = stringResource(R.string.settings_version_label),
                            style = GeoType.body,
                            color = colors.label,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.settings_version_format, versionName),
                            style = GeoType.subheadline,
                            color = colors.secondaryLabel,
                        )
                    }
                    CardDivider()
                    SettingsLinkRow(
                        label = stringResource(R.string.settings_source_code),
                        accent = true,
                    ) {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/JendaNDT/GeoReminder-Android")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { context.startActivity(intent) } catch (_: Exception) {}
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

@Composable
private fun SettingsLinkRow(
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
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
            color = if (accent) colors.accent else colors.label,
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
