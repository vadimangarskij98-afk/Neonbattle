package com.example.model

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class RgbColor(
    val r: Int,
    val g: Int,
    val b: Int,
    val w: Int = 0
) {
    fun toComposeColor(): Color = Color(
        red = r.coerceIn(0, 255) / 255f,
        green = g.coerceIn(0, 255) / 255f,
        blue = b.coerceIn(0, 255) / 255f,
        alpha = 1f
    )

    fun scaled(brightness: Float): RgbColor {
        val factor = brightness.coerceIn(0f, 1f)
        return RgbColor(
            r = (r * factor).roundToInt().coerceIn(0, 255),
            g = (g * factor).roundToInt().coerceIn(0, 255),
            b = (b * factor).roundToInt().coerceIn(0, 255),
            w = (w * factor).roundToInt().coerceIn(0, 255)
        )
    }

    fun applyPinOrder(order: PinOrder): RgbColor = when (order) {
        PinOrder.RGB -> RgbColor(r, g, b, w)
        PinOrder.RBG -> RgbColor(r, b, g, w)
        PinOrder.GRB -> RgbColor(g, r, b, w)
        PinOrder.GBR -> RgbColor(g, b, r, w)
        PinOrder.BRG -> RgbColor(b, r, g, w)
        PinOrder.BGR -> RgbColor(b, g, r, w)
    }

    fun toHexString(): String = "#%02X%02X%02X".format(
        r.coerceIn(0, 255),
        g.coerceIn(0, 255),
        b.coerceIn(0, 255)
    )

    companion object {
        val Black = RgbColor(0, 0, 0)
        val White = RgbColor(255, 255, 255, 255)
        val Cyan = RgbColor(0, 229, 255)
        val Magenta = RgbColor(255, 46, 147)
        val Violet = RgbColor(139, 92, 246)
        val Crimson = RgbColor(255, 18, 48)

        fun fromHex(hex: String): RgbColor {
            val cleaned = hex.removePrefix("#").trim()
            if (cleaned.length != 6) return Cyan
            return runCatching {
                RgbColor(
                    r = cleaned.substring(0, 2).toInt(16),
                    g = cleaned.substring(2, 4).toInt(16),
                    b = cleaned.substring(4, 6).toInt(16)
                )
            }.getOrDefault(Cyan)
        }

        fun fromHsv(hue: Float, saturation: Float, value: Float = 1f): RgbColor {
            val h = ((hue % 360f) + 360f) % 360f
            val s = saturation.coerceIn(0f, 1f)
            val v = value.coerceIn(0f, 1f)
            val c = v * s
            val x = c * (1f - abs((h / 60f) % 2f - 1f))
            val m = v - c
            val (r1, g1, b1) = when {
                h < 60f -> Triple(c, x, 0f)
                h < 120f -> Triple(x, c, 0f)
                h < 180f -> Triple(0f, c, x)
                h < 240f -> Triple(0f, x, c)
                h < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return RgbColor(
                r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255),
                g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255),
                b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
            )
        }

        fun toHsv(color: RgbColor): Triple<Float, Float, Float> {
            val r = color.r / 255f
            val g = color.g / 255f
            val b = color.b / 255f
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val delta = maxC - minC
            val hue = when {
                delta < 0.0001f -> 0f
                maxC == r -> 60f * (((g - b) / delta) % 6f)
                maxC == g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }.let { if (it < 0f) it + 360f else it }
            val sat = if (maxC < 0.0001f) 0f else delta / maxC
            return Triple(hue, sat, maxC)
        }

        fun fromKelvin(kelvin: Int): RgbColor {
            val temp = kelvin.coerceIn(2000, 7500) / 100.0
            val r = if (temp <= 66.0) {
                255.0
            } else {
                329.698727446 * (temp - 60.0).pow(-0.1332047592)
            }
            val g = if (temp <= 66.0) {
                99.4708025861 * ln(temp) - 161.1195681661
            } else {
                288.1221695283 * (temp - 60.0).pow(-0.0755148492)
            }
            val b = when {
                temp >= 66.0 -> 255.0
                temp <= 19.0 -> 0.0
                else -> 138.5177312231 * ln(temp - 10.0) - 305.0447927307
            }
            val warmWhite = ((7500 - kelvin.coerceIn(2000, 7500)) / 5500f * 255f).roundToInt()
            return RgbColor(
                r = r.roundToInt().coerceIn(0, 255),
                g = g.roundToInt().coerceIn(0, 255),
                b = b.roundToInt().coerceIn(0, 255),
                w = warmWhite.coerceIn(0, 255)
            )
        }

        fun lerp(start: RgbColor, end: RgbColor, fraction: Float): RgbColor {
            val t = fraction.coerceIn(0f, 1f)
            return RgbColor(
                r = (start.r + (end.r - start.r) * t).roundToInt().coerceIn(0, 255),
                g = (start.g + (end.g - start.g) * t).roundToInt().coerceIn(0, 255),
                b = (start.b + (end.b - start.b) * t).roundToInt().coerceIn(0, 255),
                w = (start.w + (end.w - start.w) * t).roundToInt().coerceIn(0, 255)
            )
        }
    }
}

enum class PinOrder(val label: String, val lotusWireCode: Int) {
    RGB("RGB", 0x01),
    RBG("RBG", 0x02),
    GRB("GRB", 0x03),
    GBR("GBR", 0x04),
    BRG("BRG", 0x05),
    BGR("BGR", 0x06)
}

enum class ControllerProtocol(
    val displayName: String,
    val description: String,
    val defaultPort: Int,
    val isWifi: Boolean
) {
    WIFI_MAGIC_HOME(
        displayName = "Wi-Fi Magic Home / Flux",
        description = "Стандартный Wi-Fi LED контроллер (TCP 5577)",
        defaultPort = 5577,
        isWifi = true
    ),
    WIFI_WLED(
        displayName = "Wi-Fi WLED (HTTP + UDP)",
        description = "ESP8266/ESP32 WLED с посегментным DRGB потоком",
        defaultPort = 80,
        isWifi = true
    ),
    WIFI_LOTUS_TCP(
        displayName = "Wi-Fi Lotus / ELK TCP",
        description = "Пакеты Lotus Lantern 7E..EF через Wi-Fi TCP",
        defaultPort = 5577,
        isWifi = true
    ),
    WIFI_LOTUS_UDP(
        displayName = "Wi-Fi Lotus / ELK UDP",
        description = "Быстрый поток 7E..EF через Wi-Fi UDP",
        defaultPort = 8899,
        isWifi = true
    ),
    BLE_LOTUS_ELK(
        displayName = "Bluetooth Lotus / ELK / Triones",
        description = "Прямое BLE управление без Wi-Fi",
        defaultPort = 0,
        isWifi = false
    )
}

/**
 * Exact Bluetooth LE packet dialects used by Lotus Lantern (wl.smartled),
 * ELK-BLEDOM, MELK-OA/OC, LotusLanternX (BJ_LED), Triones, and MagicBLE strips.
 */
enum class BleChipset(val shortName: String, val label: String) {
    LOTUS_ELK_V6("Lotus V6 / MELK", "Lotus Lantern V6 / ELK-BLEDOM (7E 07..10 EF)"),
    LOTUS_ELK_LEGACY("ELK Классика", "ELK-BLEDOM Классика (7E 00..00 EF)"),
    LOTUS_X_BC("LotusX / BJ_LED", "LotusLanternX / BJ_LED RGBIC (BC..55)"),
    TRIONES_HAPPY("Triones / Happy", "Triones / HappyLighting (56..AA)"),
    MAGIC_BLE_31("Magic BLE", "Magic Home / Zengge BLE (31..0F)")
}

data class LedDevice(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val protocol: ControllerProtocol,
    val macAddress: String = "",
    val isSaved: Boolean = false,
    val rssi: Int = -60,
    val isLikelyLed: Boolean = true,
    val serviceHint: String = ""
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val device: LedDevice) : ConnectionState
    data class Connected(
        val device: LedDevice,
        val chipsetInfo: String = "",
        val gattChannelInfo: String = ""
    ) : ConnectionState
    data class Error(val message: String, val device: LedDevice? = null) : ConnectionState
}

enum class MainTab {
    COLOR, SCENES, MUSIC, TIMER
}

enum class ColorControlSubMode {
    WHEEL, CCT, RGB
}

enum class ActiveControllerMode {
    STATIC_COLOR, CCT_WHITE, SCENE, MUSIC_MIC, MUSIC_PLAYER
}

enum class SceneCategory(val title: String) {
    ALL("Все"),
    RED_LUXURY("Красные"),
    FLOW_ZONES("Бегущие зоны"),
    SMOOTH("Плавные"),
    AMBIENT("Атмосфера"),
    PARTY("Динамика"),
    CUSTOM("Мои")
}

enum class FlowDirection(val title: String, val shortArrow: String) {
    FORWARD("Вправо", "→"),
    REVERSE("Влево", "←"),
    MIRROR_OUT("Из центра", "←•→"),
    MIRROR_IN("К центру", "→•←")
}

enum class TransitionCurve(val displayName: String) {
    RUNNING_ZONES("Бегущие метры"),
    SEGMENT_CHASE("Прерывистый бег"),
    COMET_TRAIL("Комета со шлейфом"),
    SCANNER_BOUNCE("Лазер-маятник"),
    DUAL_COLLIDE("Встречные потоки"),
    HEARTBEAT_PULSE("Сердцебиение"),
    SILKY_SINE("Шёлковый перелив"),
    BREATHING("Мягкое дыхание"),
    LINEAR_GLIDE("Ровная волна"),
    ENERGY_PULSE("Ритм-пульс"),
    CANDLE_FLICKER("Живой огонь"),
    LIGHTNING_STORM("Грозовые вспышки"),
    COLOR_JUMP("Чёткая смена")
}

data class LightScene(
    val id: String,
    val name: String,
    val subtitle: String,
    val category: SceneCategory,
    val colors: List<RgbColor>,
    val curve: TransitionCurve,
    val defaultSpeed: Float = 0.45f,
    val defaultSmoothness: Float = 0.85f,
    val defaultZoneMeters: Float = 1.0f,
    val hardwareModeCode: Int? = null,
    val isCustom: Boolean = false
)

enum class MusicReactiveMode(val title: String, val description: String) {
    BASS_PULSE("Бас-пульс", "Мощное свечение в такт ударным и басу"),
    SPECTRUM_FLOW("Спектр", "Оттенок зависит от частоты звучания"),
    BEAT_SHIFT("Смена на бит", "Плавный перелив с ускорением на ударах"),
    CINEMA_AMBIENT("Кино-эмбиент", "Очень мягкое дыхание без резких вспышек")
}

enum class MicSource(val title: String) {
    PHONE("Микрофон телефона"),
    EXTERNAL_STRIP("Микрофон ленты")
}

data class AudioFrame(
    val level: Float = 0f,
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val isBeat: Boolean = false,
    val bars: List<Float> = List(24) { 0f }
)

data class AudioTrackItem(
    val uriString: String,
    val title: String,
    val artist: String,
    val durationMs: Long
)

enum class ScheduleAction(val title: String) {
    TURN_ON("Включить цвет"),
    SUNRISE_FADE("Плавный рассвет"),
    TURN_OFF("Плавно выключить")
}

data class ScheduleRule(
    val id: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: Set<Int>, // 1=Mon .. 7=Sun
    val action: ScheduleAction,
    val colorHex: String = "#FF1230",
    val brightness: Float = 0.85f,
    val enabled: Boolean = true
)
