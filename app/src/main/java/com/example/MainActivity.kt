package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.MainTab
import com.example.ui.components.NeonStripHeader
import com.example.ui.screens.ColorStudioScreen
import com.example.ui.screens.MusicScreen
import com.example.ui.screens.ScenesScreen
import com.example.ui.screens.TimerScreen
import com.example.ui.sheets.ControllerSettingsSheet
import com.example.ui.sheets.DeviceManagerSheet
import com.example.ui.theme.NeonFlowTheme
import com.example.viewmodel.NeonViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            NeonFlowTheme {
                NeonFlowRoot()
            }
        }
    }
}

private data class NavDestination(
    val tab: MainTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navDestinations = listOf(
    NavDestination(MainTab.COLOR, R.string.nav_color, Icons.Rounded.Palette, Icons.Outlined.Palette),
    NavDestination(MainTab.SCENES, R.string.nav_scenes, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
    NavDestination(MainTab.MUSIC, R.string.nav_music, Icons.Rounded.GraphicEq, Icons.Outlined.GraphicEq),
    NavDestination(MainTab.TIMER, R.string.nav_timer, Icons.Rounded.Schedule, Icons.Outlined.Schedule)
)

@Composable
fun NeonFlowRoot(
    viewModel: NeonViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDevicesSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = state.selectedTab != MainTab.COLOR) {
        viewModel.selectTab(MainTab.COLOR)
    }

    LaunchedEffect(state.statusBannerMessage) {
        val msg = state.statusBannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBannerMessage()
        }
    }

    val ambientColor = if (state.isPowerOn) {
        state.livePrimaryColor.toComposeColor().copy(alpha = 0.24f)
    } else {
        Color.Transparent
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                if (ambientColor.alpha > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ambientColor, Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.08f),
                            radius = size.width * 0.95f
                        ),
                        radius = size.width * 0.95f,
                        center = Offset(size.width * 0.5f, size.height * 0.08f)
                    )
                }
            }
    ) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        tonalElevation = 0.dp
                    ) {
                        navDestinations.forEach { dest ->
                            val selected = state.selectedTab == dest.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_tab_${dest.tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navDestinations.forEach { dest ->
                            val selected = state.selectedTab == dest.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("nav_rail_${dest.tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .widthIn(max = 640.dp)
                    ) {
                        NeonStripHeader(
                            state = state,
                            onPowerToggle = viewModel::togglePower,
                            onOpenDevices = {
                                viewModel.refreshConnectivityFlags()
                                showDevicesSheet = true
                            },
                            onOpenSettings = { showSettingsSheet = true }
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            when (state.selectedTab) {
                                MainTab.COLOR -> ColorStudioScreen(
                                    state = state,
                                    onSubModeSelected = viewModel::selectColorSubMode,
                                    onColorSelected = viewModel::setStaticColor,
                                    onKelvinSelected = viewModel::setCctKelvin,
                                    onBrightnessChange = viewModel::setBrightness,
                                    onSmoothnessChange = viewModel::setSmoothness,
                                    onSaveFavorite = viewModel::saveFavoriteColor
                                )

                                MainTab.SCENES -> ScenesScreen(
                                    state = state,
                                    onCategorySelected = viewModel::selectSceneCategory,
                                    onSceneSelected = viewModel::activateScene,
                                    onSpeedChange = viewModel::setSpeed,
                                    onSmoothnessChange = viewModel::setSmoothness,
                                    onBrightnessChange = viewModel::setBrightness,
                                    onZoneMetersChange = viewModel::setZoneMeters,
                                    onFlowDirectionChange = viewModel::setFlowDirection,
                                    onCreateCustomScene = viewModel::createCustomScene,
                                    onDeleteCustomScene = viewModel::deleteCustomScene
                                )

                                MainTab.MUSIC -> MusicScreen(
                                    state = state,
                                    hasRecordPermission = viewModel::hasRecordAudioPermission,
                                    onSelectMusicTab = viewModel::selectMusicTab,
                                    onSelectMicSource = viewModel::setMicSource,
                                    onToggleMic = viewModel::toggleMicrophoneReactive,
                                    onSelectReactiveMode = viewModel::setMusicReactiveMode,
                                    onSelectMusicPalette = viewModel::setMusicPalette,
                                    onSensitivityChange = viewModel::setMicSensitivity,
                                    onDecaySmoothnessChange = viewModel::setMicDecaySmoothness,
                                    onBassBoostChange = viewModel::setBassBoost,
                                    onAddTracks = viewModel::addAudioTracks,
                                    onPlayTrackAt = viewModel::playTrackAt,
                                    onTogglePlayPause = viewModel::toggleTrackPlayPause,
                                    onNextTrack = viewModel::nextTrack,
                                    onPrevTrack = viewModel::previousTrack,
                                    onSeekTrack = viewModel::seekTrack,
                                    onRemoveTrack = viewModel::removeTrack
                                )

                                MainTab.TIMER -> TimerScreen(
                                    state = state,
                                    onStartSleepTimer = viewModel::startSleepTimer,
                                    onCancelSleepTimer = viewModel::cancelSleepTimer,
                                    onToggleSleepFadeOut = viewModel::setSleepTimerFadeOut,
                                    onAddSchedule = viewModel::addSchedule,
                                    onToggleSchedule = viewModel::toggleSchedule,
                                    onDeleteSchedule = viewModel::deleteSchedule
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDevicesSheet) {
        DeviceManagerSheet(
            state = state,
            hasBlePermissions = viewModel::hasBlePermissions,
            onRefreshConnectivity = viewModel::refreshConnectivityFlags,
            onDismiss = {
                viewModel.stopBleScan()
                showDevicesSheet = false
            },
            onScanWifi = viewModel::scanWifiDevices,
            onScanBle = viewModel::startBleScan,
            onStopBleScan = viewModel::stopBleScan,
            onAddManualWifi = viewModel::addManualWifiDevice,
            onConnectDevice = viewModel::saveAndConnectDevice,
            onRemoveSavedDevice = viewModel::removeSavedDevice,
            onDisconnect = viewModel::disconnectDevice
        )
    }

    if (showSettingsSheet) {
        ControllerSettingsSheet(
            state = state,
            onDismiss = { showSettingsSheet = false },
            onSelectPinOrder = viewModel::setPinOrder,
            onTestPinColor = viewModel::testPinColor,
            onToggleSoftPowerFade = viewModel::setSoftPowerFade,
            onSelectFpsLimit = viewModel::setFpsLimit
        )
    }
}
