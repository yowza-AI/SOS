package com.sos.app.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Orchestrates recording of video (dual-camera), audio, and GPS.
 * Coordinates Camera2Recorder, AudioRecorder, and GPSTracker.
 */
class RecordingManager(private val context: Context) {
    private var cameraRecorder: Camera2Recorder? = null
    private var audioRecorder: AudioRecorder? = null
    private var gpsTracker: GPSTracker? = null
    private var incidentBuffer: IncidentBuffer? = null

    private var isRecording = false
    private var recordingStartTime = 0L

    companion object {
        private const val TAG = "RecordingManager"
    }

    fun start(contacts: List<String>): Boolean {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress")
            return false
        }

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Required permissions not granted")
            return false
        }

        return try {
            incidentBuffer = IncidentBuffer(context)
            cameraRecorder = Camera2Recorder(context)
            audioRecorder = AudioRecorder(incidentBuffer!!.getAudioFile())
            gpsTracker = GPSTracker(context, incidentBuffer!!.getGPSFile())

            // Start camera recording
            if (!cameraRecorder!!.start(incidentBuffer!!.getVideoRearFile())) {
                Log.e(TAG, "Failed to start camera")
                return false
            }

            // Start audio recording
            if (!audioRecorder!!.start()) {
                Log.e(TAG, "Failed to start audio")
                cameraRecorder!!.stop()
                return false
            }

            // Start GPS tracking
            gpsTracker!!.start()

            isRecording = true
            recordingStartTime = System.currentTimeMillis()

            // Save metadata
            incidentBuffer!!.saveMetadata(0, contacts)

            Log.d(TAG, "Recording started, incident ID: ${incidentBuffer!!.getIncidentId()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            stop()
            false
        }
    }

    fun stop(): Boolean {
        if (!isRecording) {
            Log.w(TAG, "Recording not in progress")
            return false
        }

        return try {
            val duration = (System.currentTimeMillis() - recordingStartTime) / 1000

            cameraRecorder?.stop()
            audioRecorder?.stop()
            gpsTracker?.stop()

            isRecording = false

            // Save final metadata
            incidentBuffer?.saveMetadata(duration, emptyList())

            Log.d(TAG, "Recording stopped, duration: ${duration}s, incident ID: ${incidentBuffer?.getIncidentId()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
            false
        }
    }

    fun getIncidentId(): String? = incidentBuffer?.getIncidentId()

    fun getIncidentBuffer(): IncidentBuffer? = incidentBuffer

    fun getRecordingDuration(): Long {
        return if (isRecording) {
            (System.currentTimeMillis() - recordingStartTime) / 1000
        } else {
            0
        }
    }

    fun isRecording(): Boolean = isRecording

    private fun hasRequiredPermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasConcurrentCameraSupport(): Boolean = cameraRecorder?.hasConcurrentCameraSupport() ?: false
}
