package com.offtrail.maps.geo

import com.offtrail.maps.licensing.LicenseManager
import kotlin.math.PI

/** Distance units; Chains are Pro-gated in the reference app. */
enum class DistanceUnit(val title: String, val proOnly: Boolean = false) {
    METRIC("Meters / Kilometers"),
    IMPERIAL("Feet / Yards / Miles"),
    NAUTICAL("Nautical miles"),
    CHAINS("Chains", proOnly = true);

    fun metersPerUnit(): Double = when (this) {
        METRIC -> 1.0
        IMPERIAL -> 0.3048
        NAUTICAL -> 1852.0
        CHAINS -> 20.1168
    }
}

/** Angle units; milliradians are Pro-gated in the reference app. */
enum class AngleUnit(val title: String, val proOnly: Boolean = false) {
    DEGREES("Degrees"),
    MILLIRADIANS("Milliradians", proOnly = true);
}

object Units {
    fun availableDistanceUnits(): List<DistanceUnit> =
        DistanceUnit.entries.filter { !it.proOnly || LicenseManager.canUseChains() }

    fun availableAngleUnits(): List<AngleUnit> =
        AngleUnit.entries.filter { !it.proOnly || LicenseManager.canUseMilliRadians() }

    fun formatDistance(meters: Double, unit: DistanceUnit): String = when (unit) {
        DistanceUnit.METRIC ->
            if (meters >= 1000) "%.2f km".format(meters / 1000) else "%.1f m".format(meters)
        DistanceUnit.IMPERIAL -> {
            val feet = meters / 0.3048
            if (feet >= 5280) "%.2f mi".format(feet / 5280)
            else if (feet >= 30) "%.0f yd".format(feet / 3)
            else "%.1f ft".format(feet)
        }
        DistanceUnit.NAUTICAL -> "%.2f NM".format(meters / 1852.0)
        DistanceUnit.CHAINS -> "%.2f ch".format(meters / 20.1168)
    }

    fun formatAngle(radians: Double, unit: AngleUnit): String = when (unit) {
        AngleUnit.DEGREES -> "%.1f°".format(Math.toDegrees(radians).mod(360.0))
        AngleUnit.MILLIRADIANS -> "%.0f mils".format((radians * 1000).mod(2 * PI * 1000))
    }
}
