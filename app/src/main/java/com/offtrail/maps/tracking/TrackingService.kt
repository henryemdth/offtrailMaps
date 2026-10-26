package com.offtrail.maps.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.offtrail.maps.MainActivity
import com.offtrail.maps.R
import com.offtrail.maps.geo.Projections
import com.offtrail.maps.io.Gpx
import com.offtrail.maps.model.AppRepository
import com.offtrail.maps.model.GeoFeature
import com.offtrail.maps.model.TrackPoint

/**
 * Foreground track recording service. Mirrors the reference app's TrackingService
 * behaviors: persistent notification ("Recording track …" / paused state),
 * pause/resume, distance/elevation/duration statistics, saved as a Track feature.
 * Uses a location-update callback instead of Play-services geofencing.
 */
class TrackingService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.offtrail.maps.tracking.START"
        const val ACTION_PAUSE = "com.offtrail.maps.tracking.PAUSE"
        const val ACTION_RESUME = "com.offtrail.maps.tracking.RESUME"
        const val ACTION_STOP = "com.offtrail.maps.tracking.STOP"
        const val CHANNEL_ID = "offtrail_tracking"

        var isRunning = false
            private set
        var isPaused = false
            private set

        // Live stats, read by the UI.
        @Volatile var liveDistanceM: Double = 0.0
        @Volatile var liveGainM: Double = 0.0
        @Volatile var liveDurationSec: Long = 0

        fun start(context: Context) {
            val intent = Intent(context, TrackingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendAction(context: Context, action: String) {
            context.startService(Intent(context, TrackingService::class.java).setAction(action))
        }
    }

    private val points = mutableListOf<TrackPoint>()
    private var lastPoint: TrackPoint? = null
    private var startedAtMs = 0L
    private var accumulatedMs = 0L
    private var segmentStartMs = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (isPaused) return
            result.lastLocation ?: return
            val loc = result.lastLocation!!
            val tp = TrackPoint(loc.latitude, loc.longitude, loc.altitude, System.currentTimeMillis())
            lastPoint?.let { prev ->
                liveDistanceM += Projections.distanceMeters(prev.lat, prev.lon, tp.lat, tp.lon)
                liveGainM += maxOf(0.0, tp.elevationM - prev.elevationM)
            }
            points += tp
            lastPoint = tp
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> begin()
            ACTION_PAUSE -> { if (!isPaused) { isPaused = true; accumulatedMs += System.currentTimeMillis() - segmentStartMs; refreshNotification() } }
            ACTION_RESUME -> { if (isPaused) { isPaused = false; segmentStartMs = System.currentTimeMillis(); refreshNotification() } }
            ACTION_STOP -> finish()
        }
        return START_STICKY
    }

    private fun begin() {
        isRunning = true
        isPaused = false
        points.clear()
        lastPoint = null
        liveDistanceM = 0.0
        liveGainM = 0.0
        startedAtMs = System.currentTimeMillis()
        segmentStartMs = startedAtMs
        startForeground(1, buildNotification())
        requestUpdates()
    }

    @Suppress("MissingPermission")
    private fun requestUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateDistanceMeters(2f)
            .build()
        LocationServices.getFusedLocationProviderClient(this)
            .requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun stopUpdates() {
        LocationServices.getFusedLocationProviderClient(this)
            .removeLocationUpdates(locationCallback)
    }

    private fun finish() {
        stopUpdates()
        saveTrack()
        isRunning = false
        isPaused = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveTrack() {
        if (points.size < 2) return
        val feature: GeoFeature = Gpx.trackToFeature(
            name = "Track ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(startedAtMs))}",
            points = points,
        )
        AppRepository.addFeature(feature)
    }

    override fun onDestroy() {
        if (isRunning) { stopUpdates(); isRunning = false }
        super.onDestroy()
    }

    private fun refreshNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, buildNotification())
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.tracking_channel), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val actionText = if (isPaused) "Resume" else "Pause"
        val actionIntent = Intent(this, TrackingService::class.java).setAction(if (isPaused) ACTION_RESUME else ACTION_PAUSE)
        val actionPi = PendingIntent.getService(this, 1, actionIntent, PendingIntent.FLAG_IMMUTABLE)
        val stopPi = PendingIntent.getService(this, 2, Intent(this, TrackingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(if (isPaused) R.string.tracking_notification_paused else R.string.tracking_notification_title))
            .setContentText(com.offtrail.maps.geo.Units.formatDistance(liveDistanceM, com.offtrail.maps.geo.DistanceUnit.METRIC))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, actionText, actionPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
            .build()
    }
}
