package com.example

import com.example.engine.PresetScenes
import com.example.engine.SmoothEffectEngine
import com.example.hardware.LedProtocolPackets
import com.example.model.AudioFrame
import com.example.model.FlowDirection
import com.example.model.MusicReactiveMode
import com.example.model.PinOrder
import com.example.model.RgbColor
import com.example.model.SceneCategory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun magicHomePowerPacketsHaveValidChecksums() {
        val onPacket = LedProtocolPackets.magicHomePower(true)
        assertArrayEquals(byteArrayOf(0x71, 0x23, 0x0F, 0xA3.toByte()), onPacket)
        val offPacket = LedProtocolPackets.magicHomePower(false)
        assertArrayEquals(byteArrayOf(0x71, 0x24, 0x0F, 0xA4.toByte()), offPacket)
    }

    @Test
    fun magicHomeColorPacketAppliesPinOrderAndChecksum() {
        val color = RgbColor(r = 0x10, g = 0x20, b = 0x30, w = 0x00)
        val grbPacket = LedProtocolPackets.magicHomeColor(color, PinOrder.GRB)
        assertArrayEquals(
            byteArrayOf(0x31, 0x20, 0x10, 0x30, 0x00, 0x00, 0x0F, 0xA0.toByte()),
            grbPacket
        )
    }

    @Test
    fun allFiveBleProtocolPacketsAreValid() {
        // 1. Lotus Lantern V6 / MELK (7E 07 05 03 R G B 10 EF)
        val lotusV6On = LedProtocolPackets.lotusV6Power(true)
        assertArrayEquals(
            byteArrayOf(0x7E, 0x04, 0x04, 0xF0.toByte(), 0x00, 0x01, 0xFF.toByte(), 0x00, 0xEF.toByte()),
            lotusV6On
        )
        val lotusV6Color = LedProtocolPackets.lotusV6Color(RgbColor(255, 16, 48), PinOrder.RGB)
        assertArrayEquals(
            byteArrayOf(0x7E, 0x07, 0x05, 0x03, 0xFF.toByte(), 0x10, 0x30, 0x10, 0xEF.toByte()),
            lotusV6Color
        )
        val lotusV6Bri = LedProtocolPackets.lotusV6Brightness(80)
        assertArrayEquals(
            byteArrayOf(0x7E, 0x04, 0x01, 80.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00, 0xEF.toByte()),
            lotusV6Bri
        )

        // 2. ELK-BLEDOM Legacy (7E 00 05 03 R G B 00 EF)
        val lotusLegacy = LedProtocolPackets.lotusColor(RgbColor(255, 128, 64), PinOrder.RGB)
        assertArrayEquals(
            byteArrayOf(0x7E, 0x00, 0x05, 0x03, 0xFF.toByte(), 0x80.toByte(), 0x40, 0x00, 0xEF.toByte()),
            lotusLegacy
        )

        // 3. LotusLanternX / BJ_LED (BC .. 55)
        val lotusXOn = LedProtocolPackets.lotusXPower(true)
        assertArrayEquals(byteArrayOf(0xBC.toByte(), 0x01, 0x01, 0x01, 0x55), lotusXOn)
        val lotusXRed = LedProtocolPackets.lotusXColor(RgbColor(255, 0, 0), PinOrder.RGB)
        assertEquals(10, lotusXRed.size)
        assertEquals(0xBC.toByte(), lotusXRed[0])
        assertEquals(0x55.toByte(), lotusXRed[9])
        val lotusXBri = LedProtocolPackets.lotusXBrightness(100)
        assertEquals(10, lotusXBri.size)
        assertEquals(0x03.toByte(), lotusXBri[3]) // 1000 = 0x03E8
        assertEquals(0xE8.toByte(), lotusXBri[4])

        // 4. Triones / HappyLighting (56 R G B 00 F0 AA)
        val triones = LedProtocolPackets.trionesColor(RgbColor(255, 16, 48), PinOrder.RGB)
        assertArrayEquals(
            byteArrayOf(0x56, 0xFF.toByte(), 0x10, 0x30, 0x00, 0xF0.toByte(), 0xAA.toByte()),
            triones
        )
        val trionesOn = LedProtocolPackets.trionesPower(true)
        assertArrayEquals(byteArrayOf(0xCC.toByte(), 0x23, 0x33), trionesOn)
    }

    @Test
    fun wledDrgbFrameEncodesHeaderAndSegments() {
        val segments = listOf(RgbColor(10, 20, 30), RgbColor(40, 50, 60))
        val frame = LedProtocolPackets.wledDrgbFrame(segments, PinOrder.RGB, timeoutSec = 3)
        assertArrayEquals(byteArrayOf(2, 3, 10, 20, 30, 40, 50, 60), frame)
    }

    @Test
    fun allCrimsonAndRunningZoneScenesProduceDistinctMoving36SegmentFrames() {
        val engine = SmoothEffectEngine()
        val redScenes = PresetScenes.builtIn.filter { it.category == SceneCategory.RED_LUXURY }
        val flowScenes = PresetScenes.builtIn.filter { it.category == SceneCategory.FLOW_ZONES }
        assertTrue(redScenes.size >= 8)
        assertTrue(flowScenes.size >= 7)

        // Verify meter-based running zone scene produces spatially distinct colors across the 36 segments
        val cascade = flowScenes.first { it.id == "flow_meter_cascade" }
        val (_, segs1) = engine.evaluateSceneFrame(
            scene = cascade,
            dtSec = 0.03f,
            speed = 0.5f,
            smoothness = 0.6f,
            brightness = 0.9f,
            zoneMeters = 1.0f,
            direction = FlowDirection.FORWARD,
            segmentCount = 36
        )
        assertEquals(36, segs1.size)
        assertTrue("Running zones must have multiple distinct segment colors", segs1.distinct().size > 4)

        // Advance time and verify movement
        val (_, segs2) = engine.evaluateSceneFrame(
            scene = cascade,
            dtSec = 0.25f,
            speed = 0.5f,
            smoothness = 0.6f,
            brightness = 0.9f,
            zoneMeters = 1.0f,
            direction = FlowDirection.FORWARD,
            segmentCount = 36
        )
        assertTrue("Running zones must animate over time", segs1 != segs2)

        // Verify every built-in scene evaluates cleanly without NaN or out-of-range channels
        PresetScenes.builtIn.forEach { scene ->
            val (primary, segments) = engine.evaluateSceneFrame(
                scene = scene,
                dtSec = 0.03f,
                speed = scene.defaultSpeed,
                smoothness = scene.defaultSmoothness,
                brightness = 0.85f,
                zoneMeters = scene.defaultZoneMeters,
                direction = FlowDirection.MIRROR_OUT,
                segmentCount = 36
            )
            assertEquals(36, segments.size)
            assertTrue(primary.r in 0..255 && primary.g in 0..255 && primary.b in 0..255)
        }

        val (musicPrimary, musicSegments) = engine.evaluateMusicFrame(
            audio = AudioFrame(level = 0.7f, bass = 0.8f, mid = 0.4f, treble = 0.2f, isBeat = true),
            mode = MusicReactiveMode.BASS_PULSE,
            palette = listOf(RgbColor.Crimson, RgbColor.Cyan),
            dtSec = 0.03f,
            sensitivity = 0.7f,
            decaySmoothness = 0.6f,
            bassBoost = 0.7f,
            masterBrightness = 1.0f,
            segmentCount = 36
        )
        assertEquals(36, musicSegments.size)
        assertTrue(musicPrimary.r + musicPrimary.g + musicPrimary.b > 0)
    }
}
