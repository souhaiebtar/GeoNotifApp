package com.geonotif.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Destination(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 500,
    val enabled: Boolean = true,
    // null means the system alarm; empty means silent.
    val soundUri: String? = null,
    val vibration: String = "Strong"
)

object DestinationStore {
    fun load(context: Context): List<Destination> {
        val raw = context.getSharedPreferences("destinations", Context.MODE_PRIVATE)
            .getString("items", "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Destination(
                    id = o.getString("id"), name = o.getString("name"),
                    latitude = o.getDouble("latitude"), longitude = o.getDouble("longitude"),
                    radiusMeters = o.getInt("radius"), enabled = o.optBoolean("enabled", true),
                    soundUri = if (o.isNull("sound")) null else o.getString("sound"),
                    vibration = o.optString("vibration", "Strong")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun save(context: Context, destinations: List<Destination>) {
        val array = JSONArray()
        destinations.forEach { d ->
            array.put(JSONObject().apply {
                put("id", d.id); put("name", d.name)
                put("latitude", d.latitude); put("longitude", d.longitude)
                put("radius", d.radiusMeters); put("enabled", d.enabled)
                put("sound", d.soundUri ?: JSONObject.NULL); put("vibration", d.vibration)
            })
        }
        context.getSharedPreferences("destinations", Context.MODE_PRIVATE)
            .edit().putString("items", array.toString()).apply()
    }
}

object ArrivalRules {
    fun validCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun isArrival(distance: Float, accuracy: Float, radius: Int, ageMillis: Long): Boolean =
        distance.isFinite() && accuracy.isFinite() && accuracy >= 0 &&
            ageMillis in 0..30_000 && accuracy <= maxOf(100, radius).toFloat() && distance <= radius
}
