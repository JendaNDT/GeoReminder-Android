package cz.jenda.georeminder

import cz.jenda.georeminder.data.LanguageController
import cz.jenda.georeminder.notify.TtsSpeaker
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TtsSpeakerTest {

    @Test
    fun `cestina pouzije cs CZ`() {
        val locale = TtsSpeaker.resolvePreferredLocale(
            LanguageController.LANG_CS,
            Locale.GERMANY,
        )
        assertEquals("cs", locale.language)
        assertEquals("CZ", locale.country)
    }

    @Test
    fun `anglictina pouzije en US`() {
        val locale = TtsSpeaker.resolvePreferredLocale(
            LanguageController.LANG_EN,
            Locale.GERMANY,
        )
        assertEquals(Locale.US, locale)
    }

    @Test
    fun `system s anglickym telefonem pouzije anglictinu`() {
        val locale = TtsSpeaker.resolvePreferredLocale(
            LanguageController.LANG_SYSTEM,
            Locale.UK,
        )
        assertEquals(Locale.US, locale)
    }

    @Test
    fun `system s nepodporovanym jazykem pouzije cesky fallback`() {
        val locale = TtsSpeaker.resolvePreferredLocale(
            LanguageController.LANG_SYSTEM,
            Locale.GERMANY,
        )
        assertEquals("cs", locale.language)
        assertEquals("CZ", locale.country)
    }
}
