package com.offtrail.maps.geo

import com.offtrail.maps.licensing.LicenseManager

/**
 * Coordinate display formats, mirroring the reference app's CoordinateFormat enum:
 * DD/DMS/DDM (signed and hemisphere-named) on WGS84, UTM (zone + easting/northing),
 * MGRS, British National Grid (Pro), What3Words (Pro, requires API key).
 */
enum class CoordinateFormat(val title: String, val proOnly: Boolean = false) {
    DD_SIGNED("Degrees (signed)"),
    DD_NAMED("Degrees (named)"),
    DMS_SIGNED("Degrees Minutes Seconds (signed)"),
    DMS_NAMED("Degrees Minutes Seconds (named)"),
    DDM_SIGNED("Degrees Decimal Minutes (signed)"),
    DDM_NAMED("Degrees Decimal Minutes (named)"),
    UTM("UTM Zone Easting Northing"),
    MGRS("MGRS"),
    BNG("British National Grid", proOnly = true),
    WHAT3WORDS("What3Words", proOnly = true);

    companion object {
        fun available(): List<CoordinateFormat> = entries.filter { !it.proOnly || LicenseManager.isProLicense() }
        fun isProFormat(format: CoordinateFormat): Boolean = format.proOnly
    }
}

object CoordinateFormatter {

    fun format(lat: Double, lon: Double, format: CoordinateFormat): String = when (format) {
        CoordinateFormat.DD_SIGNED -> "%.6f, %.6f".format(lat, lon)
        CoordinateFormat.DD_NAMED -> "%s %.6f, %s %.6f".format(ns(lat), kotlin.math.abs(lat), ew(lon), kotlin.math.abs(lon))
        CoordinateFormat.DMS_SIGNED -> "%s, %s".format(dms(lat, true), dms(lon, false))
        CoordinateFormat.DMS_NAMED -> "%s %s, %s %s".format(ns(lat), dms(lat, true), ew(lon), dms(lon, false))
        CoordinateFormat.DDM_SIGNED -> "%s, %s".format(ddm(lat, true), ddm(lon, false))
        CoordinateFormat.DDM_NAMED -> "%s %s, %s %s".format(ns(lat), ddm(lat, true), ew(lon), ddm(lon, false))
        CoordinateFormat.UTM -> Projections.toUtm(lat, lon).let { "%d%s %dE %dN".format(it.zone, it.band, it.easting.toInt(), it.northing.toInt()) }
        CoordinateFormat.MGRS -> Projections.toMgrs(lat, lon)
        CoordinateFormat.BNG -> Bng.toBng(lat, lon)
        CoordinateFormat.WHAT3WORDS -> "///(requires What3Words API key)"
    }

    private fun ns(lat: Double) = if (lat >= 0) "N" else "S"
    private fun ew(lon: Double) = if (lon >= 0) "E" else "W"

    private fun dms(value: Double, latitude: Boolean): String {
        val dir = if (latitude) (if (value >= 0) "" else "-") else (if (value >= 0) "" else "-")
        val v = kotlin.math.abs(value)
        val d = v.toInt()
        val mFull = (v - d) * 60
        val m = mFull.toInt()
        val s = (mFull - m) * 60
        return "%s%d°%02d'%04.1f\"".format(dir, d, m, s)
    }

    private fun ddm(value: Double, latitude: Boolean): String {
        val dir = if (latitude) (if (value >= 0) "" else "-") else (if (value >= 0) "" else "-")
        val v = kotlin.math.abs(value)
        val d = v.toInt()
        val m = (v - d) * 60
        return "%s%d°%.4f'".format(dir, d, m)
    }
}
