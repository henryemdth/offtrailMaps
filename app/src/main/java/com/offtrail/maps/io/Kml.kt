package com.offtrail.maps.io

import com.offtrail.maps.model.FeatureType
import com.offtrail.maps.model.GeoFeature
import com.offtrail.maps.model.LatLon
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/** KML/KMZ (unzipped) reader/writer — free-tier import in the reference app. */
object Kml {

    fun parse(stream: InputStream): List<GeoFeature> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(stream))
        val root = doc.documentElement ?: return emptyList()
        val features = mutableListOf<GeoFeature>()

        root.getElementsByTagName("Placemark").let { list ->
            for (i in 0 until list.length) {
                val el = list.item(i) as org.w3c.dom.Element
                val name = el.childText("name") ?: "Placemark"
                val desc = el.childText("description") ?: ""
                val coordsText = el.childText("coordinates") ?: continue
                val points = parseCoords(coordsText)
                val type = when {
                    el.getElementsByTagName("Polygon").length > 0 -> FeatureType.AREA
                    el.getElementsByTagName("LineString").length > 0 -> FeatureType.LINE
                    points.size == 1 -> FeatureType.PLACEMARK
                    else -> FeatureType.LINE
                }
                if (points.isNotEmpty()) {
                    features += GeoFeature(
                        id = com.offtrail.maps.model.AppRepository.newId(),
                        name = name,
                        type = type,
                        points = points,
                        description = desc,
                    )
                }
            }
        }
        return features
    }

    private fun parseCoords(text: String): List<LatLon> = text.trim()
        .split(Regex("\\s+"))
        .mapNotNull { triple ->
            val parts = triple.split(",")
            if (parts.size < 2) null
            else LatLon(parts[1].toDoubleOrNull() ?: return@mapNotNull null, parts[0].toDoubleOrNull() ?: return@mapNotNull null)
        }

    fun export(features: List<GeoFeature>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>\n")
        features.forEach { f ->
            sb.append("<Placemark><name>${f.name.escape()}</name><description>${f.description.escape()}</description>")
            when (f.type) {
                FeatureType.PLACEMARK -> {
                    val p = f.points.firstOrNull()
                    if (p != null) sb.append("<Point><coordinates>${p.lon},${p.lat},0</coordinates></Point>")
                }
                FeatureType.LINE, FeatureType.TRACK ->
                    sb.append("<LineString><coordinates>${coords(f.points)}</coordinates></LineString>")
                FeatureType.AREA ->
                    sb.append("<Polygon><outerBoundaryIs><LinearRing><coordinates>${coords(f.points)}</coordinates></LinearRing></outerBoundaryIs></Polygon>")
                FeatureType.PHOTO -> {}
            }
            sb.append("</Placemark>\n")
        }
        sb.append("</Document></kml>\n")
        return sb.toString()
    }

    private fun coords(points: List<LatLon>) = points.joinToString(" ") { "${it.lon},${it.lat},0" }

    private fun org.w3c.dom.Element.childText(tag: String): String? {
        val list = getElementsByTagName(tag)
        return if (list.length > 0) list.item(0).textContent.trim() else null
    }

    private fun String.escape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
