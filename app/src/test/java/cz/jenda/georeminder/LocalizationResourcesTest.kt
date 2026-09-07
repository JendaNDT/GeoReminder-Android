package cz.jenda.georeminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalizationResourcesTest {

    @Test
    fun czechAndEnglishStringResourcesHaveMatchingKeys() {
        val resDir = sequenceOf(
            File("src/main/res"),
            File("app/src/main/res"),
        ).firstOrNull { it.isDirectory }
            ?: error("Nelze najít src/main/res pro kontrolu lokalizace")

        val cs = stringNames(File(resDir, "values/strings.xml"))
        val en = stringNames(File(resDir, "values-en/strings.xml"))

        assertTrue("Výchozí CZ resources nesmí být prázdné", cs.isNotEmpty())
        assertEquals(
            "CZ a EN strings.xml musí mít stejnou množinu klíčů. " +
                "Chybí v EN: ${cs - en}; chybí v CZ: ${en - cs}",
            cs,
            en,
        )
    }

    private fun stringNames(file: File): Set<String> {
        require(file.isFile) { "Chybí resource soubor: ${file.path}" }
        val regex = Regex("<string\\s+name=\"([^\"]+)\"")
        return regex.findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
    }
}
