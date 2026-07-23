package fr.fuelradar

import fr.fuelradar.domain.formatDistance
import fr.fuelradar.domain.formatPriceEuro
import fr.fuelradar.domain.haversineKm
import fr.fuelradar.domain.priceBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {

    @Test
    fun haversine_parisToMarseille_isAround660km() {
        val d = haversineKm(48.8566, 2.3522, 43.2965, 5.3698)
        assertTrue("expected ~660km, got $d", d in 640.0..680.0)
    }

    @Test
    fun formatPriceEuro_usesFrenchComma() {
        assertEquals("1,789 €", formatPriceEuro(1.789))
    }

    @Test
    fun formatDistance_switchesUnits() {
        assertEquals("500 m", formatDistance(0.5))
        assertTrue(formatDistance(3.4).endsWith("km"))
    }

    @Test
    fun priceBounds_handlesEmptyAndValues() {
        assertEquals(0.0 to 1.0, priceBounds(emptyList()))
        val (min, max) = priceBounds(listOf(1.0, 1.5, 2.0))
        assertTrue(min <= max)
    }
}
