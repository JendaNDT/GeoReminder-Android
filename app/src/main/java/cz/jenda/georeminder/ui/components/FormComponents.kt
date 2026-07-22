package cz.jenda.georeminder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cz.jenda.georeminder.model.AlertStyle
import cz.jenda.georeminder.model.FavoritePlace
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType

/** Pole pro zadání náznaku / názvu připomínky. */
@Composable
fun ReminderTitleInput(
    title: String,
    onTitleChange: (String) -> Unit,
    placeholder: String = "Co ti mám připomenout?",
    modifier: Modifier = Modifier,
) {
    val colors = GeoTheme.colors
    InsetCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (title.isEmpty()) {
                Text(
                    text = placeholder,
                    style = GeoType.body,
                    color = colors.tertiaryLabel,
                )
            }
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                textStyle = GeoType.body.copy(color = colors.label),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Řada čipů oblíbených míst pod sekcí Kde. */
@Composable
fun FavoritePlacesChipsRow(
    favorites: List<FavoritePlace>,
    onSelect: (FavoritePlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GeoTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.width(8.dp))
        favorites.forEach { fav ->
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.card)
                    .iosClickable { onSelect(fav) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = colors.yellow,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = fav.name,
                    style = GeoType.subheadline,
                    color = colors.label,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
    }
}

/** Výběr dnů v týdnu pro týdenní opakování (Po–Ne). */
@Composable
fun WeekdayChipsRow(
    selectedDays: Set<Int>,
    onToggleDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GeoTheme.colors
    val dayLabels = listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne")
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        dayLabels.forEachIndexed { idx, label ->
            val dayIso = idx + 1
            val selected = dayIso in selectedDays
            val (bg, textColor) = if (selected) {
                colors.accent to Color.White
            } else {
                colors.segmentTrack to colors.secondaryLabel
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .toggleable(
                        value = selected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Checkbox,
                        onValueChange = { onToggleDay(dayIso) },
                    )
                    .semantics {
                        contentDescription = "Den $label ${if (selected) "vybrán" else "nevybrán"}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = GeoType.footnoteBold,
                    color = textColor,
                )
            }
        }
    }
}
