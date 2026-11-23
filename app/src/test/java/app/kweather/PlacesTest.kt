package app.kweather

import app.kweather.data.openmeteo.GeocodingResponse
import app.kweather.data.openmeteo.toPlace
import app.kweather.data.openmeteo.toPlaces
import app.kweather.domain.Place
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun geocodingResultsBecomePlaces() {
        val body = """
            {"results":[
              {"id":3435910,"name":"Buenos Aires","latitude":-34.61315,"longitude":-58.37723,
               "country_code":"AR","country":"Argentina","admin1":"Buenos Aires F.D.","timezone":"America/Argentina/Buenos_Aires"},
              {"id":5882142,"name":"Alert","latitude":82.5,"longitude":-62.35,"country_code":"CA","country":"Canada","admin1":"Nunavut"},
              {"id":1,"name":"Monaco","latitude":43.73,"longitude":7.42,"country_code":"MC","country":"Monaco","admin1":"Monaco"}
            ]}
        """.trimIndent()
        val places = json.decodeFromString<GeocodingResponse>(body).results.map { it.toPlace() }
        assertEquals("geo:3435910", places[0].id)
        assertEquals("Buenos Aires F.D., Argentina", places[0].region)
        assertEquals("AR", places[0].countryCode)
        assertEquals("Nunavut, Canada", places[1].region)
        // An admin area named like the place itself is not repeated.
        assertEquals("Monaco", places[2].region)
        assertFalse(places[0].isCurrent)
    }

    @Test fun duplicateNamesGetTheirDistrict() {
        val body = """
            {"results":[
              {"id":1,"name":"Rosario","latitude":-32.9,"longitude":-60.6,"country":"Argentina","admin1":"Santa Fe","admin2":"Rosario Department"},
              {"id":2,"name":"Rosario","latitude":13.8,"longitude":121.2,"country":"Philippines","admin1":"Calabarzon","admin2":"Province of Batangas"},
              {"id":3,"name":"Rosario","latitude":14.4,"longitude":120.9,"country":"Philippines","admin1":"Calabarzon","admin2":"Province of Cavite"}
            ]}
        """.trimIndent()
        val places = json.decodeFromString<GeocodingResponse>(body).results.toPlaces()
        assertEquals("Santa Fe, Argentina", places[0].region)
        assertEquals("Province of Batangas, Calabarzon, Philippines", places[1].region)
        assertEquals("Province of Cavite, Calabarzon, Philippines", places[2].region)
    }

    @Test fun emptySearchHasNoResultsKey() {
        assertTrue(json.decodeFromString<GeocodingResponse>("""{"generationtime_ms":0.5}""").results.isEmpty())
    }

    @Test fun savedPlacesRoundTrip() {
        val list = listOf(
            Place(Place.CURRENT_ID, "Palermo", "Buenos Aires, Argentina", -34.58, -58.42, "AR"),
            Place("geo:1", "Alert", "Nunavut, Canada", 82.5, -62.35, "CA"),
        )
        val serializer = ListSerializer(Place.serializer())
        val decoded = json.decodeFromString(serializer, json.encodeToString(serializer, list))
        assertEquals(list, decoded)
        assertTrue(decoded[0].isCurrent)
    }
}
