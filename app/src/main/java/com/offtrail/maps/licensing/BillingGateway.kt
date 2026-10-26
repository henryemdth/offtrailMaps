package com.offtrail.maps.licensing

/**
 * Pluggable purchase gateway. The reference app drives this through Google Play
 * Billing (SKUs `com.avenza.pro.oneyear` / `com.avenza.plus.oneyear`) and confirms
 * with its licensing server via updateWithInAppSubscription(); this mock gateway
 * grants the subscription locally so the full upgrade flow is exercisable without
 * store configuration. Swap in a Play Billing implementation behind the same
 * interface for production use.
 */
interface BillingGateway {
    suspend fun products(): List<BillingProduct>
    suspend fun purchase(productId: String): BillingProduct
}

class MockBillingGateway : BillingGateway {

    private val catalog = listOf(
        BillingProduct(
            id = "plus.oneyear",
            title = "Offtrail Maps (unlimited import)",
            description = "Import as many custom maps as you like and bring in GPX data.",
            price = "$19.99 / year",
        ),
        BillingProduct(
            id = "pro.oneyear",
            title = "Offtrail Maps Pro",
            description = "Everything in Plus, plus shapefiles, advanced coordinate systems, geofence tools and more.",
            price = "$29.99 / year",
        ),
    )

    override suspend fun products(): List<BillingProduct> = catalog

    override suspend fun purchase(productId: String): BillingProduct =
        catalog.first { it.id == productId }
}
