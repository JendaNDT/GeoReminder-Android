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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val currentMode by ThemeController.mode.collectAsStateWithLifecycle()

    val ttsEnabled by FeatureSettings.ttsEnabled.collectAsStateWithLifecycle()
    val ttsReadFullText by FeatureSettings.ttsReadFullText.collectAsStateWithLifecycle()
    val groupByPlace by FeatureSettings.groupByPlace.collectAsStateWithLifecycle()

    var showCalendarSheet by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val success = BackupManager.exportBackup(context, uri)
            Toast.makeText(
                context,
                if (success) "Záloha byla úspěšně vytvořena" else "Export zálohy selhal",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val success = BackupManager.importBackup(context, uri)
            Toast.makeText(
                context,
                if (success) "Záloha byla úspěšně obnovena" else "Obnovení zálohy selhalo",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        SheetHeader(
            title = "Nastavení",
            leftText = "Hotovo",
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
                SectionHeader("Vzhled")
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
                                text = mode.label,
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
                    text = "Widget na ploše se řídí nastavením systému.",
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
                                text = "Číst Připomínky nahlas (TTS)",
                                style = GeoType.body,
                                color = colors.label,
                            )
                            Text(
                                text = "Při spuštění přečte název česky",
                                style = GeoType.caption,
                                color = colors.secondaryLabel,
                            )
                        }
                        IOSSwitch(
                            checked = ttsEnabled,
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
                                    text = "Číst i celý podtitulek",
                                    style = GeoType.body,
                                    color = colors.label,
                                )
                                Text(
                                    text = "Přečte místo i čas spuštění",
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
                                    TtsSpeaker.speakText(context, "GeoReminder: Toto je ukázka hlasitého čtení připomínek.")
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
                                text = "Vyzkoušet hlasové čtení",
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
                                text = "Seskupit notifikace na stejném místě",
                                style = GeoType.body,
                                color = colors.label,
                            )
                            Text(
                                text = "Více připomínek na jednom místě spojí do jedné notifikace",
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

            // --- SEKCIE 3: ZÁLOHOVÁNÍ & IMPORT ---
            Column {
                SectionHeader("Zálohování & Kalendář")
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
                        Text("Importovat z Google Kalendáře", style = GeoType.body, color = colors.accent, modifier = Modifier.weight(1f))
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
                            Text("Exportovat zálohu (JSON)", style = GeoType.body, color = colors.accent)
                            Text("Uloží seznam připomínek a oblíbených do souboru (bez příloh)", style = GeoType.caption, color = colors.secondaryLabel)
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
                            Text("Importovat zálohu (JSON)", style = GeoType.body, color = colors.accent)
                            Text("Načte seznam připomínek a oblíbených ze záložního JSONu", style = GeoType.caption, color = colors.secondaryLabel)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // --- SEKCIE 4: SPOLEHLIVOST & SYSTÉM ---
            Column {
                SectionHeader("Spolehlivost & Oprávnění")
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
                            text = "Nastavení notifikací telefonu",
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
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Optimalizace baterie",
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
                            text = "Všechna oprávnění v Nastavení",
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
                SectionHeader("O aplikaci")
                InsetCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Verze aplikace",
                            style = GeoType.body,
                            color = colors.label,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "v2.5 (Redesign Vytříbený)",
                            style = GeoType.subheadline,
                            color = colors.secondaryLabel,
                        )
                    }
                    CardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosClickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JendaNDT/GeoReminder-Android"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Zdrojový kód na GitHubu",
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
