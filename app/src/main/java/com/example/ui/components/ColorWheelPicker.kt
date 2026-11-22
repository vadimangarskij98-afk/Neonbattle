package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.RgbColor
import com.example.ui.theme.MonoFontFamily
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun HsvNeonColorWheel(
    selectedColor: RgbColor,
    onColorChanged: (RgbColor) -> Unit,
    modifier: Modifier = Modifier,
    wheelSize: Dp = 248.dp
) {
    var activeHue by remember { mutableFloatStateOf(RgbColor.toHsv(selectedColor).first) }
    var activeSat by remember { mutableFloatStateOf(RgbColor.toHsv(selectedColor).second.coerceAtLeast(0.15f)) }
    var isDragging by remember { mutableStateOf(false) }

    // Sync external color updates (e.g. preset tap) without snapping thumb mid-drag
    LaunchedEffect(selectedColor) {
        if (!isDragging) {
            val (h, s, _) = RgbColor.toHsv(selectedColor)
            if (s > 0.05f) {
                activeHue = h
            }
            activeSat = s.coerceIn(0.12f, 1f)
        }
    }

    val spectrumColors = remember {
        listOf(
            Color(0xFFFF0000),
            Color(0xFFFF8800),
            Color(0xFFFFFF00),
            Color(0xFF00FF44),
            Color(0xFF00FFFF),
            Color(0xFF0066FF),
            Color(0xFF8800FF),
            Color(0xFFFF00AA),
            Color(0xFFFF0000)
        )
    }

    fun updateFromPosition(pos: Offset, width: Float, height: Float) {
        val center = Offset(width / 2f, height / 2f)
        val dx = pos.x - center.x
        val dy = pos.y - center.y
        val maxRadius = minOf(width, height) / 2f
        val innerRadius = maxRadius * 0.44f
        val outerRadius = maxRadius * 0.94f

        var angleDeg = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat()
        if (angleDeg < 0f) angleDeg += 360f

        val dist = hypot(dx, dy)
        val sat = ((dist - innerRadius * 0.65f) / (outerRadius - innerRadius * 0.65f))
            .coerceIn(0.12f, 1f)

        activeHue = angleDeg
        activeSat = sat
        onColorChanged(RgbColor.fromHsv(angleDeg, sat, 1f))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(wheelSize)
            .semantics { contentDescription = "Цветовой круг для выбора оттенка" }
            .testTag("hsv_color_wheel")
    ) {
        Canvas(
            modifier = Modifier
                .size(wheelSize)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        updateFromPosition(offset, size.width.toFloat(), size.height.toFloat())
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, _ ->
                        change.consume()
                        updateFromPosition(change.position, size.width.toFloat(), size.height.toFloat())
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = minOf(size.width, size.height) / 2f
            val ringThickness = maxR * 0.34f
            val ringRadius = maxR - ringThickness / 2f - 6.dp.toPx()

            val previewColor = selectedColor.toComposeColor()

            // Soft ambient glow behind wheel
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(previewColor.copy(alpha = 0.32f), Color.Transparent),
                    center = center,
                    radius = maxR
                ),
                radius = maxR,
                center = center
            )

            // Main Sweep Spectrum Ring
            drawCircle(
                brush = Brush.sweepGradient(colors = spectrumColors, center = center),
                radius = ringRadius,
                center = center,
                style = Stroke(width = ringThickness)
            )

            // Subtle inner saturation soft white blend
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.45f to Color.White.copy(alpha = 0.25f),
                        0.72f to Color.Transparent
                    ),
                    center = center,
                    radius = ringRadius + ringThickness / 2f
                ),
                radius = ringRadius,
                center = center,
                style = Stroke(width = ringThickness)
            )

            // Center dark housing + glowing orb
            val innerOrbRadius = maxR * 0.36f
            drawCircle(
                color = Color(0xFF0E1220),
                radius = innerOrbRadius + 8.dp.toPx(),
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(previewColor.copy(alpha = 0.55f), Color.Transparent),
                    center = center,
                    radius = innerOrbRadius + 14.dp.toPx()
                ),
                radius = innerOrbRadius + 14.dp.toPx(),
                center = center
            )
            drawCircle(
                color = previewColor,
                radius = innerOrbRadius,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.32f),
                radius = innerOrbRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Thumb indicator on the ring
            val rad = activeHue * PI / 180.0
            val thumbDist = (ringRadius - ringThickness * 0.38f) + (activeSat * ringThickness * 0.76f)
            val thumbOffset = Offset(
                x = center.x + (cos(rad) * thumbDist).toFloat(),
                y = center.y + (sin(rad) * thumbDist).toFloat()
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.45f),
                radius = 15.dp.toPx(),
                center = thumbOffset
            )
            drawCircle(
                color = Color.White,
                radius = 13.dp.toPx(),
                center = thumbOffset
            )
            drawCircle(
                color = previewColor,
                radius = 9.dp.toPx(),
                center = thumbOffset
            )
        }
    }
}

@Composable
fun CctKelvinBar(
    kelvin: Int,
    onKelvinChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = ((kelvin - 2000f) / 5500f).coerceIn(0f, 1f)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onKelvinChange((2000 + f * 5500f).roundToInt())
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onKelvinChange((2000 + f * 5500f).roundToInt())
                    }
                }
                .testTag("cct_kelvin_bar")
        ) {
            val barHeight = 16.dp.toPx()
            val top = (size.height - barHeight) / 2f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFF9329),
                        Color(0xFFFFD8A6),
                        Color(0xFFFFFFFF),
                        Color(0xFFCDE6FF),
                        Color(0xFF9AC9FF)
                    )
                ),
                topLeft = Offset(0f, top),
                size = Size(size.width, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
            )
            val thumbX = (fraction * size.width).coerceIn(14.dp.toPx(), size.width - 14.dp.toPx())
            drawCircle(
                color = Color.Black.copy(alpha = 0.4f),
                radius = 15.dp.toPx(),
                center = Offset(thumbX, size.height / 2f)
            )
            drawCircle(
                color = Color.White,
                radius = 12.dp.toPx(),
                center = Offset(thumbX, size.height / 2f)
            )
            drawCircle(
                color = RgbColor.fromKelvin(kelvin).toComposeColor(),
                radius = 8.dp.toPx(),
                center = Offset(thumbX, size.height / 2f)
            )
        }
    }
}

@Composable
fun LabeledNeonSlider(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: ImageVector,
    accentColor: Color,
    testTagName: String,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = accentColor
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTagName)
        )
    }
}
