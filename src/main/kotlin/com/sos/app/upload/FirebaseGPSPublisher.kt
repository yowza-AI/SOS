package com.sos.app.upload

import android.location.Location
import android.util.Log
import com.google.firebase.database.FirebaseDatabase

/**
 * Publishes live GPS coordinates to Firebase Realtime Database.
 * Contacts can view incident map in real-time via web dashboard.
 */
class FirebaseGPSPublisher(private val incidentId: String) {
    private val database = FirebaseDatabase.getInstance().reference
    private val TAG = "FirebaseGPSPublisher"

    fun publishLocation(location: Location) {
        try {
            val locationData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracy" to location.accuracy,
                "altitude" to location.altitude
            )

            database.child("incidents").child(incidentId).child("gps").push()
                .setValue(locationData)
                .addOnSuccessListener {
                    Log.d(TAG, "GPS published: ${location.latitude}, ${location.longitude}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to publish GPS: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing GPS: ${e.message}")
        }
    }

    fun publishIncidentMetadata(duration: Long, contacts: List<String>) {
        try {
            val metadata = mapOf(
                "incidentId" to incidentId,
                "startTime" to System.currentTimeMillis(),
                "duration" to duration,
                "contacts" to contacts,
                "status" to "active"
            )

            database.child("incidents").child(incidentId).setValue(metadata)
                .addOnSuccessListener {
                    Log.d(TAG, "Incident metadata published")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to publish metadata: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing metadata: ${e.message}")
        }
    }

    fun closeIncident() {
        try {
            database.child("incidents").child(incidentId).child("status")
                .setValue("closed")
                .addOnSuccessListener {
                    Log.d(TAG, "Incident marked as closed")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to close incident: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing incident: ${e.message}")
        }
    }
}
