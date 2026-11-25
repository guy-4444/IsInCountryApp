package com.guy.isincountryapp

import com.guy.isincountrylibrary.CountryLocator
import com.guy.isincountrylibrary.inWhichCountry
import com.guy.isincountrylibrary.isInCountry
import com.guy.isincountrylibrary.isInSea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class CountryLocatorTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val candidates = listOf(
                File("../isInCountryLibrary/src/main/assets/countries.json"),
                File("isInCountryLibrary/src/main/assets/countries.json")
            )
            val assetFile = candidates.firstOrNull { it.exists() }
                ?: error("Could not locate countries.json in isInCountryLibrary assets")
            assetFile.inputStream().use { CountryLocator.loadFromStream(it) }
        }
    }

    @Test
    fun appCanUseLibraryFunctions() {
        assertTrue(isInCountry(32.0853, 34.7818, "IL"))
        assertEquals("IL", inWhichCountry(32.0853, 34.7818))
        assertFalse(isInSea(32.0853, 34.7818))

        assertTrue(isInSea(32.0853, 34.0000))
        assertNull(inWhichCountry(32.0853, 34.0000))
    }
}
