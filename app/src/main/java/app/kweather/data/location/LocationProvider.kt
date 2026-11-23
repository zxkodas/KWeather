package app.kweather.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import app.kweather.domain.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.roundToInt

sealed interface LocationResult {
    data class Found(val place: Place) : LocationResult
    data object NoPermission : LocationResult
    data object LocationOff : LocationResult
    data object Unavailable : LocationResult
}

/**
 * Finds the phone's approximate position with the platform LocationManager (no Play Services needed)
 * and names it with the platform Geocoder.
 */
class LocationProvider(private val context: Context) {

    private val manager = context.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun locate(): LocationResult {
        if (!hasPermission()) return LocationResult.NoPermission
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocationResult.LocationOff
        val location = withTimeoutOrNull(15_000) { fresh() } ?: lastKnown() ?: return LocationResult.Unavailable
        // ~1 km precision is plenty for a forecast, keeps the cache stable and shares less.
        val lat = round2(location.latitude)
        val lon = round2(location.longitude)
        val address = reverseGeocode(lat, lon)
        val name = address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: "Current location"
        val region = listOfNotNull(address?.adminArea?.takeIf { it != name }, address?.countryName).joinToString(", ")
        return LocationResult.Found(
            Place(
                id = Place.CURRENT_ID,
                name = name,
                region = region,
                latitude = lat,
                longitude = lon,
                countryCode = address?.countryCode,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun fresh(): Location? = suspendCancellableCoroutine { cont ->
        val provider = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.hasProvider(LocationManager.FUSED_PROVIDER) &&
                manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(manager, provider, signal, executor) { location ->
            if (cont.isActive) cont.resume(location)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? =
        manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    private suspend fun reverseGeocode(lat: Double, lon: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.ENGLISH)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
            }
        }
    }

    private fun round2(v: Double) = (v * 100).roundToInt() / 100.0
}
