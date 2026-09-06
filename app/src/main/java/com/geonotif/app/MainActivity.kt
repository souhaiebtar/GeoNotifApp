package com.geonotif.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.widget.*

class MainActivity : Activity() {
    private val ink = Color.rgb(26, 47, 62)
    private val teal = Color.rgb(22, 125, 141)
    private lateinit var content: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var monitorButton: Button
    private var destinations = emptyList<Destination>()
    private var selectedSound: String? = null
    private var soundButton: Button? = null
    private var currentLocationListener: android.location.LocationListener? = null
    private var currentLocationTimeout: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private val statusPoll = object : Runnable {
        override fun run() {
            statusText.text = LocationMonitorService.status
            monitorButton.text = if (LocationMonitorService.running) "Stop monitoring" else "Start monitoring"
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        handler.post(statusPoll)
    }

    override fun onPause() {
        handler.removeCallbacks(statusPoll)
        super.onPause()
    }

    override fun onDestroy() {
        cancelCurrentLocation()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun cancelCurrentLocation() {
        currentLocationListener?.let { getSystemService(LocationManager::class.java).removeUpdates(it) }
        currentLocationListener = null
        currentLocationTimeout?.let { handler.removeCallbacks(it) }
        currentLocationTimeout = null
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 16f, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(ink)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(5))
    }
    private fun button(label: String, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setTextColor(teal)
        setOnClickListener { click() }
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun card(): LinearLayout = column().apply {
        setPadding(dp(18), dp(12), dp(18), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.WHITE); cornerRadius = dp(18).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    }

    private fun render() {
        destinations = DestinationStore.load(this)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = column().apply { setPadding(dp(22), dp(20), dp(22), dp(24)) }
        scroll.addView(content)
        // Android 15 enforces edge-to-edge for target 35. Respect system bars and keyboard.
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(scroll)
        content.addView(text("GeoNotif", 32f, true))
        content.addView(text("Rest on the ride. Know when you’re close.", 16f))
        val monitoring = card()
        monitoring.addView(text("YOUR JOURNEY", 12f, true))
        statusText = text(LocationMonitorService.status, 18f, true)
        monitoring.addView(statusText)
        monitoring.addView(text("Each enabled destination alerts once per session. Restart monitoring to re-arm your stops.", 14f))
        monitorButton = button(if (LocationMonitorService.running) "Stop monitoring" else "Start monitoring") {
            if (LocationMonitorService.running) stopService(Intent(this, LocationMonitorService::class.java))
            else startMonitoring()
        }
        monitoring.addView(monitorButton)
        monitoring.addView(button("Dismiss current alarm") {
            if (LocationMonitorService.running) startService(Intent(this, LocationMonitorService::class.java)
                .setAction(LocationMonitorService.SILENCE))
        })
        content.addView(monitoring)
        content.addView(text("Saved destinations", 23f, true))
        content.addView(button("+ Add destination") { showAddDestinationOptions() })
        if (destinations.isEmpty()) {
            content.addView(card().apply {
                addView(text("Where are you heading?", 20f, true))
                addView(text("Save your current location or choose a stop on the map, then pick your arrival radius, sound, and vibration."))
            })
        }
        destinations.forEach { destination ->
            content.addView(card().apply {
                addView(Switch(this@MainActivity).apply {
                    text = destination.name; textSize = 20f; setTextColor(ink)
                    isChecked = destination.enabled
                    contentDescription = "Enable ${destination.name}"
                    setOnCheckedChangeListener { _, checked ->
                        destinations = destinations.map { if (it.id == destination.id) it.copy(enabled = checked) else it }
                        persist()
                    }
                })
                addView(text("${destination.latitude}, ${destination.longitude}", 14f))
                addView(text("Alert within ${destination.radiusMeters} m", 17f, true))
                addView(text("${soundName(destination.soundUri)} · ${destination.vibration} vibration", 14f))
                addView(LinearLayout(this@MainActivity).apply {
                    addView(button("Edit") { editDestination(destinations.first { it.id == destination.id }) },
                        LinearLayout.LayoutParams(0, -2, 1f))
                    addView(button("Delete") {
                        AlertDialog.Builder(this@MainActivity).setTitle("Delete ${destination.name}?")
                            .setMessage("This removes the saved destination.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                                destinations = destinations.filterNot { it.id == destination.id }
                                persist(); render()
                            }.show()
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                })
            })
        }
        content.addView(text("Before you rest", 20f, true))
        content.addView(text("Keep location and notifications enabled. Audio uses your alarm volume; silent mode, Do Not Disturb, battery restrictions, and poor GPS reception can affect alerts. A 500–1,000 m radius is a useful starting point on public transport. This is not a guaranteed wake-up alarm.", 14f))
        content.addView(button("Notification settings") {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        })
        content.addView(button("Location settings") { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) })
    }

    private fun showAddDestinationOptions() {
        val choices = column().apply { setPadding(dp(18), dp(8), dp(18), dp(8)) }
        val dialog = AlertDialog.Builder(this).setTitle("Add destination")
            .setView(choices).setNegativeButton("Cancel", null).create()
        choices.addView(button("Save current location") {
            dialog.dismiss()
            saveCurrentLocation()
        })
        choices.addView(button("Choose other location") {
            dialog.dismiss()
            val intent = Intent(this, MapPickerActivity::class.java)
            destinations.lastOrNull()?.let {
                intent.putExtra(MapPickerActivity.LATITUDE, it.latitude)
                intent.putExtra(MapPickerActivity.LONGITUDE, it.longitude)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 201)
        })
        choices.addView(text("The map uses OpenStreetMap. Online tiles share the viewed area and your IP address with the tile provider. No API key is needed.", 13f))
        dialog.show()
    }

    private fun saveCurrentLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 102)
            return
        }
        val progress = AlertDialog.Builder(this).setTitle("Current location")
            .setMessage("Getting a fresh location… This may take up to 20 seconds.")
            .setNegativeButton("Cancel", null).create()
        progress.setOnDismissListener { cancelCurrentLocation() }
        progress.show()
        requestCurrentLocation(onLocation = { location ->
            progress.dismiss()
            if (!isFinishing && !isDestroyed) {
                editDestination(null, location.latitude, location.longitude)
                toast("Location found (accuracy ±${location.accuracy.toInt()} m). Name your stop and tap Save.")
            }
        }, onFailure = { message ->
            progress.dismiss()
            if (!isFinishing && !isDestroyed) {
                AlertDialog.Builder(this).setTitle("Location unavailable").setMessage(message)
                    .setNegativeButton("Cancel", null)
                    .setNeutralButton("Location settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                    .setPositiveButton("Try again") { _, _ -> saveCurrentLocation() }.show()
            }
        })
    }

    private fun persist() {
        DestinationStore.save(this, destinations)
        if (LocationMonitorService.running) startService(Intent(this, LocationMonitorService::class.java)
            .setAction(LocationMonitorService.REFRESH))
    }

    private fun startMonitoring() {
        if (destinations.none { it.enabled }) { toast("Add and enable at least one destination first."); return }
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
            permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (permissions.isNotEmpty()) {
            AlertDialog.Builder(this).setTitle("Allow arrival alerts")
                .setMessage("GeoNotif needs precise location while monitoring and notification permission to alert you with the screen off. Monitoring stays on this device; the optional map downloads tiles for the area you view. Choose precise location in the permission dialog.")
                .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ ->
                    requestPermissions(permissions.toTypedArray(), 100)
                }.show()
            return
        }
        if (!getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()) {
            toast("Enable GeoNotif notifications in Settings before monitoring."); return
        }
        if (!getSystemService(LocationManager::class.java).isLocationEnabled) {
            toast("Turn on device location, then start monitoring.")
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); return
        }
        startForegroundService(Intent(this, LocationMonitorService::class.java))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                saveCurrentLocation()
            } else {
                AlertDialog.Builder(this).setTitle("Precise location needed")
                    .setMessage("Allow precise location in app settings to save your current position, or choose another location on the map without location permission.")
                    .setNegativeButton("Cancel", null).setPositiveButton("App settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }.show()
            }
        }
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startMonitoring()
            else AlertDialog.Builder(this).setTitle("Permissions needed")
                .setMessage("Precise location and notifications are required. You can enable them in app settings and then tap Start monitoring.")
                .setNegativeButton("Not now", null).setPositiveButton("App settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }.show()
        }
    }

    private fun editDestination(existing: Destination?, initialLatitude: Double? = null, initialLongitude: Double? = null) {
        val form = column().apply { setPadding(dp(22), dp(8), dp(22), dp(8)) }
        fun field(label: String, value: String, numeric: Boolean = false): EditText {
            form.addView(text(label, 14f, true))
            return EditText(this).apply {
                setSingleLine(); setText(value); hint = label
                if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                form.addView(this)
            }
        }
        val name = field("Destination name", existing?.name ?: "")
        val latitude = field("Latitude (−90 to 90)", (existing?.latitude ?: initialLatitude)?.toString() ?: "", true)
        val longitude = field("Longitude (−180 to 180)", (existing?.longitude ?: initialLongitude)?.toString() ?: "", true)
        form.addView(text("Review the selected coordinates, or update them with your current position below.", 13f))
        form.addView(button("Use current location") { useCurrentLocation(latitude, longitude) })
        val radius = field("Arrival radius in meters (100–10,000)", (existing?.radiusMeters ?: 500).toString(), true)
        selectedSound = existing?.soundUri
        soundButton = button("Sound: ${soundName(selectedSound)}") { chooseSound() }
        form.addView(soundButton)
        form.addView(text("Vibration", 14f, true))
        val patterns = listOf("Off", "Gentle", "Strong")
        val vibration = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, patterns)
            setSelection(patterns.indexOf(existing?.vibration ?: "Strong").coerceAtLeast(0))
        }
        form.addView(vibration)
        form.addView(text("Sound and vibration repeat for up to 60 seconds. Pick Silent and Off for notification-only feedback.", 13f))
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "Add destination" else "Edit destination")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val lat = latitude.text.toString().toDoubleOrNull()
                val lon = longitude.text.toString().toDoubleOrNull()
                val meters = radius.text.toString().toIntOrNull()
                when {
                    name.text.isBlank() -> name.error = "Enter a name"
                    lat == null || !lat.isFinite() || lat !in -90.0..90.0 -> latitude.error = "Enter a valid latitude"
                    lon == null || !ArrivalRules.validCoordinates(lat, lon) -> longitude.error = "Enter a valid longitude"
                    meters == null || meters !in 100..10_000 -> radius.error = "Enter a whole number from 100 to 10,000"
                    else -> {
                        val destination = Destination(id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.text.toString().trim(), latitude = lat, longitude = lon,
                            radiusMeters = meters, enabled = existing?.enabled ?: true,
                            soundUri = selectedSound, vibration = patterns[vibration.selectedItemPosition])
                        destinations = if (existing == null) destinations + destination
                            else destinations.map { if (it.id == existing.id) destination else it }
                        persist(); dialog.dismiss(); render()
                    }
                }
            }
        }
        dialog.setOnDismissListener {
            soundButton = null
            cancelCurrentLocation()
        }
        dialog.show()
    }

    private fun useCurrentLocation(latitude: EditText, longitude: EditText) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 101)
            toast("After granting precise location, tap Use current location again."); return
        }
        toast("Getting a fresh location…")
        requestCurrentLocation(onLocation = { location ->
            if (latitude.isAttachedToWindow) {
                latitude.setText(location.latitude.toString())
                longitude.setText(location.longitude.toString())
                toast("Position saved to form (accuracy ±${location.accuracy.toInt()} m).")
            }
        }, onFailure = { toast(it) })
    }

    private fun requestCurrentLocation(
        onLocation: (android.location.Location) -> Unit,
        onFailure: (String) -> Unit
    ) {
        cancelCurrentLocation()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onFailure("Precise location permission is required. Enable it in app settings or use the map.")
            return
        }
        val manager = getSystemService(LocationManager::class.java)
        if (!manager.isLocationEnabled) { onFailure("Enable device location first, then try again."); return }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { it in manager.allProviders && manager.isProviderEnabled(it) }
        if (providers.isEmpty()) { onFailure("No location provider is available. Choose a stop on the map instead."); return }
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                val age = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
                if (age !in 0..30_000 || !location.hasAccuracy() || location.accuracy > 100f) return
                cancelCurrentLocation()
                onLocation(location)
            }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Legacy callback")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try {
            currentLocationListener = listener
            providers.forEach { provider ->
                manager.requestLocationUpdates(provider, 1_000, 0f, listener, Looper.getMainLooper())
            }
            currentLocationTimeout = Runnable {
                cancelCurrentLocation()
                onFailure("No accurate location fix yet. Try outdoors, or choose the destination on the map.")
            }.also { handler.postDelayed(it, 20_000) }
        } catch (_: Exception) {
            cancelCurrentLocation()
            onFailure("Could not get a location. Check location permission or choose the destination on the map.")
        }
    }

    private fun chooseSound() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Arrival sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, when (selectedSound) {
                null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                "" -> null
                else -> Uri.parse(selectedSound)
            })
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 200)
        } catch (_: android.content.ActivityNotFoundException) {
            AlertDialog.Builder(this).setTitle("Arrival sound")
                .setItems(arrayOf("Default alarm", "Silent")) { _, which ->
                    selectedSound = if (which == 0) null else ""
                    soundButton?.text = "Sound: ${soundName(selectedSound)}"
                }.show()
        }
    }

    @Deprecated("Platform activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 201 && resultCode == RESULT_OK && data != null) {
            val latitude = data.getDoubleExtra(MapPickerActivity.LATITUDE, Double.NaN)
            val longitude = data.getDoubleExtra(MapPickerActivity.LONGITUDE, Double.NaN)
            if (ArrivalRules.validCoordinates(latitude, longitude)) editDestination(null, latitude, longitude)
        }
        if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            @Suppress("DEPRECATION")
            val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            selectedSound = uri?.toString() ?: ""
            soundButton?.text = "Sound: ${soundName(selectedSound)}"
        }
    }

    private fun soundName(uri: String?): String = when (uri) {
        null -> "Default alarm"
        "" -> "Silent"
        else -> try { RingtoneManager.getRingtone(this, Uri.parse(uri))?.getTitle(this) ?: "Custom sound" }
            catch (_: Exception) { "Custom sound (default fallback if unavailable)" }
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
