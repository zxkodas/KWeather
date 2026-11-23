package app.kweather.data.alerts

import app.kweather.domain.Alert
import app.kweather.domain.AlertLevel
import app.kweather.domain.LatLon
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal CAP 1.2 reader. Tolerates both default-namespace documents and `cap:`-prefixed ones,
 * which SMN uses for different products. One [Alert] per `<info>` block.
 */
object CapParser {

    fun parse(xml: String, source: String, fallbackSourceName: String): List<Alert> {
        val root = document(xml) ?: return emptyList()
        if (root.name() != "alert") return emptyList()
        val status = root.child("status")?.text()
        val msgType = root.child("msgType")?.text()
        if (!status.equals("Actual", ignoreCase = true)) return emptyList()
        if (msgType.equals("Cancel", ignoreCase = true)) return emptyList()
        val identifier = root.child("identifier")?.text().orEmpty()
        val sent = root.child("sent")?.text()?.let(::time)

        return root.children("info").mapIndexed { index, info ->
            val severity = info.child("severity")?.text().orEmpty()
            val headline = info.child("headline")?.text().orEmpty()
            val areas = info.children("area")
            Alert(
                id = if (index == 0) identifier else "$identifier#$index",
                source = source,
                sourceName = info.child("senderName")?.text()?.takeIf { it.isNotBlank() } ?: fallbackSourceName,
                event = info.child("event")?.text().orEmpty(),
                headline = headline,
                description = info.child("description")?.text().orEmpty(),
                instruction = info.child("instruction")?.text()?.takeIf { it.isNotBlank() },
                level = level(severity, headline),
                urgency = info.child("urgency")?.text().orEmpty(),
                sent = sent,
                onset = (info.child("onset") ?: info.child("effective"))?.text()?.let(::time),
                expires = info.child("expires")?.text()?.let(::time),
                web = info.child("web")?.text()?.takeIf { it.startsWith("http") },
                areaDescription = areas.mapNotNull { it.child("areaDesc")?.text()?.takeIf(String::isNotBlank) }
                    .joinToString("; ")
                    .replace(Regex("\\s+"), " ")
                    .trim(),
                polygons = areas.flatMap { area -> area.children("polygon").mapNotNull { polygon(it.text()) } },
            )
        }
    }

    /** CAP severity, overridden by an explicit color in the headline ("AVISO NARANJA ..."). */
    internal fun level(severity: String, headline: String): AlertLevel {
        val h = headline.uppercase()
        return when {
            "ROJO" in h || "ROJA" in h -> AlertLevel.Red
            "NARANJA" in h -> AlertLevel.Orange
            "AMARILL" in h -> AlertLevel.Yellow
            severity.equals("Extreme", ignoreCase = true) -> AlertLevel.Red
            severity.equals("Severe", ignoreCase = true) -> AlertLevel.Orange
            else -> AlertLevel.Yellow
        }
    }

    internal fun polygon(text: String): List<LatLon>? {
        val points = text.trim().split(Regex("\\s+")).mapNotNull { pair ->
            val parts = pair.split(',')
            val lat = parts.getOrNull(0)?.toDoubleOrNull()
            val lon = parts.getOrNull(1)?.toDoubleOrNull()
            if (lat != null && lon != null) LatLon(lat, lon) else null
        }
        return points.takeIf { it.size >= 3 }
    }

    /** Links of an RSS 2.0 index, in feed order, or null if the document is not RSS at all. */
    fun rssLinks(xml: String): List<String>? {
        val root = document(xml) ?: return null
        if (root.name() != "rss") return null
        val channel = root.child("channel") ?: return null
        return channel.children("item").mapNotNull { it.child("link")?.text()?.trim()?.takeIf { l -> l.startsWith("http") } }
    }

    private fun time(s: String): OffsetDateTime? = runCatching { OffsetDateTime.parse(s.trim()) }.getOrNull()

    private fun document(xml: String): Element? {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Feeds never need a DOCTYPE; refusing it blocks entity-expansion tricks.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        return runCatching {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))).documentElement
        }.getOrNull()
    }

    private fun Node.name(): String = (localName ?: nodeName).substringAfter(':')

    private fun Element.children(name: String): List<Element> {
        val out = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n is Element && n.name() == name) out += n
        }
        return out
    }

    private fun Element.child(name: String): Element? = children(name).firstOrNull()

    private fun Element.text(): String = textContent.orEmpty().trim()
}
