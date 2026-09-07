package cz.jenda.georeminder.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.jenda.georeminder.MainActivity
import cz.jenda.georeminder.R
import cz.jenda.georeminder.data.ActivityInsets
import cz.jenda.georeminder.data.LocationHolder
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.data.SharedStorage
import cz.jenda.georeminder.data.SystemAccess
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import kotlinx.coroutines.launch

/** Kořen aplikace: onboarding, záložky, oprávnění a special access. */
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
    val reminders by store.reminders.collectAsStateWithLifecycle()
    val resumeScope = rememberCoroutineScope()

    var backgroundAccessMissing by remember { mutableStateOf(false) }
    var exactAlarmAccessMissing by remember { mutableStateOf(false) }
    var backgroundPromptDismissed by rememberSaveable { mutableStateOf(false) }
    var exactAlarmPromptDismissed by rememberSaveable { mutableStateOf(false) }
    var android10BackgroundRequestStarted by rememberSaveable { mutableStateOf(false) }

    fun refreshSpecialAccessState(items: List<Reminder>) {
        val hasActiveLocation = items.any { !it.isDone && it.kind == ReminderKind.LOCATION }
        val hasActiveTime = items.any { !it.isDone && it.kind == ReminderKind.TIME }

        backgroundAccessMissing = hasActiveLocation &&
            LocationHolder.hasFineLocation(context) &&
            !LocationHolder.hasBackgroundLocation(context)

        exactAlarmAccessMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            hasActiveTime &&
            !SystemAccess.canScheduleExactAlarms(context)
    }

    LaunchedEffect(reminders) {
        refreshSpecialAccessState(reminders)
    }

    val density = LocalDensity.current
    val navigationBottomPx = WindowInsets.navigationBars.getBottom(density)
    LaunchedEffect(navigationBottomPx) {
        if (navigationBottomPx > 0) {
            ActivityInsets.navigationBottomPx.value = navigationBottomPx
        }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshSpecialAccessState(store.reminders.value)
        store.resyncAll()
        LocationHolder.refresh(context)
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val anyLocationGranted = fineGranted ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (anyLocationGranted) {
            LocationHolder.refresh(context)
            store.resyncAll()
        }
        refreshSpecialAccessState(store.reminders.value)
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

    LaunchedEffect(backgroundAccessMissing, reminders) {
        if (
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
            backgroundAccessMissing &&
            !android10BackgroundRequestStarted
        ) {
            android10BackgroundRequestStarted = true
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeScope.launch {
                    val loadResult = store.reloadAndWait()
                    if (loadResult != ReminderStore.ReloadResult.ERROR) {
                        store.resyncAll()
                    }
                    LocationHolder.refresh(context)
                    refreshSpecialAccessState(store.reminders.value)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "auroraTransition")
    val auroraOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (colors.isGlass) {
                    Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF5468E8),
                                Color(0xFF7A5AE0),
                                Color(0xFFB95CC8),
                                Color(0xFFE58BA6),
                            ),
                            start = Offset(auroraOffset, 0f),
                            end = Offset(1000f - auroraOffset, 1500f)
                        )
                    )
                } else {
                    Modifier.background(colors.background)
                }
            )
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

            LaunchedEffect(Unit) {
                MainActivity.shortcutRequest.collect { kind ->
                    if (kind != null) selectedTab = 0
                }
            }
            LaunchedEffect(Unit) {
                MainActivity.sharedPlaceText.collect { text ->
                    if (text != null) selectedTab = 0
                }
            }
            LaunchedEffect(Unit) {
                MainActivity.notificationReminderRequest.collect { reminderId ->
                    if (reminderId != null) selectedTab = 0
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

    val shouldShowBackgroundDialog = hasSeenOnboarding &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        backgroundAccessMissing &&
        !backgroundPromptDismissed

    if (shouldShowBackgroundDialog) {
        val optionLabel = SystemAccess.backgroundLocationOptionLabel(context)
        AlertDialog(
            onDismissRequest = { backgroundPromptDismissed = true },
            title = { Text(stringResource(R.string.background_location_title)) },
            text = {
                Text(stringResource(R.string.background_location_message, optionLabel))
            },
            confirmButton = {
                TextButton(onClick = {
                    backgroundPromptDismissed = true
                    SystemAccess.openAppDetails(context)
                }) {
                    Text(stringResource(R.string.action_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { backgroundPromptDismissed = true }) {
                    Text(stringResource(R.string.action_not_now))
                }
            },
        )
    }

    if (
        hasSeenOnboarding && exactAlarmAccessMissing && !exactAlarmPromptDismissed &&
        !shouldShowBackgroundDialog
    ) {
        AlertDialog(
            onDismissRequest = { exactAlarmPromptDismissed = true },
            title = { Text(stringResource(R.string.exact_alarm_title)) },
            text = { Text(stringResource(R.string.exact_alarm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    exactAlarmPromptDismissed = true
                    SystemAccess.openExactAlarmSettings(context)
                }) {
                    Text(stringResource(R.string.exact_alarm_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { exactAlarmPromptDismissed = true }) {
                    Text(stringResource(R.string.action_later))
                }
            },
        )
    }
}

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
