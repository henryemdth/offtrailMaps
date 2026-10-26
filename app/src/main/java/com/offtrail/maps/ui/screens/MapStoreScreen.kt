package com.offtrail.maps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.offtrail.maps.licensing.LicenseManager
import com.offtrail.maps.mapstore.MapStoreManager
import com.offtrail.maps.model.AppRepository
import com.offtrail.maps.model.MapStoreProduct

/**
 * Map Store — mirrors the reference app's MapStoreHomeActivity + ProductDetails:
 * catalog of free/paid maps, account gate before purchase/download
 * (authorization_required), version warnings, and open-after-install.
 */
@Composable
fun MapStoreScreen(nav: NavHostController) {
    val data by AppRepository.data.collectAsState()
    var selected by remember { mutableStateOf<MapStoreProduct?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var showRegister by remember { mutableStateOf(false) }

    val products = MapStoreManager.catalog

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Map Store", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        if (MapStoreManager.isAuthenticated()) {
            Row {
                Text("Signed in as ${MapStoreManager.account().email}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { MapStoreManager.signOut() }) { Text("Sign out") }
            }
        } else {
            TextButton(onClick = { showRegister = true }) { Text("Create a free Map Store account") }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products, key = { it.id }) { product ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(product.title, style = MaterialTheme.typography.titleSmall)
                        Text(product.publisher, style = MaterialTheme.typography.bodySmall)
                        Text(product.description, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Text(MapStoreManager.priceLabel(product), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            val installed = data.maps.any { it.storeProductId == product.id }
                            OutlinedButton(onClick = { selected = product }) {
                                Text(if (installed) "Open / Update" else if (product.priceCents == 0L) "Get" else "Buy")
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { product ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(product.title) },
            text = {
                Column {
                    Text(product.description)
                    Text("Version: ${product.version} • %.0f MB".format(product.sizeBytes / 1e6), style = MaterialTheme.typography.bodySmall)
                    val sizeMb = product.sizeBytes / 1_000_000
                    if (sizeMb > MapStoreManager.MAX_CELLULAR_DOWNLOAD_MB) {
                        Text(
                            "This map is ${sizeMb} MB. Downloads over ${MapStoreManager.MAX_CELLULAR_DOWNLOAD_MB} MB are typically done over Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = MapStoreManager.purchase(product)
                    when (result) {
                        is MapStoreManager.Acquisition.NeedsAccount -> {
                            dialog = result.message; showRegister = true
                        }
                        is MapStoreManager.Acquisition.VersionWarning -> dialog = if (result.sameOrNewer) {
                            "The same or a newer version of this product is already installed."
                        } else {
                            "A newer version of this product is available."
                        }
                        is MapStoreManager.Acquisition.Purchased -> {
                            when (val d = MapStoreManager.download(product)) {
                                is MapStoreManager.Acquisition.Downloaded -> dialog = "Downloaded “${d.product.title}”. It is now in My Maps."
                                else -> dialog = "Download failed."
                            }
                        }
                        else -> {}
                    }
                    selected = null
                }) { Text(if (product.priceCents == 0L) "Download" else "Buy") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } },
        )
    }

    if (showRegister) {
        RegisterAccountDialog(onDismiss = { showRegister = false })
    }

    dialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Map Store") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun RegisterAccountDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a Free Map Store Account") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                MapStoreManager.register(name, email)
                onDismiss()
            }) { Text("Create Account") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
