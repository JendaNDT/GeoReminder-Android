package cz.jenda.georeminder.ui

import android.location.Address
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.jenda.georeminder.ui.theme.MapStyles
import cz.jenda.georeminder.ui.theme.ThemeController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.jenda.georeminder.R
import cz.jenda.georeminder.data.ActivityInsets
import cz.jenda.georeminder.data.FavoritesStore
import cz.jenda.georeminder.data.LocationHolder
import cz.jenda.georeminder.data.RecentPlaces
import cz.jenda.georeminder.model.CzechFormat
import cz.jenda.georeminder.ui.components.CapsulePillButton
import cz.jenda.georeminder.ui.components.PrimaryButton
import cz.jenda.georeminder.ui.components.RadiusSlider
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.cos
import kotlin.math.ln

/** Kategorie místa – určuje ikonku ve výsledcích hledání. */
enum class PlaceCategory { SHOP, FOOD, TRANSIT, HEALTH, SCHOOL, CITY, PLACE }

data class GeoSearchResult(
    val title: String,
    val subtitle: String,
    val position: LatLng,
    val distanceMeters: Float? = null,
    val category: PlaceCategory = PlaceCategory.PLACE,
)

fun categoryIcon(category: PlaceCategory) = when (category) {
    PlaceCategory.SHOP -> Icons.Filled.Storefront
    PlaceCategory.FOOD -> Icons.Filled.Restaurant
    PlaceCategory.TRANSIT -> Icons.Filled.DirectionsBus
    PlaceCategory.HEALTH -> Icons.Filled.LocalHospital
    PlaceCategory.SCHOOL -> Icons.Filled.School
    PlaceCategory.CITY -> Icons.Filled.LocationCity
    PlaceCategory.PLACE -> Icons.Filled.Place
}

/**
 * Výběr místa: hledání adresy, ťuknutí do mapy, živý náhled kruhu s poloměrem.
 * Rozložení podle DESIGN_SPEC §5.4 (Google Maps místo Apple Maps).
 */
@Composable
fun LocationPickerSheet(
    initialName: String,
    initialCoordinate: LatLng?,
    initialRadius: Double,
    onCancel: () -> Unit,
    onConfirm: (name: String, coordinate: LatLng, radius: Double) -> Unit,
) {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    var selected by remember { mutableStateOf(initialCoordinate) }
    var selectedName by remember { mutableStateOf(if (initialCoordinate != null) initialName else "") }
    var radius by remember { mutableStateOf(initialRadius) }
    var searchText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchedOnce by remember { mutableStateOf(false) }
    var networkError by remember { mutableStateOf(false) }

    val searchInteraction = remember { MutableInteractionSource() }
    val searchFocused by searchInteraction.collectIsFocusedAsState()
    val favoritePlaces by remember { FavoritesStore.get(context).favorites }
        .collectAsStateWithLifecycle()
    val recentPlaces = remember { RecentPlaces.load(context) }

    // Živé našeptávání: hledá se samo ~třetinu vteřiny po posledním písmenu
    LaunchedEffect(searchText) {
        val query = searchText.trim()
        if (query.length < 2) {
            results = emptyList()
            isSearching = false
            searchedOnce = false
            networkError = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(350)
        when (val outcome = searchPlaces(context, query, LocationHolder.location.value)) {
            is SearchOutcome.Ok -> {
                results = outcome.results
                networkError = false
            }
            SearchOutcome.Offline -> {
                results = emptyList()
                networkError = true
            }
        }
        isSearching = false
        searchedOnce = true
    }

    val cameraPositionState = rememberCameraPositionState {
        val user = LocationHolder.location.value
        position = when {
            initialCoordinate != null ->
                CameraPosition.fromLatLngZoom(
                    initialCoordinate, zoomForSpan(1200.0, initialCoordinate.latitude)
                )
            user != null ->
                CameraPosition.fromLatLngZoom(
                    LatLng(user.latitude, user.longitude), zoomForSpan(1500.0, user.latitude)
                )
            else ->
                // Výchozí pohled: Praha
                CameraPosition.fromLatLngZoom(
                    LatLng(50.0755, 14.4378), zoomForSpan(5000.0, 50.0755)
                )
        }
    }

    fun select(coord: LatLng, name: String) {
        selected = coord
        selectedName = name
        searchText = ""
        results = emptyList()
        searchedOnce = false
        keyboard?.hide()
        focusManager.clearFocus()
        scope.launch {
            runCatching {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(
                        boundsAround(coord, maxOf(radius * 6, 800.0)), 40
                    ),
                    600,
                )
            }
        }
        if (name.isEmpty()) {
            scope.launch {
                val resolved = reverseGeocode(context, coord)
                if (resolved != null && selected == coord) {
                    selectedName = resolved
                }
            }
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val hasFine = remember { LocationHolder.hasFineLocation(context) }
        val currentThemeMode by ThemeController.mode.collectAsStateWithLifecycle()
        val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasFine,
                mapStyleOptions = MapStyles.getMapStyle(currentThemeMode, isSystemDark),
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
            ),
            onMapClick = { latLng -> select(latLng, "") },
        ) {
            selected?.let { coord ->
                Marker(
                    state = MarkerState(position = coord),
                    title = selectedName.ifEmpty { "Vybrané místo" },
                )
                Circle(
                    center = coord,
                    radius = radius,
                    fillColor = colors.accent.copy(alpha = 0.15f),
                    strokeColor = colors.accent,
                    strokeWidth = with(density) { 2.dp.toPx() },
                )
            }
        }

        // Horní vrstva: titulek + hledání + výsledky
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                CapsulePillButton(
                    text = "Zrušit",
                    modifier = Modifier.align(Alignment.CenterStart),
                    onClick = onCancel,
                )
                Text(
                    text = "Vybrat místo",
                    style = GeoType.headline,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // Vyhledávací pole (materiálový vzhled)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(3.dp, RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.glass)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Hledat místo",
                    tint = colors.secondaryLabel,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    textStyle = GeoType.body.copy(color = colors.label),
                    cursorBrush = SolidColor(colors.accent),
                    singleLine = true,
                    interactionSource = searchInteraction,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (searchText.isEmpty()) {
                                Text(
                                    "Hledat adresu nebo místo…",
                                    style = GeoType.body,
                                    color = colors.tertiaryLabel,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent,
                    )
                } else if (searchText.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .iosClickable {
                                searchText = ""
                                results = emptyList()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Cancel,
                            contentDescription = "Smazat hledání",
                            tint = colors.secondaryLabel,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Výsledky hledání / hláška / návrhy při prázdném poli
            if (results.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .shadow(3.dp, RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.glass),
                ) {
                    val shown = results.take(5)
                    shown.forEachIndexed { index, result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .iosClickable {
                                    select(result.position, result.title)
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = categoryIcon(result.category),
                                contentDescription = null,
                                tint = colors.secondaryLabel,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    result.title,
                                    style = GeoType.body,
                                    color = colors.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val detail = listOfNotNull(
                                    result.subtitle.takeIf { it.isNotBlank() },
                                    result.distanceMeters?.let { CzechFormat.distanceShort(it) },
                                ).joinToString(" • ")
                                if (detail.isNotEmpty()) {
                                    Text(
                                        detail,
                                        style = GeoType.caption,
                                        color = colors.secondaryLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        if (index != shown.lastIndex) {
                            HorizontalDivider(thickness = 0.7.dp, color = colors.separator)
                        }
                    }
                }
            } else if (searchedOnce && !isSearching && searchText.trim().length >= 2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.glass),
                ) {
                    Text(
                        if (networkError) {
                            "Vypadá to na výpadek připojení – zkontroluj internet, nebo ťukni rovnou do mapy."
                        } else {
                            "Nic jsem nenašel – zkus jiný název, nebo ťukni rovnou do mapy."
                        },
                        style = GeoType.footnote,
                        color = colors.secondaryLabel,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else if (searchFocused && searchText.isBlank() &&
                (favoritePlaces.isNotEmpty() || recentPlaces.isNotEmpty())
            ) {
                val favs = favoritePlaces.take(3)
                val recents = recentPlaces
                    .filter { recent -> favs.none { it.name.equals(recent.name, ignoreCase = true) } }
                    .take(3)
                val totalCount = favs.size + recents.size
                var rowIndex = 0
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .shadow(3.dp, RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.glass),
                ) {
                    favs.forEach { place ->
                        SuggestionRow(
                            icon = Icons.Filled.Star,
                            iconTint = colors.yellow,
                            title = place.name,
                            subtitle = "oblíbené místo",
                        ) {
                            radius = place.radius
                            select(LatLng(place.latitude, place.longitude), place.name)
                        }
                        rowIndex++
                        if (rowIndex < totalCount) {
                            HorizontalDivider(thickness = 0.7.dp, color = colors.separator)
                        }
                    }
                    recents.forEach { recent ->
                        SuggestionRow(
                            icon = Icons.Filled.History,
                            iconTint = colors.secondaryLabel,
                            title = recent.name,
                            subtitle = "nedávné místo",
                        ) {
                            select(LatLng(recent.latitude, recent.longitude), recent.name)
                        }
                        rowIndex++
                        if (rowIndex < totalCount) {
                            HorizontalDivider(thickness = 0.7.dp, color = colors.separator)
                        }
                    }
                }
            }
        }

        // Spodní lišta s poloměrem a potvrzením.
        // Odsazení od spodní systémové lišty: bere se VĚTŠÍ z hodnoty tohoto
        // okna a hodnoty změřené v hlavním okně appky – dialogová okna na
        // Androidu 15 (Samsung) vlastní hodnotu nedostávají a tlačítko
        // „Použít toto místo" by zmizelo pod lištou.
        if (selected != null) {
            val activityNavPx by ActivityInsets.navigationBottomPx.collectAsState()
            val dialogNavPx = WindowInsets.navigationBars.getBottom(density)
            val bottomInset = with(density) { maxOf(activityNavPx, dialogNavPx).toDp() }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(colors.glass)
                    .padding(16.dp)
                    .padding(bottom = bottomInset),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = selectedName.ifEmpty { "Vybrané místo" },
                    style = GeoType.headline,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Poloměr", style = GeoType.subheadline, color = colors.label)
                    Spacer(Modifier.width(10.dp))
                    RadiusSlider(
                        radius = radius,
                        onRadiusChange = { radius = it },
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            "${radius.toInt()} m",
                            // Tabulkové číslice, ať hodnota při tažení neposkakuje.
                            style = GeoType.subheadline.copy(fontFeatureSettings = "tnum"),
                            color = colors.label,
                        )
                    }
                }
                PrimaryButton(text = "Použít toto místo") {
                    val coord = selected ?: return@PrimaryButton
                    val finalName = selectedName.ifEmpty { "Vybrané místo" }
                    RecentPlaces.add(context, finalName, coord.latitude, coord.longitude)
                    onConfirm(finalName, coord, radius)
                }
            }
        }
    }
}

/** Řádek návrhu (oblíbené / nedávné místo) pod hledacím polem. */
@Composable
private fun SuggestionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = GeoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .iosClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = GeoType.body,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = GeoType.caption,
                color = colors.secondaryLabel,
            )
        }
    }
}

/** Přibližný zoom, aby byl vidět výřez daný v metrech (šířka displeje ~1080 px). */
fun zoomForSpan(meters: Double, latitude: Double, widthPx: Double = 1080.0): Float {
    val metersPerPixelAtZoom0 = 156_543.03392 * cos(Math.toRadians(latitude))
    val zoom = ln(metersPerPixelAtZoom0 * widthPx / meters) / ln(2.0)
    return zoom.toFloat().coerceIn(2f, 20f)
}

/** Obdélník kolem středu o dané šířce/výšce v metrech. */
fun boundsAround(center: LatLng, spanMeters: Double): LatLngBounds {
    val dLat = spanMeters / 2.0 / 111_320.0
    val cosLat = cos(Math.toRadians(center.latitude)).coerceAtLeast(0.01)
    val dLng = spanMeters / 2.0 / (111_320.0 * cosLat)
    return LatLngBounds(
        LatLng(center.latitude - dLat, center.longitude - dLng),
        LatLng(center.latitude + dLat, center.longitude + dLng),
    )
}

/** Výsledek hledání – rozlišuje „nic nenalezeno" od výpadku sítě. */
sealed class SearchOutcome {
    data class Ok(val results: List<GeoSearchResult>) : SearchOutcome()
    object Offline : SearchOutcome()
}

private suspend fun searchPlaces(
    context: android.content.Context,
    query: String,
    near: android.location.Location?,
): SearchOutcome = withContext(Dispatchers.IO) {
    var photonFailed = false
    val photonResults = try {
        cz.jenda.georeminder.data.PhotonLocationRepository.search(query, near).map { item ->
            GeoSearchResult(
                title = item.title,
                subtitle = item.subtitle,
                position = LatLng(item.latitude, item.longitude),
                category = placeCategory(item.osmKey, item.osmValue),
            )
        }
    } catch (_: Exception) {
        photonFailed = true
        emptyList()
    }
    val base = photonResults.ifEmpty { geocoderSearch(context, query, near) }
    if (base.isEmpty() && photonFailed) {
        // Photon spadl (nejspíš není síť) a záloha (geokodér) nic nevrátila → offline.
        return@withContext SearchOutcome.Offline
    }
    // Doplnit vzdálenost od uživatele
    val withDistance = if (near == null) base else base.map { result ->
        val out = FloatArray(1)
        android.location.Location.distanceBetween(
            near.latitude, near.longitude,
            result.position.latitude, result.position.longitude, out,
        )
        result.copy(distanceMeters = out[0])
    }
    SearchOutcome.Ok(withDistance)
}

/** Kategorie místa podle OpenStreetMap značek (pro ikonku ve výsledku). */
private fun placeCategory(key: String, value: String): PlaceCategory = when {
    key == "shop" -> PlaceCategory.SHOP
    key == "amenity" && value in setOf(
        "restaurant", "cafe", "fast_food", "pub", "bar", "food_court", "ice_cream", "biergarten",
    ) -> PlaceCategory.FOOD
    key == "amenity" && value in setOf(
        "pharmacy", "hospital", "clinic", "doctors", "dentist", "veterinary",
    ) -> PlaceCategory.HEALTH
    key == "amenity" && value in setOf(
        "school", "university", "college", "kindergarten", "library",
    ) -> PlaceCategory.SCHOOL
    key == "railway" || key == "public_transport" -> PlaceCategory.TRANSIT
    key == "highway" && value == "bus_stop" -> PlaceCategory.TRANSIT
    key == "amenity" && value in setOf("bus_station", "ferry_terminal") -> PlaceCategory.TRANSIT
    key == "place" && value in setOf(
        "city", "town", "village", "suburb", "hamlet", "quarter", "neighbourhood",
    ) -> PlaceCategory.CITY
    else -> PlaceCategory.PLACE
}

/** Záloha: vestavěný geokodér Androidu (jen adresy, ale funguje offline služby). */
@Suppress("DEPRECATION")
private fun geocoderSearch(
    context: android.content.Context,
    query: String,
    near: android.location.Location?,
): List<GeoSearchResult> {
    return try {
        val geocoder = Geocoder(context, Locale("cs", "CZ"))
        val nearby: List<Address> = if (near != null) {
            val dLat = 0.135
            val dLng = 0.135 / cos(Math.toRadians(near.latitude)).coerceAtLeast(0.1)
            geocoder.getFromLocationName(
                query, 5,
                near.latitude - dLat, near.longitude - dLng,
                near.latitude + dLat, near.longitude + dLng,
            ) ?: emptyList()
        } else emptyList()

        val addresses = nearby.ifEmpty {
            geocoder.getFromLocationName(query, 5) ?: emptyList()
        }
        addresses.map { address ->
            GeoSearchResult(
                title = addressTitle(address),
                subtitle = address.getAddressLine(0) ?: "",
                position = LatLng(address.latitude, address.longitude),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/** Reverzní geokódování – název místa po ťuknutí do mapy. */
@Suppress("DEPRECATION")
private suspend fun reverseGeocode(
    context: android.content.Context,
    coord: LatLng,
): String? = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(context, Locale("cs", "CZ"))
        val address = geocoder.getFromLocation(coord.latitude, coord.longitude, 1)
            ?.firstOrNull() ?: return@withContext null
        addressTitle(address)
    } catch (_: Exception) {
        null
    }
}

/** Čitelný titulek adresy: „Sokolovská 55" / název místa / město. */
private fun addressTitle(address: Address): String {
    val street = listOfNotNull(address.thoroughfare, address.subThoroughfare)
        .joinToString(" ")
    val feature = address.featureName
    return when {
        !feature.isNullOrBlank() && feature != address.subThoroughfare &&
                !feature.matches(Regex("^[\\d/]+$")) -> feature
        street.isNotBlank() -> street
        !feature.isNullOrBlank() -> feature
        !address.locality.isNullOrBlank() -> address.locality
        else -> "Bez názvu"
    }
}
