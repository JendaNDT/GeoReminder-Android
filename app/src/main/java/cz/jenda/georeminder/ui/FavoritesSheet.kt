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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        SheetHeader(
            title = "Oblíbená místa",
            leftText = "Hotovo",
            onLeft = onClose,
            rightContent = {
                GlassCircleButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Nové oblíbené místo",
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
                    title = "Žádná oblíbená místa",
                    text = "Ťukni na + a ulož si třeba Domov nebo Práci. Připomínky pak zadáš na dva ťuky.",
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
        ModalBottomSheet(
            onDismissRequest = {
                addingNew = false
                editingPlace = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = null,
        ) {
            EditFavoriteSheet(
                existing = editingPlace,
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
            title = "Smazat oblíbené místo?",
            message = "Opravdu chcete smazat místo „${place.name}"?",
            confirmText = "Smazat",
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
                Icon(Icons.Filled.Delete, null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Smazat", style = GeoType.footnoteBold, color = Color.White)
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
                        CustomAccessibilityAction("Smazat") { showConfirmDialog = true; true }
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
                    text = "výchozí poloměr ${place.radius.toInt()} m",
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
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val store = remember { FavoritesStore.get(context) }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var placeName by remember { mutableStateOf(existing?.name ?: "") }
    var coordinate by remember {
        mutableStateOf(existing?.let { LatLng(it.latitude, it.longitude) })
    }
    var radius by remember { mutableStateOf(existing?.radius ?: DEFAULT_RADIUS) }
    var showPicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val initialName = remember { existing?.name ?: "" }
    val initialCoord = remember { existing?.let { LatLng(it.latitude, it.longitude) } }
    val initialRadius = remember { existing?.radius ?: DEFAULT_RADIUS }

    val isDirty = name.trim() != initialName.trim() || coordinate != initialCoord || radius != initialRadius
    val canSave = name.trim().isNotEmpty() && coordinate != null

    fun handleClose() {
        if (isDirty) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        handleClose()
    }

    fun save() {
        val coord = coordinate ?: return
        val cleanName = name.trim()
        if (existing != null) {
            store.update(
                existing.copy(
                    name = cleanName,
                    latitude = coord.latitude,
                    longitude = coord.longitude,
                    radius = radius,
                )
            )
        } else {
            store.add(
                FavoritePlace(
                    name = cleanName,
                    latitude = coord.latitude,
                    longitude = coord.longitude,
                    radius = radius,
                )
            )
        }
        onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .statusBarsPadding()
    ) {
        SheetHeader(
            title = if (existing == null) "Nové místo" else "Upravit místo",
            leftText = "Zrušit",
            onLeft = { handleClose() },
            rightText = "Uložit",
            rightEnabled = canSave,
            onRight = { save() },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            SectionHeader("Název", Modifier.padding(top = 8.dp))
            InsetCard {
                FormTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Např. Domov",
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("Místo")
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
                            coordinate == null -> "Vybrat místo na mapě"
                            placeName.isNotEmpty() -> placeName
                            else -> "Místo vybráno"
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
                            text = "Výchozí poloměr: ${radius.toInt()} m",
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
