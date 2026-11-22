package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.LocalMusicPlayerEngine
import com.example.audio.MicrophoneAudioEngine
import com.example.data.NeonPreferencesRepository
import com.example.data.StoredAppSettings
import com.example.engine.PresetScenes
import com.example.engine.SmoothEffectEngine
import com.example.hardware.BleLedTransport
import com.example.hardware.WifiLedTransport
import com.example.model.ActiveControllerMode
import com.example.model.AudioFrame
import com.example.model.AudioTrackItem
import com.example.model.ColorControlSubMode
import com.example.model.ConnectionState
import com.example.model.ControllerProtocol
import com.example.model.FlowDirection
import com.example.model.LedDevice
import com.example.model.LightScene
import com.example.model.MainTab
import com.example.model.MicSource
import com.example.model.MusicReactiveMode
import com.example.model.PinOrder
import com.example.model.RgbColor
import com.example.model.SceneCategory
import com.example.model.ScheduleAction
import com.example.model.ScheduleRule
import com.example.model.TransitionCurve
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

data class MusicPalettePreset(
    val id: String,
    val name: String,
    val colors: List<RgbColor>
)

object MusicPalettes {
    val all = listOf(
        MusicPalettePreset("crimson", "Алый неон", listOf(RgbColor(255, 16, 44), RgbColor(190, 0, 56), RgbColor(255, 102, 24))),
        MusicPalettePreset("cyber", "Кибер", listOf(RgbColor(0, 229, 255), RgbColor(139, 92, 246), RgbColor(255, 46, 147))),
        MusicPalettePreset("fire", "Огонь", listOf(RgbColor(255, 36, 0), RgbColor(255, 138, 0), RgbColor(255, 214, 10))),
        MusicPalettePreset("aurora", "Аврора", listOf(RgbColor(0, 245, 160), RgbColor(0, 217, 245), RgbColor(124, 77, 255))),
        MusicPalettePreset("club", "Клуб", listOf(RgbColor(255, 0, 128), RgbColor(0, 255, 200), RgbColor(255, 220, 0), RgbColor(140, 0, 255))),
        MusicPalettePreset("warm", "Лаунж", listOf(RgbColor(255, 140, 66), RgbColor(255, 89, 131), RgbColor(177, 114, 255)))
    )
}

data class NeonUiState(
    val selectedTab: MainTab = MainTab.COLOR,
    val isPowerOn: Boolean = true,
    val activeMode: ActiveControllerMode = ActiveControllerMode.SCENE,
    val colorSubMode: ColorControlSubMode = ColorControlSubMode.WHEEL,

    // Target static color & live 36-segment rendered output
    val selectedColor: RgbColor = RgbColor.Crimson,
    val cctKelvin: Int = 3800,
    val brightness: Float = 0.88f,
    val smoothness: Float = 0.82f,
    val speed: Float = 0.48f,
    val zoneMeters: Float = 1.0f,
    val flowDirection: FlowDirection = FlowDirection.FORWARD,
    val livePrimaryColor: RgbColor = RgbColor.Crimson,
    val liveStripSegments: List<RgbColor> = List(36) { RgbColor.Crimson },

    // Presets & Favorites
    val favoriteColors: List<RgbColor> = StoredAppSettings.defaultFavoriteColors,
    val selectedSceneCategory: SceneCategory = SceneCategory.ALL,
    val customScenes: List<LightScene> = emptyList(),
    val activeSceneId: String? = "red_scarlet_velvet",

    // Music & Mic
    val musicTabIndex: Int = 0,
    val micSource: MicSource = MicSource.PHONE,
    val musicReactiveMode: MusicReactiveMode = MusicReactiveMode.BASS_PULSE,
    val selectedMusicPaletteId: String = MusicPalettes.all.first().id,
    val micSensitivity: Float = 0.65f,
    val micDecaySmoothness: Float = 0.68f,
    val bassBoost: Float = 0.60f,
    val isMicActive: Boolean = false,
    val audioFrame: AudioFrame = AudioFrame(),
    val micError: String? = null,
    val playlist: List<AudioTrackItem> = emptyList(),
    val currentTrackIndex: Int = -1,
    val isTrackPlaying: Boolean = false,
    val trackPositionMs: Long = 0L,
    val trackDurationMs: Long = 1L,

    // Timer & Schedules
    val sleepTimerTotalSec: Int = 0,
    val sleepTimerRemainingSec: Int = 0,
    val sleepTimerFadeOut: Boolean = true,
    val schedules: List<ScheduleRule> = emptyList(),

    // Devices & Settings
    val isWifiAvailable: Boolean = true,
    val isBluetoothEnabled: Boolean = true,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val savedDevices: List<LedDevice> = emptyList(),
    val discoveredWifiDevices: List<LedDevice> = emptyList(),
    val discoveredBleDevices: List<LedDevice> = emptyList(),
    val isWifiScanning: Boolean = false,
    val isBleScanning: Boolean = false,
    val pinOrder: PinOrder = PinOrder.RGB,
    val softPowerFade: Boolean = true,
    val fpsLimit: Int = 25,
    val statusBannerMessage: String? = null
) {
    val allScenes: List<LightScene>
        get() = customScenes + PresetScenes.builtIn

    val activeScene: LightScene?
        get() = activeSceneId?.let { id -> allScenes.firstOrNull { it.id == id } }

    val selectedMusicPalette: MusicPalettePreset
        get() = MusicPalettes.all.firstOrNull { it.id == selectedMusicPaletteId }
            ?: MusicPalettes.all.first()
}

class NeonViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = NeonPreferencesRepository(application)
    private val wifiTransport = WifiLedTransport(application)
    private val bleTransport = BleLedTransport(application)
    private val effectEngine = SmoothEffectEngine()
    private val micEngine = MicrophoneAudioEngine(application)
    private val musicPlayer = LocalMusicPlayerEngine(application)

    private val _uiState = MutableStateFlow(NeonUiState())
    val uiState: StateFlow<NeonUiState> = _uiState.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var powerToggleJob: Job? = null
    private var persistSliderJob: Job? = null
    private var bleScanTimeoutJob: Job? = null

    private var lastSentColor: RgbColor? = null
    private var lastHardwareSendMs: Long = 0L
    private var lastTriggeredScheduleMinute: Int = -1

    init {
        loadInitialPreferences()
        observeAudioAndBle()
        refreshConnectivityFlags()
        startRenderLoop()
        startScheduleClock()
    }

    /**
     * Load persisted settings once on startup to prevent DataStore emissions from causing
     * slider jump/jitter during active user dragging.
     */
    private fun loadInitialPreferences() {
        viewModelScope.launch {
            val stored = prefsRepo.settingsFlow.first()
            _uiState.update { state ->
                state.copy(
                    pinOrder = stored.pinOrder,
                    softPowerFade = stored.softPowerFade,
                    fpsLimit = stored.fpsLimit,
                    brightness = stored.brightness,
                    smoothness = stored.smoothness,
                    speed = stored.speed,
                    zoneMeters = stored.zoneMeters,
                    flowDirection = stored.flowDirection,
                    favoriteColors = stored.favoriteColors,
                    savedDevices = stored.savedDevices,
                    customScenes = stored.customScenes,
                    schedules = stored.schedules
                )
            }
        }
    }

    fun refreshConnectivityFlags() {
        _uiState.update {
            it.copy(
                isWifiAvailable = wifiTransport.isWifiConnected(),
                isBluetoothEnabled = bleTransport.isBluetoothEnabled()
            )
        }
    }

    private fun observeAudioAndBle() {
        viewModelScope.launch {
            bleTransport.scannedDevices.collect { devices ->
                _uiState.update { it.copy(discoveredBleDevices = devices) }
            }
        }
        viewModelScope.launch {
            bleTransport.isScanning.collect { scanning ->
                _uiState.update { it.copy(isBleScanning = scanning) }
            }
        }
        viewModelScope.launch {
            bleTransport.disconnectEvents.collect { reason ->
                val currentConn = _uiState.value.connectionState as? ConnectionState.Connected
                if (currentConn != null && !currentConn.device.protocol.isWifi) {
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.Error(reason, currentConn.device),
                            statusBannerMessage = reason
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            micEngine.isCapturing.collect { active ->
                _uiState.update { it.copy(isMicActive = active) }
            }
        }
        viewModelScope.launch {
            micEngine.error.collect { err ->
                _uiState.update { it.copy(micError = err) }
            }
        }
        viewModelScope.launch {
            micEngine.audioFrame.collect { frame ->
                if (_uiState.value.activeMode == ActiveControllerMode.MUSIC_MIC) {
                    _uiState.update { it.copy(audioFrame = frame) }
                }
            }
        }
        viewModelScope.launch {
            musicPlayer.playlist.collect { list ->
                _uiState.update { it.copy(playlist = list) }
            }
        }
        viewModelScope.launch {
            musicPlayer.currentIndex.collect { idx ->
                _uiState.update { it.copy(currentTrackIndex = idx) }
            }
        }
        viewModelScope.launch {
            musicPlayer.isPlaying.collect { playing ->
                _uiState.update { it.copy(isTrackPlaying = playing) }
            }
        }
        viewModelScope.launch {
            musicPlayer.positionMs.collect { pos ->
                _uiState.update { it.copy(trackPositionMs = pos) }
            }
        }
        viewModelScope.launch {
            musicPlayer.durationMs.collect { dur ->
                _uiState.update { it.copy(trackDurationMs = dur) }
            }
        }
        viewModelScope.launch {
            musicPlayer.audioFrame.collect { frame ->
                if (_uiState.value.activeMode == ActiveControllerMode.MUSIC_PLAYER) {
                    _uiState.update { it.copy(audioFrame = frame) }
                }
            }
        }
    }

    private fun startRenderLoop() {
        viewModelScope.launch {
            var lastFrameNs = System.nanoTime()
            while (isActive) {
                val nowNs = System.nanoTime()
                val dtSec = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.008f, 0.08f)
                lastFrameNs = nowNs

                musicPlayer.pollPlaybackProgress()

                val state = _uiState.value
                val powerMultiplier = if (state.isPowerOn) computeSleepFadeMultiplier(state) else 0f
                val effectiveBrightness = (state.brightness * powerMultiplier).coerceIn(0f, 1f)

                val (primary, segments) = when {
                    !state.isPowerOn && !state.softPowerFade -> {
                        RgbColor.Black to List(36) { RgbColor.Black }
                    }
                    state.activeMode == ActiveControllerMode.SCENE && state.isPowerOn -> {
                        val scene = state.activeScene ?: PresetScenes.builtIn.first()
                        effectEngine.evaluateSceneFrame(
                            scene = scene,
                            dtSec = dtSec,
                            speed = state.speed,
                            smoothness = state.smoothness,
                            brightness = effectiveBrightness,
                            zoneMeters = state.zoneMeters,
                            direction = state.flowDirection,
                            segmentCount = 36
                        )
                    }
                    (state.activeMode == ActiveControllerMode.MUSIC_MIC && state.isMicActive &&
                        state.micSource == MicSource.PHONE && state.isPowerOn) ||
                        (state.activeMode == ActiveControllerMode.MUSIC_PLAYER && state.isTrackPlaying && state.isPowerOn) -> {
                        effectEngine.evaluateMusicFrame(
                            audio = state.audioFrame,
                            mode = state.musicReactiveMode,
                            palette = state.selectedMusicPalette.colors,
                            dtSec = dtSec,
                            sensitivity = state.micSensitivity,
                            decaySmoothness = state.micDecaySmoothness,
                            bassBoost = state.bassBoost,
                            masterBrightness = effectiveBrightness,
                            segmentCount = 36
                        )
                    }
                    else -> {
                        val targetColor = if (state.activeMode == ActiveControllerMode.CCT_WHITE) {
                            RgbColor.fromKelvin(state.cctKelvin)
                        } else {
                            state.selectedColor
                        }
                        effectEngine.evaluateStaticSmoothFrame(
                            targetColor = targetColor,
                            targetPowerMultiplier = powerMultiplier,
                            dtSec = dtSec,
                            smoothness = if (state.softPowerFade) state.smoothness else 0f,
                            brightness = state.brightness,
                            segmentCount = 36
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        livePrimaryColor = primary,
                        liveStripSegments = segments
                    )
                }

                maybeSendFrameToHardware(state, primary, segments)
                delay(28L)
            }
        }
    }

    private fun computeSleepFadeMultiplier(state: NeonUiState): Float {
        if (!state.sleepTimerFadeOut || state.sleepTimerRemainingSec <= 0 || state.sleepTimerTotalSec <= 0) {
            return 1f
        }
        val fadeWindow = minOf(180, state.sleepTimerTotalSec)
        return if (state.sleepTimerRemainingSec <= fadeWindow) {
            (state.sleepTimerRemainingSec.toFloat() / fadeWindow.toFloat()).coerceIn(0.05f, 1f)
        } else 1f
    }

    private suspend fun maybeSendFrameToHardware(
        state: NeonUiState,
        primary: RgbColor,
        segments: List<RgbColor>
    ) {
        val connected = state.connectionState as? ConnectionState.Connected ?: return
        val now = System.currentTimeMillis()
        // BLE GATT is happiest up to ~18 FPS so packets never queue; Wi-Fi supports up to 30 FPS
        val effectiveFps = if (connected.device.protocol.isWifi) {
            state.fpsLimit.coerceIn(10, 30)
        } else {
            state.fpsLimit.coerceIn(10, 18)
        }
        val minIntervalMs = 1000L / effectiveFps
        if (now - lastHardwareSendMs < minIntervalMs) return

        val isAnimated = state.activeMode == ActiveControllerMode.SCENE ||
            state.activeMode == ActiveControllerMode.MUSIC_MIC ||
            state.activeMode == ActiveControllerMode.MUSIC_PLAYER

        val prev = lastSentColor
        val changedSignificantly = prev == null ||
            kotlin.math.abs(prev.r - primary.r) >= 1 ||
            kotlin.math.abs(prev.g - primary.g) >= 1 ||
            kotlin.math.abs(prev.b - primary.b) >= 1 ||
            kotlin.math.abs(prev.w - primary.w) >= 2

        if (!changedSignificantly && !isAnimated) return

        lastHardwareSendMs = now
        lastSentColor = primary

        val device = connected.device
        if (device.protocol.isWifi) {
            wifiTransport.sendColorFrame(
                color = primary,
                segments = segments,
                pinOrder = state.pinOrder,
                brightnessPercent = (state.brightness * 100f).toInt().coerceIn(1, 100),
                smoothness = state.smoothness,
                isStreamingAnimation = isAnimated
            )
        } else {
            bleTransport.sendColor(primary, state.pinOrder)
        }
    }

    // -------------------------------------------------------------------------
    // Navigation & Power
    // -------------------------------------------------------------------------

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun togglePower() {
        val nextOn = !_uiState.value.isPowerOn
        _uiState.update { it.copy(isPowerOn = nextOn) }
        powerToggleJob?.cancel()
        val connected = _uiState.value.connectionState as? ConnectionState.Connected
        if (connected != null) {
            powerToggleJob = viewModelScope.launch {
                if (!nextOn && _uiState.value.softPowerFade) {
                    delay(420L)
                }
                if (_uiState.value.isPowerOn != nextOn) return@launch
                if (connected.device.protocol.isWifi) {
                    wifiTransport.sendPower(nextOn)
                } else {
                    bleTransport.sendPower(nextOn)
                }
            }
        }
    }

    fun clearBannerMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    // -------------------------------------------------------------------------
    // Color & CCT Studio
    // -------------------------------------------------------------------------

    fun selectColorSubMode(subMode: ColorControlSubMode) {
        _uiState.update {
            it.copy(
                colorSubMode = subMode,
                activeMode = if (subMode == ColorControlSubMode.CCT) {
                    ActiveControllerMode.CCT_WHITE
                } else {
                    ActiveControllerMode.STATIC_COLOR
                },
                activeSceneId = null,
                isPowerOn = true
            )
        }
        stopActiveAudioIfNeeded()
    }

    fun setStaticColor(color: RgbColor) {
        stopActiveAudioIfNeeded()
        _uiState.update {
            it.copy(
                selectedColor = color,
                activeMode = ActiveControllerMode.STATIC_COLOR,
                activeSceneId = null,
                isPowerOn = true
            )
        }
    }

    fun setCctKelvin(kelvin: Int) {
        stopActiveAudioIfNeeded()
        val clamped = kelvin.coerceIn(2000, 7500)
        _uiState.update {
            it.copy(
                cctKelvin = clamped,
                selectedColor = RgbColor.fromKelvin(clamped),
                activeMode = ActiveControllerMode.CCT_WHITE,
                activeSceneId = null,
                isPowerOn = true
            )
        }
    }

    fun setBrightness(value: Float) {
        val clamped = value.coerceIn(0.05f, 1f)
        _uiState.update { it.copy(brightness = clamped) }
        schedulePersistSliders()
    }

    fun setSmoothness(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _uiState.update { it.copy(smoothness = clamped) }
        schedulePersistSliders()
    }

    fun setSpeed(value: Float) {
        val clamped = value.coerceIn(0.05f, 1f)
        _uiState.update { it.copy(speed = clamped) }
        schedulePersistSliders()
    }

    fun setZoneMeters(meters: Float) {
        val clamped = meters.coerceIn(0.35f, 2.5f)
        _uiState.update { it.copy(zoneMeters = clamped) }
        schedulePersistSliders()
    }

    fun setFlowDirection(direction: FlowDirection) {
        _uiState.update { it.copy(flowDirection = direction) }
        schedulePersistSliders()
    }

    fun saveFavoriteColor(slotIndex: Int) {
        val current = _uiState.value.selectedColor
        val updated = _uiState.value.favoriteColors.toMutableList()
        if (slotIndex in updated.indices) {
            updated[slotIndex] = current
            _uiState.update {
                it.copy(
                    favoriteColors = updated,
                    statusBannerMessage = "Цвет сохранён в избранное #${slotIndex + 1}"
                )
            }
            viewModelScope.launch { prefsRepo.saveFavorites(updated) }
        }
    }

    private fun schedulePersistSliders() {
        persistSliderJob?.cancel()
        persistSliderJob = viewModelScope.launch {
            delay(250L)
            val s = _uiState.value
            prefsRepo.saveSliders(s.brightness, s.smoothness, s.speed, s.zoneMeters, s.flowDirection)
        }
    }

    // -------------------------------------------------------------------------
    // Smooth Scenes & Custom Scene Builder
    // -------------------------------------------------------------------------

    fun selectSceneCategory(category: SceneCategory) {
        _uiState.update { it.copy(selectedSceneCategory = category) }
    }

    fun activateScene(scene: LightScene) {
        stopActiveAudioIfNeeded()
        _uiState.update {
            it.copy(
                activeSceneId = scene.id,
                activeMode = ActiveControllerMode.SCENE,
                speed = scene.defaultSpeed,
                smoothness = scene.defaultSmoothness,
                zoneMeters = scene.defaultZoneMeters,
                isPowerOn = true
            )
        }
        schedulePersistSliders()
    }

    fun createCustomScene(
        name: String,
        colors: List<RgbColor>,
        curve: TransitionCurve,
        speed: Float,
        smoothness: Float,
        zoneMeters: Float
    ) {
        val trimmed = name.trim().ifEmpty { "Моя сцена" }
        val safeColors = colors.take(5).ifEmpty { listOf(RgbColor.Crimson, RgbColor.Cyan) }
        val newScene = LightScene(
            id = "custom_${UUID.randomUUID()}",
            name = trimmed,
            subtitle = curve.displayName,
            category = SceneCategory.CUSTOM,
            colors = safeColors,
            curve = curve,
            defaultSpeed = speed,
            defaultSmoothness = smoothness,
            defaultZoneMeters = zoneMeters,
            isCustom = true
        )
        val updated = listOf(newScene) + _uiState.value.customScenes
        _uiState.update {
            it.copy(
                customScenes = updated,
                activeSceneId = newScene.id,
                activeMode = ActiveControllerMode.SCENE,
                isPowerOn = true,
                statusBannerMessage = "Сцена «$trimmed» создана и запущена"
            )
        }
        viewModelScope.launch { prefsRepo.saveCustomScenes(updated) }
    }

    fun deleteCustomScene(sceneId: String) {
        val updated = _uiState.value.customScenes.filterNot { it.id == sceneId }
        _uiState.update { state ->
            state.copy(
                customScenes = updated,
                activeSceneId = if (state.activeSceneId == sceneId) null else state.activeSceneId,
                activeMode = if (state.activeSceneId == sceneId) ActiveControllerMode.STATIC_COLOR else state.activeMode
            )
        }
        viewModelScope.launch { prefsRepo.saveCustomScenes(updated) }
    }

    // -------------------------------------------------------------------------
    // Music & Microphone Reactive Mode
    // -------------------------------------------------------------------------

    fun selectMusicTab(index: Int) {
        _uiState.update { it.copy(musicTabIndex = index) }
    }

    fun setMicSource(source: MicSource) {
        _uiState.update { it.copy(micSource = source) }
        if (source == MicSource.EXTERNAL_STRIP) {
            micEngine.stopCapture()
            val connected = _uiState.value.connectionState as? ConnectionState.Connected
            if (connected != null) {
                val sens = (_uiState.value.micSensitivity * 100f).toInt()
                viewModelScope.launch {
                    val sent = if (connected.device.protocol.isWifi) {
                        wifiTransport.sendExternalMic(true, sens)
                    } else {
                        bleTransport.sendExternalMic(true, sens)
                    }
                    _uiState.update {
                        it.copy(
                            activeMode = ActiveControllerMode.MUSIC_MIC,
                            isPowerOn = true,
                            statusBannerMessage = if (sent) {
                                "Включён встроенный микрофон контроллера"
                            } else {
                                "Этот протокол использует микрофон телефона"
                            }
                        )
                    }
                }
            }
        }
    }

    fun toggleMicrophoneReactive() {
        if (_uiState.value.isMicActive) {
            micEngine.stopCapture()
        } else {
            musicPlayer.stopAndRelease()
            _uiState.update {
                it.copy(
                    activeMode = ActiveControllerMode.MUSIC_MIC,
                    micSource = MicSource.PHONE,
                    activeSceneId = null,
                    isPowerOn = true
                )
            }
            micEngine.startCapture(viewModelScope)
        }
    }

    fun hasRecordAudioPermission(): Boolean = micEngine.hasRecordPermission()

    fun setMusicReactiveMode(mode: MusicReactiveMode) {
        _uiState.update { it.copy(musicReactiveMode = mode) }
    }

    fun setMusicPalette(paletteId: String) {
        _uiState.update { it.copy(selectedMusicPaletteId = paletteId) }
    }

    fun setMicSensitivity(value: Float) {
        _uiState.update { it.copy(micSensitivity = value.coerceIn(0.1f, 1f)) }
    }

    fun setMicDecaySmoothness(value: Float) {
        _uiState.update { it.copy(micDecaySmoothness = value.coerceIn(0.05f, 0.98f)) }
    }

    fun setBassBoost(value: Float) {
        _uiState.update { it.copy(bassBoost = value.coerceIn(0f, 1f)) }
    }

    fun addAudioTracks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        musicPlayer.addTrackUris(uris)
        _uiState.update { it.copy(statusBannerMessage = "Добавлено треков: ${uris.size}") }
    }

    fun playTrackAt(index: Int) {
        micEngine.stopCapture()
        _uiState.update {
            it.copy(
                activeMode = ActiveControllerMode.MUSIC_PLAYER,
                activeSceneId = null,
                isPowerOn = true
            )
        }
        musicPlayer.playTrackAt(index)
    }

    fun toggleTrackPlayPause() {
        micEngine.stopCapture()
        _uiState.update {
            it.copy(
                activeMode = ActiveControllerMode.MUSIC_PLAYER,
                activeSceneId = null,
                isPowerOn = true
            )
        }
        musicPlayer.togglePlayPause()
    }

    fun nextTrack() = musicPlayer.playNext()
    fun previousTrack() = musicPlayer.playPrevious()
    fun seekTrack(fraction: Float) = musicPlayer.seekTo(fraction)
    fun removeTrack(index: Int) = musicPlayer.removeTrack(index)

    private fun stopActiveAudioIfNeeded() {
        if (_uiState.value.isMicActive) micEngine.stopCapture()
    }

    // -------------------------------------------------------------------------
    // Sleep Timer & Schedules
    // -------------------------------------------------------------------------

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val totalSec = minutes * 60
        _uiState.update {
            it.copy(
                sleepTimerTotalSec = totalSec,
                sleepTimerRemainingSec = totalSec,
                isPowerOn = true,
                statusBannerMessage = "Таймер сна запущен на $minutes мин"
            )
        }
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSec
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _uiState.update { it.copy(sleepTimerRemainingSec = remaining) }
            }
            if (remaining == 0) {
                _uiState.update {
                    it.copy(
                        sleepTimerTotalSec = 0,
                        sleepTimerRemainingSec = 0,
                        isPowerOn = false,
                        statusBannerMessage = "Таймер завершён — лента выключена"
                    )
                }
                val connected = _uiState.value.connectionState as? ConnectionState.Connected
                if (connected != null) {
                    if (connected.device.protocol.isWifi) {
                        wifiTransport.sendPower(false)
                    } else {
                        bleTransport.sendPower(false)
                    }
                }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.update {
            it.copy(
                sleepTimerTotalSec = 0,
                sleepTimerRemainingSec = 0,
                statusBannerMessage = "Таймер сна отменён"
            )
        }
    }

    fun setSleepTimerFadeOut(enabled: Boolean) {
        _uiState.update { it.copy(sleepTimerFadeOut = enabled) }
    }

    fun addSchedule(rule: ScheduleRule) {
        val updated = (_uiState.value.schedules + rule).sortedBy { it.hour * 60 + it.minute }
        _uiState.update { it.copy(schedules = updated) }
        viewModelScope.launch { prefsRepo.saveSchedules(updated) }
    }

    fun toggleSchedule(ruleId: String) {
        val updated = _uiState.value.schedules.map {
            if (it.id == ruleId) it.copy(enabled = !it.enabled) else it
        }
        _uiState.update { it.copy(schedules = updated) }
        viewModelScope.launch { prefsRepo.saveSchedules(updated) }
    }

    fun deleteSchedule(ruleId: String) {
        val updated = _uiState.value.schedules.filterNot { it.id == ruleId }
        _uiState.update { it.copy(schedules = updated) }
        viewModelScope.launch { prefsRepo.saveSchedules(updated) }
    }

    private fun startScheduleClock() {
        viewModelScope.launch {
            while (isActive) {
                val cal = Calendar.getInstance()
                val currentMinuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                if (currentMinuteOfDay != lastTriggeredScheduleMinute) {
                    lastTriggeredScheduleMinute = currentMinuteOfDay
                    val isoDay = when (cal.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> 1
                        Calendar.TUESDAY -> 2
                        Calendar.WEDNESDAY -> 3
                        Calendar.THURSDAY -> 4
                        Calendar.FRIDAY -> 5
                        Calendar.SATURDAY -> 6
                        else -> 7
                    }
                    _uiState.value.schedules
                        .filter { it.enabled && it.hour * 60 + it.minute == currentMinuteOfDay && isoDay in it.daysOfWeek }
                        .forEach { applyScheduleRule(it) }
                }
                delay(15_000L)
            }
        }
    }

    private fun applyScheduleRule(rule: ScheduleRule) {
        when (rule.action) {
            ScheduleAction.TURN_ON -> {
                _uiState.update {
                    it.copy(
                        selectedColor = RgbColor.fromHex(rule.colorHex),
                        brightness = rule.brightness,
                        activeMode = ActiveControllerMode.STATIC_COLOR,
                        isPowerOn = true,
                        statusBannerMessage = "Сработало расписание: ${rule.label}"
                    )
                }
            }
            ScheduleAction.SUNRISE_FADE -> {
                val sunrise = PresetScenes.builtIn.firstOrNull { it.id == "sunrise_wake" }
                if (sunrise != null) activateScene(sunrise)
                _uiState.update { it.copy(statusBannerMessage = "Запущен рассвет: ${rule.label}") }
            }
            ScheduleAction.TURN_OFF -> {
                if (_uiState.value.isPowerOn) togglePower()
                _uiState.update { it.copy(statusBannerMessage = "Выключение по расписанию: ${rule.label}") }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Wi-Fi & BLE Device Discovery, Connection & Controller Settings
    // -------------------------------------------------------------------------

    fun scanWifiDevices() {
        refreshConnectivityFlags()
        if (_uiState.value.isWifiScanning) return
        _uiState.update { it.copy(isWifiScanning = true) }
        viewModelScope.launch {
            val discovered = wifiTransport.discoverLanControllers()
            _uiState.update {
                it.copy(
                    discoveredWifiDevices = discovered,
                    isWifiScanning = false,
                    statusBannerMessage = if (discovered.isEmpty()) {
                        if (!it.isWifiAvailable) {
                            "Wi-Fi отключён — переключитесь на вкладку Bluetooth LE для прямого управления"
                        } else {
                            "Контроллеры в Wi-Fi не ответили — добавьте IP вручную или подключитесь через Bluetooth"
                        }
                    } else {
                        "Найдено Wi-Fi контроллеров: ${discovered.size}"
                    }
                )
            }
        }
    }

    fun hasBlePermissions(): Boolean = bleTransport.hasBlePermissions()

    fun startBleScan() {
        refreshConnectivityFlags()
        val result = bleTransport.startScan()
        result.onFailure { err ->
            _uiState.update { it.copy(statusBannerMessage = err.message ?: "Ошибка BLE сканирования") }
            return
        }
        bleScanTimeoutJob?.cancel()
        bleScanTimeoutJob = viewModelScope.launch {
            delay(12_000L)
            bleTransport.stopScan()
        }
    }

    fun stopBleScan() {
        bleScanTimeoutJob?.cancel()
        bleTransport.stopScan()
    }

    fun addManualWifiDevice(name: String, ip: String, port: Int, protocol: ControllerProtocol) {
        val cleanIp = ip.trim()
        if (cleanIp.isBlank()) return
        val device = LedDevice(
            id = "wifi_${cleanIp}_$port",
            name = name.trim().ifEmpty { "Неоновая лента ($cleanIp)" },
            address = cleanIp,
            port = port.takeIf { it in 1..65535 } ?: protocol.defaultPort,
            protocol = protocol,
            isSaved = true
        )
        saveAndConnectDevice(device)
    }

    fun saveAndConnectDevice(device: LedDevice) {
        val savedDevice = device.copy(isSaved = true)
        val merged = (_uiState.value.savedDevices.filterNot { it.id == savedDevice.id } + savedDevice)
        _uiState.update {
            it.copy(
                savedDevices = merged,
                connectionState = ConnectionState.Connecting(savedDevice)
            )
        }
        viewModelScope.launch {
            prefsRepo.saveDevices(merged, savedDevice.id)
            wifiTransport.disconnect()
            bleTransport.disconnect()

            if (savedDevice.protocol.isWifi) {
                val result = wifiTransport.connect(savedDevice)
                result.fold(
                    onSuccess = {
                        lastSentColor = null
                        _uiState.update {
                            it.copy(
                                connectionState = ConnectionState.Connected(
                                    device = savedDevice,
                                    chipsetInfo = savedDevice.protocol.displayName
                                ),
                                statusBannerMessage = "Подключено по Wi-Fi: ${savedDevice.name}"
                            )
                        }
                        wifiTransport.sendPower(_uiState.value.isPowerOn)
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                connectionState = ConnectionState.Error(
                                    message = err.message ?: "Не удалось подключиться по Wi-Fi",
                                    device = savedDevice
                                ),
                                statusBannerMessage = "Wi-Fi ошибка: ${err.message ?: "проверьте сеть и IP"}"
                            )
                        }
                    }
                )
            } else {
                val result = bleTransport.connect(savedDevice)
                result.fold(
                    onSuccess = { chipset ->
                        lastSentColor = null
                        _uiState.update {
                            it.copy(
                                connectionState = ConnectionState.Connected(
                                    device = savedDevice,
                                    chipsetInfo = chipset.label
                                ),
                                statusBannerMessage = "BLE подключено: ${savedDevice.name} (${chipset.label})"
                            )
                        }
                        bleTransport.sendPower(_uiState.value.isPowerOn)
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                connectionState = ConnectionState.Error(
                                    message = err.message ?: "Не удалось подключиться по Bluetooth",
                                    device = savedDevice
                                ),
                                statusBannerMessage = "BLE ошибка: ${err.message ?: "попробуйте снова"}"
                            )
                        }
                    }
                )
            }
        }
    }

    fun removeSavedDevice(deviceId: String) {
        val currentConnected = (_uiState.value.connectionState as? ConnectionState.Connected)?.device
        if (currentConnected?.id == deviceId) {
            disconnectDevice()
        }
        val updated = _uiState.value.savedDevices.filterNot { it.id == deviceId }
        _uiState.update { it.copy(savedDevices = updated) }
        viewModelScope.launch { prefsRepo.saveDevices(updated, null) }
    }

    fun disconnectDevice() {
        viewModelScope.launch {
            wifiTransport.disconnect()
            bleTransport.disconnect()
            _uiState.update {
                it.copy(
                    connectionState = ConnectionState.Disconnected,
                    statusBannerMessage = "Устройство отключено"
                )
            }
        }
    }

    fun setPinOrder(order: PinOrder) {
        _uiState.update { it.copy(pinOrder = order) }
        lastSentColor = null
        viewModelScope.launch {
            prefsRepo.savePinOrder(order)
            val connected = _uiState.value.connectionState as? ConnectionState.Connected
            if (connected != null) {
                if (connected.device.protocol.isWifi) {
                    wifiTransport.sendPinOrder(order)
                } else {
                    bleTransport.sendPinOrder(order)
                }
            }
        }
    }

    fun testPinColor(color: RgbColor) {
        setStaticColor(color)
    }

    fun setSoftPowerFade(enabled: Boolean) {
        _uiState.update { it.copy(softPowerFade = enabled) }
        viewModelScope.launch { prefsRepo.saveSoftPowerFade(enabled) }
    }

    fun setFpsLimit(fps: Int) {
        val clamped = fps.coerceIn(10, 30)
        _uiState.update { it.copy(fpsLimit = clamped) }
        viewModelScope.launch { prefsRepo.saveFpsLimit(clamped) }
    }

    override fun onCleared() {
        super.onCleared()
        micEngine.stopCapture()
        musicPlayer.stopAndRelease()
        bleTransport.stopScan()
        bleTransport.disconnect()
    }
}
