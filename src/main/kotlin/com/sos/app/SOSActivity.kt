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
import com.sos.app.data.ContactStorage
import com.sos.app.recording.RecordingManager
import com.sos.app.upload.UploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SOSActivity : AppCompatActivity() {
    private lateinit var recordingStateView: TextView
    private lateinit var sosButton: Button
    private lateinit var recordingManager: RecordingManager
    private lateinit var uploadManager: UploadManager
    private lateinit var contactStorage: ContactStorage
    private val TAG = "SOSActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos)

        recordingStateView = findViewById(R.id.recording_state)
        sosButton = findViewById(R.id.sos_button)
        recordingManager = RecordingManager(this)
        uploadManager = UploadManager(this)
        contactStorage = ContactStorage(this)

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

        val contacts = contactStorage.getVerifiedContacts().map { it.email }
        if (contacts.isEmpty()) {
            recordingStateView.text = "❌ No verified contacts\nAdd contacts in Setup"
            Log.e(TAG, "No verified contacts")
            return
        }

        if (recordingManager.start(contacts)) {
            recordingStateView.text = "🔴 RECORDING"
            recordingStateView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            sosButton.text = "RECORDING..."
            Log.d(TAG, "Recording started, contacts: ${contacts.size}")
        } else {
            recordingStateView.text = "❌ Failed to start"
            Log.e(TAG, "Failed to start recording")
        }
    }

    private fun stopRecording() {
        if (!recordingManager.isRecording()) return

        if (recordingManager.stop()) {
            val duration = recordingManager.getRecordingDuration()
            recordingStateView.text = "✓ Recorded ${duration}s\n⏳ Uploading..."
            recordingStateView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            sosButton.text = "UPLOADING..."

            val buffer = recordingManager.getIncidentBuffer()
            if (buffer != null) {
                Log.d(TAG, "Starting upload for incident ${buffer.getIncidentId()}")

                // Upload in background
                CoroutineScope(Dispatchers.IO).launch {
                    val result = uploadManager.uploadIncident(buffer)
                    runOnUiThread {
                        if (result.success) {
                            recordingStateView.text = "✅ Sent to ${result.contactResults.size} contacts\n${result.message}"
                            recordingStateView.setBackgroundColor(ContextCompat.getColor(this@SOSActivity, android.R.color.holo_green_dark))
                            sosButton.text = "COMPLETE"
                            Log.d(TAG, "Upload successful: ${result.message}")
                        } else {
                            recordingStateView.text = "⚠️ Upload error\n${result.message}"
                            recordingStateView.setBackgroundColor(ContextCompat.getColor(this@SOSActivity, android.R.color.holo_orange_dark))
                            Log.e(TAG, "Upload failed: ${result.message}")
                        }
                    }
                }
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
