package com.sos.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class SOSActivity : AppCompatActivity() {
    private lateinit var recordingStateView: TextView
    private lateinit var sosButton: Button
    private lateinit var locationClient: FusedLocationProviderClient

    private var isRecording = false
    private var recordingStartTime = 0L
    private var lastKnownLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos)

        recordingStateView = findViewById(R.id.recording_state)
        sosButton = findViewById(R.id.sos_button)
        locationClient = LocationServices.getFusedLocationProviderClient(this)

        setupSOSButton()
        requestRequiredPermissions()
        getLastKnownLocation()
    }

    private fun setupSOSButton() {
        sosButton.apply {
            text = "HOLD TO RECORD"
            setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> startRecording()
                    android.view.MotionEvent.ACTION_UP -> stopRecording()
                }
                true
            }
        }
    }

    private fun startRecording() {
        if (isRecording) return
        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        recordingStateView.text = "🔴 RECORDING"
        recordingStateView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        sosButton.text = "RECORDING..."
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        val duration = (System.currentTimeMillis() - recordingStartTime) / 1000
        recordingStateView.text = "✓ Recorded ${duration}s\nUploading..."
        recordingStateView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        sosButton.text = "UPLOADING..."

        // TODO: Start upload process
        // TODO: Notify contacts
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 100)
        }
    }

    private fun getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        locationClient.lastLocation.addOnSuccessListener { location ->
            lastKnownLocation = location
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            // Handle permission results
        }
    }
}
