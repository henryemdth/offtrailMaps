package com.offtrail.maps.io

import com.offtrail.maps.model.FeatureType
import com.offtrail.maps.model.GeoFeature
import com.offtrail.maps.model.LatLon
import java.io.BufferedReader
import java.io.InputStream

/**
 * CSV placemark import/export. Reference behavior: CSV export carries placemarks only
 * (export_csv_warning: "Lines and tracks won't be exported to CSV").
 */
object Csv {

    fun parse(stream: InputStream): List<GeoFeature> {
        val reader = stream.bufferedReader()
        val lines = mutableListOf<String>()
        reader.forEachLine { lines += it }
        if (lines.isEmpty()) return emptyList()

        val header = splitCsvLine(lines.first()).map { it.trim().lowercase() }
        val latIdx = header.indexOfFirst { it in setOf("lat", "latitude", "y") }
        val lonIdx = header.indexOfFirst { it in setOf("lon", "lng", "long", "longitude", "x") }
        val nameIdx = header.indexOfFirst { it in setOf("name", "title", "label") }
        val descIdx = header.indexOfFirst { it in setOf("desc", "description", "notes") }
        if (latIdx < 0 || lonIdx < 0) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val cells = splitCsvLine(line)
            val lat = cells.getOrNull(latIdx)?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = cells.getOrNull(lonIdx)?.toDoubleOrNull() ?: return@mapNotNull null
            GeoFeature(
                id = com.offtrail.maps.model.AppRepository.newId(),
                name = nameIdx.takeIf { it >= 0 }?.let { cells.getOrNull(it) } ?: "Placemark",
                type = FeatureType.PLACEMARK,
                points = listOf(LatLon(lat, lon)),
                description = descIdx.takeIf { it >= 0 }?.let { cells.getOrNull(it) } ?: "",
            )
        }
    }

    fun export(features: List<GeoFeature>): String {
        val sb = StringBuilder("name,latitude,longitude,description\n")
        features.filter { it.type == FeatureType.PLACEMARK }.forEach { f ->
            val p = f.points.firstOrNull() ?: return@forEach
            sb.append("\"${f.name}\",${p.lat},${p.lon},\"${f.description}\"\n")
        }
        return sb.toString()
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { out += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
        }
        out += cur.toString()
        return out
    }
}
