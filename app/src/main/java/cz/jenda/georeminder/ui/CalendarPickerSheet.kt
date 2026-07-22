package cz.jenda.georeminder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.jenda.georeminder.data.CalendarImport
import cz.jenda.georeminder.model.CzechFormat
import cz.jenda.georeminder.ui.components.CardDivider
import cz.jenda.georeminder.ui.components.EmptyState
import cz.jenda.georeminder.ui.components.InsetCard
import cz.jenda.georeminder.ui.components.SectionHeader
import cz.jenda.georeminder.ui.components.SheetHeader
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Výběr události z kalendáře pro jednorázový import do připomínky.
 * Události načte přes [CalendarImport]; po ťuknutí předá vybranou událost
 * zpět ([onPick]), kde se z ní udělá předvyplněný formulář.
 */
@Composable
fun CalendarPickerSheet(
    onPick: (CalendarImport.Event) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    // null = ještě se načítá
    var events by remember { mutableStateOf<List<CalendarImport.Event>?>(null) }

    LaunchedEffect(Unit) {
        events = withContext(Dispatchers.IO) { CalendarImport.upcoming(context) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .statusBarsPadding(),
    ) {
        SheetHeader(
            title = "Import z kalendáře",
            leftText = "Zrušit",
            onLeft = onClose,
        )

        val list = events
        when {
            list == null -> {
                Text(
                    text = "Načítám události…",
                    style = GeoType.body,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(20.dp),
                )
            }
            list.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Filled.EventBusy,
                        title = "Žádné události",
                        text = "V nejbližších 30 dnech nemáš v kalendáři žádnou událost k importu.",
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 40.dp),
                ) {
                    SectionHeader("Nadcházející události", Modifier.padding(top = 8.dp))
                    InsetCard {
                        list.forEachIndexed { index, event ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .iosClickable { onPick(event) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = event.title,
                                        style = GeoType.body,
                                        color = colors.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildEventSubtitle(event),
                                        style = GeoType.caption,
                                        color = colors.secondaryLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (index != list.lastIndex) CardDivider()
                        }
                    }
                    Text(
                        text = "Vyber událost – převezmu z ní název, čas a případné místo. " +
                            "Je to jednorázový import (bez průběžné synchronizace).",
                        style = GeoType.caption2,
                        color = colors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private fun buildEventSubtitle(event: CalendarImport.Event): String {
    val time = if (event.allDay) CzechFormat.date(event.begin) else CzechFormat.dateTime(event.begin)
    val loc = event.location?.trim()?.takeIf { it.isNotBlank() }
    return if (loc != null) "$time • $loc" else time
}
