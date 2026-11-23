package app.kweather.data.alerts

import app.kweather.domain.Alert
import app.kweather.domain.Place

/** One national alert source. Later: Environment Canada, NWS, other CAP feeds. */
interface AlertProvider {
    fun covers(place: Place): Boolean

    /** All current alerts from this source; matching to places happens in [AlertsRepository]. */
    suspend fun currentAlerts(): List<Alert>
}
