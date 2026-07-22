package cz.jenda.georeminder.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.model.TriggerType
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import java.util.Calendar

/** Sheet s rychlými akcemi pro vybranou připomínku. */
@Composable
fun QuickActionSheet(
    reminder: Reminder,
    onSnoozeTomorrow: () -> Unit,
    onNavigate: (() -> Unit)?,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = GeoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        SheetHeader(
            title = reminder.title,
            leftText = "Zavřít",
            onLeft = onClose,
        )

        SectionHeader("Rychlé akce", Modifier.padding(top = 8.dp))
        InsetCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            // 1. Odložit na zítra ráno
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .iosClickable {
                        onSnoozeTomorrow()
                        onClose()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Schedule, null, tint = colors.orange, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Odložit na zítra ráno (8:00)", style = GeoType.body, color = colors.label, modifier = Modifier.weight(1f))
            }

            // 2. Navigovat (pokud je připomínka na místo)
            if (reminder.kind == ReminderKind.LOCATION && onNavigate != null) {
                CardDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .iosClickable {
                            onNavigate()
                            onClose()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Spustit navigaci na místo", style = GeoType.body, color = colors.accent, modifier = Modifier.weight(1f))
                }
            }

            // 3. Sdílet
            CardDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .iosClickable {
                        onShare()
                        onClose()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Share, null, tint = colors.purple, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Sdílet připomínku", style = GeoType.body, color = colors.label, modifier = Modifier.weight(1f))
            }

            // 4. Upravit
            CardDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .iosClickable {
                        onEdit()
                        onClose()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Edit, null, tint = colors.label, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Upravit připomínku", style = GeoType.body, color = colors.label, modifier = Modifier.weight(1f))
            }

            // 5. Smazat
            CardDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .iosClickable {
                        onDelete()
                        onClose()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, null, tint = colors.red, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Smazat připomínku", style = GeoType.body, color = colors.red, modifier = Modifier.weight(1f))
            }
        }
    }
}

fun nextMorningMillis(): Long {
    val cal = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 8)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/** Určení barevné kategorie, ikony a barvy pro řádek dle Vytříbený. */
@Composable
fun rememberCategoryStyle(reminder: Reminder): Triple<ImageVector, Color, String?> {
    val colors = GeoTheme.colors
    return remember(reminder, colors) {
        val isRepeating = reminder.repeats || reminder.timeRepeat != TimeRepeat.NEVER || !reminder.weekdays.isNullOrEmpty()
        when {
            reminder.isDone -> Triple(
                when {
                    isRepeating -> Icons.Filled.Autorenew
                    reminder.kind == ReminderKind.TIME -> Icons.Filled.Schedule
                    reminder.trigger == TriggerType.LEAVE -> Icons.AutoMirrored.Filled.DirectionsWalk
                    else -> Icons.Filled.LocationOn
                },
                colors.secondaryLabel,
                "hotovo"
            )
            isRepeating -> Triple(
                Icons.Filled.Autorenew,
                colors.purple,
                if (!reminder.weekdays.isNullOrEmpty()) "Po–Pá" else "opakuje"
            )
            reminder.kind == ReminderKind.TIME -> Triple(
                Icons.Filled.Schedule,
                colors.orange,
                null
            )
            reminder.trigger == TriggerType.LEAVE -> Triple(
                Icons.AutoMirrored.Filled.DirectionsWalk,
                colors.teal,
                "odjezd"
            )
            else -> Triple(
                Icons.Filled.LocationOn,
                colors.accent,
                null
            )
        }
    }
}

/** Řádek se swipe akcemi: doprava Hotovo/Vrátit (zelená), doleva Smazat (červená). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeReminderRow(
    reminder: Reminder,
    distance: String?,
    onTap: () -> Unit,
    onLongTap: () -> Unit,
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
        ReminderRow(
            reminder = reminder,
            distance = distance,
            onTap = onTap,
            onLongTap = onLongTap,
            modifier = Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(
                        if (reminder.isDone) "Vrátit" else "Hotovo"
                    ) { onToggleDone(); true },
                    CustomAccessibilityAction("Smazat") { onDelete(); true },
                )
            },
        )
    }
}

/** Jeden řádek seznamu: 36×36 dlaždice typu, titulek, podtitulek a pravý barevný čip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReminderRow(
    reminder: Reminder,
    distance: String?,
    onTap: () -> Unit,
    onLongTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GeoTheme.colors
    val haptics = LocalHapticFeedback.current
    val (icon, categoryColor, defaultBadge) = rememberCategoryStyle(reminder)
    val chipText = distance ?: defaultBadge

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.card)
            .combinedClickable(
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongTap()
                },
                onClick = onTap,
            )
            .defaultMinSize(minHeight = 62.dp)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Barevná dlaždice typu (36×36, radius 11px)
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = if (reminder.isDone) colors.secondaryLabel.copy(alpha = 0.12f) else categoryColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(11.dp),
                )
                .border(
                    width = 1.dp,
                    color = if (reminder.isDone) colors.separator else categoryColor.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(11.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (reminder.isDone) colors.secondaryLabel else categoryColor,
                modifier = Modifier.size(20.dp),
            )
            if (reminder.isDone) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.BottomEnd)
                        .background(colors.green, RoundedCornerShape(999.dp))
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = reminder.title,
                style = GeoType.body,
                color = if (reminder.isDone) colors.secondaryLabel else colors.label,
                textDecoration = if (reminder.isDone) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reminder.subtitle,
                style = GeoType.caption,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Vpravo čip laděný k typu (320 m, odjezd, hotovo apod.)
        if (!chipText.isNullOrEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(
                        color = if (reminder.isDone) colors.secondaryLabel.copy(alpha = 0.10f) else categoryColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = chipText,
                    style = GeoType.chip,
                    color = if (reminder.isDone) colors.secondaryLabel else categoryColor,
                )
            }
        }
    }
}
