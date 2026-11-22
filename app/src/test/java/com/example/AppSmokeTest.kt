package com.example

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Launches the real MainActivity and walks every screen and sheet, so a crash in any composable
 * fails the build. Screenshots land in app/build/screens for a visual check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File("build/screens").apply { mkdirs() }
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun allScreensAndSheetsRender() {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(600)
        shot("1_color")
        listOf("rgb", "cct", "wheel").forEach {
            compose.onNodeWithTag("color_submode_$it").performClick()
            compose.mainClock.advanceTimeBy(300)
        }
        compose.onNodeWithTag("nav_tab_scenes").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("flow_dir_mirror_out").performClick()
        compose.mainClock.advanceTimeBy(300)
        shot("2_scenes")
        compose.onNodeWithTag("nav_tab_music").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("3_music")
        compose.onNodeWithTag("nav_tab_timer").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("4_timer")
        compose.onNodeWithTag("power_toggle_button").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("power_toggle_button").performClick()
        compose.onNodeWithTag("settings_button").performClick()
        compose.mainClock.advanceTimeBy(800)
        shot("5_settings")
    }
}
