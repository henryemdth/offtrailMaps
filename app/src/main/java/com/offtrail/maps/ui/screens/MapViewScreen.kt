package com.offtrail.maps.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.offtrail.maps.geo.CoordinateFormat
import com.offtrail.maps.geo.CoordinateFormatter
import com.offtrail.maps.geo.DistanceUnit
import com.offtrail.maps.geo.Projections
import com.offtrail.maps.geo.Units
import com.offtrail.maps.io.ImportExportManager
import com.offtrail.maps.licensing.License
import com.offtrail.maps.licensing.LicenseManager
import com.offtrail.maps.model.AppRepository
import com.offtrail.maps.model.FeatureType
import com.offtrail.maps.model.Geofence
import com.offtrail.maps.model.GeofenceScope
import com.offtrail.maps.model.GeoFeature
import com.offtrail.maps.model.LatLon
import com.offtrail.maps.model.OfftrailMap
import com.offtrail.maps.tracking.TrackingService
import com.offtrail.maps.ui.GateDialog
import com.offtrail.maps.ui.GateMessages
import kotlinx.coroutines.delay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapViewScreen(nav: NavHostController, mapId: String?) {
    val context = LocalContext.current
    val data by AppRepository.data.collectAsState()
    val map = remember(mapId) { AppRepository.mapById(mapId ?: "") }

    var selectedFeature by remember { mutableStateOf<GeoFeature?>(null) }
    var gate by remember { mutableStateOf<Pair<String, String>?>(null) }
    var measureMode by remember { mutableStateOf(false) }
    var measurePoints by remember { mutableStateOf(listOf<LatLon>()) }
    var showMeasureDialog by remember { mutableStateOf(false) }
    var navTarget by remember { mutableStateOf<GeoFeature?>(null) }
    var tracking by remember { mutableStateOf(TrackingService.isRunning) }
    var paused by remember { mutableStateOf(TrackingService.isPaused) }
    var hint by remember { mutableStateOf<String?>(null) }
    var center by remember { mutableStateOf<LatLon?>(null) }

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val url = result.contents
        if (url != null) hint = "Map source URL: $url — store downloads are simulated in this build."
    }

    val featureImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        when (val result = ImportExportManager.importFeatures(context, uri)) {
            is com.offtrail.maps.io.ImportResult.GateBlocked -> {
                val (t, m) = GateMessages.forRequired(result.required, "importing this file type")
                gate = Pair(t, m)
            }
            is com.offtrail.maps.io.ImportResult.Success -> {
                result.features.forEach { AppRepository.addFeature(it.copy(mapId = mapId)) }
            }
            is com.offtrail.maps.io.ImportResult.Failure -> hint = result.message
        }
    }

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            map = map,
            features = AppRepository.featuresForMap(mapId),
            geofences = data.geofences,
            measureMode = measureMode,
            measurePoints = measurePoints,
            onFeatureSelected = { selectedFeature = it },
            onMapTap = { latLon ->
                when {
                    measureMode -> {
                        measurePoints = measurePoints + latLon
                        if (measurePoints.size >= 3) showMeasureDialog = true
                    }
                    else -> selectedFeature = null
                }
            },
            onCenterChanged = { center = it },
        )

        // Top bar
        Surface(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(map?.name ?: "Unnamed map", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = {
                    if (LicenseManager.canUseLockOrientation()) hint = "Orientation locked to north-up."
                    else gate = GateMessages.forRequired(License.PRO, "locking the map orientation")
                }) { Icon(Icons.Filled.CenterFocusStrong, "Lock orientation") }
                IconButton(onClick = {
                    qrLauncher.launch(ScanOptions().apply { setDesiredBarcodeFormats(ScanOptions.QR_CODE); setPrompt("Scan a map QR code") })
                }) { Icon(Icons.Filled.QrCodeScanner, "Scan QR") }
                IconButton(onClick = { featureImportLauncher.launch("*/*") }) { Icon(Icons.Filled.Tune, "Import features") }
                IconButton(onClick = { nav.navigate("trackStats") }) { Icon(Icons.Filled.BarChart, "Track stats") }
            }
        }

        // Bottom panel
        Surface(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                center?.let { c ->
                    val format = runCatching { CoordinateFormat.valueOf(data.coordinateFormat) }.getOrDefault(CoordinateFormat.DD_NAMED)
                    Text(CoordinateFormatter.format(c.lat, c.lon, format), style = MaterialTheme.typography.bodySmall)
                }
                navTarget?.let { target ->
                    Text("Navigating to ${target.name} — follow the bearing arrow.", style = MaterialTheme.typography.bodySmall)
                }
                selectedFeature?.let { sel ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sel.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { navTarget = sel }) { Icon(Icons.Filled.Navigation, null); Text("Navigate") }
                        TextButton(onClick = {
                            val ok = AppRepository.addGeofence(
                                Geofence(id = AppRepository.newId(), scope = GeofenceScope.FEATURE, targetId = sel.id, radiusMeters = 50.0)
                            )
                            if (!ok) gate = GateMessages.geofenceLimit(individual = true)
                        }) { Icon(Icons.Filled.Circle, null); Text("Geofence") }
                        TextButton(onClick = {
                            if (sel.type == FeatureType.TRACK && !LicenseManager.isProLicense()) {
                                gate = GateMessages.forRequired(License.PRO, "converting a recorded track into an area")
                            } else {
                                if (sel.type == FeatureType.TRACK) {
                                    AppRepository.addFeature(sel.copy(id = AppRepository.newId(), name = "${sel.name} (area)", type = FeatureType.AREA))
                                }
                                AppRepository.deleteFeature(sel.id)
                                selectedFeature = null
                            }
                        }) { Text("To Area") }
                        TextButton(onClick = { AppRepository.deleteFeature(sel.id); selectedFeature = null }) { Text("Delete") }
                    }
                }
                Row {
                    TextButton(onClick = { measureMode = !measureMode; measurePoints = emptyList() }) {
                        Icon(Icons.Filled.Straighten, null); Text(if (measureMode) "Stop measuring" else "Draw & Measure")
                    }
                    Spacer(Modifier.width(8.dp))
                    when {
                        !tracking -> TextButton(onClick = { TrackingService.start(context); tracking = true; paused = false }) { Text("Start Tracking") }
                        paused -> {
                            TextButton(onClick = { TrackingService.sendAction(context, TrackingService.ACTION_RESUME); paused = false }) { Text("Resume") }
                            TextButton(onClick = { TrackingService.sendAction(context, TrackingService.ACTION_STOP); tracking = false }) { Text("Stop") }
                        }
                        else -> {
                            TextButton(onClick = { TrackingService.sendAction(context, TrackingService.ACTION_PAUSE); paused = true }) { Text("Pause") }
                            TextButton(onClick = { TrackingService.sendAction(context, TrackingService.ACTION_STOP); tracking = false }) { Text("Stop") }
                        }
                    }
                }
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    if (showMeasureDialog && measurePoints.size >= 2) {
        val dist = measurePoints.zipWithNext { a, b -> Projections.distanceMeters(a.lat, a.lon, b.lat, b.lon) }.sum()
        val area = Projections.polygonAreaM2(measurePoints.map { Pair(it.lat, it.lon) })
        val unit = runCatching { DistanceUnit.valueOf(data.distanceUnit) }.getOrDefault(DistanceUnit.METRIC)
        AlertDialog(
            onDismissRequest = { showMeasureDialog = false },
            title = { Text("Draw & Measure") },
            text = {
                Column {
                    Text("Distance: " + Units.formatDistance(dist, unit))
                    Text("Area: %.0f m²".format(area))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AppRepository.addFeature(GeoFeature(id = AppRepository.newId(), name = "Measure line", type = FeatureType.LINE, mapId = mapId, points = measurePoints, distanceMeters = dist))
                    showMeasureDialog = false; measureMode = false; measurePoints = emptyList()
                }) { Text("Save Line") }
            },
            dismissButton = {
                TextButton(onClick = {
                    AppRepository.addFeature(GeoFeature(id = AppRepository.newId(), name = "Measure area", type = FeatureType.AREA, mapId = mapId, points = measurePoints, areaSqm = area))
                    showMeasureDialog = false; measureMode = false; measurePoints = emptyList()
                }) { Text("Save Area") }
            },
        )
    }

    gate?.let { (title, message) ->
        GateDialog(title, message, onDismiss = { gate = null }, onLearnMore = { nav.navigate("upgrade") })
    }
}

@Composable
private fun OsmMap(
    map: OfftrailMap?,
    features: List<GeoFeature>,
    geofences: List<Geofence>,
    measureMode: Boolean,
    measurePoints: List<LatLon>,
    onFeatureSelected: (GeoFeature?) -> Unit,
    onMapTap: (LatLon) -> Unit,
    onCenterChanged: (LatLon) -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(46.5, 8.0))
        }
    }

    DisposableEffect(Unit) { onDispose { mapView.onDetach() } }

    // Publish center coordinates for the readout.
    LaunchedEffect(Unit) {
        while (true) {
            val c = mapView.mapCenter
            onCenterChanged(LatLon(c.latitude, c.longitude))
            delay(500)
        }
    }

    // Initial camera: jump to a georeferenced map's extent.
    LaunchedEffect(map?.id) {
        val m = map ?: return@LaunchedEffect
        if (m.isGeoreferenced && m.topLeftLat != null && m.bottomRightLat != null) {
            val latC = (m.topLeftLat!! + m.bottomRightLat!!) / 2
            val lonC = (m.topLeftLon!! + m.bottomRightLon!!) / 2
            mapView.controller.setZoom(16.0)
            mapView.controller.setCenter(GeoPoint(latC, lonC))
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = { mv ->
            mv.overlays.clear()

            mv.overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        if (p == null) return false
                        onMapTap(LatLon(p.latitude, p.longitude))
                        return measureMode
                    }
                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                })
            )

            geofences.forEach { g ->
                val anchor = features.firstOrNull { it.id == g.targetId }?.points?.firstOrNull() ?: return@forEach
                mv.overlays.add(Polygon(mv).apply {
                    points = Polygon.pointsAsCircle(GeoPoint(anchor.lat, anchor.lon), g.radiusMeters)
                    fillPaint.color = 0x33E53935
                    outlinePaint.color = 0xFFE53935.toInt()
                })
            }

            features.forEach { f ->
                when (f.type) {
                    FeatureType.PLACEMARK, FeatureType.PHOTO -> {
                        val p = f.points.firstOrNull() ?: return@forEach
                        mv.overlays.add(Marker(mv).apply {
                            position = GeoPoint(p.lat, p.lon)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = f.name
                            setOnMarkerClickListener { _, _ -> onFeatureSelected(f); true }
                        })
                    }
                    FeatureType.LINE, FeatureType.TRACK -> {
                        if (f.points.isEmpty()) return@forEach
                        mv.overlays.add(Polyline(mv).apply {
                            setPoints(f.points.map { GeoPoint(it.lat, it.lon) })
                            title = f.name
                            setOnClickListener { _, _, _ -> onFeatureSelected(f); true }
                        })
                    }
                    FeatureType.AREA -> {
                        if (f.points.size < 3) return@forEach
                        mv.overlays.add(Polygon(mv).apply {
                            points = f.points.map { GeoPoint(it.lat, it.lon) }
                            title = f.name
                            fillPaint.color = 0x332E7D64
                            outlinePaint.color = 0xFF2E7D64.toInt()
                            setOnClickListener { _, _, _ -> onFeatureSelected(f); true }
                        })
                    }
                }
            }

            if (measurePoints.size > 1) {
                mv.overlays.add(Polyline(mv).apply { setPoints(measurePoints.map { GeoPoint(it.lat, it.lon) }) })
            }
            measurePoints.forEach { p ->
                mv.overlays.add(Marker(mv).apply {
                    position = GeoPoint(p.lat, p.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "Vertex"
                })
            }

            mv.overlays.add(MyLocationNewOverlay(mv).apply {
                enableMyLocation()
                enableFollowLocation()
            })
            mv.invalidate()
        }
    )
}
