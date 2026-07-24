package cz.jenda.georeminder

import android.content.Context
import cz.jenda.georeminder.data.SharedStorage
import cz.jenda.georeminder.ui.theme.ThemeController
import cz.jenda.georeminder.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeControllerTest {

    @Test
    fun removedGlassModeMigratesToSystemTheme() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("appearanceMode", "glass").commit()

        ThemeController.init(context)

        assertEquals(ThemeMode.SYSTEM, ThemeController.mode.value)
        assertEquals("system", prefs.getString("appearanceMode", null))
    }
}
