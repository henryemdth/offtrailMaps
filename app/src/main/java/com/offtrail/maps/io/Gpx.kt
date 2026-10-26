package com.offtrail.maps.io

import com.offtrail.maps.model.FeatureType
import com.offtrail.maps.model.GeoFeature
import com.offtrail.maps.model.LatLon
import com.offtrail.maps.model.TrackPoint
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/** GPX 1.1 reader/writer. GPX import is Plus-or-Pro gated upstream of this class. */
object Gpx {

    fun parse(stream: InputStream): List<GeoFeature> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(stream))
        val root = doc.documentElement ?: return emptyList()
        val features = mutableListOf<GeoFeature>()

        root.getElementsByTagName("wpt").let { list ->
            for (i in 0 until list.length) {
                val el = list.item(i) as org.w3c.dom.Element
                val name = el.textOf("name") ?: "Placemark"
                val desc = el.textOf("desc") ?: ""
                features.appendFeature(name, FeatureType.PLACEMARK, listOf(LatLon(el.lat(), el.lon()),), desc)
            }
        }
        root.getElementsByTagName("trk").let { list ->
            for (i in 0 until list.length) {
                val el = list.item(i) as org.w3c.dom.Element
                val name = el.textOf("name") ?: "Track"
                val points = mutableListOf<TrackPoint>()
                el.getElementsByTagName("trkpt").let { pts ->
                    for (j in 0 until pts.length) {
                        val p = pts.item(j) as org.w3c.dom.Element
                        val time = p.textOf("time")?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
                        points += TrackPoint(p.lat(), p.lon(), p.textOf("ele")?.toDoubleOrNull() ?: 0.0, time)
                    }
                }
                if (points.isNotEmpty()) features += trackToFeature(name, points)
            }
        }
        root.getElementsByTagName("rte").let { list ->
            for (i in 0 until list.length) {
                val el = list.item(i) as org.w3c.dom.Element
                val name = el.textOf("name") ?: "Route"
                val points = mutableListOf<LatLon>()
                el.getElementsByTagName("rtept").let { pts ->
                    for (j in 0 until pts.length) {
                        val p = pts.item(j) as org.w3c.dom.Element
                        points += LatLon(p.lat(), p.lon())
                    }
                }
                if (points.isNotEmpty()) features.appendFeature(name, FeatureType.LINE, points)
            }
        }
        return features
    }

    fun trackToFeature(name: String, points: List<TrackPoint>): GeoFeature {
        var dist = 0.0
        var gain = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]; val b = points[i]
            dist += com.offtrail.maps.geo.Projections.distanceMeters(a.lat, a.lon, b.lat, b.lon)
            gain += maxOf(0.0, b.elevationM - a.elevationM)
        }
        val duration = if (points.size > 1) (points.last().timeEpochMs - points.first().timeEpochMs) / 1000 else 0L
        return GeoFeature(
            id = com.offtrail.maps.model.AppRepository.newId(),
            name = name,
            type = FeatureType.TRACK,
            points = points.map { LatLon(it.lat, it.lon) },
            trackPoints = points,
            distanceMeters = dist,
            elevationGainM = gain,
            durationSec = duration,
        )
    }

    fun export(features: List<GeoFeature>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Offtrail Maps\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        // Reference behavior: GPX export carries placemarks, lines and tracks — areas are excluded.
        features.filter { it.type == FeatureType.PLACEMARK }.forEach { f ->
            val p = f.points.firstOrNull() ?: return@forEach
            sb.append("  <wpt lat=\"${p.lat}\" lon=\"${p.lon}\"><name>${f.name.escape()}</name><desc>${f.description.escape()}</desc></wpt>\n")
        }
        features.filter { it.type == FeatureType.LINE }.forEach { f ->
            sb.append("  <rte><name>${f.name.escape()}</name>\n")
            f.points.forEach { sb.append("    <rtept lat=\"${it.lat}\" lon=\"${it.lon}\"/>\n") }
            sb.append("  </rte>\n")
        }
        features.filter { it.type == FeatureType.TRACK }.forEach { f ->
            sb.append("  <trk><name>${f.name.escape()}</name><trkseg>\n")
            f.trackPoints.forEach {
                sb.append("    <trkpt lat=\"${it.lat}\" lon=\"${it.lon}\"><ele>${it.elevationM}</ele><time>${java.time.Instant.ofEpochMilli(it.timeEpochMs)}</time></trkpt>\n")
            }
            sb.append("  </trkseg></trk>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun org.w3c.dom.Element.lat() = getAttribute("lat").toDoubleOrNull() ?: 0.0
    private fun org.w3c.dom.Element.lon() = getAttribute("lon").toDoubleOrNull() ?: 0.0

    private fun org.w3c.dom.Element.textOf(tag: String): String? {
        val list = getElementsByTagName(tag)
        return if (list.length > 0) list.item(0).textContent else null
    }

    private fun MutableList<GeoFeature>.appendFeature(name: String, type: FeatureType, points: List<LatLon>, desc: String = "") {
        if (points.isEmpty()) return
        this += GeoFeature(
            id = com.offtrail.maps.model.AppRepository.newId(),
            name = name,
            type = type,
            points = points,
            description = desc,
        )
    }

    private fun String.escape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
