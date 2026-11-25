package com.guy.isincountrylibrary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class CountryLocatorLibraryTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val candidates = listOf(
                File("src/main/assets/countries.json"),
                File("isInCountryLibrary/src/main/assets/countries.json")
            )
            val assetFile = candidates.firstOrNull { it.exists() }
                ?: error("Could not locate countries.json in library assets")
            assetFile.inputStream().use { CountryLocator.loadFromStream(it) }
        }
    }

    @Test
    fun isInCountry_matchesIso2Iso3AndCaseInsensitive() {
        assertTrue(isInCountry(32.0853, 34.7818, "IL"))
        assertTrue(isInCountry(32.0853, 34.7818, "il"))
        assertTrue(isInCountry(32.0853, 34.7818, "ISR"))
        assertFalse(isInCountry(32.0853, 34.7818, "US"))

        assertTrue(isInCountry(40.7128, -74.0060, "US"))
        assertTrue(isInCountry(40.7128, -74.0060, "USA"))

        assertTrue(isInCountry(51.5074, -0.1278, "GB"))
        assertTrue(isInCountry(51.5074, -0.1278, "GBR"))
        assertTrue(isInCountry(51.5074, -0.1278, "UK"))
    }

    @Test
    fun naturalEarthMinus99Countries_resolveProperly() {
        assertEquals("FR", inWhichCountry(48.8566, 2.3522))
        assertTrue(isInCountry(48.8566, 2.3522, "FR"))
        assertTrue(isInCountry(48.8566, 2.3522, "FRA"))
        assertFalse(isInSea(48.8566, 2.3522))

        assertEquals("NO", inWhichCountry(59.9139, 10.7522))
        assertTrue(isInCountry(59.9139, 10.7522, "NO"))
        assertTrue(isInCountry(59.9139, 10.7522, "NOR"))

        assertEquals("XK", inWhichCountry(42.6629, 21.1655))
        assertTrue(isInCountry(42.6629, 21.1655, "XK"))
        assertTrue(isInCountry(42.6629, 21.1655, "XKX"))
    }

    @Test
    fun enclaveHole_lesothoExcludedFromSouthAfrica() {
        assertEquals("LS", inWhichCountry(-29.3151, 27.4869))
        assertTrue(isInCountry(-29.3151, 27.4869, "LS"))
        assertFalse(isInCountry(-29.3151, 27.4869, "ZA"))
        assertFalse(isInSea(-29.3151, 27.4869))

        assertEquals("ZA", inWhichCountry(-26.2041, 28.0473))
        assertTrue(isInCountry(-26.2041, 28.0473, "ZA"))
        assertFalse(isInCountry(-26.2041, 28.0473, "LS"))
    }

    @Test
    fun multiPolygonsAntarcticaAndSeas_behaveCorrectly() {
        assertEquals("JP", inWhichCountry(35.6762, 139.6503))
        assertEquals("AQ", inWhichCountry(-80.0, 0.0))
        assertFalse(isInSea(-80.0, 0.0))

        assertTrue(isInSea(32.0853, 34.0000))
        assertNull(inWhichCountry(32.0853, 34.0000))
        assertTrue(isInSea(0.0, -30.0))
        assertTrue(isInSea(42.0, 50.5))
    }
}
