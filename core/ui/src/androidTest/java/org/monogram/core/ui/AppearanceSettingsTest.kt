package org.monogram.core.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceSettingsTest {
    @Test
    fun missingDynamicColorDefaultsOffAndSavedOptInAndMonogramSurviveInstall() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val prefsName = "appearance_test_${UUID.randomUUID()}"
        val prefs = testContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val isolatedContext = object : ContextWrapper(testContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
        }
        try {
            AppearanceSettings.install(isolatedContext)
            assertFalse(AppearanceSettings.state.value.dynamicColor)
            assertEquals(AccentPreset.Monogram, AppearanceSettings.state.value.accentPreset)
            assertTrue(prefs.all.isEmpty())

            assertTrue(prefs.edit()
                .putBoolean("dynamic_color", true)
                .putString("accent_preset", "Monogram")
                .commit())
            val stored = prefs.all.toMap()
            repeat(2) {
                AppearanceSettings.install(isolatedContext)
                assertTrue(AppearanceSettings.state.value.dynamicColor)
                assertEquals(AccentPreset.Monogram, AppearanceSettings.state.value.accentPreset)
                assertEquals(stored, prefs.all)
            }
            assertTrue(prefs.edit().putBoolean("dynamic_color", false).commit())
            AppearanceSettings.install(isolatedContext)
            assertFalse(AppearanceSettings.state.value.dynamicColor)
            assertEquals(false, prefs.all["dynamic_color"])
        } finally {
            prefs.edit().clear().commit()
            AppearanceSettings.install(InstrumentationRegistry.getInstrumentation().targetContext)
            testContext.deleteSharedPreferences(prefsName)
        }
    }
}
