package com.example.hardware

import com.example.model.PinOrder
import com.example.model.RgbColor
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Real wire-level command packet builders for Wi-Fi and Bluetooth LE LED controllers:
 * 1. Lotus Lantern V6 / ELK-BLEDOM / MELK-OA/OC (7E 07 05 03 R G B 10 EF / 7E 04 04 F0 00 01 FF 00 EF)
 * 2. Lotus / ELK-BLEDOM Legacy (7E 00 05 03 R G B 00 EF / 7E 00 04 01 00 00 00 00 EF)
 * 3. LotusLanternX / BJ_LED RGBIC (BC 01 01 01 55 / BC 04 06 H_hi H_lo S_hi S_lo 00 00 55)
 * 4. Triones / HappyLighting (56 R G B 00 F0 AA / CC 23 33)
 * 5. Magic Home / Flux / Zengge Wi-Fi TCP 5577 & BLE (31 R G B W 00 0F <cs> / 71 23 0F A3)
 * 6. WLED HTTP JSON + UDP DRGB realtime frames
 */
object LedProtocolPackets {

    // -------------------------------------------------------------------------
    // 1. Lotus Lantern V6 / ELK-BLEDOM / MELK-OA (wl.smartled modern protocol)
    // -------------------------------------------------------------------------

    fun lotusV6Power(on: Boolean): ByteArray = if (on) {
        byteArrayOf(
            0x7E, 0x04, 0x04, 0xF0.toByte(),
            0x00, 0x01, 0xFF.toByte(), 0x00,
            0xEF.toByte()
        )
    } else {
        byteArrayOf(
            0x7E, 0x04, 0x04, 0x00,
            0x00, 0x00, 0xFF.toByte(), 0x00,
            0xEF.toByte()
        )
    }

    fun lotusV6Color(color: RgbColor, pinOrder: PinOrder): ByteArray {
        val mapped = color.applyPinOrder(pinOrder)
        return byteArrayOf(
            0x7E, 0x07, 0x05, 0x03,
            mapped.r.coerceIn(0, 255).toByte(),
            mapped.g.coerceIn(0, 255).toByte(),
            mapped.b.coerceIn(0, 255).toByte(),
            0x10,
            0xEF.toByte()
        )
    }

    fun lotusV6Brightness(percent: Int): ByteArray = byteArrayOf(
        0x7E, 0x04, 0x01,
        percent.coerceIn(0, 100).toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00,
        0xEF.toByte()
    )

    // -------------------------------------------------------------------------
    // 2. Lotus / ELK-BLEDOM Legacy 9-byte protocol (7E 00 .. 00 EF)
    // -------------------------------------------------------------------------

    fun lotusPower(on: Boolean): ByteArray = byteArrayOf(
        0x7E, 0x00, 0x04,
        if (on) 0x01 else 0x00,
        0x00, 0x00, 0x00, 0x00,
        0xEF.toByte()
    )

    fun lotusColor(color: RgbColor, pinOrder: PinOrder): ByteArray {
        val mapped = color.applyPinOrder(pinOrder)
        return byteArrayOf(
            0x7E, 0x00, 0x05, 0x03,
            mapped.r.coerceIn(0, 255).toByte(),
            mapped.g.coerceIn(0, 255).toByte(),
            mapped.b.coerceIn(0, 255).toByte(),
            0x00,
            0xEF.toByte()
        )
    }

    fun lotusBrightness(percent: Int): ByteArray = byteArrayOf(
        0x7E, 0x00, 0x01,
        percent.coerceIn(0, 100).toByte(),
        0x00, 0x00, 0x00, 0x00,
        0xEF.toByte()
    )

    fun lotusBuiltInMode(modeCode: Int): ByteArray = byteArrayOf(
        0x7E, 0x05, 0x03,
        modeCode.coerceIn(0x80, 0x9F).toByte(),
        0x03, 0xFF.toByte(), 0xFF.toByte(), 0x00,
        0xEF.toByte()
    )

    fun lotusSpeed(speedPercent: Int): ByteArray = byteArrayOf(
        0x7E, 0x04, 0x02,
        speedPercent.coerceIn(0, 100).toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00,
        0xEF.toByte()
    )

    fun lotusPinOrder(pinOrder: PinOrder): ByteArray = byteArrayOf(
        0x7E, 0x00, 0x08,
        pinOrder.lotusWireCode.toByte(),
        0x00, 0x00, 0x00, 0x00,
        0xEF.toByte()
    )

    fun lotusExternalMic(enabled: Boolean, sensitivityPercent: Int): ByteArray = byteArrayOf(
        0x7E, 0x00, 0x06,
        if (enabled) 0x01 else 0x00,
        sensitivityPercent.coerceIn(0, 100).toByte(),
        0x00, 0x00, 0x00,
        0xEF.toByte()
    )

    // -------------------------------------------------------------------------
    // 3. LotusLanternX / BJ_LED RGBIC protocol (BC .. 55)
    // -------------------------------------------------------------------------

    fun lotusXPower(on: Boolean): ByteArray = byteArrayOf(
        0xBC.toByte(), 0x01, 0x01,
        if (on) 0x01 else 0x00,
        0x55
    )

    fun lotusXColor(color: RgbColor, pinOrder: PinOrder): ByteArray {
        val mapped = color.applyPinOrder(pinOrder)
        val (h, s, _) = RgbColor.toHsv(mapped)
        val hueInt = h.roundToInt().coerceIn(0, 360)
        val satInt = (s * 1000f).roundToInt().coerceIn(0, 1000)
        return byteArrayOf(
            0xBC.toByte(), 0x04, 0x06,
            ((hueInt shr 8) and 0xFF).toByte(),
            (hueInt and 0xFF).toByte(),
            ((satInt shr 8) and 0xFF).toByte(),
            (satInt and 0xFF).toByte(),
            0x00, 0x00,
            0x55
        )
    }

    fun lotusXBrightness(percent: Int): ByteArray {
        val bri = (percent.coerceIn(0, 100) * 10).coerceIn(0, 1000)
        return byteArrayOf(
            0xBC.toByte(), 0x05, 0x06,
            ((bri shr 8) and 0xFF).toByte(),
            (bri and 0xFF).toByte(),
            0x00, 0x00, 0x00, 0x00,
            0x55
        )
    }

    // -------------------------------------------------------------------------
    // 4. Triones / HappyLighting BLE protocol (GATT 0xFFD9 / 0xFFE9)
    // -------------------------------------------------------------------------

    fun trionesPower(on: Boolean): ByteArray = byteArrayOf(
        0xCC.toByte(),
        if (on) 0x23 else 0x24,
        0x33
    )

    fun trionesColor(color: RgbColor, pinOrder: PinOrder): ByteArray {
        val mapped = color.applyPinOrder(pinOrder)
        return byteArrayOf(
            0x56,
            mapped.r.coerceIn(0, 255).toByte(),
            mapped.g.coerceIn(0, 255).toByte(),
            mapped.b.coerceIn(0, 255).toByte(),
            0x00,
            0xF0.toByte(),
            0xAA.toByte()
        )
    }

    fun trionesBuiltInMode(modeCode: Int, speedPercent: Int): ByteArray {
        val wireSpeed = (31 - (speedPercent.coerceIn(0, 100) * 30 / 100)).coerceIn(1, 31)
        return byteArrayOf(
            0xBB.toByte(),
            modeCode.coerceIn(0x25, 0x38).toByte(),
            wireSpeed.toByte(),
            0x44
        )
    }

    // -------------------------------------------------------------------------
    // 5. Magic Home / Flux Wi-Fi (TCP 5577) & Magic BLE (31..0F)
    // -------------------------------------------------------------------------

    fun magicHomePower(on: Boolean): ByteArray {
        val state = if (on) 0x23 else 0x24
        return appendChecksum(byteArrayOf(0x71, state.toByte(), 0x0F))
    }

    fun magicHomeColor(color: RgbColor, pinOrder: PinOrder): ByteArray {
        val mapped = color.applyPinOrder(pinOrder)
        return appendChecksum(
            byteArrayOf(
                0x31,
                mapped.r.coerceIn(0, 255).toByte(),
                mapped.g.coerceIn(0, 255).toByte(),
                mapped.b.coerceIn(0, 255).toByte(),
                mapped.w.coerceIn(0, 255).toByte(),
                0x00,
                0x0F
            )
        )
    }

    fun magicHomeBuiltInMode(modeCode: Int, speedPercent: Int): ByteArray {
        val wireSpeed = (31 - (speedPercent.coerceIn(0, 100) * 30 / 100)).coerceIn(1, 31)
        return appendChecksum(
            byteArrayOf(
                0x61,
                modeCode.coerceIn(0x25, 0x38).toByte(),
                wireSpeed.toByte(),
                0x0F
            )
        )
    }

    // -------------------------------------------------------------------------
    // 6. WLED HTTP JSON API & UDP DRGB Realtime Stream (port 21324)
    // -------------------------------------------------------------------------

    fun wledStateJson(
        on: Boolean,
        color: RgbColor,
        pinOrder: PinOrder,
        brightness: Int,
        transitionDeciseconds: Int
    ): String {
        val mapped = color.applyPinOrder(pinOrder)
        val colArray = JSONArray().apply {
            put(JSONArray().apply {
                put(mapped.r.coerceIn(0, 255))
                put(mapped.g.coerceIn(0, 255))
                put(mapped.b.coerceIn(0, 255))
            })
        }
        val seg = JSONObject().apply {
            put("id", 0)
            put("fx", 0)
            put("col", colArray)
        }
        return JSONObject().apply {
            put("on", on)
            put("bri", brightness.coerceIn(1, 255))
            put("transition", transitionDeciseconds.coerceIn(0, 50))
            put("seg", JSONArray().put(seg))
        }.toString()
    }

    fun wledDrgbFrame(segments: List<RgbColor>, pinOrder: PinOrder, timeoutSec: Int = 2): ByteArray {
        val result = ByteArray(2 + segments.size * 3)
        result[0] = 0x02
        result[1] = timeoutSec.coerceIn(1, 10).toByte()
        segments.forEachIndexed { index, raw ->
            val mapped = raw.applyPinOrder(pinOrder)
            val offset = 2 + index * 3
            result[offset] = mapped.r.coerceIn(0, 255).toByte()
            result[offset + 1] = mapped.g.coerceIn(0, 255).toByte()
            result[offset + 2] = mapped.b.coerceIn(0, 255).toByte()
        }
        return result
    }

    private fun appendChecksum(payload: ByteArray): ByteArray {
        var sum = 0
        for (b in payload) {
            sum += (b.toInt() and 0xFF)
        }
        return payload + (sum and 0xFF).toByte()
    }
}
