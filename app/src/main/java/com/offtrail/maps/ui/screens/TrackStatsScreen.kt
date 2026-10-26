package com.offtrail.maps.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.offtrail.maps.licensing.License
import com.offtrail.maps.licensing.LicenseManager
import com.offtrail.maps.geo.DistanceUnit
import com.offtrail.maps.geo.Units
import com.offtrail.maps.model.AppRepository
import com.offtrail.maps.model.FeatureType
import com.offtrail.maps.ui.GateDialog
import com.offtrail.maps.ui.GateMessages

/**
 * Track statistics — mirrors TrackStatsActivity: recorded tracks with distance,
 * duration, elevation gain, and the Pro-gated track→area conversion
 * (needs 3+ points, reference: need_3_or_more_track_points).
 */
@Composable
fun TrackStatsScreen(nav: NavHostController) {
    val data by AppRepository.data.collectAsState()
    var gate by remember { mutableStateOf<Pair<String, String>?>(null) }
    val unit = remember(data.distanceUnit) {
        runCatching { DistanceUnit.valueOf(data.distanceUnit) }.getOrDefault(DistanceUnit.METRIC)
    }

    val tracks = data.features.filter { it.type == FeatureType.TRACK }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Track Statistics", style = MaterialTheme.typography.headlineSmall)
        }
        if (tracks.isEmpty()) {
            Text("No tracks recorded yet — start tracking from a map view.")
        }
        LazyColumn {
            items(tracks, key = { it.id }) { track ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(track.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Dist: ${Units.formatDistance(track.distanceMeters, unit)} | " +
                                "${track.durationSec / 60} min | gain ${"%.0f".format(track.elevationGainM)} m | ${track.trackPoints.size} points",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(onClick = {
                                if (track.trackPoints.size < 3) {
                                    gate = Pair("Convert to Area", "A track must have 3 or more points to convert into an area.")
                                } else if (!LicenseManager.isProLicense()) {
                                    gate = GateMessages.forRequired(License.PRO, "converting a recorded track into an area")
                                } else {
                                    AppRepository.addFeature(
                                        track.copy(
                                            id = AppRepository.newId(),
                                            name = "${track.name} (area)",
                                            type = FeatureType.AREA,
                                            areaSqm = com.offtrail.maps.geo.Projections.polygonAreaM2(track.points.map { Pair(it.lat, it.lon) }),
                                        )
                                    )
                                }
                            }) { Text("Convert to Area") }
                            TextButton(onClick = { AppRepository.deleteFeature(track.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    gate?.let { (title, message) ->
        GateDialog(title, message, onDismiss = { gate = null }, onLearnMore = { nav.navigate("upgrade") })
    }
}
