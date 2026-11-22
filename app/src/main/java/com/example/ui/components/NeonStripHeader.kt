package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.model.ActiveControllerMode
import com.example.model.ConnectionState
import com.example.model.RgbColor
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusGreen
import com.example.viewmodel.NeonUiState
import kotlin.math.roundToInt

@Composable
fun NeonStripHeader(
    state: NeonUiState,
    onPowerToggle: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeGlowColor by animateColorAsState(
        targetValue = if (state.isPowerOn) {
            state.livePrimaryColor.toComposeColor()
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(180),
        label = "header_glow"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ConnectionStatusPill(
                connectionState = state.connectionState,
                isWifiAvailable = state.isWifiAvailable,
                onClick = onOpenDevices,
                modifier = Modifier.weight(1f, fill = false)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .size(48.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.cd_settings_button),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isPowerOn) {
                                activeGlowColor.copy(alpha = 0.24f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (state.isPowerOn) activeGlowColor else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable(onClick = onPowerToggle)
                        .minimumInteractiveComponentSize()
                        .testTag("power_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = stringResource(R.string.cd_power_button),
                        tint = if (state.isPowerOn) activeGlowColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // High-Resolution 36-Segment Neon Diffuser Strip Mirror
        NeonTubeMirrorBar(
            segments = state.liveStripSegments,
            isPowerOn = state.isPowerOn,
            modeLabel = resolveModeLabel(state),
            brightnessPercent = (state.brightness * 100f).roundToInt(),
            hexLabel = state.livePrimaryColor.toHexString()
        )
    }
}

@Composable
private fun ConnectionStatusPill(
    connectionState: ConnectionState,
    isWifiAvailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (dotColor, icon, titleText, subtitleText) = when (connectionState) {
        is ConnectionState.Connected -> Quad(
            StatusGreen,
            if (connectionState.device.protocol.isWifi) Icons.Rounded.Wifi else Icons.Outlined.Bluetooth,
            connectionState.device.name,
            if (connectionState.device.protocol.isWifi) {
                "Wi-Fi • ${connectionState.device.address}"
            } else {
                "Bluetooth LE • Прямое"
            }
        )
        is ConnectionState.Connecting -> Quad(
            StatusAmber,
            if (connectionState.device.protocol.isWifi) Icons.Rounded.Wifi else Icons.Outlined.Bluetooth,
            stringResource(R.string.status_connecting),
            connectionState.device.name
        )
        is ConnectionState.Error -> Quad(
            StatusError,
            Icons.Rounded.WifiOff,
            "Нет связи",
            "Нажмите для подключения"
        )
        ConnectionState.Disconnected -> Quad(
            if (isWifiAvailable) MaterialTheme.colorScheme.onSurfaceVariant else StatusAmber,
            if (isWifiAvailable) Icons.Rounded.Wifi else Icons.Outlined.Bluetooth,
            if (isWifiAvailable) "Подключить ленту" else "Нет Wi-Fi • BLE",
            if (isWifiAvailable) "Wi-Fi или Bluetooth LE" else "Подключите по Bluetooth"
        )
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("device_connection_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.cd_devices_button),
                tint = dotColor,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.padding(end = 4.dp)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NeonTubeMirrorBar(
    segments: List<RgbColor>,
    isPowerOn: Boolean,
    modeLabel: String,
    brightnessPercent: Int,
    hexLabel: String
) {
    val safeSegments = if (isPowerOn && segments.size >= 2) {
        segments.map { it.toComposeColor() }
    } else {
        List(12) { Color(0xFF1F2438) }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isPowerOn) "$hexLabel  •  $brightnessPercent%" else "ВЫКЛ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Realistic Neon Diffuser Strip Preview (shows individual flowing meters & dark chases!)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                val trackHeight = 11.dp.toPx()
                val trackTop = (size.height - trackHeight) / 2f

                // Dark silicone strip channel housing
                drawRoundRect(
                    color = Color(0xFF0A0C14),
                    topLeft = Offset(0f, trackTop - 1.dp.toPx()),
                    size = Size(size.width, trackHeight + 2.dp.toPx()),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )

                // Soft outer neon bloom halo matching each flowing segment
                if (isPowerOn) {
                    val glowBrush = Brush.horizontalGradient(
                        colors = safeSegments.map { it.copy(alpha = 0.45f) }
                    )
                    drawRoundRect(
                        brush = glowBrush,
                        topLeft = Offset(0f, 1.dp.toPx()),
                        size = Size(size.width, size.height - 2.dp.toPx()),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }

                // Multi-segment diffuser body
                val tubeBrush = Brush.horizontalGradient(colors = safeSegments)
                drawRoundRect(
                    brush = tubeBrush,
                    topLeft = Offset(2.dp.toPx(), trackTop),
                    size = Size(size.width - 4.dp.toPx(), trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )

                // Subtle 1m ruler markers under tube for visual scale
                val meterMarks = 5
                for (m in 1 until meterMarks) {
                    val mx = size.width * (m.toFloat() / meterMarks.toFloat())
                    drawLine(
                        color = Color.White.copy(alpha = 0.16f),
                        start = Offset(mx, trackTop + trackHeight + 2.dp.toPx()),
                        end = Offset(mx, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Specular glass highlight along neon diffuser
                if (isPowerOn) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(6.dp.toPx(), trackTop + trackHeight * 0.22f),
                        size = Size((size.width - 12.dp.toPx()).coerceAtLeast(0f), trackHeight * 0.26f),
                        cornerRadius = CornerRadius(trackHeight / 4f, trackHeight / 4f)
                    )
                }
            }
        }
    }
}

private fun resolveModeLabel(state: NeonUiState): String {
    if (!state.isPowerOn) return "Лента выключена"
    return when (state.activeMode) {
        ActiveControllerMode.STATIC_COLOR -> "Статический цвет"
        ActiveControllerMode.CCT_WHITE -> "Белый свет • ${state.cctKelvin}K"
        ActiveControllerMode.SCENE -> "Сцена: ${state.activeScene?.name ?: "Перелив"}"
        ActiveControllerMode.MUSIC_MIC -> "Светомузыка • ${state.musicReactiveMode.title}"
        ActiveControllerMode.MUSIC_PLAYER -> "Плеер • ${state.musicReactiveMode.title}"
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
