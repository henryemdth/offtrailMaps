package com.offtrail.maps.licensing

import kotlinx.serialization.Serializable

/**
 * License tiers, mirroring the tier model observed in the reference app
 * (enum License { NONE, PLUS, PRO, PRO_FREE_TRIAL }).
 */
enum class License {
    NONE,
    PLUS,
    PRO,
    PRO_FREE_TRIAL;

    val isPaidTier: Boolean get() = this == PLUS || this == PRO || this == PRO_FREE_TRIAL
}

/**
 * How the license is held on device, mirroring the observed
 * LicenseType { NONE, SUBSCRIPTION_ON_DEVICE, PURCHASED_ON_DEVICE, REGISTERED }.
 */
enum class LicenseType {
    NONE,
    SUBSCRIPTION_ON_DEVICE,
    PURCHASED_ON_DEVICE,
    REGISTERED
}

/** Products offered in the upgrade flow (original SKUs: com.avenza.pro.oneyear / com.avenza.plus.oneyear). */
@Serializable
data class BillingProduct(val id: String, val title: String, val description: String, val price: String)

@Serializable
data class LicenseState(
    val license: License = License.NONE,
    val type: LicenseType = LicenseType.NONE,
    val expiryEpochSec: Long = 0L,
    val userName: String = "",
    val userEmail: String = "",
    val organization: String = "",
    val licenseCode: String = "",
    val hasUsedFreeTrial: Boolean = false,
)

/** Why a gate refused access — drives which upsell dialog the UI shows. */
enum class GateRefusal { FREE, PLUS_REQUIRED, PRO_REQUIRED, EXPIRED }
