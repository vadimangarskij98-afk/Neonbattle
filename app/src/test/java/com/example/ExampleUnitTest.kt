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
    fun lotusPowerPacketsMatchStockApp() {
        assertArrayEquals(
            byteArrayOf(0x7E, 0x00, 0x04, 0xF0.toByte(), 0x00, 0x01, 0xFF.toByte(), 0x00, 0xEF.toByte()),
            LedProtocolPackets.lotusPower(true)
        )
        assertArrayEquals(
            byteArrayOf(0x7E, 0x00, 0x04, 0x00, 0x00, 0x00, 0xFF.toByte(), 0x00, 0xEF.toByte()),
            LedProtocolPackets.lotusPower(false)
        )
    }

    @Test
    fun lotusAndTrionesBlePacketsAreValid() {
        val lotus = LedProtocolPackets.lotusColor(RgbColor(255, 128, 64), PinOrder.RGB)
        assertEquals(9, lotus.size)
        assertEquals(0x7E.toByte(), lotus[0])
        assertEquals(0xEF.toByte(), lotus[8])

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
