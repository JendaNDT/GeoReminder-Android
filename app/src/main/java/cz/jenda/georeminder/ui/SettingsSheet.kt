package cz.jenda.georeminder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.jenda.georeminder.ui.components.CardDivider
import cz.jenda.georeminder.ui.components.InsetCard
import cz.jenda.georeminder.ui.components.SectionHeader
import cz.jenda.georeminder.ui.components.SheetHeader
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import cz.jenda.georeminder.ui.theme.ThemeController
import cz.jenda.georeminder.ui.theme.ThemeMode

/**
 * Nastavení aplikace – zatím jen volba vzhledu (Android rozšíření,
 * iOS verze se řídí pouze systémem).
 */
@Composable
fun SettingsSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val currentMode by ThemeController.mode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .statusBarsPadding()
    ) {
        SheetHeader(
            title = "Nastavení",
            leftText = "Hotovo",
            onLeft = onClose,
        )

        SectionHeader("Vzhled", Modifier.padding(top = 8.dp))
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
        Spacer(Modifier.size(8.dp))
        Text(
            text = "Widget na ploše se řídí nastavením systému.",
            style = GeoType.caption2,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
