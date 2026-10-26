package com.offtrail.maps.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.offtrail.maps.model.AppRepository

/**
 * Map details — mirrors MapDetailsActivity: metadata, open, export collection
 * position, delete (with tracked-map style warnings simplified), and manual
 * corner calibration for un-georeferenced imports.
 */
@Composable
fun MapDetailsScreen(nav: NavHostController, mapId: String?) {
    val data by AppRepository.data.collectAsState()
    val map = data.maps.firstOrNull { it.id == mapId }
    if (map == null) {
        Text("Map not found", Modifier.padding(16.dp))
        return
    }

    var tlLat by remember { mutableStateOf(map.topLeftLat?.toString() ?: "") }
    var tlLon by remember { mutableStateOf(map.topLeftLon?.toString() ?: "") }
    var brLat by remember { mutableStateOf(map.bottomRightLat?.toString() ?: "") }
    var brLon by remember { mutableStateOf(map.bottomRightLon?.toString() ?: "") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(map.name, style = MaterialTheme.typography.headlineSmall)
        }
        Text("Source: " + if (map.isFromStore()) "Map Store" else "Imported file", style = MaterialTheme.typography.bodyMedium)
        Text("Size: %.1f MB".format(map.sizeBytes / 1e6), style = MaterialTheme.typography.bodySmall)
        Text("Georeferenced: ${if (map.isGeoreferenced) "yes" else "no"}", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.padding(8.dp))
        Row {
            Button(onClick = { nav.navigate("map/${map.id}") }) { Text("Open Map") }
            Spacer(Modifier.padding(8.dp))
            OutlinedButton(onClick = {
                AppRepository.deleteMap(map.id)
                nav.popBackStack()
            }) { Text("Delete") }
        }

        if (!map.isGeoreferenced) {
            Spacer(Modifier.padding(8.dp))
            Text("Calibrate corners", style = MaterialTheme.typography.titleMedium)
            Text(
                "Enter the geographic coordinates of the top-left and bottom-right corners of the map image to georeference it.",
                style = MaterialTheme.typography.bodySmall,
            )
            androidx.compose.material3.OutlinedTextField(value = tlLat, onValueChange = { tlLat = it }, label = { Text("Top latitude") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(value = tlLon, onValueChange = { tlLon = it }, label = { Text("Left longitude") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(value = brLat, onValueChange = { brLat = it }, label = { Text("Bottom latitude") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(value = brLon, onValueChange = { brLon = it }, label = { Text("Right longitude") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val a = tlLat.toDoubleOrNull(); val b = tlLon.toDoubleOrNull()
                val c = brLat.toDoubleOrNull(); val d2 = brLon.toDoubleOrNull()
                if (a != null && b != null && c != null && d2 != null) {
                    AppRepository.update { current ->
                        current.copy(
                            maps = current.maps.map {
                                if (it.id == map.id) it.copy(
                                    topLeftLat = a, topLeftLon = b, bottomRightLat = c, bottomRightLon = d2,
                                    isGeoreferenced = true,
                                ) else it
                            }
                        )
                    }
                    nav.popBackStack()
                }
            }) { Text("Save Calibration") }
        }

        TextButton(onClick = { nav.popBackStack() }) { Text("Back") }
    }
}
