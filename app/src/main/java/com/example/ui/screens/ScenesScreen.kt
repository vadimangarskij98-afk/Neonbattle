package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.FlowDirection
import com.example.model.LightScene
import com.example.model.RgbColor
import com.example.model.SceneCategory
import com.example.model.TransitionCurve
import com.example.ui.components.LabeledNeonSlider
import com.example.viewmodel.NeonUiState
import java.util.Locale
import kotlin.math.roundToInt

private val builderColorPalette = listOf(
    RgbColor(255, 16, 44),
    RgbColor(150, 0, 28),
    RgbColor(0, 229, 255),
    RgbColor(255, 46, 147),
    RgbColor(139, 92, 246),
    RgbColor(34, 229, 139),
    RgbColor(255, 138, 0),
    RgbColor(48, 128, 255),
    RgbColor(255, 214, 10),
    RgbColor(255, 244, 214)
)

@Composable
fun ScenesScreen(
    state: NeonUiState,
    onCategorySelected: (SceneCategory) -> Unit,
    onSceneSelected: (LightScene) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSmoothnessChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onZoneMetersChange: (Float) -> Unit,
    onFlowDirectionChange: (FlowDirection) -> Unit,
    onCreateCustomScene: (String, List<RgbColor>, TransitionCurve, Float, Float, Float) -> Unit,
    onDeleteCustomScene: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    val filteredScenes = remember(state.allScenes, state.selectedSceneCategory) {
        if (state.selectedSceneCategory == SceneCategory.ALL) {
            state.allScenes
        } else {
            state.allScenes.filter { it.category == state.selectedSceneCategory }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + New Scene Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Сцены и бегущие эффекты",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("create_scene_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_add_scene),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(text = stringResource(R.string.btn_create_scene))
                }
            }
        }

        // Category filter chips including Красные & Бегущие зоны
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SceneCategory.entries) { category ->
                    val isCrimson = category == SceneCategory.RED_LUXURY
                    FilterChip(
                        selected = state.selectedSceneCategory == category,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (isCrimson) {
                                Color(0xFF5A0B1C)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            selectedLabelColor = if (isCrimson) {
                                Color(0xFFFFB5C2)
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        ),
                        modifier = Modifier.testTag("scene_cat_${category.name.lowercase()}")
                    )
                }
            }
        }

        // Live Tuning Panel: Speed, Smoothness, Segment Zone Length (m), Direction & Brightness
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LabeledNeonSlider(
                        title = stringResource(R.string.label_speed),
                        valueText = "${(state.speed * 100f).roundToInt()}%",
                        value = state.speed,
                        onValueChange = onSpeedChange,
                        icon = Icons.Rounded.Speed,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        valueRange = 0.05f..1f,
                        testTagName = "scene_speed_slider"
                    )
                    LabeledNeonSlider(
                        title = "Мягкость стыков / Плавность",
                        valueText = "${(state.smoothness * 100f).roundToInt()}%",
                        value = state.smoothness,
                        onValueChange = onSmoothnessChange,
                        icon = Icons.Rounded.Waves,
                        accentColor = MaterialTheme.colorScheme.primary,
                        valueRange = 0f..1f,
                        testTagName = "scene_smoothness_slider"
                    )
                    LabeledNeonSlider(
                        title = "Длина цветной зоны",
                        valueText = String.format(Locale.US, "%.1f м", state.zoneMeters),
                        value = state.zoneMeters,
                        onValueChange = onZoneMetersChange,
                        icon = Icons.Rounded.LinearScale,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        valueRange = 0.35f..2.5f,
                        testTagName = "scene_zone_meters_slider"
                    )
                    LabeledNeonSlider(
                        title = stringResource(R.string.label_brightness),
                        valueText = "${(state.brightness * 100f).roundToInt()}%",
                        value = state.brightness,
                        onValueChange = onBrightnessChange,
                        icon = Icons.Rounded.Brightness6,
                        accentColor = state.livePrimaryColor.toComposeColor(),
                        valueRange = 0.05f..1f,
                        testTagName = "scene_brightness_slider"
                    )

                    // Flow direction chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FlowDirection.entries.forEach { dir ->
                            val selected = state.flowDirection == dir
                            FilterChip(
                                selected = selected,
                                onClick = { onFlowDirectionChange(dir) },
                                label = {
                                    Text(
                                        text = "${dir.shortArrow} ${dir.title}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("flow_dir_${dir.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Empty custom category hint
        if (filteredScenes.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Пока нет авторских сцен",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Соберите свой бегущий перелив из 2–5 цветов с нужным характером движения",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { showCreateDialog = true }) {
                            Text("Создать первую сцену")
                        }
                    }
                }
            }
        } else {
            val rows = filteredScenes.chunked(2)
            items(rows) { rowPair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowPair.forEach { scene ->
                        SceneCardItem(
                            scene = scene,
                            isActive = state.activeSceneId == scene.id,
                            onClick = { onSceneSelected(scene) },
                            onDelete = if (scene.isCustom) ({ onDeleteCustomScene(scene.id) }) else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowPair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }

    if (showCreateDialog) {
        CreateCustomSceneDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { name, colors, curve ->
                onCreateCustomScene(name, colors, curve, state.speed, state.smoothness, state.zoneMeters)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun SceneCardItem(
    scene: LightScene,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val gradientColors = if (scene.colors.size >= 2) {
        scene.colors.map { it.toComposeColor() }
    } else {
        listOf(scene.colors.first().toComposeColor(), MaterialTheme.colorScheme.primary)
    }

    val borderBrush = if (isActive) {
        Brush.horizontalGradient(gradientColors)
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.colorScheme.outlineVariant
            )
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .border(
                width = if (isActive) 2.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("scene_card_${scene.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(Brush.horizontalGradient(gradientColors))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.40f))
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.50f)
                    ) {
                        Text(
                            text = scene.curve.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    if (isActive) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF0A0D18),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = scene.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Удалить сцену",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Text(
                    text = scene.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CreateCustomSceneDialog(
    onDismiss: () -> Unit,
    onSave: (String, List<RgbColor>, TransitionCurve) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val pickedColors = remember {
        mutableStateListOf(RgbColor(255, 16, 44), RgbColor(0, 229, 255), RgbColor(139, 92, 246))
    }
    var selectedCurve by remember { mutableStateOf(TransitionCurve.RUNNING_ZONES) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_create_scene_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_scene_name)) },
                    placeholder = { Text("Например: Алый каскад") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_scene_name_input")
                )

                Text(
                    text = stringResource(R.string.label_scene_colors),
                    style = MaterialTheme.typography.labelLarge
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            Brush.horizontalGradient(
                                pickedColors.map { it.toComposeColor() }
                                    .let { if (it.size < 2) listOf(it.first(), it.first()) else it }
                            )
                        )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pickedColors.forEachIndexed { idx, color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color.toComposeColor())
                                .border(2.dp, Color.White, CircleShape)
                                .clickable {
                                    if (pickedColors.size > 2) pickedColors.removeAt(idx)
                                }
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(builderColorPalette) { paletteColor ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(paletteColor.toComposeColor())
                                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .clickable {
                                    if (pickedColors.size < 5) {
                                        pickedColors.add(paletteColor)
                                    } else {
                                        pickedColors[pickedColors.lastIndex] = paletteColor
                                    }
                                }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.label_transition_curve),
                    style = MaterialTheme.typography.labelLarge
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        listOf(
                            TransitionCurve.RUNNING_ZONES,
                            TransitionCurve.SEGMENT_CHASE,
                            TransitionCurve.COMET_TRAIL,
                            TransitionCurve.SCANNER_BOUNCE,
                            TransitionCurve.DUAL_COLLIDE,
                            TransitionCurve.HEARTBEAT_PULSE,
                            TransitionCurve.SILKY_SINE,
                            TransitionCurve.BREATHING
                        )
                    ) { curve ->
                        FilterChip(
                            selected = selectedCurve == curve,
                            onClick = { selectedCurve = curve },
                            label = { Text(curve.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, pickedColors.toList(), selectedCurve) },
                modifier = Modifier.testTag("save_custom_scene_button")
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
