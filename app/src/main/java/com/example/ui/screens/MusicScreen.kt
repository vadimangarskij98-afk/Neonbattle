package com.example.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AudioFrame
import com.example.model.MicSource
import com.example.model.MusicReactiveMode
import com.example.model.RgbColor
import com.example.ui.components.LabeledNeonSlider
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.MusicPalettes
import com.example.viewmodel.NeonUiState
import kotlin.math.roundToInt

@Composable
fun MusicScreen(
    state: NeonUiState,
    hasRecordPermission: () -> Boolean,
    onSelectMusicTab: (Int) -> Unit,
    onSelectMicSource: (MicSource) -> Unit,
    onToggleMic: () -> Unit,
    onSelectReactiveMode: (MusicReactiveMode) -> Unit,
    onSelectMusicPalette: (String) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onDecaySmoothnessChange: (Float) -> Unit,
    onBassBoostChange: (Float) -> Unit,
    onAddTracks: (List<Uri>) -> Unit,
    onPlayTrackAt: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onSeekTrack: (Float) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onToggleMic()
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onAddTracks(uris)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Mic vs Local Player Tab Switcher
        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("music_mode_switcher")
            ) {
                val tabs = listOf(
                    stringResource(R.string.music_tab_mic),
                    stringResource(R.string.music_tab_player)
                )
                tabs.forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = state.musicTabIndex == idx,
                        onClick = { onSelectMusicTab(idx) },
                        shape = SegmentedButtonDefaults.itemShape(idx, tabs.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("music_tab_$idx")
                    ) {
                        Text(text = label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Live 24-Bar Neon Spectrum & Beat Visualizer Card
        item {
            SpectrumVisualizerCard(
                frame = state.audioFrame,
                palette = state.selectedMusicPalette.colors,
                isActive = if (state.musicTabIndex == 0) state.isMicActive else state.isTrackPlaying
            )
        }

        if (state.musicTabIndex == 0) {
            // Microphone Source & Capture Control
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MicSource.entries.forEach { src ->
                                FilterChip(
                                    selected = state.micSource == src,
                                    onClick = { onSelectMicSource(src) },
                                    label = { Text(src.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("mic_source_${src.name.lowercase()}")
                                )
                            }
                        }

                        if (state.micSource == MicSource.PHONE) {
                            Button(
                                onClick = {
                                    if (state.isMicActive || hasRecordPermission()) {
                                        onToggleMic()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.isMicActive) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("toggle_mic_button")
                            ) {
                                Icon(
                                    imageVector = if (state.isMicActive) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (state.isMicActive) {
                                        stringResource(R.string.btn_stop_mic)
                                    } else {
                                        stringResource(R.string.btn_start_mic)
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        } else {
                            Text(
                                text = "Сигнал реакции берётся со встроенного микрофона контроллера ленты (для поддерживаемых моделей Lotus / ELK).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        state.micError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        } else {
            // Local Music Player Card (Zero-permission SAF audio picker)
            item {
                LocalMusicPlayerCard(
                    state = state,
                    onPickTracks = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                    onTogglePlayPause = {
                        if (!hasRecordPermission()) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        onTogglePlayPause()
                    },
                    onPlayTrackAt = onPlayTrackAt,
                    onNextTrack = onNextTrack,
                    onPrevTrack = onPrevTrack,
                    onSeekTrack = onSeekTrack,
                    onRemoveTrack = onRemoveTrack
                )
            }
        }

        // Reactive Algorithm Style Selector
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Алгоритм светомузыки",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MusicReactiveMode.entries.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { mode ->
                                val selected = state.musicReactiveMode == mode
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = if (selected) 1.5.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onSelectReactiveMode(mode) }
                                        .testTag("music_reactive_${mode.name.lowercase()}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = mode.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            text = mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Цветовая палитра",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MusicPalettes.all) { pal ->
                            val selected = state.selectedMusicPaletteId == pal.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelectMusicPalette(pal.id) }
                                    .testTag("music_palette_${pal.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp, 12.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                Brush.horizontalGradient(pal.colors.map { it.toComposeColor() })
                                            )
                                    )
                                    Text(
                                        text = pal.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sensitivity, Smoothness & Bass Boost Tuning
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LabeledNeonSlider(
                        title = stringResource(R.string.label_sensitivity),
                        valueText = "${(state.micSensitivity * 100f).roundToInt()}%",
                        value = state.micSensitivity,
                        onValueChange = onSensitivityChange,
                        icon = Icons.Rounded.GraphicEq,
                        accentColor = MaterialTheme.colorScheme.primary,
                        valueRange = 0.1f..1f,
                        testTagName = "mic_sensitivity_slider"
                    )
                    LabeledNeonSlider(
                        title = stringResource(R.string.label_decay_smoothness),
                        valueText = "${(state.micDecaySmoothness * 100f).roundToInt()}%",
                        value = state.micDecaySmoothness,
                        onValueChange = onDecaySmoothnessChange,
                        icon = Icons.Rounded.Waves,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        valueRange = 0.05f..0.98f,
                        testTagName = "mic_decay_slider"
                    )
                    LabeledNeonSlider(
                        title = stringResource(R.string.label_bass_boost),
                        valueText = "${(state.bassBoost * 100f).roundToInt()}%",
                        value = state.bassBoost,
                        onValueChange = onBassBoostChange,
                        icon = Icons.Rounded.Equalizer,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        valueRange = 0f..1f,
                        testTagName = "mic_bass_slider"
                    )
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SpectrumVisualizerCard(
    frame: AudioFrame,
    palette: List<RgbColor>,
    isActive: Boolean
) {
    val barColors = palette.map { it.toComposeColor() }
        .let { if (it.size >= 2) it else listOf(Color(0xFF00E5FF), Color(0xFFFF2E93)) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !isActive -> MaterialTheme.colorScheme.outline
                                    frame.isBeat -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                    )
                    Text(
                        text = if (isActive) "Анализ спектра в реальном времени" else "Ожидание аудиосигнала",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${(frame.level * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
            ) {
                val count = frame.bars.size.coerceAtLeast(1)
                val gap = 4.dp.toPx()
                val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(3f)
                val brush = Brush.horizontalGradient(barColors)

                frame.bars.forEachIndexed { idx, rawVal ->
                    val magnitude = if (isActive) rawVal.coerceIn(0.06f, 1f) else 0.08f
                    val h = (size.height * magnitude).coerceAtLeast(6.dp.toPx())
                    val x = idx * (barW + gap)
                    val y = size.height - h
                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(x, y),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                        alpha = if (isActive) 0.92f else 0.25f
                    )
                }
            }

            // 3-band telemetry row (Bass / Mid / Treble)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BandPill("БАС", frame.bass, MaterialTheme.colorScheme.secondary)
                BandPill("СЕРЕДИНА", frame.mid, MaterialTheme.colorScheme.tertiary)
                BandPill("ВЕРХ", frame.treble, MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BandPill(label: String, value: Float, tint: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.14f)
    ) {
        Text(
            text = "$label ${(value * 100f).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = tint,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun LocalMusicPlayerCard(
    state: NeonUiState,
    onPickTracks: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayTrackAt: (Int) -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onSeekTrack: (Float) -> Unit,
    onRemoveTrack: (Int) -> Unit
) {
    val activeTrack = state.playlist.getOrNull(state.currentTrackIndex)
    val progress = if (state.trackDurationMs > 0L) {
        (state.trackPositionMs.toFloat() / state.trackDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Плеер с синхронизацией",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FilledTonalButton(
                    onClick = onPickTracks,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("pick_audio_tracks_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_pick_audio))
                }
            }

            if (state.playlist.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = stringResource(R.string.empty_playlist_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.empty_playlist_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Now Playing Controls
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = activeTrack?.title ?: "Выберите трек",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeTrack?.artist ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Slider(
                        value = progress,
                        onValueChange = onSeekTrack,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDurationMs(state.trackPositionMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDurationMs(state.trackDurationMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPrevTrack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Предыдущий трек")
                        }
                        Spacer(Modifier.width(12.dp))
                        FilledIconButton(
                            onClick = onTogglePlayPause,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("player_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (state.isTrackPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isTrackPlaying) "Пауза" else "Воспроизвести",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        IconButton(onClick = onNextTrack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Следующий трек")
                        }
                    }
                }

                // Track queue
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.playlist.forEachIndexed { idx, track ->
                        val isCurrent = idx == state.currentTrackIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .clickable { onPlayTrackAt(idx) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artist} • ${formatDurationMs(track.durationMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = { onRemoveTrack(idx) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Убрать трек",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%02d:%02d".format(min, sec)
}
