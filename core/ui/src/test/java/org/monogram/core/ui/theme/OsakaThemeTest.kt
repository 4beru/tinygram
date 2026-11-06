package org.monogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
import org.monogram.core.ui.AccentPreset
import org.monogram.core.ui.AppearanceSettings
import org.monogram.core.ui.AppearanceState

class OsakaThemeTest {
    private val schemes = listOf(LightColorScheme, DarkColorScheme)

    @Test fun defaultIdentityAndPersistedIdsRemainMonogram() {
        assertFalse(AppearanceState().dynamicColor)
        assertTrue(AppearanceState(dynamicColor = true).dynamicColor)
        for (raw in listOf("Monogram", "unknown", null)) {
            assertEquals(AccentPreset.Monogram, AppearanceSettings.parseAccent(raw))
        }
        for ((index, scheme) in schemes.withIndex()) {
            assertSame(scheme, scheme.withAccent(AccentPreset.Monogram, dark = index == 1))
            assertEquals(scheme.primary, scheme.surfaceTint)
            assertEquals(Color.Black, scheme.scrim)
        }
        assertEquals(LightColorScheme.primary, accentSwatch(AccentPreset.Monogram))
        assertEquals(Color(0xFFFDF5E2), LightColorScheme.surface)
        assertEquals(Color(0xFFEDE7D3), LightColorScheme.surfaceContainerLow)
        assertEquals(Color(0xFF001419), DarkColorScheme.surface)
        assertEquals(Color(0xFF002C38), DarkColorScheme.surfaceContainerLow)
        assertEquals(Color(0xFF063540), DarkColorScheme.surfaceContainerHigh)
    }

    @Test fun allTextRolesIncludingMessageBubblesAndInverseAreReadable() {
        for (scheme in schemes) {
            for ((background, foreground) in listOf(
                scheme.primary to scheme.onPrimary,
                scheme.primaryContainer to scheme.onPrimaryContainer,
                scheme.secondary to scheme.onSecondary,
                scheme.secondaryContainer to scheme.onSecondaryContainer,
                scheme.tertiary to scheme.onTertiary,
                scheme.tertiaryContainer to scheme.onTertiaryContainer,
                scheme.error to scheme.onError,
                scheme.errorContainer to scheme.onErrorContainer,
                scheme.background to scheme.onBackground,
                scheme.inverseSurface to scheme.inverseOnSurface,
                scheme.inverseSurface to scheme.inversePrimary,
            )) assertContrast(background, foreground, 4.5f)
            for (surface in surfaces(scheme)) {
                assertContrast(surface, scheme.onSurface, 4.5f)
                assertContrast(surface, scheme.onSurfaceVariant, 4.5f)
                for (indicator in listOf(scheme.outline, scheme.outlineVariant, scheme.primary, scheme.secondary, scheme.tertiary)) {
                    assertContrast(surface, indicator, 3f)
                }
            }
        }
    }

    @Test fun fixedRolesAreReadableAndIdenticalAcrossModes() {
        fun fixed(scheme: ColorScheme) = with(scheme) {
            listOf(
                listOf(primaryFixed, primaryFixedDim, onPrimaryFixed, onPrimaryFixedVariant),
                listOf(secondaryFixed, secondaryFixedDim, onSecondaryFixed, onSecondaryFixedVariant),
                listOf(tertiaryFixed, tertiaryFixedDim, onTertiaryFixed, onTertiaryFixedVariant),
            )
        }
        assertEquals(fixed(LightColorScheme), fixed(DarkColorScheme))
        for (scheme in schemes) for (roles in fixed(scheme)) {
            for (background in roles.take(2)) for (foreground in roles.drop(2)) {
                assertContrast(background, foreground, 4.5f)
            }
        }
    }

    @Test fun authAndWallpaperGradientsKeepTextReadable() {
        for (scheme in schemes) {
            for (end in listOf(scheme.surfaceContainerLow, scheme.surfaceContainer)) {
                for (step in 0..20) {
                    val background = lerp(scheme.surface, end, step / 20f)
                    assertContrast(background, scheme.onSurface, 4.5f)
                    assertContrast(background, scheme.onSurfaceVariant, 4.5f)
                    assertContrast(background, scheme.primary, 4.5f)
                    assertContrast(background, scheme.secondary, 4.5f)
                }
            }
        }
    }

    @Test fun otherPresetsStillReplaceAccentsWithoutReplacingOsakaSurfaces() {
        for (preset in AccentPreset.entries.filterNot { it == AccentPreset.Monogram }) {
            for ((index, base) in schemes.withIndex()) {
                val scheme = base.withAccent(preset, dark = index == 1)
                assertEquals(surfaces(base), surfaces(scheme))
                assertNotEquals(base.primary, scheme.primary)
                assertEquals(scheme.primary, scheme.surfaceTint)
                if (index == 0) assertEquals(accentSwatch(preset), scheme.primary)
                assertEquals(preset, AppearanceSettings.parseAccent(preset.name))
            }
        }
    }

    private fun surfaces(scheme: ColorScheme) = with(scheme) {
        listOf(surface, surfaceDim, surfaceBright, surfaceVariant, surfaceContainerLowest,
            surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest)
    }

    private fun assertContrast(background: Color, foreground: Color, minimum: Float) {
        val a = background.luminance()
        val b = foreground.luminance()
        val contrast = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
        assertTrue("$foreground on $background contrast=$contrast minimum=$minimum", contrast >= minimum)
    }
}
