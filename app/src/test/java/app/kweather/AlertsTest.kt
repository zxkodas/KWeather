package app.kweather

import app.kweather.data.alerts.AlertsRepository
import app.kweather.data.alerts.CapParser
import app.kweather.data.alerts.SmnAlertProvider
import app.kweather.domain.Alert
import app.kweather.domain.AlertLevel
import app.kweather.domain.Place
import app.kweather.domain.eventLabel
import app.kweather.ui.alerts.splitSteps
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.OffsetDateTime

class AlertsTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun fixture(name: String): String =
        javaClass.getResource("/smn/$name")!!.readText(Charsets.UTF_8)

    private val storms by lazy { CapParser.parse(fixture("alerta_tormentas.xml"), "SMN", "SMN").single() }
    private val shortTerm by lazy { CapParser.parse(fixture("aviso_corto_plazo.xml"), "SMN", "SMN").single() }

    @Test fun parsesDefaultNamespaceAlert() {
        assertEquals("Tormentas", storms.event)
        assertEquals("Thunderstorms", storms.eventLabel())
        assertEquals(AlertLevel.Yellow, storms.level) // Moderate
        assertEquals(OffsetDateTime.parse("2026-11-24T03:00:00-03:00"), storms.onset)
        assertEquals(OffsetDateTime.parse("2026-11-25T14:59:59-03:00"), storms.expires)
        assertTrue(storms.description.startsWith("El área será afectada"))
        assertTrue(storms.instruction!!.startsWith("1- Evitá salir"))
        assertEquals(1, storms.polygons.size)
        assertTrue(storms.polygons[0].size > 100)
        assertFalse(storms.isShortTerm)
    }

    @Test fun parsesPrefixedShortTermAlert() {
        assertEquals("TORMENTAS FUERTES", shortTerm.event)
        // Severe, and the headline says NARANJA.
        assertEquals(AlertLevel.Orange, shortTerm.level)
        assertTrue(shortTerm.isShortTerm)
        assertNull(shortTerm.onset)
        assertEquals("SALTA: GRAL JOSE DE S. MARTIN.", shortTerm.areaDescription)
        assertEquals(6, shortTerm.polygons[0].size)
    }

    @Test fun polygonMatching() {
        assertTrue(storms.covers(-26.2, -54.3)) // inside the Misiones zone
        assertFalse(storms.covers(-34.6, -58.4)) // Buenos Aires
        assertTrue(shortTerm.covers(-22.7, -63.7))
        assertFalse(shortTerm.covers(-24.8, -65.4)) // Salta city
    }

    @Test fun levelFromSeverityAndHeadline() {
        assertEquals(AlertLevel.Red, CapParser.level("Extreme", ""))
        assertEquals(AlertLevel.Orange, CapParser.level("Severe", "Tormentas"))
        assertEquals(AlertLevel.Red, CapParser.level("Severe", "AVISO ROJO POR TORMENTAS"))
        assertEquals(AlertLevel.Yellow, CapParser.level("Moderate", "Lluvias"))
    }

    @Test fun cancelAndTestMessagesAreIgnored() {
        val xml = fixture("alerta_tormentas.xml")
        assertTrue(CapParser.parse(xml.replace("<msgType>Update</msgType>", "<msgType>Cancel</msgType>"), "SMN", "SMN").isEmpty())
        assertTrue(CapParser.parse(xml.replace("<status>Actual</status>", "<status>Test</status>"), "SMN", "SMN").isEmpty())
        assertTrue(CapParser.parse("<html>not cap</html>", "SMN", "SMN").isEmpty())
    }

    @Test fun rssIndexLinks() {
        val links = CapParser.rssLinks(fixture("index.xml"))!!
        assertEquals(3, links.size)
        assertTrue(links[0].contains("avisocortoplazo"))
        assertNull(CapParser.rssLinks("<html><body>blocked</body></html>"))
    }

    @Test fun matchingDropsExpiredAndDuplicates() {
        val place = Place("geo:1", "Eldorado", "Misiones, Argentina", -26.2, -54.3, "AR")
        val beforeExpiry = OffsetDateTime.parse("2026-11-24T10:00:00-03:00")
        val duplicate = storms.copy(id = "other-zone")
        assertEquals(1, AlertsRepository.matching(listOf(storms, duplicate), place, beforeExpiry).size)
        val afterExpiry = OffsetDateTime.parse("2026-11-26T00:00:00-03:00")
        assertTrue(AlertsRepository.matching(listOf(storms), place, afterExpiry).isEmpty())
        assertTrue(storms.isActive(beforeExpiry))
        assertFalse(storms.isActive(OffsetDateTime.parse("2026-11-23T10:00:00-03:00"))) // not started yet
    }

    @Test fun sortsBySeverityFirst() {
        val sorted = AlertsRepository.sort(listOf(storms, shortTerm, storms.copy(id = "r", level = AlertLevel.Red)))
        assertEquals(listOf(AlertLevel.Red, AlertLevel.Orange, AlertLevel.Yellow), sorted.map(Alert::level))
    }

    @Test fun instructionSteps() {
        val steps = splitSteps(storms.instruction!!)
        assertEquals(6, steps.size)
        assertEquals("Evitá salir.", steps[0])
        assertEquals(6, splitSteps(shortTerm.instruction!!).size)
    }

    @Test fun providerCachesFilesAndRetriesFailures() = runTest {
        val index = fixture("index.xml")
        val requests = mutableListOf<String>()
        var failOnce = true
        val engine = MockEngine { request ->
            val url = request.url.toString()
            requests += url
            when {
                url == SmnAlertProvider.INDEX_URL -> respond(index)
                url.contains("avisocortoplazo") -> respond(fixture("aviso_corto_plazo.xml"))
                url.endsWith("_33.xml") && failOnce -> {
                    failOnce = false
                    respondError(HttpStatusCode.ServiceUnavailable)
                }
                else -> respond(fixture("alerta_tormentas.xml"))
            }
        }
        var now = 0L
        val provider = SmnAlertProvider(HttpClient(engine) { expectSuccess = true }, tmp.root, minIntervalMillis = 1_000, clock = { now })

        now = 10_000
        val first = provider.currentAlerts()
        assertEquals(2, first.size) // one file failed
        assertEquals(4, requests.size) // index + 3 files

        now = 10_500 // inside the throttle window: no network at all
        provider.currentAlerts()
        assertEquals(4, requests.size)

        now = 20_000
        val second = provider.currentAlerts()
        assertEquals(3, second.size)
        assertEquals(6, requests.size) // index + only the file that failed
    }

    @Test fun providerRejectsNonRssIndex() = runTest {
        val engine = MockEngine { respond("<html>Access denied</html>") }
        val provider = SmnAlertProvider(HttpClient(engine) { expectSuccess = true }, tmp.root)
        val result = runCatching { provider.currentAlerts() }
        assertTrue(result.isFailure)
    }
}
