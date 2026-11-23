package app.kweather.data.alerts

import app.kweather.domain.Alert
import app.kweather.domain.Place
import kotlinx.coroutines.CancellationException
import java.time.OffsetDateTime

data class PlaceAlerts(
    val byPlace: Map<String, List<Alert>>,
    /** Providers that failed this round, so the UI can say alerts may be missing. */
    val failedSources: Set<String>,
)

class AlertsRepository(private val providers: List<AlertProvider>) {

    suspend fun alertsFor(places: List<Place>, now: OffsetDateTime = OffsetDateTime.now()): PlaceAlerts {
        val byPlace = mutableMapOf<String, List<Alert>>()
        val failed = mutableSetOf<String>()
        for (provider in providers) {
            val relevant = places.filter(provider::covers)
            if (relevant.isEmpty()) continue
            val alerts = try {
                provider.currentAlerts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += provider.javaClass.simpleName
                continue
            }
            for (place in relevant) {
                byPlace[place.id] = byPlace[place.id].orEmpty() + matching(alerts, place, now)
            }
        }
        return PlaceAlerts(byPlace.mapValues { (_, list) -> sort(list) }, failed)
    }

    companion object {
        /** Alerts covering the place that have not expired, one per event and time window. */
        fun matching(alerts: List<Alert>, place: Place, now: OffsetDateTime): List<Alert> =
            alerts
                .filter { !it.isExpired(now) && it.covers(place.latitude, place.longitude) }
                .distinctBy { listOf(it.event.lowercase(), it.level, it.onset, it.expires) }

        /** Most severe first, then whatever is happening now, then soonest. */
        fun sort(alerts: List<Alert>): List<Alert> =
            alerts.sortedWith(
                compareByDescending<Alert> { it.level.ordinal }
                    .thenByDescending { it.isShortTerm }
                    .thenBy { it.onset },
            )
    }
}
