package com.offtrail.maps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.offtrail.maps.licensing.BillingProduct
import com.offtrail.maps.licensing.License
import com.offtrail.maps.licensing.LicenseManager
import com.offtrail.maps.licensing.LicenseType
import com.offtrail.maps.licensing.MockBillingGateway
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Upgrade flow — mirrors the reference app's UpgradeActivity/UpgradeHomeFragment:
 * Pro and Plus ("Unlock") offer cards with store prices, a 30-day Pro free-trial
 * registration form (hidden once used), and purchase buttons hidden for
 * license-code (REGISTERED) users.
 */
@Composable
fun UpgradeScreen(nav: NavHostController) {
    val state by LicenseManager.state.collectAsState()
    var products by remember { mutableStateOf<List<BillingProduct>>(emptyList()) }
    var showTrialForm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        products = MockBillingGateway().products()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Upgrade", style = MaterialTheme.typography.headlineSmall)
        }
        message?.let {
            Text(it, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
        }

        val expiry = LicenseManager.expiry(state)
        Text(
            when {
                state.type == LicenseType.REGISTERED ->
                    "License registered to ${state.userEmail} — code ${state.licenseCode}"
                expiry != null ->
                    "Current: ${state.license.name.lowercase().replaceFirstChar { it.uppercase() }} — expires ${
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(expiry)
                    }"
                else -> "Current edition: Offtrail Maps (Free)"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))

        // Pro card
        val proPrice = products.firstOrNull { it.id == "pro.oneyear" }
        OfferCard(
            title = "Offtrail Maps Pro",
            price = proPrice?.price ?: "",
            bullets = listOf(
                "Shapefile (SHP) import & export",
                "British National Grid, chains and milliradian units",
                "Placemark averaging and track-to-area conversion",
                "Custom symbol sets and unlimited geofences",
                "Global geofences and map-boundary alerts",
                "Lock map orientation",
            ),
            onPurchase = {
                LicenseManager.buyLicense(License.PRO) { ok, msg ->
                    message = msg
                }
            },
            purchaseEnabled = state.type != LicenseType.REGISTERED,
        )
        Spacer(Modifier.height(12.dp))

        // Plus card (hidden when a Pro REGISTERED license is active — reference behavior)
        if (!(LicenseManager.isLicensed() && state.license == License.PRO && state.type == LicenseType.REGISTERED)) {
            val plusPrice = products.firstOrNull { it.id == "plus.oneyear" }
            OfferCard(
                title = "Offtrail Maps (unlimited import)",
                price = plusPrice?.price ?: "",
                bullets = listOf(
                    "Unlimited custom map imports",
                    "GPX file import",
                ),
                onPurchase = {
                    LicenseManager.buyLicense(License.PLUS) { ok, msg -> message = msg }
                },
                purchaseEnabled = state.type != LicenseType.REGISTERED,
            )
        }
        Spacer(Modifier.height(12.dp))

        // 30-day Pro free trial
        if (!LicenseManager.hasUsedFreeTrial() && state.type != LicenseType.REGISTERED) {
            if (!showTrialForm) {
                Button(onClick = { showTrialForm = true }, Modifier.fillMaxWidth()) {
                    Text("Start 30 Day Free Trial")
                }
                Spacer(Modifier.height(8.dp))
            } else {
                TrialForm { ok, msg ->
                    message = msg
                    if (ok) showTrialForm = false
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("Have a license code?", style = MaterialTheme.typography.titleSmall)
        LicenseEntryForm { ok, msg -> message = msg }

        if (state.license != License.NONE) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                LicenseManager.deregisterLicense()
                message = "License deregistered on this device."
            }) { Text("Deregister license on this device") }
        }
    }
}

@Composable
private fun OfferCard(title: String, price: String, bullets: List<String>, onPurchase: () -> Unit, purchaseEnabled: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (price.isNotBlank()) Text(price, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            bullets.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onPurchase, enabled = purchaseEnabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (price.isBlank()) "Learn More" else "Purchase")
            }
        }
    }
}

@Composable
private fun TrialForm(onResult: (Boolean, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Please register for your free trial.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = { LicenseManager.startFreeTrial(name, email, onResult) }, modifier = Modifier.fillMaxWidth()) {
                Text("Register")
            }
        }
    }
}

@Composable
private fun LicenseEntryForm(onResult: (Boolean, String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("OT-PRO-XXXX-XXXX") }, modifier = Modifier.weight(1f))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.width(8.dp))
    OutlinedButton(onClick = { LicenseManager.registerLicense(code, email, onResult) }) {
        Text("Register Code")
    }
}
