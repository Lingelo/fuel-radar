package fr.fuelradar

import fr.fuelradar.data.model.Station
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun parses_department_array_with_fuels_and_optional_fields() {
        // Shape of apps/web/public/data/departments/{dept}.json, plus an
        // unknown field to prove ignoreUnknownKeys keeps parsing resilient.
        val payload = """
            [
              {
                "id": 75001001,
                "lat": 48.85,
                "lng": 2.35,
                "addr": "1 Rue de Rivoli",
                "city": "Paris",
                "cp": "75001",
                "brand": "TotalEnergies",
                "h24": true,
                "fuels": { "Gazole": { "p": 1.789, "d": "2026-07-22" },
                           "SP95": { "p": 1.859, "d": "2026-07-22" } },
                "unknownFuture": 42
              },
              {
                "id": 200000123,
                "lat": 40.4,
                "lng": -3.7,
                "addr": "Calle Mayor",
                "city": "Madrid",
                "cp": "28001",
                "fuels": { "Gazole": { "p": 1.499, "d": "2026-07-21" } }
              }
            ]
        """.trimIndent()

        val stations = json.decodeFromString(ListSerializer(Station.serializer()), payload)

        assertEquals(2, stations.size)
        val paris = stations[0]
        assertEquals(75001001L, paris.id)
        assertEquals("TotalEnergies", paris.brand)
        assertTrue(paris.h24 == true)
        assertEquals(1.789, paris.fuels["Gazole"]!!.p, 1e-9)
        assertEquals("2026-07-22", paris.fuels["SP95"]!!.d)

        val madrid = stations[1]
        assertNull(madrid.brand)
        assertNull(madrid.h24)
        assertEquals(1, madrid.fuels.size)
    }
}
