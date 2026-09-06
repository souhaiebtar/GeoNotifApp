package com.geonotif.app

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*

class LocationMonitorService : Service(), LocationListener {
    companion object {
        const val STOP = "com.geonotif.STOP"
        const val SILENCE = "com.geonotif.SILENCE"
        const val REFRESH = "com.geonotif.REFRESH"
        @Volatile var running = false
            private set
        @Volatile var status = "Monitoring is off"
            private set
        private const val TRACKING = 1
        private const val ARRIVAL = 2
    }

    private lateinit var locations: LocationManager
    private lateinit var notifications: NotificationManager
    private lateinit var vibrator: Vibrator
    private val handler = Handler(Looper.getMainLooper())
    private val triggered = mutableSetOf<String>()
    private var player: MediaPlayer? = null
    private var audioWakeLock: PowerManager.WakeLock? = null
    private var watching = false
    private val silenceTask = Runnable { silence() }

    override fun onCreate() {
        super.onCreate()
        locations = getSystemService(LocationManager::class.java)
        notifications = getSystemService(NotificationManager::class.java)
        @Suppress("DEPRECATION")
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        notifications.createNotificationChannel(NotificationChannel(
            "monitoring", "Location monitoring", NotificationManager.IMPORTANCE_LOW
        ))
        notifications.createNotificationChannel(NotificationChannel(
            "arrivals", "Arrival alerts", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Arrival banners; destination-specific audio and vibration are played by the app."
            setSound(null, null)
            enableVibration(false)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> { stopSelf(); return START_NOT_STICKY }
            SILENCE -> {
                silence()
                if (!running) stopSelf()
                return START_NOT_STICKY
            }
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (DestinationStore.load(this).none { it.enabled }) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(TRACKING, trackingNotification(if (running) status else "Waiting for a fresh location…"))
        running = true
        if (!watching) {
            status = "Waiting for a fresh location…"
            try {
                // Register both sources, including temporarily disabled providers, so enabling
                // location later resumes updates without restarting the session.
                for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                    if (provider in locations.allProviders) {
                        locations.requestLocationUpdates(provider, 3_000L, 0f, this, Looper.getMainLooper())
                        watching = true
                    }
                }
                if (!locations.isLocationEnabled) updateStatus("Turn on device location to monitor")
                else if (!watching) updateStatus("No location provider available")
            } catch (_: SecurityException) { stopSelf() }
        }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        val destinations = DestinationStore.load(this).filter { it.enabled }
        if (destinations.isEmpty()) { stopSelf(); return }
        val age = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        if (!location.hasAccuracy() || age !in 0..30_000) return
        var closest: Pair<Destination, Float>? = null
        val arrivals = mutableListOf<Destination>()
        for (destination in destinations) {
            val result = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude,
                destination.latitude, destination.longitude, result)
            if (destination.id in triggered) continue
            if (closest == null || result[0] < closest.second) closest = destination to result[0]
            if (ArrivalRules.isArrival(result[0], location.accuracy, destination.radiusMeters, age)) {
                triggered.add(destination.id)
                arrivals.add(destination)
            }
        }
        if (arrivals.isNotEmpty()) alert(arrivals)
        val nearest = closest
        updateStatus(if (nearest == null) "All enabled destinations alerted · restart to re-arm"
            else "${nearest.first.name}: ${nearest.second.toInt()} m away · accuracy ±${location.accuracy.toInt()} m")
    }

    override fun onProviderDisabled(provider: String) {
        if (!locations.isLocationEnabled) updateStatus("Location is off — monitoring paused")
    }
    override fun onProviderEnabled(provider: String) { updateStatus("Waiting for a fresh location…") }
    @Deprecated("Legacy callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun updateStatus(text: String) {
        status = text
        notifications.notify(TRACKING, trackingNotification(text))
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun action(action: String): PendingIntent = PendingIntent.getService(this, action.hashCode(),
        Intent(this, LocationMonitorService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun trackingNotification(text: String): Notification = Notification.Builder(this, "monitoring")
        .setSmallIcon(R.drawable.ic_location).setContentTitle("GeoNotif is watching your stops")
        .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
        .setContentIntent(openApp()).setOngoing(true).setOnlyAlertOnce(true)
        .addAction(Notification.Action.Builder(null, "Stop monitoring", action(STOP)).build())
        .build()

    private fun alert(destinations: List<Destination>) {
        silence()
        // If circles overlap, show every arrival and use the first destination's feedback.
        val destination = destinations.first()
        val names = destinations.joinToString { it.name }
        notifications.notify(ARRIVAL, Notification.Builder(this, "arrivals")
            .setSmallIcon(R.drawable.ic_location).setContentTitle("Your stop is close!")
            .setContentText(names).setStyle(Notification.BigTextStyle().bigText(
                "$names\nYou are within the configured arrival radius. Feedback stops after 60 seconds."))
            .setCategory(Notification.CATEGORY_ALARM).setContentIntent(openApp())
            .setDeleteIntent(action(SILENCE)).setAutoCancel(false)
            .addAction(Notification.Action.Builder(null, "Dismiss alarm", action(SILENCE)).build())
            .addAction(Notification.Action.Builder(null, "Stop monitoring", action(STOP)).build())
            .build())
        audioWakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeoNotif:arrival").apply { acquire(65_000) }
        if (destination.soundUri != "") {
            val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (!playSound(destination.soundUri?.let(Uri::parse) ?: defaultSound)) playSound(defaultSound)
        }
        val pattern = when (destination.vibration) {
            "Gentle" -> longArrayOf(0, 250, 750)
            "Strong" -> longArrayOf(0, 900, 300, 900, 700)
            else -> null
        }
        if (pattern != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        handler.postDelayed(silenceTask, 60_000)
    }

    private fun playSound(uri: Uri): Boolean {
        val candidate = MediaPlayer()
        return try {
            candidate.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            candidate.setDataSource(this, uri)
            candidate.isLooping = true
            candidate.prepare()
            candidate.start()
            player = candidate
            true
        } catch (_: Exception) { candidate.release(); false }
    }

    private fun silence() {
        handler.removeCallbacks(silenceTask)
        player?.release()
        player = null
        vibrator.cancel()
        audioWakeLock?.let { if (it.isHeld) it.release() }
        audioWakeLock = null
        notifications.cancel(ARRIVAL)
    }

    override fun onDestroy() {
        locations.removeUpdates(this)
        silence()
        running = false
        status = "Monitoring is off"
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
