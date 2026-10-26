package com.offtrail.maps.licensing

import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application-wide licensing state machine. Function names intentionally mirror
 * the reference app's com.Avenza.Licensing.LicensingManager so the replica's
 * gating call sites read the same way.
 */
object LicenseManager {

    /** Mirrors the LICENSING_CHANGED broadcast: emitted after every state transition. */
    private val _licensingChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val licensingChanged: SharedFlow<Unit> = _licensingChanged.asSharedFlow()

    private val _state = MutableStateFlow(LicenseState())
    val state: StateFlow<LicenseState> = _state.asStateFlow()

    private lateinit var store: LicenseStore
    private lateinit var billing: BillingGateway
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 30-day Pro free trial, device-scoped (reference: free_trial / hasUsedFreeTrial). */
    private const val TRIAL_LENGTH_DAYS = 30L
    private const val SECONDS_PER_DAY = 86_400L

    fun init(store: LicenseStore, billing: BillingGateway) {
        this.store = store
        this.billing = billing
//        scope.launch {
//            _state.value = resolveExpiry(store.current())
//        }
        scope.launch {
            // UNLOCK-ALL: permanent Pro, ignores any previously stored state.
            val s = store.current().copy(
                license = License.PRO,
                type = LicenseType.PURCHASED_ON_DEVICE,
                expiryEpochSec = 4102444800L, // 2100-01-01; avoid 0 so the UI shows an expiry
            )
            store.save(s)
            _state.value = s
            _licensingChanged.emit(Unit)
        }
    }

    // ---------------------------------------------------------------- state

    /** License tier after expiry is applied (an expired trial or subscription is NONE). */
    fun effectiveLicense(s: LicenseState = _state.value): License {
        val resolved = resolveExpiry(s)
        return resolved.license
    }

    private fun resolveExpiry(s: LicenseState): LicenseState {
        if (s.license == License.NONE) return s
        if (s.expiryEpochSec <= 0) return s
        val now = System.currentTimeMillis() / 1000
        return if (now >= s.expiryEpochSec) {
            s.copy(license = License.NONE, type = LicenseType.NONE)
        } else s
    }

    fun expiry(s: LicenseState = _state.value): Date? {
        val resolved = resolveExpiry(s)
        return if (resolved.expiryEpochSec <= 0) null else Date(resolved.expiryEpochSec * 1000)
    }

    // ------------------------------------------------------------- gate API

    /** Any paid tier, valid right now. */
    fun hasValidLicense(): Boolean =
        hasValidLicense(License.PRO) || hasValidLicense(License.PRO_FREE_TRIAL) || hasValidLicense(License.PLUS)

    fun isLicensed(): Boolean = _state.value.license != License.NONE

    fun isProLicense(): Boolean =
        hasValidLicense(License.PRO) || hasValidLicense(License.PRO_FREE_TRIAL)

    fun hasValidLicense(license: License): Boolean {
        val current = effectiveLicense()
        if (current != license) return false
        return expiry() == null || expiry()!!.after(Date())
    }

    // Pro-only gates (mirror canUse* / canImportProIcons call sites).
    fun canImportProIcons(): Boolean = isProLicense()
    fun canUseBNGFormat(): Boolean = isProLicense()
    fun canUseChains(): Boolean = isProLicense()
    fun canUseLockOrientation(): Boolean = isProLicense()
    fun canUseMilliRadians(): Boolean = isProLicense()
    fun canUsePlacemarkAveraging(): Boolean = isProLicense()
    fun canUseShapeFiles(): Boolean = isProLicense()
    fun canImportGpx(): Boolean = hasValidLicense() // Plus or Pro
    fun getAllowGlobalGeofences(): Boolean = isProLicense()
    fun getAllowMapGeofences(): Boolean = isProLicense()
    fun getMaxIndividualGeofenceCount(): Int = TierLimits.maxIndividualGeofences(effectiveLicense())
    fun getMaxLayerGeofenceCount(): Int = TierLimits.maxLayerGeofences(effectiveLicense())
    fun getMaxImportedMaps(): Int = TierLimits.maxImportedMaps(effectiveLicense())

    /**
     * What stands between the current tier and [required] — used by the UI to pick
     * the right upsell dialog (reference: showProOnlyAlertWithMessage).
     */
    fun refusalFor(required: License): GateRefusal = when {
        hasValidLicense(required) -> GateRefusal.FREE // not actually refused
        required == License.PRO && hasValidLicense(License.PLUS) -> GateRefusal.PRO_REQUIRED
        required == License.PLUS || required == License.PRO -> GateRefusal.PLUS_REQUIRED
        _state.value.license != License.NONE -> GateRefusal.EXPIRED
        else -> GateRefusal.FREE
    }

    // ------------------------------------------------------------ mutations

    fun startFreeTrial(userName: String, userEmail: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            val s = store.current()
            if (s.hasUsedFreeTrial) {
                withContext(Dispatchers.Main) { onResult(false, "Free trial for this device has already been used.") }
                return@launch
            }
            val now = System.currentTimeMillis() / 1000
            val updated = s.copy(
                license = License.PRO_FREE_TRIAL,
                type = LicenseType.SUBSCRIPTION_ON_DEVICE,
                expiryEpochSec = now + TRIAL_LENGTH_DAYS * SECONDS_PER_DAY,
                userName = userName,
                userEmail = userEmail,
                hasUsedFreeTrial = true,
            )
            store.save(updated)
            _state.value = updated
            _licensingChanged.emit(Unit)
            withContext(Dispatchers.Main) { onResult(true, "Pro free trial started — enjoy $TRIAL_LENGTH_DAYS days of Pro.") }
        }
    }

    fun hasUsedFreeTrial(): Boolean = _state.value.hasUsedFreeTrial

    /** Hidden in the reference app too (resetHasUsedFreeTrial). */
    fun resetHasUsedFreeTrial() {
        scope.launch {
            val updated = store.current().copy(hasUsedFreeTrial = false)
            store.save(updated)
            _state.value = updated
            _licensingChanged.emit(Unit)
        }
    }

    fun buyLicense(license: License, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                val productId = if (license == License.PRO) "pro.oneyear" else "plus.oneyear"
                billing.purchase(productId)
                grantSubscription(license)
                withContext(Dispatchers.Main) { onResult(true, "Subscribed to ${if (license == License.PRO) "Pro" else "Plus"}.") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "Purchase failed.") }
            }
        }
    }

    /** Reference: updateWithInAppSubscription(license, expiry, orderId, …). */
    private suspend fun grantSubscription(license: License) {
        val now = System.currentTimeMillis() / 1000
        val updated = store.current().copy(
            license = license,
            type = LicenseType.SUBSCRIPTION_ON_DEVICE,
            expiryEpochSec = now + 365 * SECONDS_PER_DAY,
        )
        store.save(updated)
        _state.value = updated
        _licensingChanged.emit(Unit)
    }

    /** Reference: registerLicense(code, email, …) with a REGISTERED (license-code) type. */
    fun registerLicense(code: String, email: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            if (!isWellFormedLicenseCode(code)) {
                withContext(Dispatchers.Main) { onResult(false, "That license code is not well-formed.") }
                return@launch
            }
            if (!isWellFormedEmail(email)) {
                withContext(Dispatchers.Main) { onResult(false, "Please enter a valid email address.") }
                return@launch
            }
            val tier = tierForCode(code)
            val now = System.currentTimeMillis() / 1000
            val updated = store.current().copy(
                license = tier,
                type = LicenseType.REGISTERED,
                expiryEpochSec = now + 365 * SECONDS_PER_DAY,
                licenseCode = code,
                userEmail = email,
            )
            store.save(updated)
            _state.value = updated
            _licensingChanged.emit(Unit)
            withContext(Dispatchers.Main) { onResult(true, "License registered.") }
        }
    }

    fun isWellFormedEmail(email: String): Boolean =
        Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email)

    /** Reference format check; codes look like OT-PRO-XXXX-XXXX / OT-PLS-XXXX-XXXX. */
    fun isWellFormedLicenseCode(code: String): Boolean =
        Regex("^OT-(PRO|PLS)-[A-Z0-9]{4}-[A-Z0-9]{4}$").matches(code.uppercase())

    private fun tierForCode(code: String): License =
        if (code.uppercase().contains("-PRO-")) License.PRO else License.PLUS

    fun renewLicense() {
        scope.launch {
            val s = store.current()
            if (s.license == License.NONE) return@launch
            val now = System.currentTimeMillis() / 1000
            val updated = s.copy(expiryEpochSec = maxOf(now, s.expiryEpochSec) + 365 * SECONDS_PER_DAY)
            store.save(updated)
            _state.value = updated
            _licensingChanged.emit(Unit)
        }
    }

    fun deregisterLicense() {
        scope.launch {
            val updated = store.current().copy(
                license = License.NONE,
                type = LicenseType.NONE,
                expiryEpochSec = 0,
                licenseCode = "",
            )
            store.save(updated)
            _state.value = updated
            _licensingChanged.emit(Unit)
        }
    }
}
