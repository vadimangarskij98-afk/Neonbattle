package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ColorControlSubMode
import com.example.model.RgbColor
import com.example.ui.components.CctKelvinBar
import com.example.ui.components.HsvNeonColorWheel
import com.example.ui.components.LabeledNeonSlider
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.NeonUiState
import kotlin.math.roundToInt

private val classicNeonSwatches = listOf(
    "Циан" to RgbColor(0, 229, 255),
    "Маджента" to RgbColor(255, 46, 147),
    "Ультрафиолет" to RgbColor(139, 92, 246),
    "Неон-лайм" to RgbColor(34, 229, 139),
    "Янтарь" to RgbColor(255, 145, 0),
    "Рубин" to RgbColor(255, 36, 66),
    "Сапфир" to RgbColor(36, 114, 255),
    "Тёплый белый" to RgbColor(255, 226, 184)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColorStudioScreen(
    state: NeonUiState,
    onSubModeSelected: (ColorControlSubMode) -> Unit,
    onColorSelected: (RgbColor) -> Unit,
    onKelvinSelected: (Int) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onSmoothnessChange: (Float) -> Unit,
    onSaveFavorite: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = state.selectedColor.toComposeColor()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sub-mode switcher: Wheel / CCT / RGB
        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("color_submode_switcher")
            ) {
                val options = listOf(
                    ColorControlSubMode.WHEEL to stringResource(R.string.mode_wheel),
                    ColorControlSubMode.CCT to stringResource(R.string.mode_cct),
                    ColorControlSubMode.RGB to stringResource(R.string.mode_rgb)
                )
                options.forEachIndexed { idx, (mode, label) ->
                    SegmentedButton(
                        selected = state.colorSubMode == mode,
                        onClick = { onSubModeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(idx, options.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("color_submode_${mode.name.lowercase()}")
                    ) {
                        Text(text = label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Main Picker Card
        item {
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
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (state.colorSubMode) {
                        ColorControlSubMode.WHEEL -> {
                            HsvNeonColorWheel(
                                selectedColor = state.selectedColor,
                                onColorChanged = onColorSelected
                            )
                            // Compact RGB + HEX telemetry row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                ChannelReadoutBadge("R", state.selectedColor.r, Color(0xFFFF5268))
                                ChannelReadoutBadge("G", state.selectedColor.g, Color(0xFF2EE68A))
                                ChannelReadoutBadge("B", state.selectedColor.b, Color(0xFF3E95FF))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = state.selectedColor.toHexString(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = MonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        ColorControlSubMode.CCT -> {
                            val cctPreview = RgbColor.fromKelvin(state.cctKelvin).toComposeColor()
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(116.dp)
                                    .clip(CircleShape)
                                    .background(cctPreview)
                                    .border(3.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WbSunny,
                                    contentDescription = null,
                                    tint = Color(0xFF1A1D28),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text(
                                text = "${state.cctKelvin} K",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = MonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            CctKelvinBar(
                                kelvin = state.cctKelvin,
                                onKelvinChange = onKelvinSelected
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val presets = listOf(
                                    2700 to stringResource(R.string.label_warm_light),
                                    4000 to stringResource(R.string.label_neutral_light),
                                    6500 to stringResource(R.string.label_cool_light)
                                )
                                presets.forEach { (k, name) ->
                                    FilterChip(
                                        selected = kotlin.math.abs(state.cctKelvin - k) < 350,
                                        onClick = { onKelvinSelected(k) },
                                        label = { Text("$name ${k}K", maxLines = 1) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("cct_preset_$k")
                                    )
                                }
                            }
                        }

                        ColorControlSubMode.RGB -> {
                            LabeledNeonSlider(
                                title = "Красный (R)",
                                valueText = "${state.selectedColor.r}",
                                value = state.selectedColor.r / 255f,
                                onValueChange = {
                                    onColorSelected(state.selectedColor.copy(r = (it * 255f).roundToInt()))
                                },
                                icon = Icons.Rounded.Tune,
                                accentColor = Color(0xFFFF4B63),
                                testTagName = "rgb_slider_r"
                            )
                            LabeledNeonSlider(
                                title = "Зелёный (G)",
                                valueText = "${state.selectedColor.g}",
                                value = state.selectedColor.g / 255f,
                                onValueChange = {
                                    onColorSelected(state.selectedColor.copy(g = (it * 255f).roundToInt()))
                                },
                                icon = Icons.Rounded.Tune,
                                accentColor = Color(0xFF28E085),
                                testTagName = "rgb_slider_g"
                            )
                            LabeledNeonSlider(
                                title = "Синий (B)",
                                valueText = "${state.selectedColor.b}",
                                value = state.selectedColor.b / 255f,
                                onValueChange = {
                                    onColorSelected(state.selectedColor.copy(b = (it * 255f).roundToInt()))
                                },
                                icon = Icons.Rounded.Tune,
                                accentColor = Color(0xFF3E95FF),
                                testTagName = "rgb_slider_b"
                            )
                        }
                    }
                }
            }
        }

        // Brightness + Smoothness Sliders Card
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LabeledNeonSlider(
                        title = stringResource(R.string.label_brightness),
                        valueText = "${(state.brightness * 100f).roundToInt()}%",
                        value = state.brightness,
                        onValueChange = onBrightnessChange,
                        icon = Icons.Rounded.Brightness6,
                        accentColor = accent,
                        valueRange = 0.05f..1f,
                        testTagName = "brightness_slider"
                    )
                    LabeledNeonSlider(
                        title = stringResource(R.string.label_smoothness),
                        valueText = "${(state.smoothness * 100f).roundToInt()}%",
                        value = state.smoothness,
                        onValueChange = onSmoothnessChange,
                        icon = Icons.Rounded.Waves,
                        accentColor = MaterialTheme.colorScheme.primary,
                        valueRange = 0f..1f,
                        testTagName = "smoothness_slider"
                    )
                }
            }
        }

        // Favorite Custom Slots (Tap = apply, Long-press = save)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.label_favorites),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.hint_long_press_save),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        state.favoriteColors.forEachIndexed { idx, fav ->
                            val isSelected = fav.toHexString() == state.selectedColor.toHexString()
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(fav.toComposeColor())
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .combinedClickable(
                                        onClick = { onColorSelected(fav) },
                                        onLongClick = { onSaveFavorite(idx) }
                                    )
                                    .testTag("favorite_color_slot_$idx")
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF0A0D18),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Classic & Neon Presets Grid
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
                    Text(
                        text = stringResource(R.string.label_classic_neon),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    classicNeonSwatches.chunked(4).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { (label, rgb) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onColorSelected(rgb) }
                                        .padding(vertical = 4.dp)
                                        .testTag("classic_preset_${rgb.toHexString().removePrefix("#")}")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(rgb.toComposeColor())
                                            .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ChannelReadoutBadge(label: String, value: Int, tint: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.14f),
        modifier = Modifier.border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
    ) {
        Text(
            text = "$label $value",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = tint,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
