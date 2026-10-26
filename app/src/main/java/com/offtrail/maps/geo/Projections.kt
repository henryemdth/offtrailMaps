package com.offtrail.maps.geo

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * UTM projection (WGS84, k0 = 0.9996) and MGRS grid reference encoding.
 * Standard transverse-Mercator series (Snyder, "Map Projections — A Working Manual").
 */
object Projections {

    data class Utm(val zone: Int, val band: Char, val easting: Double, val northing: Double, val isSouth: Boolean)

    private const val A = 6378137.0
    private const val F = 1 / 298.257223563
    private const val K0 = 0.9996
    private val E2 = F * (2 - F)
    private val EP2 = E2 / (1 - E2)

    fun zoneFor(lon: Double): Int = ((lon + 180) / 6).toInt().coerceIn(0, 59) + 1

    fun bandFor(lat: Double): Char = when {
        lat < -80 -> 'A'
        lat < -72 -> 'C'
        lat < -64 -> 'D'
        lat < -56 -> 'E'
        lat < -48 -> 'F'
        lat < -40 -> 'G'
        lat < -32 -> 'H'
        lat < -24 -> 'J'
        lat < -16 -> 'K'
        lat < -8 -> 'L'
        lat < 0 -> 'M'
        lat < 8 -> 'N'
        lat < 16 -> 'P'
        lat < 24 -> 'Q'
        lat < 32 -> 'R'
        lat < 40 -> 'S'
        lat < 48 -> 'T'
        lat < 56 -> 'U'
        lat < 64 -> 'V'
        lat < 72 -> 'W'
        else -> 'X'
    }

    fun toUtm(lat: Double, lon: Double): Utm {
        val zone = zoneFor(lon)
        val lonOrigin = (zone - 1) * 6 - 180 + 3
        val phi = Math.toRadians(lat)
        val dLambda = Math.toRadians(lon - lonOrigin)

        val n = A / sqrt(1 - E2 * sin(phi).pow(2))
        val t = tan(phi).pow(2)
        val c = EP2 * cos(phi).pow(2)
        val aa = cos(phi) * dLambda
        val m = A * ((1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256) * phi -
            (3 * E2 / 8 + 3 * E2 * E2 / 32 + 45 * E2 * E2 * E2 / 1024) * sin(2 * phi) +
            (15 * E2 * E2 / 256 + 45 * E2 * E2 * E2 / 1024) * sin(4 * phi) -
            (35 * E2 * E2 * E2 / 3072) * sin(6 * phi))

        val easting = K0 * n * (aa + (1 - t + c) * aa.pow(3) / 6 +
            (5 - 18 * t + t * t + 72 * c - 58 * EP2) * aa.pow(5) / 120) + 500000.0
        var northing = K0 * (m + n * tan(phi) * (aa.pow(2) / 2 +
            (5 - t + 9 * c + 4 * c * c) * aa.pow(4) / 24 +
            (61 - 58 * t + t * t + 600 * c - 330 * EP2) * aa.pow(6) / 720))
        val isSouth = lat < 0
        if (isSouth) northing += 10000000.0
        return Utm(zone, bandFor(lat), easting, northing, isSouth)
    }

    fun fromUtm(zone: Int, isSouth: Boolean, easting: Double, northing: Double): Pair<Double, Double> {
        val x = easting - 500000.0
        val y = if (isSouth) northing - 10000000.0 else northing

        val m = y / K0
        val mu = m / (A * (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256))
        val e1 = (1 - sqrt(1 - E2)) / (1 + sqrt(1 - E2))
        val phi1 = mu + (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
            (21 * e1 * e1 / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
            (151 * e1.pow(3) / 96) * sin(6 * mu) + (1097 * e1.pow(4) / 512) * sin(8 * mu)

        val n1 = A / sqrt(1 - E2 * sin(phi1).pow(2))
        val t1 = tan(phi1).pow(2)
        val c1 = EP2 * cos(phi1).pow(2)
        val r1 = A * (1 - E2) / (1 - E2 * sin(phi1).pow(2)).pow(1.5)
        val d = x / (n1 * K0)

        val lat = phi1 - (n1 * tan(phi1) / r1) * (d * d / 2 -
            (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * EP2) * d.pow(4) / 24 +
            (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * EP2 - 3 * c1 * c1) * d.pow(6) / 720)
        val lon = Math.toRadians((zone * 6 - 183).toDouble()) + (d - (1 + 2 * t1 + c1) * d.pow(3) / 6 +
            (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * EP2 + 24 * t1 * t1) * d.pow(5) / 120) / cos(phi1)

        return Pair(Math.toDegrees(lat), Math.toDegrees(lon))
    }

    // ------------------------------------------------------------------ MGRS

    private val colLetters = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val rowLetters = "ABCDEFGHJKLMNPQRSTUV"

    fun toMgrs(lat: Double, lon: Double): String {
        val utm = toUtm(lat, lon)
        // 100 km column letters cycle A–H, J–R, S–Z across zone groups (every 3 zones).
        val colStart = ((utm.zone - 1) % 3) * 8
        val colIndex = (utm.easting / 100000).toInt().coerceIn(0, 7)
        val col = colLetters[(colStart + colIndex) % 24]
        // Row letters shift by five positions on even zone-groups (AFGH… alphabet).
        val set = ((utm.zone - 1) % 6) + 1
        val rowIndex = (utm.northing / 100000).toInt() % 20
        val row = rowLetters[if (set % 2 == 0) (rowIndex + 5) % 20 else rowIndex]
        val e = (utm.easting % 100000).toInt()
        val n = (utm.northing % 100000).toInt()
        return "%d%s %s%s %05d %05d".format(utm.zone, utm.band, col, row, e, n)
    }

    /** Great-circle distance in meters (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371008.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    fun bearingRadians(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (atan2(y, x) + 2 * Math.PI) % (2 * Math.PI)
    }

    /** Spherical polygon area in m² (approximate; adequate for display). */
    fun polygonAreaM2(points: List<Pair<Double, Double>>): Double {
        if (points.size < 3) return 0.0
        val r = 6371008.8
        var total = 0.0
        for (i in points.indices) {
            val (lat1, lon1) = points[i]
            val (lat2, lon2) = points[(i + 1) % points.size]
            total += Math.toRadians(lon2 - lon1) * (2 + sin(Math.toRadians(lat1)) + sin(Math.toRadians(lat2)))
        }
        val area = total * r * r / 2
        return abs(area)
    }
}
