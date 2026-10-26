package com.offtrail.maps.geofencing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.offtrail.maps.geo.Projections
import com.offtrail.maps.model.AppRepository
import com.offtrail.maps.model.Geofence
import com.offtrail.maps.model.GeofenceScope
import com.offtrail.maps.model.GeoFeature
import kotlin.math.abs

/**
 * In-app geofence monitoring. Mirrors the reference app's GeofencingService behaviors:
 *  - per-feature geofences (enter/exit radius alerts)
 *  - per-layer geofences (any feature of the layer)
 *  - map-boundary alerts (Pro)
 *  - globally-active geofences on every map (Pro)
 * Quantity caps are enforced upstream by TierLimits when geofences are created.
 */
class GeofenceEngine(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val inside = mutableSetOf<String>()
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { evaluate(it.latitude, it.longitude) }
        }
    }

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L).build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    fun stop() {
        client.removeLocationUpdates(callback)
    }

    /** Geofences that apply while viewing [mapId]: map-scoped, globally-active (Pro), plus feature/layer ones. */
    fun activeForMap(mapId: String?): List<Geofence> {
        val d = AppRepository.data.value
        return d.geofences.filter { g ->
            when (g.scope) {
                GeofenceScope.MAP -> g.targetId == mapId
                GeofenceScope.GLOBAL -> g.globallyActive
                else -> true
            }
        }
    }

    fun evaluate(lat: Double, lon: Double, mapId: String? = null) {
        val d = AppRepository.data.value
        for (g in activeForMap(mapId)) {
            val targets: List<GeoFeature> = when (g.scope) {
                GeofenceScope.FEATURE -> d.features.filter { it.id == g.targetId }
                GeofenceScope.LAYER -> d.features.filter { it.layerId != null && it.layerId == g.targetId }
                GeofenceScope.MAP -> {
                    val map = AppRepository.mapById(g.targetId ?: "")
                    if (map != null && map.isGeoreferenced) {
                        listOf(featureFromBounds(map.id, map.name, map.topLeftLat!!, map.topLeftLon!!, map.bottomRightLat!!, map.bottomRightLon!!))
                    } else emptyList()
                }
                GeofenceScope.GLOBAL -> d.features.filter { it.id == g.targetId }
            }
            for (target in targets) {
                val anchor = target.points.firstOrNull() ?: continue
                val dist = Projections.distanceMeters(lat, lon, anchor.lat, anchor.lon)
                val key = "${g.id}:${target.id}"
                val isInside = dist <= g.radiusMeters
                val wasInside = key in inside
                when {
                    isInside && !wasInside && g.alertOnEnter -> alert("Geofence entered", "${target.name}: you entered the alert radius.")
                    !isInside && wasInside && g.alertOnExit -> alert("Geofence exited", "${target.name}: you left the alert radius.")
                }
                if (isInside) inside += key else inside -= key
            }
        }
    }

    private fun featureFromBounds(id: String, name: String, tlLat: Double, tlLon: Double, brLat: Double, brLon: Double): GeoFeature =
        GeoFeature(
            id = id,
            name = "Map boundary: $name",
            type = com.offtrail.maps.model.FeatureType.AREA,
            points = listOf(
                com.offtrail.maps.model.LatLon(tlLat, tlLon),
                com.offtrail.maps.model.LatLon(brLat, tlLon),
                com.offtrail.maps.model.LatLon(brLat, brLon),
                com.offtrail.maps.model.LatLon(tlLat, brLon),
            ),
        )

    private fun alert(title: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("offtrail_geofence", "Geofence alerts", NotificationManager.IMPORTANCE_HIGH))
        }
        val notification = NotificationCompat.Builder(context, "offtrail_geofence")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            nm.notify(abs(text.hashCode()), notification)
        }
    }
}
