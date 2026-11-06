package org.monogram.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.monogram.core.ui.theme.MonogramTheme

/** The vertical gradient must not introduce horizontal pattern seams. */
class MonogramWallpaperTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun wallpaperRepeatsHorizontallyOnThePatternGrid() {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 320.dp).testTag("wall")) {
                    MonogramWallpaper(Modifier.fillMaxSize())
                }
            }
        }
        val pixels = rule.onNodeWithTag("wall").captureToImage().toPixelMap()
        val stepX = (rule.density.density * 96f).roundToInt()

        var sampled = 0
        var mismatched = 0
        var worst = 0f
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val here = pixels[x, y]
                if (x + stepX < pixels.width) {
                    val delta = channelDelta(here, pixels[x + stepX, y])
                    worst = maxOf(worst, delta)
                    if (delta > 2f) mismatched++
                    sampled++
                }
            }
        }
        assertTrue(
            "wallpaper breaks its $stepX horizontal grid on $mismatched of $sampled pixels (worst $worst)",
            mismatched <= sampled / 1000,
        )
    }

    @Test
    fun wallpaperUsesThemeGradientInBothModes() {
        var dark by mutableStateOf(false)
        var top = Color.Unspecified
        var bottom = Color.Unspecified
        rule.setContent {
            MonogramTheme(darkTheme = dark, dynamicColor = false) {
                top = MaterialTheme.colorScheme.surface
                bottom = MaterialTheme.colorScheme.surfaceContainerLow
                Box(Modifier.size(360.dp, 320.dp).testTag("wall")) {
                    MonogramWallpaper(Modifier.fillMaxSize())
                }
            }
        }
        for (mode in listOf(false, true)) {
            rule.runOnIdle { dark = mode }
            val pixels = rule.onNodeWithTag("wall").captureToImage().toPixelMap()
            val x = (rule.density.density * 10f).roundToInt()
            assertTrue("top dark=$mode", channelDelta(top, pixels[x, 1]) <= 2f)
            assertTrue("bottom dark=$mode", channelDelta(bottom, pixels[x, pixels.height - 2]) <= 2f)
            assertTrue("gradient dark=$mode", channelDelta(pixels[x, 1], pixels[x, pixels.height - 2]) > 6f)
        }
    }
}

private fun channelDelta(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float =
    maxOf(
        abs(a.red - b.red),
        abs(a.green - b.green),
        abs(a.blue - b.blue),
        abs(a.alpha - b.alpha),
    ) * 255f
