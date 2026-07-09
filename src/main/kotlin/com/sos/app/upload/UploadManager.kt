package com.sos.app.upload

import android.content.Context
import android.util.Log
import com.sos.app.data.ContactStorage
import com.sos.app.recording.IncidentBuffer
import java.io.File

/**
 * Orchestrates upload of incident data to contacts.
 * Handles chunking, email delivery, Dropbox upload, and Firebase GPS streaming.
 */
class UploadManager(private val context: Context) {
    private val contactStorage = ContactStorage(context)
    private val TAG = "UploadManager"

    fun uploadIncident(buffer: IncidentBuffer): UploadResult {
        val contacts = contactStorage.getVerifiedContacts()
        if (contacts.isEmpty()) {
            Log.w(TAG, "No verified contacts, cannot upload")
            return UploadResult(success = false, message = "No verified contacts")
        }

        return try {
            val incidentId = buffer.getIncidentId()
            val files = buffer.getAllFilesForIncident()
            Log.d(TAG, "Uploading incident $incidentId with ${files.size} files")

            // Chunk video files
            val chunks = mutableListOf<File>()
            val videoFront = buffer.getVideoFrontFile()
            val videoRear = buffer.getVideoRearFile()

            if (videoFront.exists()) {
                val frontChunks = VideoChunker(videoFront).chunk()
                chunks.addAll(frontChunks)
                Log.d(TAG, "Chunked front video: ${frontChunks.size} chunks")
            }

            if (videoRear.exists()) {
                val rearChunks = VideoChunker(videoRear).chunk()
                chunks.addAll(rearChunks)
                Log.d(TAG, "Chunked rear video: ${rearChunks.size} chunks")
            }

            // TODO: Upload chunks via email and/or Dropbox
            // TODO: Stream GPS to Firebase
            // TODO: Send notifications to contacts

            UploadResult(
                success = true,
                message = "Upload started",
                incidentId = incidentId,
                chunkCount = chunks.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}")
            UploadResult(success = false, message = e.message ?: "Unknown error")
        }
    }

    data class UploadResult(
        val success: Boolean,
        val message: String,
        val incidentId: String? = null,
        val chunkCount: Int = 0
    )
}
