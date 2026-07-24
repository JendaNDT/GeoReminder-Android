package cz.jenda.georeminder.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.jenda.georeminder.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import cz.jenda.georeminder.MainActivity
import cz.jenda.georeminder.data.ActivityInsets
import cz.jenda.georeminder.data.LocationHolder
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.data.SharedStorage
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import kotlinx.coroutines.launch

/**
 * Kořen aplikace: uvítací průvodce (jen poprvé), pak záložky Připomínky + Mapa
 * s plovoucím kapslovým tab barem. Oprávnění se žádají až PO zavření průvodce,
 * v pořadí: notifikace → poloha → poloha „Povolit vždy" (zvláštnost Androidu).
 */
@Composable
fun RootScreen() {
    val context = LocalContext.current
    val colors = GeoTheme.colors
    val prefs = remember {
        context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
    }
    var hasSeenOnboarding by remember {
        mutableStateOf(prefs.getBoolean("hasSeenOnboarding", false))
    }
    val store = remember { ReminderStore.get(context) }

    // Změřit výšku spodní systémové lišty v okně aktivity (spolehlivé)
    // a zpřístupnit ji dialogovým oknům, která ji samy nedostávají.
    val density = LocalDensity.current
    val navigationBottomPx = WindowInsets.navigationBars.getBottom(density)
    LaunchedEffect(navigationBottomPx) {
        if (navigationBottomPx > 0) {
            ActivityInsets.navigationBottomPx.value = navigationBottomPx
        }
    }

    // Řetězec žádostí o oprávnění (spouští se po průvodci)
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        store.resyncAll()
        LocationHolder.refresh(context)
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            LocationHolder.refresh(context)
            store.resyncAll()
            if (Build.VERSION.SDK_INT >= 29 &&
                !LocationHolder.hasBackgroundLocation(context)
            ) {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        locationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    fun startPermissionChain() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    // Návrat do popředí: znovu načíst data z disku a zaregistrovat spouštěče
    // (změny z tlačítek na notifikaci, widgetu…) – ekvivalent iOS scenePhase.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lifecycleOwner.lifecycleScope.launch {
                    store.reloadAndResync()
                }
                LocationHolder.refresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        if (!hasSeenOnboarding) {
            OnboardingScreen(
                onFinish = {
                    prefs.edit().putBoolean("hasSeenOnboarding", true).apply()
                    hasSeenOnboarding = true
                    startPermissionChain()
                }
            )
        } else {
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }

            // Zástupce z plochy má otevřít formulář → přepnout na záložku Připomínky,
            // kde si požadavek převezme ReminderListScreen
            LaunchedEffect(Unit) {
                MainActivity.shortcutRequest.collect { kind ->
                    if (kind != null) selectedTab = 0
                }
            }
            // Sdílené místo (z Map Google) → taky na záložku Připomínky
            LaunchedEffect(Unit) {
                MainActivity.sharedPlaceText.collect { text ->
                    if (text != null) selectedTab = 0
                }
            }

            when (selectedTab) {
                0 -> ReminderListScreen()
                else -> MapOverviewScreen()
            }

            FloatingTabBar(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp),
            )
        }
    }
}

/** Plovoucí kapslový tab bar dole na středu (vzhled iOS 26). */
@Composable
private fun FloatingTabBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GeoTheme.colors
    Surface(
        modifier = modifier.shadow(
            14.dp, CircleShape,
            spotColor = Color.Black.copy(alpha = 0.35f),
            ambientColor = Color.Black.copy(alpha = 0.25f),
        ),
        shape = CircleShape,
        color = colors.tabBarBackground,
    ) {
        Row(modifier = Modifier.padding(5.dp)) {
            TabBarItem(
                icon = Icons.Filled.Checklist,
                label = stringResource(R.string.tab_reminders),
                active = selectedTab == 0,
            ) { onSelect(0) }
            TabBarItem(
                icon = Icons.Filled.Map,
                label = stringResource(R.string.tab_map),
                active = selectedTab == 1,
            ) { onSelect(1) }
        }
    }
}

@Composable
private fun TabBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = GeoTheme.colors
    val tint = if (active) colors.accent else colors.secondaryLabel
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(56.dp)
            .clip(CircleShape)
            .background(if (active) colors.tabActiveBubble else Color.Transparent)
            .iosClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = GeoType.caption2,
                color = tint,
            )
        }
    }
}
