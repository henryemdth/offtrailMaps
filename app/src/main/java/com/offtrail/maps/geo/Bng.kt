package com.offtrail.maps.geo

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * British National Grid (OSGB36) conversion — Pro-gated format in the reference app.
 * Uses the standard Helmert datum shift WGS84 → OSGB36 followed by the national
 * grid transverse-Mercator parameters and the 100 km square lettering.
 */
object Bng {

    private const val GRID_A = 6377563.396
    private const val GRID_F = 1 / 299.3249646
    private const val K0 = 0.9996012717
    private const val LAT0 = 49.0
    private const val LON0 = -2.0
    private const val E0 = 400000.0
    private const val N0 = -100000.0

    fun toBng(lat: Double, lon: Double): String {
        val (gLat, gLon, _) = helmertWgs84ToOsgb36(lat, lon)
        val (e, n) = toGrid(Math.toRadians(gLat), Math.toRadians(gLon))
        return "${lettersFor(e, n)} ${e.toInt() % 100000} ${n.toInt() % 100000}"
    }

    private fun lettersFor(e: Double, n: Double): String {
        // Grid letters are only defined for the British Isles; clamp so that
        // coordinates far outside the grid still render a stable reference.
        val bigE = (e / 500000).toInt().coerceIn(0, 4)
        val bigN = (n / 500000).toInt().coerceIn(0, 3)
        val bigLetters = listOf("v", "w", "x", "y", "z", "q", "r", "s", "t", "m", "n", "o", "p", "h", "j", "k", "l", "f", "g")
        val big = bigLetters[((4 - bigN) * 5 + bigE - 10).coerceIn(bigLetters.indices)]
        val smallE = ((e % 500000) / 100000).toInt().coerceIn(0, 4)
        val smallN = ((n % 500000) / 100000).toInt().coerceIn(0, 4)
        val smallLetters = "VWXYZQRSTU MNOPHJKLFG"
        val idx = ((4 - smallN) * 5 + smallE).coerceIn(0, smallLetters.length - 1)
        val small = smallLetters.filterNot { it == ' ' }.getOrElse(idx) { 'A' }
        return (big + small).uppercase()
    }

    private fun toGrid(phi: Double, lambda: Double): Pair<Double, Double> {
        val e2 = GRID_F * (2 - GRID_F)
        val a = GRID_A
        val n = a / sqrt(1 - e2 * sin(phi) * sin(phi))
        val t = kotlin.math.tan(phi) * kotlin.math.tan(phi)
        val c = (e2 / (1 - e2)) * cos(phi) * cos(phi)
        val aa = cos(phi) * (lambda - Math.toRadians(LON0))
        val m = a * ((1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * phi -
            (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2 / 1024) * sin(2 * phi) +
            (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * sin(4 * phi) -
            (35 * e2 * e2 * e2 / 3072) * sin(6 * phi))
        val easting = E0 + K0 * n * (aa + (1 - t + c) * aa * aa * aa / 6 +
            (5 - 18 * t + t * t + 72 * c - 58 * e2 / (1 - e2)) * aa.pow5() / 120)
        val northing = N0 + K0 * (m + n * kotlin.math.tan(phi) * (aa * aa / 2 +
            (5 - t + 9 * c + 4 * c * c) * aa.pow4() / 24 +
            (61 - 58 * t + t * t + 600 * c - 330 * e2 / (1 - e2)) * aa.pow6() / 720))
        return Pair(easting, northing)
    }

    private fun Double.pow4() = this * this * this * this
    private fun Double.pow5() = this * this * this * this * this
    private fun Double.pow6() = this * this * this * this * this * this

    /** Simplified Helmert transform (air-miles-accurate for display purposes). */
    private fun helmertWgs84ToOsgb36(latDeg: Double, lonDeg: Double): Triple<Double, Double, Double> {
        // OSGB36 parameters (m): tx=-446.448, ty=125.157, tz=-542.060, s=20.4894ppm,
        // rx=-0.1502", ry=-0.2470", rz=-0.8421"
        val tx = -446.448; val ty = 125.157; val tz = -542.060
        val s = 20.4894e-6
        val rx = Math.toRadians(-0.1502 / 3600)
        val ry = Math.toRadians(-0.2470 / 3600)
        val rz = Math.toRadians(-0.8421 / 3600)

        val a = 6378137.0
        val f = 1 / 298.257223563
        val e2 = f * (2 - f)
        val phi = Math.toRadians(latDeg)
        val lambda = Math.toRadians(lonDeg)
        val h = 0.0
        val nu = a / sqrt(1 - e2 * sin(phi) * sin(phi))
        val x1 = (nu + h) * cos(phi) * cos(lambda)
        val y1 = (nu + h) * cos(phi) * sin(lambda)
        val z1 = ((1 - e2) * nu + h) * sin(phi)

        val x2 = tx + (1 + s) * x1 - rz * y1 + ry * z1
        val y2 = ty + rz * x1 + (1 + s) * y1 - rx * z1
        val z2 = tz - ry * x1 + rx * y1 + (1 + s) * z1

        val a2 = 6377563.396
        val f2 = 1 / 299.3249646
        val e22 = f2 * (2 - f2)
        val p = kotlin.math.atan2(z2, sqrt(x2 * x2 + y2 * y2))
        var lat = p
        repeat(5) {
            val nu2 = a2 / sqrt(1 - e22 * sin(lat) * sin(lat))
            lat = kotlin.math.atan2(z2 + e22 * nu2 * sin(lat), sqrt(x2 * x2 + y2 * y2))
        }
        val lon = kotlin.math.atan2(y2, x2)
        return Triple(Math.toDegrees(lat), Math.toDegrees(lon), 0.0)
    }
}
