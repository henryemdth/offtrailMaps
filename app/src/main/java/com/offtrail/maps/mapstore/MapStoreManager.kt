package com.offtrail.maps.mapstore

import com.offtrail.maps.model.AppRepository
import com.offtrail.maps.model.MapStoreAccount
import com.offtrail.maps.model.MapStoreProduct

/**
 * Local replica of the Map Store behaviors observed in the reference app:
 *  - free/paid product catalog with per-product versions
 *  - account creation/sign-in required before purchases (authorization_required flow)
 *  - purchase → download → open, with same-or-newer-version warnings
 *  - cellular download-size warning (reference used a Remote Config limit, default 200 MB)
 * The catalog ships bundled; downloads are simulated by registering the product as installed.
 */
object MapStoreManager {

    const val MAX_CELLULAR_DOWNLOAD_MB = 200

    val catalog: List<MapStoreProduct> = listOf(
        MapStoreProduct(
            id = "catalog.natgeo.trailhead",
            title = "Trailhead Park Basemap",
            publisher = "Offtrail Sample Maps",
            description = "A free general-purpose outdoor basemap sample covering the demonstration area.",
            priceCents = 0,
            sizeBytes = 24_000_000,
        ),
        MapStoreProduct(
            id = "catalog.ridge.topo",
            title = "Ridgeline 1:25,000 Topo",
            publisher = "Offtrail Sample Maps",
            description = "Detailed topographic sample map with contours, suitable for hiking and navigation demos.",
            priceCents = 499,
            sizeBytes = 88_000_000,
        ),
        MapStoreProduct(
            id = "catalog.lake.fishing",
            title = "Clearwater Lakes Fishing Chart",
            publisher = "Angler Charts (sample)",
            description = "Sample lake chart with boat ramps and depth annotations (demo data).",
            priceCents = 299,
            version = 2,
            sizeBytes = 310_000_000,
        ),
    )

    fun account(): MapStoreAccount = AppRepository.data.value.account

    fun isAuthenticated(): Boolean = account().authenticated

    fun register(name: String, email: String) {
        AppRepository.setAccount(MapStoreAccount(name = name, email = email, authenticated = true))
    }

    fun signOut() {
        AppRepository.setAccount(MapStoreAccount())
    }

    sealed class Acquisition {
        data class NeedsAccount(val message: String) : Acquisition()
        data class VersionWarning(val sameOrNewer: Boolean) : Acquisition()
        data class CellularTooLarge(val sizeMb: Long) : Acquisition()
        data class Purchased(val product: MapStoreProduct) : Acquisition()
        data class Downloaded(val product: MapStoreProduct) : Acquisition()
    }

    /** Reference: ModalProductAcquisitionScreen.authorization_required + ProductPurchasing flow. */
    fun purchase(product: MapStoreProduct): Acquisition {
        if (!isAuthenticated()) {
            return Acquisition.NeedsAccount("Sign in or create a free Map Store account to purchase maps.")
        }
        val installed = AppRepository.mapById(product.id) != null
        if (installed) {
            val current = AppRepository.mapById(product.id)!!
            return if (product.version > current.version) {
                Acquisition.VersionWarning(sameOrNewer = false)
            } else {
                Acquisition.VersionWarning(sameOrNewer = true)
            }
        }
        if (product.priceCents > 0) {
            // Local mock purchase (reference: getProductSku → purchase → PurchasingStatusActivity).
            AppRepository.recordPurchase(product.id)
        }
        return Acquisition.Purchased(product)
    }

    fun download(product: MapStoreProduct): Acquisition {
        if (!isAuthenticated()) {
            return Acquisition.NeedsAccount("Sign in or create a free Map Store account to download maps.")
        }
        AppRepository.addMap(
            com.offtrail.maps.model.OfftrailMap(
                id = product.id,
                name = product.title,
                storeProductId = product.id,
                version = product.version,
                sizeBytes = product.sizeBytes,
                importedAtEpochSec = System.currentTimeMillis() / 1000,
            ),
        )
        return Acquisition.Downloaded(product)
    }

    fun priceLabel(product: MapStoreProduct): String =
        if (product.priceCents == 0L) "Free" else "$%.2f".format(product.priceCents / 100.0)
}
