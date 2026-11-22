package com.example.hardware

import com.example.model.PinOrder
import com.example.model.RgbColor
import org.json.JSONArray
import org.json.JSONObject

/**
 * Binary and JSON command builders for Wi-Fi and BLE LED strip controllers.
 * Supports:
 * - Magic Home / Flux Wi-Fi (TCP 5577)
 * - Lotus Lantern / ELK-BLEDOM (BLE 0xFFF3 & Wi-Fi TCP/UDP 7E..EF)
 * - Triones / HappyLighting BLE (0xFFD9 56..AA / CC..33 / BB..44)
 * - WLED (HTTP JSON + 36-segment UDP DRGB realtime frames)
 */
object LedProtocolPackets {

    // -------------------------------------------------------------------------
    // Magic Home / Flux Wi-Fi / Zengge protocol (TCP port 5577)
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
        // Magic Home speed: 1 (fastest) .. 31 (slowest)
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

    fun magicHomeQueryState(): ByteArray = byteArrayOf(
        0x81.toByte(), 0x8A.toByte(), 0x8B.toByte(), 0x96.toByte()
    )

    // -------------------------------------------------------------------------
    // Lotus Lantern / ELK-BLEDOM 9-byte protocol (BLE GATT 0xFFF3 & Wi-Fi TCP/UDP)
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
        0x7E, 0x00, 0x03,
        modeCode.coerceIn(0x80, 0x9F).toByte(),
        0x03, 0x00, 0x00, 0x00,
        0xEF.toByte()
    )

    fun lotusSpeed(speedPercent: Int): ByteArray = byteArrayOf(
        0x7E, 0x00, 0x02,
        speedPercent.coerceIn(0, 100).toByte(),
        0x00, 0x00, 0x00, 0x00,
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
    // Triones / HappyLighting BLE protocol (GATT 0xFFD9)
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
    // WLED HTTP JSON API & UDP DRGB Realtime Stream (port 21324)
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

    /**
     * WLED DRGB UDP Realtime frame:
     * Byte 0 = 2 (DRGB protocol), Byte 1 = timeout in seconds, then R,G,B per LED segment.
     */
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
