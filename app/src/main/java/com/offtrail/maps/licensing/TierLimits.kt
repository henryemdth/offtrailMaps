package com.offtrail.maps.licensing

import com.offtrail.maps.licensing.License as Tier

/**
 * Per-tier numeric caps.
 *
 * In the reference app these values are computed inside the native core
 * (getMaxIndividualGeofencesCount / getMaxGeofenceLayerCount / …) and return
 * -1 for "unlimited"; the exact production numbers are not observable from
 * static analysis, so they are exposed here as clearly-marked, configurable
 * defaults with the same semantics.
 */
object TierLimits {
    const val UNLIMITED = -1

    // Geofence caps (defaults chosen; adjust to match a specific deployment).
    fun maxIndividualGeofences(tier: Tier): Int = when (tier) {
        Tier.PRO, Tier.PRO_FREE_TRIAL -> UNLIMITED
        Tier.PLUS -> 5
        Tier.NONE -> 1
    }

    fun maxLayerGeofences(tier: Tier): Int = when (tier) {
        Tier.PRO, Tier.PRO_FREE_TRIAL -> UNLIMITED
        Tier.PLUS -> 1
        Tier.NONE -> 0
    }

    // "Unlimited import" is the Plus headline benefit; Free caps custom-map imports.
    fun maxImportedMaps(tier: Tier): Int = when (tier) {
        Tier.NONE -> 3
        else -> UNLIMITED
    }

    // Map-view feature display cap; Pro can disable it ("Show Unlimited Number of Features").
    const val DEFAULT_FEATURE_DISPLAY_CAP = 500
}
