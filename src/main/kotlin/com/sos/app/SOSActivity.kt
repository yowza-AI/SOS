package com.sos.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sos.app.recording.RecordingManager

class SOSActivity : AppCompatActivity() {
    private lateinit var recordingStateView: TextView
    private lateinit var sosButton: Button
    private lateinit var recordingManager: RecordingManager
    private val TAG = "SOSActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos)

        recordingStateView = findViewById(R.id.recording_state)
        sosButton = findViewById(R.id.sos_button)
        recordingManager = RecordingManager(this)

        setupSOSButton()
        requestRequiredPermissions()
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
        if (recordingManager.isRecording()) return

        // TODO: Get contacts from SharedPreferences/Database
        val contacts = listOf("emergency@example.com")

        if (recordingManager.start(contacts)) {
            recordingStateView.text = "🔴 RECORDING"
            recordingStateView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            sosButton.text = "RECORDING..."
            Log.d(TAG, "Recording started")
        } else {
            recordingStateView.text = "❌ Failed to start"
            Log.e(TAG, "Failed to start recording")
        }
    }

    private fun stopRecording() {
        if (!recordingManager.isRecording()) return

        if (recordingManager.stop()) {
            val duration = recordingManager.getRecordingDuration()
            recordingStateView.text = "✓ Recorded ${duration}s\nUploading..."
            recordingStateView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            sosButton.text = "UPLOADING..."

            val buffer = recordingManager.getIncidentBuffer()
            if (buffer != null) {
                Log.d(TAG, "Incident buffered: ${buffer.getIncidentId()}, size: ${buffer.getTotalSize()} bytes")
                // TODO: Start upload process with buffer.getAllFilesForIncident()
            }
        } else {
            recordingStateView.text = "❌ Error stopping"
            Log.e(TAG, "Failed to stop recording")
        }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Log.d(TAG, "All permissions granted")
            } else {
                Log.w(TAG, "Some permissions denied")
            }
        }
    }

    override fun onDestroy() {
        if (recordingManager.isRecording()) {
            recordingManager.stop()
        }
        super.onDestroy()
    }
}
