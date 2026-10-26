package com.offtrail.maps

import android.app.Application
import com.offtrail.maps.licensing.BillingGateway
import com.offtrail.maps.licensing.LicenseManager
import com.offtrail.maps.licensing.LicenseStore
import com.offtrail.maps.model.AppRepository
import org.osmdroid.config.Configuration
import java.io.File

class OfftrailApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(filesDir, "osmdroid/tiles")
        }
        AppRepository.init(this)
        LicenseManager.init(LicenseStore(this), billingGateway())
    }

    /** Swap for a Play-Billing-backed gateway in production builds. */
    private fun billingGateway(): BillingGateway = com.offtrail.maps.licensing.MockBillingGateway()
}
