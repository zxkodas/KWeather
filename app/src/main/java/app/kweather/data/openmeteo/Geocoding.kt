package app.kweather.data.openmeteo

import app.kweather.domain.Place
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult> = emptyList(),
)

@Serializable
data class GeocodingResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
)

/** Places with the same name and region get their district added so the list can tell them apart. */
fun List<GeocodingResult>.toPlaces(): List<Place> {
    val places = map { it.toPlace() }
    val dupes = places.groupingBy { it.name to it.region }.eachCount().filterValues { it > 1 }.keys
    return places.mapIndexed { i, place ->
        val district = this[i].admin2
        if ((place.name to place.region) in dupes && district != null && district != place.name) {
            place.copy(region = "$district, ${place.region}")
        } else {
            place
        }
    }
}

fun GeocodingResult.toPlace(): Place = Place(
    id = "geo:$id",
    name = name,
    region = listOfNotNull(admin1?.takeIf { it != name }, country).joinToString(", "),
    latitude = latitude,
    longitude = longitude,
    countryCode = countryCode,
)
