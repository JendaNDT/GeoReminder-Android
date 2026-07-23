package cz.jenda.georeminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import cz.jenda.georeminder.ui.components.*
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.LatLng
import cz.jenda.georeminder.data.FavoritesStore
import cz.jenda.georeminder.model.DEFAULT_RADIUS
import cz.jenda.georeminder.model.FavoritePlace
import cz.jenda.georeminder.ui.components.CardDivider
import cz.jenda.georeminder.ui.components.EmptyState
import cz.jenda.georeminder.ui.components.GlassCircleButton
import cz.jenda.georeminder.ui.components.InsetCard
import cz.jenda.georeminder.ui.components.RadiusSlider
import cz.jenda.georeminder.ui.components.SectionHeader
import cz.jenda.georeminder.ui.components.SheetHeader
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import kotlinx.coroutines.launch

/**
 * Správa oblíbených míst: seznam, přidání, úprava, mazání (DESIGN_SPEC §5.5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val store = remember { FavoritesStore.get(context) }
    val favorites by store.favorites.collectAsStateWithLifecycle()

    var addingNew by remember { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf<FavoritePlace?>(null) }
    var editorDismissRequest by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        SheetHeader(
            title = stringResource(cz.jenda.georeminder.R.string.favorites_title),
            leftText = stringResource(cz.jenda.georeminder.R.string.action_done),
            onLeft = onClose,
            rightContent = {
                GlassCircleButton(
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(cz.jenda.georeminder.R.string.favorites_add),
                ) { addingNew = true }
            },
        )

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 90.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Filled.StarBorder,
                    title = stringResource(cz.jenda.georeminder.R.string.favorites_empty_title),
                    text = stringResource(cz.jenda.georeminder.R.string.favorites_empty_text),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 40.dp),
            ) {
                InsetCard {
                    favorites.forEachIndexed { index, place ->
                        key(place.id) {
                            SwipeFavoriteRow(
                                place = place,
                                onTap = { editingPlace = place },
                                onDelete = { store.delete(place) },
                            )
                        }
                        if (index != favorites.lastIndex) CardDivider(startIndent = 56.dp)
                    }
                }
            }
        }
    }

    if (addingNew || editingPlace != null) {
        Dialog(
            onDismissRequest = { editorDismissRequest++ },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
        ) {
            EditFavoriteSheet(
                existing = editingPlace,
                dismissRequest = editorDismissRequest,
                onClose = {
                    addingNew = false
                    editingPlace = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeFavoriteRow(
    place: FavoritePlace,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = GeoTheme.colors
    val redContent = if (colors.red.luminance() > 0.18f) Color.Black else Color.White
    val deleteLabel = stringResource(cz.jenda.georeminder.R.string.action_delete_short)
    var showConfirmDialog by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showConfirmDialog = true
            }
            false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.4f },
    )

    if (showConfirmDialog) {
        IOSConfirmDialog(
            title = stringResource(cz.jenda.georeminder.R.string.favorites_delete_title),
            message = stringResource(cz.jenda.georeminder.R.string.favorites_delete_message, place.name),
            confirmText = stringResource(cz.jenda.georeminder.R.string.action_delete),
            isDestructive = true,
            onConfirm = {
                showConfirmDialog = false
                onDelete()
            },
            onDismiss = { showConfirmDialog = false },
        )
    }

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.red)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Icon(Icons.Filled.Delete, null, tint = redContent)
                Spacer(Modifier.width(6.dp))
                Text(deleteLabel, style = GeoType.footnoteBold, color = redContent)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .iosClickable(onClick = onTap)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(deleteLabel) { showConfirmDialog = true; true }
                    )
                }
                .defaultMinSize(minHeight = 58.dp)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Star, null,
                tint = colors.yellow,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = place.name,
                    style = GeoType.body,
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        cz.jenda.georeminder.R.string.radius_default_compact,
                        place.radius.toInt()
                    ),
                    style = GeoType.caption,
                    color = colors.secondaryLabel,
                )
            }
        }
    }
}

/** Formulář pro jedno oblíbené místo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFavoriteSheet(
    existing: FavoritePlace?,
    dismissRequest: Int = 0,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val store = remember { FavoritesStore.get(context) }
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var placeName by remember { mutableStateOf(existing?.name ?: "") }
    var coordinate by remember {
        mutableStateOf(existing?.let { LatLng(it.latitude, it.longitude) })
    }
    var radius by remember { mutableDoubleStateOf(existing?.radius ?: DEFAULT_RADIUS) }
    var showPicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    val initialName = remember { existing?.name ?: "" }
    val initialCoord = remember { existing?.let { LatLng(it.latitude, it.longitude) } }
    val initialRadius = remember { existing?.radius ?: DEFAULT_RADIUS }

    val isDirty = name.trim() != initialName.trim() || coordinate != initialCoord || radius != initialRadius
    val canSave = name.trim().isNotEmpty() && coordinate != null && !isSaving

    fun handleClose() {
        if (isSaving) return
        if (isDirty) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    val initialDismissRequest = remember { dismissRequest }
    LaunchedEffect(dismissRequest) {
        if (dismissRequest != initialDismissRequest) handleClose()
    }

    fun save() {
        val coord = coordinate ?: return
        if (!canSave) return
        val cleanName = name.trim().take(200)
        val placeToSave = if (existing != null) {
            existing.copy(
                    name = cleanName,
                    latitude = coord.latitude,
                    longitude = coord.longitude,
                    radius = radius,
            )
        } else {
            FavoritePlace(
                    name = cleanName,
                    latitude = coord.latitude,
                    longitude = coord.longitude,
                    radius = radius,
            )
        }
        isSaving = true
        saveFailed = false
        coroutineScope.launch {
            val success = if (existing != null) {
                store.updateDurably(placeToSave)
            } else {
                store.addDurably(placeToSave)
            }
            isSaving = false
            if (success) onClose() else saveFailed = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .statusBarsPadding()
    ) {
        SheetHeader(
            title = if (existing == null) stringResource(cz.jenda.georeminder.R.string.favorite_new_title) else stringResource(cz.jenda.georeminder.R.string.favorite_edit_title),
            leftText = stringResource(cz.jenda.georeminder.R.string.action_cancel),
            onLeft = { handleClose() },
            rightText = stringResource(cz.jenda.georeminder.R.string.action_save),
            rightEnabled = canSave,
            onRight = { save() },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            SectionHeader(stringResource(cz.jenda.georeminder.R.string.favorite_name_label), Modifier.padding(top = 8.dp))
            InsetCard {
                FormTextField(
                    value = name,
                    onValueChange = { name = it.take(200) },
                    placeholder = stringResource(cz.jenda.georeminder.R.string.favorite_name_hint),
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader(stringResource(cz.jenda.georeminder.R.string.kind_location))
            InsetCard {
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
                        text = when {
                            coordinate == null -> stringResource(cz.jenda.georeminder.R.string.location_picker_title)
                            placeName.isNotEmpty() -> placeName
                            else -> stringResource(cz.jenda.georeminder.R.string.favorite_selected_place)
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(
                                cz.jenda.georeminder.R.string.radius_default_value,
                                radius.toInt()
                            ),
                            style = GeoType.subheadline,
                            color = colors.label,
                        )
                        RadiusSlider(
                            radius = radius,
                            onRadiusChange = { radius = it },
                        )
                    }
                }
            }
        }
        if (saveFailed) {
            Text(
                text = stringResource(cz.jenda.georeminder.R.string.storage_error),
                style = GeoType.caption,
                color = colors.red,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }

    if (showDiscardDialog) {
        IOSDiscardDialog(
            onConfirm = {
                showDiscardDialog = false
                onClose()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }

    if (showPicker) {
        // Přes celý displej (Dialog) – stejné řešení jako ve formuláři připomínky
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
                onConfirm = { newName, coord, newRadius ->
                    placeName = newName
                    coordinate = coord
                    radius = newRadius
                    showPicker = false
                },
            )
        }
    }
}
