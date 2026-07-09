package com.sos.app.recording

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.util.UUID

/**
 * Local encrypted storage for incident recordings.
 * Manages files for a single incident (video, audio, GPS metadata).
 */
class IncidentBuffer(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val bufferDir = File(context.filesDir, "incidents").apply {
        if (!exists()) mkdirs()
    }

    private val incidentId = UUID.randomUUID().toString()
    private val incidentDir = File(bufferDir, incidentId).apply {
        if (!exists()) mkdirs()
    }

    companion object {
        private const val TAG = "IncidentBuffer"
        private const val VIDEO_FRONT_FILE = "video_front.mp4"
        private const val VIDEO_REAR_FILE = "video_rear.mp4"
        private const val AUDIO_FILE = "audio.aac"
        private const val GPS_FILE = "gps.csv"
        private const val METADATA_FILE = "metadata.json"
    }

    fun getVideoFrontFile(): File = File(incidentDir, VIDEO_FRONT_FILE)
    fun getVideoRearFile(): File = File(incidentDir, VIDEO_REAR_FILE)
    fun getAudioFile(): File = File(incidentDir, AUDIO_FILE)
    fun getGPSFile(): File = File(incidentDir, GPS_FILE)
    fun getMetadataFile(): File = File(incidentDir, METADATA_FILE)

    fun getIncidentId(): String = incidentId

    fun saveMetadata(duration: Long, contactEmails: List<String>) {
        try {
            val metadata = """
                {
                    "incidentId": "$incidentId",
                    "timestamp": ${System.currentTimeMillis()},
                    "duration": $duration,
                    "contacts": ${contactEmails.joinToString(",", "[\"", "\"]") { "\"$it\"" }}
                }
            """.trimIndent()

            val metadataFile = getMetadataFile()
            val encryptedFile = EncryptedFile.using(
                metadataFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            )
            encryptedFile.openFileOutput().use { fos ->
                fos.write(metadata.toByteArray())
            }
            Log.d(TAG, "Metadata saved for incident $incidentId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save metadata: ${e.message}")
        }
    }

    fun getAllFilesForIncident(): List<File> {
        return listOfNotNull(
            getVideoFrontFile().takeIf { it.exists() },
            getVideoRearFile().takeIf { it.exists() },
            getAudioFile().takeIf { it.exists() },
            getGPSFile().takeIf { it.exists() },
            getMetadataFile().takeIf { it.exists() }
        )
    }

    fun deleteIncident() {
        try {
            incidentDir.deleteRecursively()
            Log.d(TAG, "Incident $incidentId deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete incident: ${e.message}")
        }
    }

    fun getTotalSize(): Long {
        return getAllFilesForIncident().sumOf { it.length() }
    }
}
