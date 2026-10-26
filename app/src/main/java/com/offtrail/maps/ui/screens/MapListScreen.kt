package com.offtrail.maps.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.offtrail.maps.io.ImportExportManager
import com.offtrail.maps.io.MapImportResult
import com.offtrail.maps.licensing.License
import com.offtrail.maps.model.AppRepository
import com.offtrail.maps.ui.GateMessages
import com.offtrail.maps.ui.GateDialog

/**
 * Map list with collections — mirrors the reference app's MapList: collections
 * (folders), imported maps, map store downloads, and the import entry points.
 * The Free import cap ("unlimited import" is the Plus benefit) is enforced here.
 */
@Composable
fun MapListScreen(nav: NavHostController) {
    val data by AppRepository.data.collectAsState()

    var showAddCollection by remember { mutableStateOf(false) }
    var showGate by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingFeatureUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val mapImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported map"
        when (val result = ImportExportManager.importMapImage(nav.context, uri, name, data.collections.firstOrNull()?.id)) {
            is MapImportResult.GateBlocked -> showGate = GateMessages.importLimit()
            is MapImportResult.Failure -> showGate = Pair("Import failed", result.message)
            is MapImportResult.Success -> {}
        }
    }

    val featureImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pendingFeatureUri = uri
    }

    // Feature import handled after gate check (GPX → Plus, SHP → Pro).
    pendingFeatureUri?.let { uri ->
        when (val result = ImportExportManager.importFeatures(nav.context, uri)) {
            is com.offtrail.maps.io.ImportResult.GateBlocked -> {
                val (t, m) = GateMessages.forRequired(if (result.required == License.PRO) License.PRO else License.PLUS, "importing this file type")
                showGate = Pair(t, m)
                pendingFeatureUri = null
            }
            is com.offtrail.maps.io.ImportResult.Success -> {
                result.features.forEach { AppRepository.addFeature(it.copy(mapId = null)) }
                pendingFeatureUri = null
            }
            is com.offtrail.maps.io.ImportResult.Failure -> {
                showGate = Pair("Import failed", result.message)
                pendingFeatureUri = null
            }
        }
    }

    showGate?.let { (title, message) ->
        GateDialog(title, message, onDismiss = { showGate = null }, onLearnMore = { nav.navigate("upgrade") })
    }

    if (showAddCollection) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCollection = false },
            title = { Text("New Collection") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) AppRepository.addCollection(name.trim())
                    showAddCollection = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddCollection = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = { featureImportLauncher.launch("*/*") }) {
                    Icon(Icons.Filled.Description, contentDescription = "Import features")
                }
                FloatingActionButton(onClick = {
                    if (AppRepository.canImportMoreMaps()) mapImportLauncher.launch("image/*")
                    else showGate = GateMessages.importLimit()
                }) { Icon(Icons.Filled.FileDownload, contentDescription = "Import map") }
                FloatingActionButton(onClick = { showAddCollection = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add collection")
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Maps", style = MaterialTheme.typography.headlineMedium)
                val tier = com.offtrail.maps.licensing.LicenseManager.effectiveLicense()
                Text(
                    "Edition: " + when (tier) {
                        License.NONE -> "Offtrail Maps (Free)"
                        License.PLUS -> "Offtrail Maps (unlimited import)"
                        License.PRO, License.PRO_FREE_TRIAL -> "Offtrail Maps Pro"
                    } + if (tier == License.PRO_FREE_TRIAL) " — trial" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            data.collections.sortedBy { it.order }.forEach { collection ->
                item(key = collection.id) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(collection.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { AppRepository.deleteCollection(collection.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete collection", tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                val maps = data.maps.filter { it.collectionId == collection.id }
                if (maps.isEmpty()) {
                    item { Text("No maps yet — import one or visit the Store.", style = MaterialTheme.typography.bodySmall) }
                }
                items(maps, key = { it.id }) { map ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Map, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(map.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (map.isGeoreferenced) "Georeferenced" else "Not calibrated",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = { nav.navigate("map/${map.id}") }) { Text("Open") }
                            TextButton(onClick = { nav.navigate("mapDetails/${map.id}") }) { Text("Details") }
                        }
                    }
                }
            }
        }
    }
}
