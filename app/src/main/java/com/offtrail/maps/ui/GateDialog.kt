package com.offtrail.maps.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.offtrail.maps.R
import com.offtrail.maps.licensing.GateRefusal
import com.offtrail.maps.licensing.License
import com.offtrail.maps.licensing.LicenseManager
import com.offtrail.maps.licensing.TierLimits

/**
 * The replica's equivalent of the reference app's LicensingUtils.showProOnlyAlertWithMessage:
 * a tier-specific explainer dialog with a "Learn More" action that jumps to the upgrade screen.
 */
object GateMessages {

    fun forRequired(required: License, feature: String): Pair<String, String> = when (required) {
        License.PLUS -> Pair(
            "Feature unlocked with Plus",
            "This feature — $feature — requires a Plus or Pro subscription.",
        )
        License.PRO -> Pair(
            "Feature unlocked in Pro version",
            "The Pro version of Offtrail Maps unlocks $feature.",
        )
        else -> Pair("Upgrade", "Upgrade to use $feature.")
    }

    fun geofenceLimit(individual: Boolean): Pair<String, String> {
        val max = if (individual) LicenseManager.getMaxIndividualGeofenceCount() else LicenseManager.getMaxLayerGeofenceCount()
        val kind = if (individual) "geofences" else "layer geofences"
        return Pair(
            "Geofence limit reached",
            "Only $max $kind are available with your version of the app. If you require additional geofences, tap Learn More to see our premium offerings.",
        )
    }

    fun importLimit(): Pair<String, String> = Pair(
        "Import limit reached",
        "The Free edition stores up to ${TierLimits.maxImportedMaps(com.offtrail.maps.licensing.License.NONE)} custom maps. \"Offtrail Maps (unlimited import)\" removes the limit.",
    )
}

@Composable
fun GateDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onLearnMore: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onLearnMore() }) { Text("Learn More") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Convenience wrapper matching the original's per-feature message style. */
@Composable
fun ProGateDialog(feature: String, onDismiss: () -> Unit, onLearnMore: () -> Unit) {
    val (title, message) = GateMessages.forRequired(License.PRO, feature)
    GateDialog(title, message, onDismiss, onLearnMore)
}
