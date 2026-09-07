package cz.jenda.georeminder.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.ui.theme.GeoTheme

/**
 * Jediný host pro editor reminderu. Swipe dolů, Back i kliknutí mimo sheet
 * používají stejnou dirty ochranu uvnitř EditReminderSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditorModal(
    existing: Reminder?,
    initialKind: ReminderKind = ReminderKind.LOCATION,
    initialPlaceName: String = "",
    initialCoordinate: LatLng? = null,
    onClose: () -> Unit,
    shape: Shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
) {
    val colors = GeoTheme.colors
    var editorDirty by remember(existing?.id, initialKind, initialPlaceName, initialCoordinate) {
        mutableStateOf(false)
    }
    var dismissRequestNonce by remember(existing?.id, initialKind, initialPlaceName, initialCoordinate) {
        mutableIntStateOf(0)
    }

    fun requestClose() {
        if (editorDirty) dismissRequestNonce++ else onClose()
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && editorDirty) {
                dismissRequestNonce++
                false
            } else {
                true
            }
        },
    )

    ModalBottomSheet(
        onDismissRequest = { requestClose() },
        sheetState = sheetState,
        containerColor = colors.background,
        shape = shape,
        dragHandle = null,
    ) {
        EditReminderSheet(
            existing = existing,
            initialKind = initialKind,
            initialPlaceName = initialPlaceName,
            initialCoordinate = initialCoordinate,
            onClose = onClose,
            dismissRequestNonce = dismissRequestNonce,
            onDirtyChange = { editorDirty = it },
        )
    }
}
