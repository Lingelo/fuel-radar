package fr.fuelradar

import fr.fuelradar.data.DeptIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeptIndexTest {

    @Test
    fun getDepartment_mainland() {
        assertEquals("75", DeptIndex.getDepartment("75015"))
        assertEquals("13", DeptIndex.getDepartment("13001"))
    }

    @Test
    fun getDepartment_corsica_splits_on_20200() {
        assertEquals("2A", DeptIndex.getDepartment("20000"))
        assertEquals("2B", DeptIndex.getDepartment("20200"))
        assertEquals("2B", DeptIndex.getDepartment("20600"))
    }

    @Test
    fun getDepartment_overseas_uses_three_digits() {
        assertEquals("974", DeptIndex.getDepartment("97400"))
        assertEquals("971", DeptIndex.getDepartment("97110"))
    }

    @Test
    fun findOverlappingDepts_returns_only_overlapping() {
        // [minLat, maxLat, minLng, maxLng]
        val index = mapOf(
            "75" to listOf(48.8, 48.9, 2.3, 2.4),
            "13" to listOf(43.2, 43.4, 5.3, 5.5),
        )
        val target = DeptIndex.boundingBox(lat = 48.85, lng = 2.35, radiusKm = 5.0)
        val depts = DeptIndex.findOverlappingDepts(index, target)
        assertTrue(depts.contains("75"))
        assertFalse(depts.contains("13"))
    }

    @Test
    fun boundingBox_is_centered_and_ordered() {
        val bb = DeptIndex.boundingBox(lat = 45.0, lng = 5.0, radiusKm = 10.0)
        assertTrue(bb.minLat < 45.0 && bb.maxLat > 45.0)
        assertTrue(bb.minLng < 5.0 && bb.maxLng > 5.0)
    }
}
