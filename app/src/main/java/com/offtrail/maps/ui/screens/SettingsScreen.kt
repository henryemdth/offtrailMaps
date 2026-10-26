package com.offtrail.maps.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.offtrail.maps.geo.AngleUnit
import com.offtrail.maps.geo.CoordinateFormat
import com.offtrail.maps.geo.DistanceUnit
import com.offtrail.maps.licensing.License
import com.offtrail.maps.licensing.LicenseManager
import com.offtrail.maps.licensing.LicenseType
import com.offtrail.maps.model.AppRepository
import com.offtrail.maps.ui.GateDialog
import com.offtrail.maps.ui.GateMessages

/**
 * Settings — mirrors the reference app's SettingsActivity sections: theme,
 * units (distance/angle with Pro-gated chains and mils), coordinate format
 * (Pro-gated BNG and What3Words), symbol management (Pro), feature display cap
 * ("Show Unlimited Number of Features", Pro), license info and database stats.
 */
@Composable
fun SettingsScreen(nav: NavHostController) {
    val data by AppRepository.data.collectAsState()
    var gate by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        SectionTitle("License")
        val license = LicenseManager.effectiveLicense()
        val type = LicenseManager.state.collectAsState().value.type
        Text(
            when (license) {
                License.NONE -> "Offtrail Maps (Free)"
                License.PLUS -> "Offtrail Maps (unlimited import)"
                License.PRO -> "Offtrail Maps Pro"
                License.PRO_FREE_TRIAL -> "Offtrail Maps Pro (30-day trial)"
            } + " • " + when (type) {
                LicenseType.SUBSCRIPTION_ON_DEVICE -> "subscription"
                LicenseType.PURCHASED_ON_DEVICE -> "purchased"
                LicenseType.REGISTERED -> "registered code"
                LicenseType.NONE -> "not licensed"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        LicenseManager.expiry()?.let { Text("Expires: $it", style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { nav.navigate("upgrade") }) { Text("Upgrade / Manage license") }

        SectionTitle("Theme")
        listOf("system" to "Use System Setting", "light" to "Light Theme", "dark" to "Dark Theme").forEach { (value, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = data.themeMode == value, onClick = { AppRepository.setSetting { it.copy(themeMode = value) } })
                Text(label)
            }
        }

        SectionTitle("Distance Units")
        com.offtrail.maps.geo.Units.availableDistanceUnits().forEach { unit ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = data.distanceUnit == unit.name,
                    onClick = {
                        if (unit.proOnly && !LicenseManager.canUseChains()) {
                            gate = GateMessages.forRequired(License.PRO, "measuring distances in Chains")
                        } else AppRepository.setSetting { it.copy(distanceUnit = unit.name) }
                    },
                )
                Text(unit.title + if (unit.proOnly) " (Pro)" else "")
            }
        }

        SectionTitle("Angle Units")
        com.offtrail.maps.geo.Units.availableAngleUnits().forEach { unit ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = data.angleUnit == unit.name,
                    onClick = {
                        if (unit.proOnly && !LicenseManager.canUseMilliRadians()) {
                            gate = GateMessages.forRequired(License.PRO, "measuring angles in milliradians")
                        } else AppRepository.setSetting { it.copy(angleUnit = unit.name) }
                    },
                )
                Text(unit.title + if (unit.proOnly) " (Pro)" else "")
            }
        }

        SectionTitle("Coordinate Format")
        CoordinateFormat.entries.forEach { format ->
            val available = CoordinateFormat.available().contains(format)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = data.coordinateFormat == format.name,
                    onClick = {
                        if (!available) {
                            gate = GateMessages.forRequired(
                                License.PRO,
                                when (format) {
                                    CoordinateFormat.BNG -> "viewing and entering coordinates using the British National Grid"
                                    CoordinateFormat.WHAT3WORDS -> "What3Words coordinates"
                                    else -> "this coordinate format"
                                },
                            )
                        } else AppRepository.setSetting { it.copy(coordinateFormat = format.name) }
                    },
                )
                Text(format.title + if (format.proOnly) " (Pro)" else "")
            }
        }

        SectionTitle("Symbols")
        TextButton(onClick = {
            if (!LicenseManager.canImportProIcons()) {
                gate = GateMessages.forRequired(License.PRO, "importing sets of custom symbology")
            } else {
                gate = Pair("Manage Symbols", "Symbol set import UI — pick a symbol set in the map viewer's feature editor.")
            }
        }) { Text("Manage Symbols") }

        SectionTitle("Map View")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val unlocked = data.showUnlimitedFeatures
            Switch(
                checked = unlocked,
                onCheckedChange = { checked ->
                    if (checked && !LicenseManager.isProLicense()) {
                        gate = GateMessages.forRequired(License.PRO, "removing the feature display restriction")
                    } else AppRepository.setSetting { it.copy(showUnlimitedFeatures = checked) }
                },
            )
            Spacer(Modifier.padding(4.dp))
            Text("Show Unlimited Number of Features (Pro)")
        }
        Text(
            "The number of placemarks, areas, tracks, and lines displayed in the map view is restricted to keep the app responsive (${com.offtrail.maps.licensing.TierLimits.DEFAULT_FEATURE_DISPLAY_CAP}).",
            style = MaterialTheme.typography.bodySmall,
        )

        SectionTitle("Geofences")
        Text(
            "Individual geofences: " + cap(LicenseManager.getMaxIndividualGeofenceCount()) +
                "\nLayer geofences: " + cap(LicenseManager.getMaxLayerGeofenceCount()) +
                "\nGlobal geofences (all maps): " + (if (LicenseManager.getAllowGlobalGeofences()) "included" else "Pro") +
                "\nMap-boundary alerts: " + (if (LicenseManager.getAllowMapGeofences()) "included" else "Pro"),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = { nav.navigate("upgrade") }) { Text("Learn about premium offerings") }

        SectionTitle("Database")
        val d = data
        Text(
            "Collections: ${d.collections.size}\nMaps: ${d.maps.size}\nFeatures: ${d.features.size}\nLayers: ${d.layers.size}\nGeofences: ${d.geofences.size}",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(24.dp))
    }

    gate?.let { (title, message) ->
        GateDialog(title, message, onDismiss = { gate = null }, onLearnMore = { nav.navigate("upgrade") })
    }
}

private fun cap(max: Int) = if (max == com.offtrail.maps.licensing.TierLimits.UNLIMITED) "unlimited" else max.toString()

@Composable
private fun SectionTitle(title: String) {
    HorizontalDivider(Modifier.padding(vertical = 10.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
}
