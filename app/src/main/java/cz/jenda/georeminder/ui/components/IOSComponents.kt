package cz.jenda.georeminder.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer

/** Klik bez agresivního Material ripple efektu (iOS vzhled), s jemnou vizuální a hmatovou odezvou. */
@Composable
fun Modifier.iosClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.6f else 1.0f,
        label = "iosClickAlpha"
    )

    return this
        .graphicsLayer { this.alpha = alpha }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Kruhové „skleněné" tlačítko v toolbaru (hvězdička, plus). */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = if (GeoTheme.colors.isGlass) Color.White else GeoTheme.colors.label,
    onClick: () -> Unit,
) {
    val colors = GeoTheme.colors
    val bgColor = if (colors.isGlass) Color.White.copy(alpha = 0.22f) else colors.card
    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .size(size)
            .shadow(4.dp, CircleShape, spotColor = colors.shadow)
            .iosClickable(onClick = onClick),
        shape = CircleShape,
        color = bgColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

/** Kapslové textové tlačítko v hlavičce sheetu (Zrušit / Uložit / Hotovo). */
@Composable
fun CapsulePillButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bold: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = GeoTheme.colors
    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .shadow(3.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.25f))
            .iosClickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = colors.card,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = if (bold) GeoType.headline else GeoType.body,
                color = if (enabled) colors.accent else colors.tertiaryLabel,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

/** Hlavička sheetu: vlevo/vpravo kapslová tlačítka, uprostřed inline titulek. */
@Composable
fun SheetHeader(
    title: String,
    leftText: String? = null,
    onLeft: (() -> Unit)? = null,
    rightText: String? = null,
    rightEnabled: Boolean = true,
    onRight: (() -> Unit)? = null,
    rightContent: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = GeoType.headline,
            color = GeoTheme.colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 72.dp),
        )
        if (leftText != null && onLeft != null) {
            CapsulePillButton(
                text = leftText,
                modifier = Modifier.align(Alignment.CenterStart),
                onClick = onLeft,
            )
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            when {
                rightContent != null -> rightContent()
                rightText != null && onRight != null -> CapsulePillButton(
                    text = rightText,
                    enabled = rightEnabled,
                    bold = true,
                    onClick = onRight,
                )
            }
        }
    }
}

/** Hlavička sekce nad kartou („Aktivní", „Kde"…). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = GeoType.subheadline,
        color = GeoTheme.colors.secondaryLabel,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
    )
}

/** Karta seznamu / formulářové sekce – zaoblení 26, okraje 16 (DESIGN_SPEC §3). */
@Composable
fun InsetCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(GeoTheme.colors.card),
        content = content,
    )
}

/** Oddělovač řádků uvnitř karty – odsazený zleva. */
@Composable
fun CardDivider(startIndent: Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent),
        thickness = 0.7.dp,
        color = GeoTheme.colors.separator,
    )
}

/** iOS přepínač (zelený toggle) s haptickou odezvou a min. 48dp dotykovou plochou. */
@Composable
fun IOSSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = GeoTheme.colors
    val haptics = LocalHapticFeedback.current
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.green else colors.switchTrackOff,
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        label = "switchThumb",
    )
    Box(
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(51.dp)
                .height(31.dp)
                .clip(CircleShape)
                .background(trackColor)
                .toggleable(
                    value = checked,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Switch,
                    onValueChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCheckedChange(it)
                    },
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .size(27.dp)
                    .shadow(3.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.3f))
                    .background(Color.White, CircleShape),
            )
        }
    }
}

/** iOS slider – tenká dráha, velký bílý kulatý palec. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IOSSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
) {
    val colors = GeoTheme.colors
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = modifier.height(32.dp),
        thumb = {
            Box(
                modifier = Modifier
                    .size(27.dp)
                    .shadow(3.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
                    .background(Color.White, CircleShape),
            )
        },
        track = { state ->
            val fraction =
                (state.value - state.valueRange.start) /
                    (state.valueRange.endInclusive - state.valueRange.start)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.sliderTrack),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .background(colors.accent),
                )
            }
        },
    )
}

/** Slider poloměru geo-oblasti (50–1000 m, krok 25 m) – sjednocuje formulář, oblíbená i výběr místa. */
@Composable
fun RadiusSlider(
    radius: Double,
    onRadiusChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    IOSSlider(
        value = radius.toFloat(),
        onValueChange = { onRadiusChange(Math.round(it / 25.0) * 25.0) },
        valueRange = 50f..1000f,
        steps = 37,
        modifier = modifier,
    )
}

/** iOS segmentovaný přepínač s klouzajícím jezdcem a haptikou. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val colors = GeoTheme.colors
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .height(38.dp)
            .clip(CircleShape)
            .background(colors.segmentTrack),
    ) {
        val segmentWidth = maxWidth / options.size
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            label = "segmentThumb",
        )
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(2.dp)
                .shadow(2.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.25f))
                .background(colors.segmentThumb, CircleShape),
        )
        Row(modifier = Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { index, option ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = index == selectedIndex,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(index)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = GeoType.segment,
                        color = colors.label,
                    )
                }
            }
        }
    }
}

/** Prázdný stav (88×88 dlaždice v accent barvě dle Vytříbený). */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = GeoTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    color = colors.accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = title,
            style = GeoType.emptyTitle,
            color = colors.label,
            textAlign = TextAlign.Center,
        )
        Text(
            text = text,
            style = GeoType.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

/** Primární kapslové tlačítko („Použít toto místo", „Pokračovat"). */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = GeoTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.accent else colors.accent.copy(alpha = 0.4f))
            .iosClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = GeoType.headline, color = Color.White)
    }
}

/** Standardní iOS potvrdzovací dialog pro stornování neuložených změn. */
@Composable
fun IOSDiscardDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GeoTheme.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(cz.jenda.georeminder.R.string.discard_dialog_title), style = GeoType.headline) },
        text = { Text(androidx.compose.ui.res.stringResource(cz.jenda.georeminder.R.string.discard_dialog_text), style = GeoType.body) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(androidx.compose.ui.res.stringResource(cz.jenda.georeminder.R.string.discard_dialog_confirm), color = colors.red)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(cz.jenda.georeminder.R.string.discard_dialog_dismiss))
            }
        }
    )
}

/** Obecný potvrdzovací dialog (např. mazání položky). */
@Composable
fun IOSConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Potvrdit",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GeoTheme.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = GeoType.headline) },
        text = { Text(message, style = GeoType.body) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (isDestructive) colors.red else colors.accent)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Zrušit")
            }
        }
    )
}
