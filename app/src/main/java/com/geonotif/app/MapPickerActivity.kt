package com.geonotif.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale

/** Interactive OSM tile picker. No geocoder, API key, or location permission required. */
class MapPickerActivity : Activity() {
    companion object {
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
    }

    private lateinit var map: MapView
    private lateinit var marker: Marker
    private lateinit var coordinates: TextView
    private lateinit var confirm: Button
    private var selected: GeoPoint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            // Identify the app to the public tile server and keep HTTP-aware caching enabled.
            userAgentValue = "GeoNotif/1.0 (com.geonotif.app; Android)"
            osmdroidBasePath = File(cacheDir, "osmdroid").apply { mkdirs() }
            osmdroidTileCache = File(osmdroidBasePath, "tiles").apply { mkdirs() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        fun label(value: String, size: Float) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.rgb(26, 47, 62))
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        root.addView(label("Choose a destination", 23f))
        root.addView(label("Drag to explore, pinch to zoom, and tap to place your stop.", 14f))
        root.addView(label("Internet is needed for uncached map areas. If tiles are blank, check your connection and try again.", 12f))
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 2.0
            maxZoomLevel = 19.0
            contentDescription = "OpenStreetMap destination picker. Tap the map to select a location."
        }
        root.addView(map, LinearLayout.LayoutParams(-1, 0, 1f))
        // Always visible attribution, not obscured by map controls or selection UI.
        root.addView(label("© OpenStreetMap contributors", 13f).apply {
            setTextColor(Color.rgb(22, 125, 141))
            setOnClickListener {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) }
                catch (_: android.content.ActivityNotFoundException) { }
            }
        })
        coordinates = label("No location selected — tap the map", 15f)
        root.addView(coordinates)
        val actions = LinearLayout(this)
        actions.addView(Button(this).apply {
            text = "Cancel"; isAllCaps = false
            setOnClickListener { setResult(RESULT_CANCELED); finish() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        confirm = Button(this).apply {
            text = "Use this location"; isAllCaps = false; isEnabled = false
            setOnClickListener {
                selected?.let {
                    setResult(RESULT_OK, Intent().putExtra(LATITUDE, it.latitude).putExtra(LONGITUDE, it.longitude))
                    finish()
                }
            }
        }
        actions.addView(confirm, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)
        setContentView(root)

        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint): Boolean { select(point); return true }
            override fun longPressHelper(point: GeoPoint): Boolean { select(point); return true }
        }))
        marker = Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Selected destination"
            setOnMarkerClickListener { _, _ -> true }
        }
        val latitude = savedInstanceState?.getDouble("centerLat") ?: intent.getDoubleExtra(LATITUDE, 20.0)
        val longitude = savedInstanceState?.getDouble("centerLon") ?: intent.getDoubleExtra(LONGITUDE, 0.0)
        map.controller.setZoom(savedInstanceState?.getDouble("zoom") ?: if (intent.hasExtra(LATITUDE)) 13.0 else 3.0)
        map.controller.setCenter(GeoPoint(latitude, longitude))
        if (savedInstanceState?.containsKey("selectedLat") == true) {
            select(GeoPoint(savedInstanceState.getDouble("selectedLat"), savedInstanceState.getDouble("selectedLon")))
        }
    }

    private fun select(point: GeoPoint) {
        if (!ArrivalRules.validCoordinates(point.latitude, point.longitude)) return
        selected = point
        marker.position = point
        if (!map.overlays.contains(marker)) map.overlays.add(marker)
        coordinates.text = String.format(Locale.US, "Selected: %.6f, %.6f", point.latitude, point.longitude)
        confirm.isEnabled = true
        map.invalidate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDouble("centerLat", map.mapCenter.latitude)
        outState.putDouble("centerLon", map.mapCenter.longitude)
        outState.putDouble("zoom", map.zoomLevelDouble)
        selected?.let {
            outState.putDouble("selectedLat", it.latitude)
            outState.putDouble("selectedLon", it.longitude)
        }
    }
    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { map.onPause(); super.onPause() }
    override fun onDestroy() { map.onDetach(); super.onDestroy() }
}
