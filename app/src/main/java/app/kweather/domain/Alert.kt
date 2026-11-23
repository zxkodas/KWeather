package app.kweather.domain

import java.time.OffsetDateTime

/** SMN's color scale, which CAP severity maps onto. */
enum class AlertLevel(val label: String) {
    Yellow("Yellow"),
    Orange("Orange"),
    Red("Red"),
}

data class LatLon(val lat: Double, val lon: Double)

data class Alert(
    val id: String,
    /** Short source name, e.g. "SMN". */
    val source: String,
    val sourceName: String,
    /** Event as issued, e.g. "Tormentas". */
    val event: String,
    val headline: String,
    val description: String,
    val instruction: String?,
    val level: AlertLevel,
    /** CAP urgency: Immediate for short-term storm warnings, Future/Expected for forecasts. */
    val urgency: String,
    val sent: OffsetDateTime?,
    val onset: OffsetDateTime?,
    val expires: OffsetDateTime?,
    val web: String?,
    val areaDescription: String,
    val polygons: List<List<LatLon>>,
) {
    val isShortTerm: Boolean get() = urgency.equals("Immediate", ignoreCase = true)

    fun isActive(now: OffsetDateTime): Boolean =
        (expires == null || expires.isAfter(now)) && (onset == null || !onset.isAfter(now))

    fun isExpired(now: OffsetDateTime): Boolean = expires != null && !expires.isAfter(now)

    fun covers(lat: Double, lon: Double): Boolean = polygons.any { pointInPolygon(lat, lon, it) }

    companion object {
        /** Ray casting with longitude as x and latitude as y. */
        fun pointInPolygon(lat: Double, lon: Double, polygon: List<LatLon>): Boolean {
            if (polygon.size < 3) return false
            var inside = false
            var j = polygon.lastIndex
            for (i in polygon.indices) {
                val a = polygon[i]
                val b = polygon[j]
                if ((a.lat > lat) != (b.lat > lat) &&
                    lon < (b.lon - a.lon) * (lat - a.lat) / (b.lat - a.lat) + a.lon
                ) {
                    inside = !inside
                }
                j = i
            }
            return inside
        }
    }
}

/** English names for SMN's usual event names; anything else is shown as issued. */
fun Alert.eventLabel(): String {
    val e = event.lowercase().trim()
    return when {
        e.startsWith("tormenta") -> "Thunderstorms"
        e.contains("zonda") -> "Zonda wind"
        e.startsWith("viento") -> "Wind"
        e.startsWith("lluvia") -> "Rain"
        e.startsWith("nevada") || e.startsWith("nieve") -> "Snow"
        e.contains("calor") || e.contains("temperatura") && e.contains("alta") -> "Extreme heat"
        e.contains("frío") || e.contains("frio") -> "Extreme cold"
        e.startsWith("niebla") -> "Fog"
        e.contains("polvo") || e.contains("arena") -> "Dust"
        e.contains("granizo") -> "Hail"
        else -> event.lowercase().replaceFirstChar { it.titlecase() }
    }
}
