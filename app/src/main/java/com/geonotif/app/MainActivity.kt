package com.geonotif.app

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.widget.*

class MainActivity : AppCompatActivity() {
    private val palette by lazy { AppUi.palette(this) }
    private val ink get() = palette.ink
    private val teal get() = palette.teal
    private lateinit var journeyTitle: TextView
    private lateinit var journeyBadge: TextView
    private lateinit var destinationCount: TextView
    private lateinit var dismissButton: Button
    private lateinit var content: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var monitorButton: Button
    private var destinations = emptyList<Destination>()
    private var selectedSound: String? = null
    private var soundButton: Button? = null
    private var editorState: (() -> Bundle)? = null
    private var currentLocationListener: android.location.LocationListener? = null
    private var currentLocationTimeout: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private val statusPoll = object : Runnable {
        override fun run() {
            updateJourneyState()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        savedInstanceState?.getBundle("editor")?.let { draft ->
            val existing = destinations.find { it.id == draft.getString("existingId") }
            editDestination(existing, restored = draft)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        editorState?.let { outState.putBundle("editor", it()) }
        super.onSaveInstanceState(outState)
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
    private fun text(value: String, size: Float = 16f, bold: Boolean = false) = AppUi.label(this, value, size, bold)
    private fun button(label: String, click: () -> Unit) = AppUi.button(this, label, onClick = click)
    private fun primaryButton(label: String, click: () -> Unit) = AppUi.button(this, label, primary = true, onClick = click)
    private fun muted(value: String, size: Float = 14f) = text(value, size).apply { setTextColor(palette.muted) }
    private fun gap(height: Int = 12) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun pill(value: String) = text(value, 12f, true).apply {
        setTextColor(teal)
        background = AppUi.shape(this@MainActivity, palette.pale, 12)
        setPadding(dp(10), dp(6), dp(10), dp(6))
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun card(): LinearLayout = column().apply {
        setPadding(dp(20), dp(18), dp(20), dp(18))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun render() {
        destinations = DestinationStore.load(this)
        val scroll = ScrollView(this).apply {
            isFillViewport = true; isVerticalScrollBarEnabled = false
            setBackgroundColor(palette.background)
        }
        content = column().apply { setPadding(dp(22), dp(24), dp(22), dp(32)) }
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
        val brand = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        brand.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_location); imageTintList = android.content.res.ColorStateList.valueOf(AppUi.ink)
            background = AppUi.shape(this@MainActivity, AppUi.mint, 15)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(12) })
        brand.addView(text("GeoNotif", 26f, true), LinearLayout.LayoutParams(0, -2, 1f))
        brand.addView(pill("ON THE GO"))
        content.addView(brand)
        content.addView(gap(20))
        content.addView(text("Enjoy the journey.", 30f, true))
        content.addView(muted("A little peace of mind, all the way to your stop.", 15f))
        content.addView(gap(18))
        val monitoring = card()
        val heroTop = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        val heroCopy = column()
        journeyBadge = text("", 11f, true).apply { setTextColor(AppUi.mint); letterSpacing = 0.12f }
        journeyTitle = text("", 24f, true).apply { setTextColor(Color.WHITE) }
        heroCopy.addView(journeyBadge)
        heroCopy.addView(journeyTitle)
        heroTop.addView(heroCopy, LinearLayout.LayoutParams(0, -2, 1f))
        heroTop.addView(JourneyArt(this), LinearLayout.LayoutParams(dp(105), dp(105)))
        monitoring.addView(heroTop)
        statusText = text("", 14f).apply {
            setTextColor(Color.rgb(209, 225, 227))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        monitoring.addView(statusText)
        monitoring.addView(gap(10))
        monitorButton = primaryButton("Start monitoring") {
            if (LocationMonitorService.running) stopService(Intent(this, LocationMonitorService::class.java))
            else startMonitoring()
        }
        (monitorButton as MaterialButton).apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(AppUi.mint)
            setTextColor(AppUi.ink)
        }
        monitoring.addView(monitorButton)
        dismissButton = button("Dismiss current alarm") {
            if (LocationMonitorService.running) startService(Intent(this, LocationMonitorService::class.java)
                .setAction(LocationMonitorService.SILENCE))
        }
        monitoring.addView(dismissButton)
        monitoring.addView(text("One alert per stop, per journey. Restart to re-arm.", 12f).apply { setTextColor(Color.rgb(191, 213, 215)) })
        content.addView(AppUi.surface(this, monitoring, dark = true))
        content.addView(gap(8))
        val section = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        section.addView(text("Your destinations", 23f, true), LinearLayout.LayoutParams(0, -2, 1f))
        destinationCount = pill("")
        section.addView(destinationCount)
        content.addView(section)
        content.addView(muted("Your places. Your wake-up call."))
        content.addView(button("+  Add destination") { showAddDestinationOptions() })
        content.addView(gap(10))
        if (destinations.isEmpty()) {
            content.addView(AppUi.surface(this, card().apply {
                addView(pill("YOUR NEXT STOP STARTS HERE"), LinearLayout.LayoutParams(-2, -2))
                addView(gap())
                addView(text("Where are we headed?", 22f, true))
                addView(muted("Save a favorite stop or drop a pin on the map. We’ll keep an eye on the distance while you take a break."))
            }))
        }
        destinations.forEachIndexed { index, destination ->
            content.addView(AppUi.surface(this, card().apply {
                val heading = LinearLayout(this@MainActivity).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
                heading.addView(pill(String.format(java.util.Locale.US, "%02d", index + 1)),
                    LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(12) })
                heading.addView(text(destination.name, 21f, true), LinearLayout.LayoutParams(0, -2, 1f))
                heading.addView(MaterialSwitch(this@MainActivity).apply {
                    isChecked = destination.enabled
                    minHeight = dp(48); minWidth = dp(48)
                    contentDescription = "Enable ${destination.name}"
                    setOnCheckedChangeListener { _, checked ->
                        destinations = destinations.map { if (it.id == destination.id) it.copy(enabled = checked) else it }
                        persist(); updateJourneyState()
                    }
                })
                addView(heading)
                addView(muted(String.format(java.util.Locale.US, "%.5f, %.5f", destination.latitude, destination.longitude), 12f))
                addView(gap(10))
                addView(pill("WITHIN ${destination.radiusMeters} m"), LinearLayout.LayoutParams(-2, -2))
                addView(gap(8))
                addView(muted("Sound  ·  ${soundName(destination.soundUri)}"))
                addView(muted("Vibration  ·  ${destination.vibration}"))
                addView(gap(8))
                addView(LinearLayout(this@MainActivity).apply {
                    addView(button("Edit stop") { editDestination(destinations.first { it.id == destination.id }) },
                        LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
                    addView(button("Delete") {
                        MaterialAlertDialogBuilder(this@MainActivity).setTitle("Delete ${destination.name}?")
                            .setMessage("This removes the saved destination.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                                destinations = destinations.filterNot { it.id == destination.id }
                                persist(); render()
                            }.show()
                    }.apply {
                        setTextColor(palette.danger)
                        backgroundTintList = android.content.res.ColorStateList.valueOf(palette.dangerContainer)
                    },
                        LinearLayout.LayoutParams(0, -2, 1f))
                })
            }))
        }
        content.addView(gap(8))
        content.addView(AppUi.surface(this, card().apply {
            addView(text("A smoother ride", 19f, true))
            addView(muted("Keep location and notifications on, and check your alarm volume. A 500–1,000 m radius works well as a starting point."))
            addView(muted("GPS reception, Do Not Disturb, and battery restrictions can affect alerts. This is not a guaranteed wake-up alarm.", 12f))
            addView(button("Notification settings") {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            })
            addView(button("Location settings") { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) })
        }))
        content.addView(muted("MADE FOR THE JOURNEY", 11f).apply {
            gravity = android.view.Gravity.CENTER; letterSpacing = 0.15f
        })
        updateJourneyState()
    }

    private fun updateJourneyState() {
        val running = LocationMonitorService.running
        val newStatus = if (running) LocationMonitorService.status else "Pick your stops, settle in, and start monitoring when you’re ready."
        if (statusText.text.toString() != newStatus) statusText.text = newStatus
        journeyBadge.text = if (running) "●  JOURNEY ACTIVE" else "●  READY TO GO"
        journeyTitle.text = if (running) "Watching\nyour stops." else "Your next stop,\nin sight."
        monitorButton.text = if (running) "Stop monitoring" else "Start monitoring  →"
        dismissButton.visibility = if (running) View.VISIBLE else View.GONE
        destinationCount.text = "${destinations.count { it.enabled }} active"
    }

    private fun showAddDestinationOptions() {
        val choices = column().apply { setPadding(dp(24), dp(12), dp(24), dp(16)) }
        choices.addView(muted("Every good journey starts with a destination."))
        choices.addView(gap())
        val dialog = MaterialAlertDialogBuilder(this).setTitle("Where to?")
            .setView(choices).setNegativeButton("Cancel", null).create()
        choices.addView(primaryButton("Save current location") {
            dialog.dismiss()
            saveCurrentLocation()
        })
        choices.addView(muted("Make a stop out of where you are right now.", 13f))
        choices.addView(gap())
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
        choices.addView(muted("Explore the map and drop a pin on your stop.", 13f))
        choices.addView(gap())
        choices.addView(muted("Maps by OpenStreetMap. The tile provider receives your IP address and viewed area.", 12f))
        dialog.show()
    }

    private fun saveCurrentLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 102)
            return
        }
        val progress = MaterialAlertDialogBuilder(this).setTitle("Current location")
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
                MaterialAlertDialogBuilder(this).setTitle("Location unavailable").setMessage(message)
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
            MaterialAlertDialogBuilder(this).setTitle("Allow arrival alerts")
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
                MaterialAlertDialogBuilder(this).setTitle("Precise location needed")
                    .setMessage("Allow precise location in app settings to save your current position, or choose another location on the map without location permission.")
                    .setNegativeButton("Cancel", null).setPositiveButton("App settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }.show()
            }
        }
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startMonitoring()
            else MaterialAlertDialogBuilder(this).setTitle("Permissions needed")
                .setMessage("Precise location and notifications are required. You can enable them in app settings and then tap Start monitoring.")
                .setNegativeButton("Not now", null).setPositiveButton("App settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }.show()
        }
    }

    private fun editDestination(existing: Destination?, initialLatitude: Double? = null, initialLongitude: Double? = null, restored: Bundle? = null) {
        val form = column().apply { setPadding(dp(24), dp(12), dp(24), dp(16)) }
        form.addView(muted("Make it your stop. Give it a name and choose how you’d like to be alerted."))
        form.addView(gap())
        fun field(label: String, value: String, numeric: Boolean = false): EditText {
            val container = TextInputLayout(this).apply {
                hint = label
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            }
            val input = TextInputEditText(container.context).apply {
                setSingleLine(); setText(value)
                minHeight = dp(56)
                if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            container.addView(input, LinearLayout.LayoutParams(-1, -2))
            form.addView(container, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            return input
        }
        val name = field("Destination name", restored?.getString("name") ?: existing?.name ?: "")
        val latitude = field("Latitude (−90 to 90)", restored?.getString("latitude") ?: (existing?.latitude ?: initialLatitude)?.toString() ?: "", true)
        val longitude = field("Longitude (−180 to 180)", restored?.getString("longitude") ?: (existing?.longitude ?: initialLongitude)?.toString() ?: "", true)
        form.addView(text("Review the selected coordinates, or update them with your current position below.", 13f))
        form.addView(button("Use current location") { useCurrentLocation(latitude, longitude) })
        val radius = field("Arrival radius in meters (100–10,000)", restored?.getString("radius") ?: (existing?.radiusMeters ?: 500).toString(), true)
        val presets = LinearLayout(this)
        listOf(250, 500, 1000).forEach { meters ->
            presets.addView(button("${meters} m") { radius.setText(meters.toString()) },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        }
        form.addView(presets)
        form.addView(gap())
        form.addView(text("Your arrival feedback", 18f, true))
        selectedSound = if (restored != null) restored.getString("sound") else existing?.soundUri
        soundButton = button("Sound: ${soundName(selectedSound)}") { chooseSound() }
        form.addView(soundButton)
        val patterns = listOf("Off", "Gentle", "Strong")
        val vibrationContainer = TextInputLayout(this).apply {
            hint = "Vibration pattern"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        }
        val vibration = com.google.android.material.textfield.MaterialAutoCompleteTextView(vibrationContainer.context).apply {
            inputType = InputType.TYPE_NULL
            minHeight = dp(56)
            setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, patterns))
            setText(restored?.getString("vibration") ?: existing?.vibration ?: "Strong", false)
        }
        vibrationContainer.addView(vibration, LinearLayout.LayoutParams(-1, -2))
        form.addView(vibrationContainer)
        form.addView(text("Sound and vibration repeat for up to 60 seconds. Pick Silent and Off for notification-only feedback.", 13f))
        val dialog = MaterialAlertDialogBuilder(this).setTitle(if (existing == null) "Add destination" else "Edit destination")
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
                            soundUri = selectedSound, vibration = vibration.text.toString().takeIf { it in patterns } ?: "Strong")
                        destinations = if (existing == null) destinations + destination
                            else destinations.map { if (it.id == existing.id) destination else it }
                        persist(); dialog.dismiss(); render()
                    }
                }
            }
        }
        editorState = {
            Bundle().apply {
                putString("existingId", existing?.id)
                putString("name", name.text.toString())
                putString("latitude", latitude.text.toString())
                putString("longitude", longitude.text.toString())
                putString("radius", radius.text.toString())
                putString("sound", selectedSound)
                putString("vibration", vibration.text.toString())
            }
        }
        dialog.setOnDismissListener {
            editorState = null
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
            MaterialAlertDialogBuilder(this).setTitle("Arrival sound")
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
