package app.kweather.data.alerts

import app.kweather.domain.Alert
import app.kweather.domain.Place
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Servicio Meteorológico Nacional (Argentina), CC BY 4.0.
 *
 * The RSS index lists one CAP file per alert zone (often 100+), and only the CAP files carry the
 * polygons, so each file is downloaded once and kept on disk. File names include a timestamp and
 * never change, so later refreshes only fetch new ones. SMN reportedly allows about 200 XML
 * requests per 10 minutes, so the index is read at most every [minIntervalMillis].
 */
class SmnAlertProvider(
    private val http: HttpClient,
    private val cacheDir: File,
    private val minIntervalMillis: Long = 5 * 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) : AlertProvider {

    private val mutex = Mutex()
    private var lastFetch = 0L
    private var lastAlerts: List<Alert> = emptyList()
    private val parsed = mutableMapOf<String, List<Alert>>()

    override fun covers(place: Place): Boolean =
        place.countryCode.equals("AR", ignoreCase = true) ||
            // Older saved places may lack a country code: use Argentina's bounding box.
            (place.countryCode == null && place.latitude in -55.2..-21.7 && place.longitude in -73.7..-53.5)

    override suspend fun currentAlerts(): List<Alert> = mutex.withLock {
        if (clock() - lastFetch < minIntervalMillis && lastFetch != 0L) return lastAlerts
        val dir = File(cacheDir, "smn_cap").apply { mkdirs() }
        // Not RSS (an error page, say): fail instead of treating it as "no alerts".
        val links = (CapParser.rssLinks(http.get(INDEX_URL).bodyAsText()) ?: error("SMN index was not RSS"))
            .filter { it.startsWith("https://ssl.smn.gob.ar/") && it.endsWith(".xml") }
            .distinct()
        val names = links.associateBy { fileName(it) }

        // Forget files that left the index.
        withContext(Dispatchers.IO) {
            dir.listFiles()?.filter { it.name !in names }?.forEach { it.delete() }
        }
        parsed.keys.retainAll(names.keys)

        val gate = Semaphore(4)
        coroutineScope {
            names.filterKeys { it !in parsed }.map { (name, url) ->
                async {
                    val file = File(dir, name)
                    val xml = withContext(Dispatchers.IO) { file.takeIf { it.exists() }?.readText() }
                        ?: runCatching { gate.withPermit { http.get(url).bodyAsText() } }.getOrNull()
                            ?.also { body -> withContext(Dispatchers.IO) { file.writeText(body) } }
                    name to xml?.let { CapParser.parse(it, SOURCE, SOURCE_NAME) }
                }
            }.awaitAll().forEach { (name, alerts) ->
                // A failed download stays out of the map so the next refresh retries it.
                if (alerts != null) parsed[name] = alerts
            }
        }

        lastFetch = clock()
        lastAlerts = parsed.values.flatten()
        lastAlerts
    }

    private fun fileName(url: String): String {
        // Both products live in different folders; keep the folder in the name to avoid clashes.
        val parts = url.substringAfter("/feeds/CAP/").split('/')
        return parts.joinToString("_").replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    companion object {
        const val SOURCE = "SMN"
        const val SOURCE_NAME = "Servicio Meteorológico Nacional (Argentina)"
        const val INDEX_URL = "https://ssl.smn.gob.ar/CAP/AR.php"
        const val ALERTS_PAGE = "https://www.smn.gob.ar/alertas"
    }
}
