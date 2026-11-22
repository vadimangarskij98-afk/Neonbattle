package com.example.engine

import com.example.model.AudioFrame
import com.example.model.FlowDirection
import com.example.model.LightScene
import com.example.model.MusicReactiveMode
import com.example.model.RgbColor
import com.example.model.SceneCategory
import com.example.model.TransitionCurve
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

object PresetScenes {
    val builtIn: List<LightScene> = listOf(
        // =====================================================================
        // КРАСНЫЕ ЭФФЕКТЫ (RED LUXURY)
        // =====================================================================
        LightScene(
            id = "red_scarlet_velvet",
            name = "Алый бархат",
            subtitle = "Глубокий бордовый, рубин и ярко-алый плавно бегут по ленте",
            category = SceneCategory.RED_LUXURY,
            colors = listOf(
                RgbColor(115, 0, 22),
                RgbColor(255, 16, 44),
                RgbColor(210, 0, 56),
                RgbColor(68, 0, 14)
            ),
            curve = TransitionCurve.RUNNING_ZONES,
            defaultSpeed = 0.42f,
            defaultSmoothness = 0.90f,
            defaultZoneMeters = 1.0f,
            hardwareModeCode = 0x87
        ),
        LightScene(
            id = "red_crimson_comet",
            name = "Красная комета",
            subtitle = "Ярко-алый метеор с плавно гаснущим рубиновым шлейфом",
            category = SceneCategory.RED_LUXURY,
            colors = listOf(
                RgbColor(255, 120, 120),
                RgbColor(255, 18, 36),
                RgbColor(135, 0, 18)
            ),
            curve = TransitionCurve.COMET_TRAIL,
            defaultSpeed = 0.56f,
            defaultSmoothness = 0.85f,
            defaultZoneMeters = 0.9f
        ),
        LightScene(
            id = "red_laser_scanner",
            name = "Красный лазер",
            subtitle = "Точный алый луч плавно скользит туда-обратно с послесвечением",
            category = SceneCategory.RED_LUXURY,
            colors = listOf(
                RgbColor(255, 10, 28),
                RgbColor(255, 74, 48),
                RgbColor(95, 0, 14)
            ),
            curve = TransitionCurve.SCANNER_BOUNCE,
            defaultSpeed = 0.52f,
            defaultSmoothness = 0.82f,
            defaultZoneMeters = 0.8f
        ),
        LightScene(
            id = "red_heartbeat",
            name = "Сердцебиение",
            subtitle = "Двойной бархатный пульс глубокого красного неона",
            category = SceneCategory.RED_LUXURY,
            colors = listOf(
                RgbColor(255, 12, 36),
                RgbColor(170, 0, 28),
                RgbColor(255, 38, 68)
            ),
            curve = TransitionCurve.HEARTBEAT_PULSE,
            defaultSpeed = 0.48f,
            defaultSmoothness = 0.88f,
            defaultZoneMeters = 1.2f
        ),
        LightScene(
            id = "red_crimson_ice_split",
            name = "Алый + Лёд",
            subtitle = "Метр огненно-красного переходит в метр ледяного циана по кругу",
            category = SceneCategory.RED_LUXURY,
            colors = listOf(
                RgbColor(255, 18, 42),
                RgbColor(0, 224, 255),
                RgbColor(220, 0, 72),
                RgbColor(32, 92, 255)
            ),
            curve = TransitionCurve.RUNNING_ZONES,
            defaultSpeed = 0.46f,
            defaultSmoothness = 0.72f,
            defaultZoneMeters = 1.0f
        ),
        LightScene(
            id = "red_ruby_lava",
            name = "Рубиновая лава",
            subtitle = "Тягучие потоки кармина, алого и раскалённого янтаря",
            category = SceneCategory.RED_LUXURY,
            colors = listOf(
                RgbColor(255, 22, 16),
                RgbColor(140, 0, 20),
                RgbColor(255, 86, 12),
                RgbColor(215, 0, 42)
            ),
            curve = TransitionCurve.SILKY_SINE,
            defaultSpeed = 0.36f,
            defaultSmoothness = 0.94f,
            defaultZoneMeters = 1.1f
        ),
        LightScene(
            id = "red_blood_moon",
            name = "Кровавая луна",
            subtitle = "Гипнотическое медленное дыхание тёмного граната",
            category = SceneCategory.RED_LUXURY,
            colors = listOf(
                RgbColor(185, 0, 26),
                RgbColor(255, 32, 54),
                RgbColor(96, 0, 18)
            ),
            curve = TransitionCurve.BREATHING,
            defaultSpeed = 0.30f,
            defaultSmoothness = 0.96f,
            defaultZoneMeters = 1.3f
        ),
        LightScene(
            id = "red_ruby_gold",
            name = "Рубин и золото",
            subtitle = "Премиальный перелив глубокого красного и тёплого шампанского",
            category = SceneCategory.RED_LUXURY,
            colors = listOf(
                RgbColor(215, 12, 38),
                RgbColor(255, 178, 52),
                RgbColor(145, 0, 32),
                RgbColor(255, 118, 28)
            ),
            curve = TransitionCurve.RUNNING_ZONES,
            defaultSpeed = 0.38f,
            defaultSmoothness = 0.86f,
            defaultZoneMeters = 1.0f
        ),

        // =====================================================================
        // БЕГУЩИЕ ЗОНЫ И ПРЕРЫВИСТЫЕ ПОТОКИ (FLOW_ZONES)
        // =====================================================================
        LightScene(
            id = "flow_meter_cascade",
            name = "Метровый каскад",
            subtitle = "Метр один цвет, следующий метр другой — плавно бегут по кругу",
            category = SceneCategory.FLOW_ZONES,
            colors = listOf(
                RgbColor(255, 24, 68),
                RgbColor(0, 229, 255),
                RgbColor(148, 66, 255),
                RgbColor(255, 176, 0)
            ),
            curve = TransitionCurve.RUNNING_ZONES,
            defaultSpeed = 0.50f,
            defaultSmoothness = 0.68f,
            defaultZoneMeters = 1.0f,
            hardwareModeCode = 0x88
        ),
        LightScene(
            id = "flow_interrupted_neon",
            name = "Прерывистый неон",
            subtitle = "Светящиеся цветные сегменты с мягкими тёмными паузами в движении",
            category = SceneCategory.FLOW_ZONES,
            colors = listOf(
                RgbColor(255, 24, 96),
                RgbColor(0, 238, 255),
                RgbColor(139, 92, 246),
                RgbColor(34, 229, 139)
            ),
            curve = TransitionCurve.SEGMENT_CHASE,
            defaultSpeed = 0.54f,
            defaultSmoothness = 0.65f,
            defaultZoneMeters = 0.85f
        ),
        LightScene(
            id = "flow_running_ruby",
            name = "Бегущий рубин",
            subtitle = "Прерывистые ярко-красные и оранжевые блоки бегут цепочкой",
            category = SceneCategory.FLOW_ZONES,
            colors = listOf(
                RgbColor(255, 14, 34),
                RgbColor(255, 92, 0),
                RgbColor(220, 0, 74)
            ),
            curve = TransitionCurve.SEGMENT_CHASE,
            defaultSpeed = 0.58f,
            defaultSmoothness = 0.62f,
            defaultZoneMeters = 0.9f
        ),
        LightScene(
            id = "flow_dual_collide",
            name = "Встречные потоки",
            subtitle = "Алая и бирюзовая волны летят навстречу друг другу и сливаются",
            category = SceneCategory.FLOW_ZONES,
            colors = listOf(
                RgbColor(255, 20, 56),
                RgbColor(0, 232, 255),
                RgbColor(175, 58, 255)
            ),
            curve = TransitionCurve.DUAL_COLLIDE,
            defaultSpeed = 0.52f,
            defaultSmoothness = 0.80f,
            defaultZoneMeters = 1.0f
        ),
        LightScene(
            id = "flow_rainbow_express",
            name = "Радужный экспресс",
            subtitle = "Полный спектр метровыми зонами непрерывно движется по кругу",
            category = SceneCategory.FLOW_ZONES,
            colors = listOf(
                RgbColor(255, 28, 56),
                RgbColor(255, 152, 0),
                RgbColor(24, 232, 132),
                RgbColor(0, 224, 255),
                RgbColor(124, 72, 255),
                RgbColor(255, 36, 156)
            ),
            curve = TransitionCurve.RUNNING_ZONES,
            defaultSpeed = 0.48f,
            defaultSmoothness = 0.78f,
            defaultZoneMeters = 0.85f,
            hardwareModeCode = 0x89
        ),
        LightScene(
            id = "flow_cyber_train",
            name = "Кибер-поезд",
            subtitle = "Маджента, фиолет и циан бегут ровными светящимися вагонами",
            category = SceneCategory.FLOW_ZONES,
            colors = listOf(
                RgbColor(255, 28, 148),
                RgbColor(0, 240, 255),
                RgbColor(140, 56, 255)
            ),
            curve = TransitionCurve.SEGMENT_CHASE,
            defaultSpeed = 0.55f,
            defaultSmoothness = 0.70f,
            defaultZoneMeters = 1.0f
        ),
        LightScene(
            id = "flow_ice_pendulum",
            name = "Ледяной маятник",
            subtitle = "Яркий неоново-голубой луч мягко скользит от края до края",
            category = SceneCategory.FLOW_ZONES,
            colors = listOf(
                RgbColor(0, 238, 255),
                RgbColor(168, 248, 255),
                RgbColor(28, 84, 255)
            ),
            curve = TransitionCurve.SCANNER_BOUNCE,
            defaultSpeed = 0.48f,
            defaultSmoothness = 0.86f,
            defaultZoneMeters = 0.85f
        ),

        // =====================================================================
        // ПЛАВНЫЕ ПЕРЕЛИВЫ (SMOOTH)
        // =====================================================================
        LightScene(
            id = "aurora_borealis",
            name = "Северное сияние",
            subtitle = "Изумруд, бирюза и глубокий фиолет",
            category = SceneCategory.SMOOTH,
            colors = listOf(
                RgbColor(0, 245, 160),
                RgbColor(0, 217, 245),
                RgbColor(112, 72, 232),
                RgbColor(18, 184, 134)
            ),
            curve = TransitionCurve.SILKY_SINE,
            defaultSpeed = 0.35f,
            defaultSmoothness = 0.92f
        ),
        LightScene(
            id = "sunset_drift",
            name = "Закат у моря",
            subtitle = "Тёплый янтарь, коралл и вечерний пурпур",
            category = SceneCategory.SMOOTH,
            colors = listOf(
                RgbColor(255, 110, 48),
                RgbColor(255, 46, 120),
                RgbColor(145, 48, 224),
                RgbColor(255, 174, 66)
            ),
            curve = TransitionCurve.SILKY_SINE,
            defaultSpeed = 0.30f,
            defaultSmoothness = 0.95f
        ),
        LightScene(
            id = "cyber_neon",
            name = "Киберпанк",
            subtitle = "Электрический циан и яркая маджента",
            category = SceneCategory.SMOOTH,
            colors = listOf(
                RgbColor(0, 238, 255),
                RgbColor(139, 92, 246),
                RgbColor(255, 24, 138)
            ),
            curve = TransitionCurve.LINEAR_GLIDE,
            defaultSpeed = 0.50f,
            defaultSmoothness = 0.85f
        ),
        LightScene(
            id = "ocean_breath",
            name = "Дыхание океана",
            subtitle = "Спокойные приливы голубого и сапфира",
            category = SceneCategory.SMOOTH,
            colors = listOf(
                RgbColor(0, 120, 255),
                RgbColor(0, 229, 255),
                RgbColor(24, 72, 198)
            ),
            curve = TransitionCurve.BREATHING,
            defaultSpeed = 0.32f,
            defaultSmoothness = 0.96f
        ),
        LightScene(
            id = "pastel_silk",
            name = "Пастельный шёлк",
            subtitle = "Мягкие лавандовые и персиковые оттенки",
            category = SceneCategory.SMOOTH,
            colors = listOf(
                RgbColor(255, 173, 198),
                RgbColor(186, 162, 255),
                RgbColor(148, 234, 255),
                RgbColor(255, 212, 168)
            ),
            curve = TransitionCurve.SILKY_SINE,
            defaultSpeed = 0.25f,
            defaultSmoothness = 0.98f
        ),
        LightScene(
            id = "rainbow_glide",
            name = "Шёлковая радуга",
            subtitle = "Непрерывный спектр без резких скачков",
            category = SceneCategory.SMOOTH,
            colors = listOf(
                RgbColor(255, 48, 79),
                RgbColor(255, 166, 0),
                RgbColor(34, 229, 139),
                RgbColor(0, 229, 255),
                RgbColor(124, 77, 255),
                RgbColor(255, 46, 147)
            ),
            curve = TransitionCurve.LINEAR_GLIDE,
            defaultSpeed = 0.42f,
            defaultSmoothness = 0.90f
        ),

        // =====================================================================
        // АТМОСФЕРА (AMBIENT)
        // =====================================================================
        LightScene(
            id = "fireplace_glow",
            name = "Уютный камин",
            subtitle = "Живое мерцание тёплых углей и пламени",
            category = SceneCategory.AMBIENT,
            colors = listOf(
                RgbColor(255, 76, 12),
                RgbColor(255, 134, 24),
                RgbColor(215, 38, 0),
                RgbColor(255, 178, 56)
            ),
            curve = TransitionCurve.CANDLE_FLICKER,
            defaultSpeed = 0.55f,
            defaultSmoothness = 0.72f
        ),
        LightScene(
            id = "warm_candle",
            name = "Свеча",
            subtitle = "Мягкий янтарный свет для релакса",
            category = SceneCategory.AMBIENT,
            colors = listOf(
                RgbColor(255, 152, 42),
                RgbColor(255, 192, 96),
                RgbColor(232, 118, 24)
            ),
            curve = TransitionCurve.CANDLE_FLICKER,
            defaultSpeed = 0.35f,
            defaultSmoothness = 0.85f
        ),
        LightScene(
            id = "deep_nebula",
            name = "Туманность",
            subtitle = "Космический индиго и ультрамарин",
            category = SceneCategory.AMBIENT,
            colors = listOf(
                RgbColor(48, 24, 140),
                RgbColor(122, 40, 210),
                RgbColor(18, 92, 185),
                RgbColor(195, 44, 158)
            ),
            curve = TransitionCurve.BREATHING,
            defaultSpeed = 0.28f,
            defaultSmoothness = 0.94f
        ),
        LightScene(
            id = "forest_mist",
            name = "Ночной лес",
            subtitle = "Спокойный хвойный и мятный полусвет",
            category = SceneCategory.AMBIENT,
            colors = listOf(
                RgbColor(14, 158, 98),
                RgbColor(64, 224, 148),
                RgbColor(12, 110, 92)
            ),
            curve = TransitionCurve.BREATHING,
            defaultSpeed = 0.26f,
            defaultSmoothness = 0.92f
        ),
        LightScene(
            id = "thunder_storm",
            name = "Ночная гроза",
            subtitle = "Глубокое небо с мягкими вспышками молний",
            category = SceneCategory.AMBIENT,
            colors = listOf(
                RgbColor(24, 42, 115),
                RgbColor(64, 98, 210),
                RgbColor(225, 242, 255)
            ),
            curve = TransitionCurve.LIGHTNING_STORM,
            defaultSpeed = 0.50f,
            defaultSmoothness = 0.60f
        ),
        LightScene(
            id = "sunrise_wake",
            name = "Рассвет",
            subtitle = "Пробуждение от багрянца до золотого дня",
            category = SceneCategory.AMBIENT,
            colors = listOf(
                RgbColor(168, 36, 72),
                RgbColor(255, 112, 48),
                RgbColor(255, 206, 108),
                RgbColor(255, 244, 214)
            ),
            curve = TransitionCurve.SILKY_SINE,
            defaultSpeed = 0.22f,
            defaultSmoothness = 0.95f
        ),

        // =====================================================================
        // ДИНАМИКА / ВЕЧЕРИНКА (PARTY)
        // =====================================================================
        LightScene(
            id = "synthwave_drive",
            name = "Синтвейв 80-х",
            subtitle = "Ретро-неон: розовый, пурпур и бирюза",
            category = SceneCategory.PARTY,
            colors = listOf(
                RgbColor(255, 20, 147),
                RgbColor(138, 43, 226),
                RgbColor(0, 245, 255),
                RgbColor(255, 94, 0)
            ),
            curve = TransitionCurve.ENERGY_PULSE,
            defaultSpeed = 0.62f,
            defaultSmoothness = 0.65f
        ),
        LightScene(
            id = "club_pulse",
            name = "Клубный пульс",
            subtitle = "Энергичные неоновые импульсы",
            category = SceneCategory.PARTY,
            colors = listOf(
                RgbColor(255, 0, 102),
                RgbColor(0, 255, 213),
                RgbColor(153, 0, 255),
                RgbColor(255, 217, 0)
            ),
            curve = TransitionCurve.ENERGY_PULSE,
            defaultSpeed = 0.72f,
            defaultSmoothness = 0.45f
        ),
        LightScene(
            id = "matrix_flow",
            name = "Матрица",
            subtitle = "Бегущие импульсы неоново-зелёного кода",
            category = SceneCategory.PARTY,
            colors = listOf(
                RgbColor(0, 255, 102),
                RgbColor(0, 168, 68),
                RgbColor(145, 255, 178)
            ),
            curve = TransitionCurve.COMET_TRAIL,
            defaultSpeed = 0.60f,
            defaultSmoothness = 0.65f
        ),
        LightScene(
            id = "neon_jump",
            name = "Неоновый ритм",
            subtitle = "Чёткая смена ярких оттенков",
            category = SceneCategory.PARTY,
            colors = listOf(
                RgbColor(0, 229, 255),
                RgbColor(255, 46, 147),
                RgbColor(139, 92, 246),
                RgbColor(34, 229, 139),
                RgbColor(255, 196, 0)
            ),
            curve = TransitionCurve.COLOR_JUMP,
            defaultSpeed = 0.55f,
            defaultSmoothness = 0.25f
        ),
        LightScene(
            id = "siren_beacon",
            name = "Полицейский маяк",
            subtitle = "Бегущие алый и ярко-синий сегменты",
            category = SceneCategory.PARTY,
            colors = listOf(
                RgbColor(255, 16, 40),
                RgbColor(16, 84, 255)
            ),
            curve = TransitionCurve.RUNNING_ZONES,
            defaultSpeed = 0.78f,
            defaultSmoothness = 0.22f,
            defaultZoneMeters = 0.75f
        )
    )
}

/**
 * High-resolution 36-segment silky smooth neon strip engine.
 * Supports meter-based running zones, interrupted chases, comet trails, laser scanners,
 * colliding flows, velvet red heartbeats, static transitions, and real-time audio reactivity.
 */
class SmoothEffectEngine {

    private var phase: Float = 0f
    private var musicHuePhase: Float = 195f
    private var smoothedColor: RgbColor = RgbColor.Crimson
    private var smoothedBrightnessFactor: Float = 1f
    private var smoothedAudioEnergy: Float = 0f

    fun evaluateSceneFrame(
        scene: LightScene,
        dtSec: Float,
        speed: Float,
        smoothness: Float,
        brightness: Float,
        zoneMeters: Float = scene.defaultZoneMeters,
        direction: FlowDirection = FlowDirection.FORWARD,
        segmentCount: Int = 36
    ): Pair<RgbColor, List<RgbColor>> {
        val colors = scene.colors.ifEmpty { listOf(RgbColor.Crimson, RgbColor.Cyan) }
        val cycleSpeed = 0.14f + speed.coerceIn(0.05f, 1f).pow(1.42f) * 2.15f
        phase = (phase + dtSec * cycleSpeed) % 10000f

        // Assume a standard 5-meter neon strip represented by segmentCount points.
        val stripMeters = 5.0f
        val safeZoneMeters = zoneMeters.coerceIn(0.35f, 2.5f)
        val zonesAcrossStrip = stripMeters / safeZoneMeters

        val segments = List(segmentCount) { idx ->
            val uRaw = idx.toFloat() / (segmentCount - 1).coerceAtLeast(1).toFloat()
            val uDirected = when (direction) {
                FlowDirection.FORWARD -> uRaw
                FlowDirection.REVERSE -> 1f - uRaw
                FlowDirection.MIRROR_OUT -> abs(uRaw * 2f - 1f)
                FlowDirection.MIRROR_IN -> 1f - abs(uRaw * 2f - 1f)
            }

            val rawSegmentColor = evaluateSpatialSample(
                colors = colors,
                u = uDirected,
                phase = phase,
                zonesAcrossStrip = zonesAcrossStrip,
                curve = scene.curve,
                smoothness = smoothness
            )
            rawSegmentColor.scaled(brightness)
        }

        // Primary representative color for single-zone RGB controllers (smooth center-weighted readout)
        val representativeRaw = evaluateSpatialSample(
            colors = colors,
            u = 0.5f,
            phase = phase,
            zonesAcrossStrip = zonesAcrossStrip,
            curve = scene.curve,
            smoothness = smoothness
        )
        val alpha = computeSmoothingAlpha(dtSec, smoothness)
        smoothedColor = RgbColor.lerp(smoothedColor, representativeRaw, alpha)
        val finalPrimary = smoothedColor.scaled(brightness)
        return finalPrimary to segments
    }

    fun evaluateStaticSmoothFrame(
        targetColor: RgbColor,
        targetPowerMultiplier: Float,
        dtSec: Float,
        smoothness: Float,
        brightness: Float,
        segmentCount: Int = 36
    ): Pair<RgbColor, List<RgbColor>> {
        val alpha = computeSmoothingAlpha(dtSec, smoothness)
        smoothedColor = RgbColor.lerp(smoothedColor, targetColor, alpha)
        val powerAlpha = computeSmoothingAlpha(dtSec, (smoothness * 0.85f).coerceAtLeast(0.2f))
        smoothedBrightnessFactor += (targetPowerMultiplier - smoothedBrightnessFactor) * powerAlpha
        val effectiveBri = (brightness * smoothedBrightnessFactor).coerceIn(0f, 1f)
        val finalColor = smoothedColor.scaled(effectiveBri)
        return finalColor to List(segmentCount) { finalColor }
    }

    fun evaluateMusicFrame(
        audio: AudioFrame,
        mode: MusicReactiveMode,
        palette: List<RgbColor>,
        dtSec: Float,
        sensitivity: Float,
        decaySmoothness: Float,
        bassBoost: Float,
        masterBrightness: Float,
        segmentCount: Int = 36
    ): Pair<RgbColor, List<RgbColor>> {
        val safePalette = palette.ifEmpty { listOf(RgbColor.Crimson, RgbColor.Cyan, RgbColor.Violet) }
        val gain = 0.6f + sensitivity * 2.2f
        val bassWeight = 0.5f + bassBoost * 1.4f
        val rawEnergy = ((audio.bass * bassWeight + audio.mid * 0.65f + audio.treble * 0.35f) * gain)
            .coerceIn(0f, 1f)

        val attackRate = (1f - decaySmoothness * 0.45f).coerceIn(0.25f, 0.95f)
        val decayRate = (0.04f + (1f - decaySmoothness) * 0.35f).coerceIn(0.03f, 0.5f)
        smoothedAudioEnergy = if (rawEnergy > smoothedAudioEnergy) {
            smoothedAudioEnergy + (rawEnergy - smoothedAudioEnergy) * attackRate
        } else {
            smoothedAudioEnergy + (rawEnergy - smoothedAudioEnergy) * decayRate
        }

        val beatPush = if (audio.isBeat) (0.35f + bassBoost * 0.45f) else 0f
        musicHuePhase = (musicHuePhase + dtSec * (16f + smoothedAudioEnergy * 78f) + beatPush * 28f) % 360f

        val targetPrimary: RgbColor
        val targetBri: Float

        when (mode) {
            MusicReactiveMode.BASS_PULSE -> {
                val baseColor = samplePaletteSmooth(safePalette, musicHuePhase / 360f)
                val pulseBri = (0.14f + smoothedAudioEnergy * 0.86f).coerceIn(0.08f, 1f)
                targetPrimary = baseColor
                targetBri = pulseBri * masterBrightness
            }
            MusicReactiveMode.SPECTRUM_FLOW -> {
                val spectralRatio = if (audio.bass + audio.mid + audio.treble > 0.01f) {
                    (audio.mid * 0.5f + audio.treble * 1.0f) / (audio.bass + audio.mid + audio.treble)
                } else 0.5f
                val idxFloat = ((spectralRatio + musicHuePhase / 720f) % 1f)
                targetPrimary = samplePaletteSmooth(safePalette, idxFloat)
                targetBri = (0.22f + smoothedAudioEnergy * 0.78f).coerceIn(0.15f, 1f) * masterBrightness
            }
            MusicReactiveMode.BEAT_SHIFT -> {
                if (audio.isBeat) musicHuePhase = (musicHuePhase + 45f) % 360f
                targetPrimary = samplePaletteSmooth(safePalette, musicHuePhase / 360f)
                targetBri = (0.28f + smoothedAudioEnergy * 0.72f).coerceIn(0.2f, 1f) * masterBrightness
            }
            MusicReactiveMode.CINEMA_AMBIENT -> {
                targetPrimary = samplePaletteSmooth(safePalette, (musicHuePhase / 360f) * 0.4f)
                val gentleBri = (0.38f + smoothedAudioEnergy * 0.48f).coerceIn(0.32f, 0.92f)
                targetBri = gentleBri * masterBrightness
            }
        }

        val colorSmoothAlpha = computeSmoothingAlpha(dtSec, decaySmoothness)
        smoothedColor = RgbColor.lerp(smoothedColor, targetPrimary, colorSmoothAlpha)
        val primaryScaled = smoothedColor.scaled(targetBri)

        val segments = List(segmentCount) { idx ->
            val centerMirror = abs((idx.toFloat() / (segmentCount - 1).coerceAtLeast(1)) * 2f - 1f)
            val barIdx = (centerMirror * (audio.bars.size - 1)).toInt().coerceIn(0, audio.bars.lastIndex)
            val barLevel = (audio.bars.getOrElse(barIdx) { smoothedAudioEnergy } * gain).coerceIn(0f, 1f)
            val segColor = samplePaletteSmooth(
                safePalette,
                ((musicHuePhase / 360f) + centerMirror * 0.42f) % 1f
            )
            val segBri = (0.16f + (barLevel * 0.68f + smoothedAudioEnergy * 0.32f) * 0.84f)
                .coerceIn(0.10f, 1f) * masterBrightness
            RgbColor.lerp(primaryScaled, segColor.scaled(segBri), 0.62f)
        }

        return primaryScaled to segments
    }

    private fun evaluateSpatialSample(
        colors: List<RgbColor>,
        u: Float,
        phase: Float,
        zonesAcrossStrip: Float,
        curve: TransitionCurve,
        smoothness: Float
    ): RgbColor {
        val count = colors.size
        if (count == 1) return colors.first()

        return when (curve) {
            // Meter-sized distinct color zones circulating around the strip with configurable feathered borders
            TransitionCurve.RUNNING_ZONES -> {
                val pos = positiveMod(u * zonesAcrossStrip - phase * 0.75f, count.toFloat())
                val idx = floor(pos).toInt().coerceIn(0, count - 1)
                val nextIdx = (idx + 1) % count
                val frac = pos - idx
                // smoothness controls edge feathering: 0.1 = crisp 1m blocks, 0.95 = silky velvet blend
                val feather = (0.06f + smoothness * 0.44f).coerceIn(0.06f, 0.50f)
                val blend = when {
                    frac < 1f - feather -> 0f
                    else -> {
                        val local = ((frac - (1f - feather)) / feather).coerceIn(0f, 1f)
                        (1f - cos(local * PI.toFloat())) * 0.5f
                    }
                }
                RgbColor.lerp(colors[idx], colors[nextIdx], blend)
            }

            // Interrupted running neon dashes with dark gaps gliding continuously
            TransitionCurve.SEGMENT_CHASE -> {
                val pos = positiveMod(u * zonesAcrossStrip * 1.15f - phase * 0.95f, count.toFloat())
                val idx = floor(pos).toInt().coerceIn(0, count - 1)
                val frac = pos - idx
                // Each zone is ~62% lit dash + ~38% dark gap with silky envelope
                val litWidth = 0.54f + smoothness * 0.14f
                val envelope = when {
                    frac < litWidth -> {
                        val norm = frac / litWidth
                        sin(norm * PI.toFloat()).pow(0.55f + (1f - smoothness) * 0.6f)
                    }
                    else -> 0.04f // subtle ambient dark floor so neon tube stays rich
                }
                colors[idx].scaled(envelope.coerceIn(0.04f, 1f))
            }

            // Glowing meteor head with exponential smooth fading tail
            TransitionCurve.COMET_TRAIL -> {
                val headPos = positiveMod(phase * 0.42f, 1f)
                val distBehind = positiveMod(headPos - u, 1f)
                val tailLength = (0.28f + smoothness * 0.42f).coerceIn(0.22f, 0.75f)
                val colorIdx = floor(phase * 0.42f).toInt().mod(count)
                val headColor = colors.first()
                val bodyColor = colors[colorIdx]
                val tailColor = colors.last()

                if (distBehind < 0.06f) {
                    val coreT = (1f - distBehind / 0.06f).coerceIn(0f, 1f)
                    RgbColor.lerp(bodyColor, headColor, coreT)
                } else if (distBehind < tailLength) {
                    val decayT = (distBehind - 0.06f) / (tailLength - 0.06f)
                    val glow = exp((-decayT * 3.1f).toDouble()).toFloat()
                    RgbColor.lerp(bodyColor, tailColor, decayT).scaled(glow.coerceIn(0.05f, 1f))
                } else {
                    tailColor.scaled(0.05f)
                }
            }

            // Precision laser scanner bouncing smoothly left-to-right-to-left
            TransitionCurve.SCANNER_BOUNCE -> {
                val centerPos = (sin(phase * 1.65f) + 1f) * 0.5f
                val dist = abs(u - centerPos)
                val beamWidth = (0.09f + smoothness * 0.16f).coerceIn(0.08f, 0.28f)
                val intensity = exp((-(dist / beamWidth).pow(2f) * 2.2f).toDouble()).toFloat()
                val base = RgbColor.lerp(colors.last(), colors.first(), intensity)
                base.scaled((0.06f + intensity * 0.94f).coerceIn(0.06f, 1f))
            }

            // Two opposite waves flying from left & right and blooming smoothly where they cross
            TransitionCurve.DUAL_COLLIDE -> {
                val wavePos = positiveMod(phase * 0.48f, 1f)
                val dLeft = abs(u - wavePos)
                val dRight = abs(u - (1f - wavePos))
                val width = (0.14f + smoothness * 0.16f).coerceIn(0.12f, 0.34f)
                val iLeft = exp((-(dLeft / width).pow(2f) * 2.4f).toDouble()).toFloat()
                val iRight = exp((-(dRight / width).pow(2f) * 2.4f).toDouble()).toFloat()
                val cLeft = colors.first().scaled(iLeft)
                val cRight = colors[1 % count].scaled(iRight)
                val overlap = (iLeft * iRight * 1.8f).coerceIn(0f, 1f)
                val bloom = colors.last().scaled(overlap)
                RgbColor(
                    r = (cLeft.r + cRight.r + bloom.r).coerceIn(10, 255),
                    g = (cLeft.g + cRight.g + bloom.g).coerceIn(4, 255),
                    b = (cLeft.b + cRight.b + bloom.b).coerceIn(8, 255)
                )
            }

            // Realistic double-peak heartbeat (lub-dub) with gentle wave propagation
            TransitionCurve.HEARTBEAT_PULSE -> {
                val cycle = positiveMod(phase * 0.65f - u * 0.25f, 1f)
                val beat1 = exp((-((cycle - 0.16f) / 0.065f).pow(2f)).toDouble()).toFloat()
                val beat2 = exp((-((cycle - 0.34f) / 0.085f).pow(2f)).toDouble()).toFloat() * 0.78f
                val pulse = (0.14f + (beat1 + beat2).coerceIn(0f, 1f) * 0.86f).coerceIn(0.12f, 1f)
                val tint = samplePaletteSmooth(colors, positiveMod(phase * 0.12f + u * 0.3f, 1f))
                tint.scaled(pulse)
            }

            TransitionCurve.SILKY_SINE -> {
                val t = positiveMod(phase * 0.45f + u * zonesAcrossStrip * 0.35f, count.toFloat())
                val idx = floor(t).toInt().coerceIn(0, count - 1)
                val nextIdx = (idx + 1) % count
                val frac = t - idx
                val eased = (1f - cos(frac * PI.toFloat())) * 0.5f
                RgbColor.lerp(colors[idx], colors[nextIdx], eased)
            }

            TransitionCurve.LINEAR_GLIDE -> {
                val t = positiveMod(phase * 0.5f + u * zonesAcrossStrip * 0.4f, count.toFloat())
                val idx = floor(t).toInt().coerceIn(0, count - 1)
                val nextIdx = (idx + 1) % count
                RgbColor.lerp(colors[idx], colors[nextIdx], t - idx)
            }

            TransitionCurve.BREATHING -> {
                val t = positiveMod(phase * 0.35f + u * 0.35f, count.toFloat())
                val idx = floor(t).toInt().coerceIn(0, count - 1)
                val nextIdx = (idx + 1) % count
                val eased = (1f - cos((t - idx) * PI.toFloat())) * 0.5f
                val base = RgbColor.lerp(colors[idx], colors[nextIdx], eased)
                val breath = 0.24f + 0.76f * ((sin(phase * PI.toFloat() * 1.15f - u * 1.1f) + 1f) * 0.5f)
                base.scaled(breath)
            }

            TransitionCurve.ENERGY_PULSE -> {
                val t = positiveMod(phase * 0.65f + u * zonesAcrossStrip * 0.4f, count.toFloat())
                val idx = floor(t).toInt().coerceIn(0, count - 1)
                val nextIdx = (idx + 1) % count
                val frac = t - idx
                val sharpFrac = if (smoothness > 0.5f) {
                    (1f - cos(frac * PI.toFloat())) * 0.5f
                } else frac.pow(2f)
                val base = RgbColor.lerp(colors[idx], colors[nextIdx], sharpFrac)
                val pulse = 0.42f + 0.58f * abs(sin(phase * PI.toFloat() * 1.8f + u * 2.2f))
                base.scaled(pulse)
            }

            TransitionCurve.CANDLE_FLICKER -> {
                val t = positiveMod(phase * 0.45f + u * 0.8f, count.toFloat())
                val idx = floor(t).toInt().coerceIn(0, count - 1)
                val nextIdx = (idx + 1) % count
                val eased = (1f - cos((t - idx) * PI.toFloat())) * 0.5f
                val base = RgbColor.lerp(colors[idx], colors[nextIdx], eased)
                val wave1 = sin(phase * 6.8f + u * 5.2f) * 0.15f
                val wave2 = sin(phase * 15.3f - u * 9.1f) * 0.09f
                val wave3 = cos(phase * 3.4f + u * 2.5f) * 0.10f
                val flicker = (0.74f + wave1 + wave2 + wave3).coerceIn(0.32f, 1f)
                base.scaled(flicker)
            }

            TransitionCurve.LIGHTNING_STORM -> {
                val sky = RgbColor.lerp(
                    colors.first(),
                    colors[minOf(1, count - 1)],
                    (sin(phase * 1.4f + u * 2f) + 1f) * 0.5f
                )
                val flashWave = sin(phase * 4.7f + u * 1.6f) * sin(phase * 11.3f) * sin(phase * 2.1f)
                if (flashWave > 0.68f) {
                    val intensity = ((flashWave - 0.68f) / 0.32f).coerceIn(0f, 1f)
                    RgbColor.lerp(sky, colors.last(), intensity)
                } else {
                    sky.scaled(0.42f)
                }
            }

            TransitionCurve.COLOR_JUMP -> {
                val t = positiveMod(phase * 0.6f, count.toFloat())
                val idx = floor(t).toInt().coerceIn(0, count - 1)
                val nextIdx = (idx + 1) % count
                val frac = t - idx
                val blendZone = (smoothness * 0.38f).coerceIn(0.02f, 0.38f)
                if (frac > 1f - blendZone) {
                    val localT = (frac - (1f - blendZone)) / blendZone
                    RgbColor.lerp(colors[idx], colors[nextIdx], localT)
                } else {
                    colors[idx]
                }
            }
        }
    }

    private fun samplePaletteSmooth(colors: List<RgbColor>, normalizedPos: Float): RgbColor {
        if (colors.size == 1) return colors.first()
        val pos = positiveMod(normalizedPos, 1f) * colors.size
        val idx = floor(pos).toInt().coerceIn(0, colors.lastIndex)
        val next = (idx + 1) % colors.size
        val frac = pos - idx
        val eased = (1f - cos(frac * PI.toFloat())) * 0.5f
        return RgbColor.lerp(colors[idx], colors[next], eased)
    }

    private fun positiveMod(value: Float, mod: Float): Float {
        val r = value % mod
        return if (r < 0f) r + mod else r
    }

    private fun computeSmoothingAlpha(dtSec: Float, smoothness: Float): Float {
        val timeConstant = 0.02f + smoothness.coerceIn(0f, 1f).pow(1.8f) * 0.42f
        return (1f - exp((-dtSec / timeConstant).toDouble()).toFloat()).coerceIn(0.03f, 1f)
    }
}
