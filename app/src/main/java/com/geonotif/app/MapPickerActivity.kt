package com.geonotif.app

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import android.content.Intent
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
class MapPickerActivity : AppCompatActivity() {
    companion object {
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
    }

    private val palette by lazy { AppUi.palette(this) }
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
            setBackgroundColor(palette.background)
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
        fun dp(value: Int) = AppUi.dp(this, value)
        fun label(value: String, size: Float) = AppUi.label(this, value, size)
        root.addView(MaterialToolbar(this).apply {
            title = "Find your stop"
            subtitle = "Pan, zoom, and tap to drop a pin"
            setNavigationIcon(R.drawable.ic_arrow_back)
            navigationContentDescription = "Cancel location selection"
            setNavigationOnClickListener { setResult(RESULT_CANCELED); finish() }
            setTitleTextColor(palette.ink)
            setSubtitleTextColor(palette.muted)
            setNavigationIconTint(palette.ink)
        }, LinearLayout.LayoutParams(-1, -2))
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            if (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                overlayManager.tilesOverlay.setColorFilter(org.osmdroid.views.overlay.TilesOverlay.INVERT_COLORS)
            }
            setMultiTouchControls(true)
            minZoomLevel = 2.0
            maxZoomLevel = 19.0
            contentDescription = "OpenStreetMap destination picker. Tap the map to select a location."
        }
        root.addView(AppUi.surface(this, map).apply { radius = dp(24).toFloat() },
            LinearLayout.LayoutParams(-1, 0, 1f).apply {
                marginStart = dp(12); marginEnd = dp(12); topMargin = dp(8)
            })
        // Always visible attribution, not obscured by map controls or selection UI.
        root.addView(label("© OpenStreetMap contributors", 13f).apply {
            setTextColor(palette.teal)
            minHeight = dp(48)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            setOnClickListener {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) }
                catch (_: android.content.ActivityNotFoundException) { }
            }
        })
        val selection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        coordinates = label("Tap the map to choose your destination", 16f)
        selection.addView(coordinates)
        selection.addView(label("Blank map? Connect to the internet to load new areas.", 12f).apply { setTextColor(palette.muted) })
        confirm = AppUi.button(this, "Use this location", primary = true) {
            selected?.let {
                setResult(RESULT_OK, Intent().putExtra(LATITUDE, it.latitude).putExtra(LONGITUDE, it.longitude))
                finish()
            }
        }.apply { isEnabled = false }
        selection.addView(confirm)
        root.addView(AppUi.surface(this, selection), LinearLayout.LayoutParams(-1, -2).apply {
            marginStart = dp(12); marginEnd = dp(12); bottomMargin = dp(12)
        })
        setContentView(root)

        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint): Boolean { select(point); return true }
            override fun longPressHelper(point: GeoPoint): Boolean { select(point); return true }
        }))
        marker = Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Selected destination"
            icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(this@MapPickerActivity, R.drawable.ic_map_pin)
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
