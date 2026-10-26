package com.offtrail.maps.io

import com.offtrail.maps.model.FeatureType
import com.offtrail.maps.model.GeoFeature
import com.offtrail.maps.model.LatLon
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal ESRI Shapefile (.shp) reader for point / polyline / polygon geometries.
 * The reference app gates shapefile import and export behind Pro
 * (canUseShapeFiles / shape_pro_only_message); this reader is intentionally
 * geometry-only (no .dbf attribute parsing) and is called exclusively from the
 * Pro-gated import path.
 */
object Shp {

    private const val SHAPE_POINT = 1
    private const val SHAPE_POLYLINE = 3
    private const val SHAPE_POLYGON = 5

    fun parse(stream: InputStream): List<GeoFeature> {
        val bytes = stream.readBytes()
        if (bytes.size < 100) return emptyList()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(32)
        val shapeTypeFile = buf.int
        if (shapeTypeFile !in intArrayOf(SHAPE_POINT, SHAPE_POLYLINE, SHAPE_POLYGON)) return emptyList()

        val features = mutableListOf<GeoFeature>()
        buf.position(100)
        while (buf.remaining() >= 8) {
            // Record header (record number + content length in 16-bit words) is big-endian.
            val header = ByteBuffer.wrap(bytes, buf.position(), 8).order(ByteOrder.BIG_ENDIAN)
            header.int // record number
            val contentLength = header.int * 2
            if (contentLength <= 0 || contentLength > buf.remaining() - 8) break
            buf.position(buf.position() + 8)
            val recordStart = buf.position()
            val type = buf.int
            when (type) {
                SHAPE_POINT -> {
                    val x = buf.double; val y = buf.double
                    features += feature("Point", FeatureType.PLACEMARK, listOf(LatLon(y, x)))
                }
                SHAPE_POLYLINE, SHAPE_POLYGON -> {
                    buf.double; buf.double; buf.double; buf.double // bbox
                    val numParts = buf.int
                    val numPoints = buf.int
                    val parts = IntArray(numParts) { buf.int }
                    val points = Array(numPoints) { val x = buf.double; val y = buf.double; LatLon(y, x) }
                    if (numParts <= 1) {
                        val shapeType = if (type == SHAPE_POLYGON) FeatureType.AREA else FeatureType.LINE
                        features += feature("Shape", shapeType, points.toList())
                    } else {
                        // Multi-part: import each part as its own feature.
                        for (p in 0 until numParts) {
                            val from = parts[p]
                            val to = if (p + 1 < numParts) parts[p + 1] else numPoints
                            val part = points.copyOfRange(from, to).toList()
                            if (part.isNotEmpty()) {
                                val shapeType = if (type == SHAPE_POLYGON) FeatureType.AREA else FeatureType.LINE
                                features += feature("Shape ${p + 1}", shapeType, part)
                            }
                        }
                    }
                }
                else -> buf.position(recordStart + contentLength)
            }
            buf.position(recordStart + contentLength)
        }
        return features
    }

    private fun feature(name: String, type: FeatureType, points: List<LatLon>) = GeoFeature(
        id = com.offtrail.maps.model.AppRepository.newId(),
        name = name,
        type = type,
        points = points,
    )
}
