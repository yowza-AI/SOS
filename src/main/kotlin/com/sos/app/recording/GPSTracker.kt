package com.sos.app.recording

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import java.io.File

/**
 * GPS location tracker for emergency incidents.
 * Logs coordinates at 5-second intervals with timestamp.
 */
class GPSTracker(context: Context, private val outputFile: File) {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val locations = mutableListOf<LocationPoint>()
    private var isTracking = false

    data class LocationPoint(
        val timestamp: Long,
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val altitude: Double
    )

    companion object {
        private const val TAG = "GPSTracker"
    }

    fun start() {
        isTracking = true
        locations.clear()
        // TODO: Implement location updates using LocationRequest
        Log.d(TAG, "GPS tracking started")
    }

    fun stop() {
        isTracking = false
        saveToFile()
        Log.d(TAG, "GPS tracking stopped, ${locations.size} points recorded")
    }

    fun addLocation(location: Location) {
        if (!isTracking) return
        locations.add(
            LocationPoint(
                timestamp = System.currentTimeMillis(),
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                altitude = location.altitude
            )
        )
    }

    private fun saveToFile() {
        try {
            outputFile.writeText(
                locations.joinToString("\n") { point ->
                    "${point.timestamp},${point.latitude},${point.longitude},${point.accuracy},${point.altitude}"
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save GPS data: ${e.message}")
        }
    }

    fun getLastLocation(): Location? {
        return locations.lastOrNull()?.let { point ->
            Location("gps").apply {
                latitude = point.latitude
                longitude = point.longitude
                accuracy = point.accuracy
                altitude = point.altitude
            }
        }
    }
}
