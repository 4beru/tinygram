package org.monogram

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.monogram.core.ui.theme.MonogramTheme
import org.monogram.feature.auth.AuthStore
import org.monogram.feature.auth.ui.AuthScreen
import org.monogram.feature.auth.ui.AuthTestTags
import org.monogram.feature.auth.ui.PhoneInputTags
import java.io.File
import java.util.Locale

class TinygramAppearanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<UiStateTestActivity>? = null
    private val intents = mutableListOf<AuthStore.Intent>()
    private var proxyOpened = false
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        try {
            scenario?.close()
        } finally {
            scenario = null
            UiStateTestActivity.content = {}
            intents.clear()
            proxyOpened = false
        }
    }

    @Test
    fun phoneEntryLightHasAccessibleActions() = checkPhoneEntry(dark = false)

    @Test
    fun phoneEntryDarkHasAccessibleActions() = checkPhoneEntry(dark = true)

    @Test
    fun narrowLightPhoneEntryRemainsReachableAtLargeFontScale() =
        checkPhoneEntry(dark = false, fontScale = 1.6f, width = 320)

    @Test
    fun narrowDarkPhoneEntryRemainsReachableAtLargeFontScale() =
        checkPhoneEntry(dark = true, fontScale = 1.6f, width = 320)

    @Test
    fun tinygramBrandResolvesInEverySupportedLocale() {
        val brands = listOf(
            R.string.app_name,
            org.monogram.feature.auth.R.string.auth_brand,
            org.monogram.feature.chats.R.string.chats_brand,
            org.monogram.feature.settings.R.string.settings_about,
            org.monogram.feature.settings.R.string.settings_accent_monogram,
        )
        for (tag in listOf("en", "es", "ru", "zh", "ja", "pt-BR", "hy", "sk", "tr", "uk")) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
            val localized = context.createConfigurationContext(configuration)
            for (id in brands) {
                assertEquals(
                    "$tag: ${context.resources.getResourceEntryName(id)}",
                    "Tinygram",
                    localized.getString(id),
                )
            }
        }
    }

    private fun checkPhoneEntry(dark: Boolean, fontScale: Float = 1f, width: Int = 360) {
        UiStateTestActivity.content = {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MonogramTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.requiredSize(width.dp, 560.dp).testTag("appearance-fixture")) {
                        AuthScreen(
                            state = AuthStore.State(),
                            onIntent = { intents += it },
                            onOpenProxy = { proxyOpened = true },
                        )
                    }
                }
            }
        }
        scenario = ActivityScenario.launch(UiStateTestActivity::class.java)
        compose.waitForIdle()
        closeSoftKeyboard()
        val mode = if (dark) "dark" else "light"
        capture("auth-$mode-${width}dp-font$fontScale.png")

        val proxyLabel = context.getString(org.monogram.feature.auth.R.string.auth_connection)
        compose.onNodeWithContentDescription(proxyLabel)
            .assertIsDisplayed().assertHasClickAction().performClick()
        compose.runOnIdle { assertTrue(proxyOpened) }

        val title = compose.onNodeWithTag(AuthTestTags.TITLE)
        title.performScrollTo().assertIsDisplayed()
        title.assertTextEquals(context.getString(org.monogram.feature.auth.R.string.auth_title))
        val phone = compose.onNodeWithTag(PhoneInputTags.FIELD)
        phone.performScrollTo().assertIsDisplayed().assertIsEnabled()
        val primary = compose.onNodeWithTag(AuthTestTags.PRIMARY)
        primary.performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        primary.assertTextEquals(context.getString(org.monogram.feature.auth.R.string.auth_send_code))
        val primaryBounds = primary.getBoundsInRoot()
        val rootBounds = compose.onNodeWithTag(AuthTestTags.ROOT).getBoundsInRoot()
        assertTrue(
            "Primary action must stay inside the narrow viewport",
            primaryBounds.left >= rootBounds.left && primaryBounds.right <= rootBounds.right,
        )
        assertTrue("Primary action must retain a 48dp touch target", primaryBounds.bottom - primaryBounds.top >= 48.dp)

        val qr = compose.onNodeWithText(context.getString(org.monogram.feature.auth.R.string.auth_qr_login))
        qr.performScrollTo().assertIsDisplayed().assertIsEnabled().assertHasClickAction()
        capture("auth-$mode-${width}dp-font$fontScale-actions.png")
        qr.performClick()
        compose.runOnIdle {
            assertEquals(listOf(AuthStore.Intent.StartQrLogin), intents)
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("appearance-fixture").captureToImage().asAndroidBitmap()
        try {
            val directory = File(requireNotNull(context.getExternalFilesDir(null)), "tinygram-appearance")
            assertTrue("Cannot create fixture screenshot directory", directory.isDirectory || directory.mkdirs())
            File(directory, name).outputStream().use {
                assertTrue("Cannot encode fixture screenshot", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
