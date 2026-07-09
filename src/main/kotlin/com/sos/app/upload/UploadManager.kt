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
class UploadManager(
    private val context: Context,
    private val sendgridApiKey: String = "", // TODO: Inject from config
    private val twilioAccountSid: String = "",
    private val twilioAuthToken: String = ""
) {
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

            // Generate live location link (Firebase incident dashboard)
            val locationLink = "https://sos.app/incident/$incidentId"

            // Upload to each contact
            val uploadResults = mutableListOf<ContactUploadResult>()
            for (contact in contacts) {
                val contactResult = uploadToContact(contact, incidentId, chunks, locationLink)
                uploadResults.add(contactResult)
                Log.d(TAG, "Upload to ${contact.email}: ${contactResult.success}")
            }

            // Stream GPS to Firebase
            val gpsFile = buffer.getGPSFile()
            if (gpsFile.exists()) {
                publishGPSToFirebase(incidentId, gpsFile)
            }

            UploadResult(
                success = uploadResults.any { it.success },
                message = "Upload completed (${uploadResults.count { it.success }}/${uploadResults.size} contacts reached)",
                incidentId = incidentId,
                chunkCount = chunks.size,
                contactResults = uploadResults
            )
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}")
            UploadResult(success = false, message = e.message ?: "Unknown error")
        }
    }

    private fun uploadToContact(
        contact: com.sos.app.data.Contact,
        incidentId: String,
        chunks: List<File>,
        locationLink: String
    ): ContactUploadResult {
        val result = ContactUploadResult(contact.email)

        // Send email with video chunks
        if (contact.email.isNotEmpty() && sendgridApiKey.isNotEmpty()) {
            try {
                val emailUploader = EmailUploader(sendgridApiKey)
                val emailResults = emailUploader.sendChunks(contact.email, incidentId, chunks, locationLink)
                result.emailSuccess = emailResults.all { it.success }
                Log.d(TAG, "Email chunks: ${emailResults.size} sent")
            } catch (e: Exception) {
                Log.e(TAG, "Email upload failed: ${e.message}")
            }
        }

        // Upload to Dropbox if contact has token
        if (!contact.dropboxAccessToken.isNullOrEmpty()) {
            try {
                val dropboxUploader = DropboxUploader(contact.dropboxAccessToken!!)
                val dropboxResults = dropboxUploader.uploadChunks(incidentId, chunks)
                result.dropboxSuccess = dropboxResults.all { it.success }
                Log.d(TAG, "Dropbox chunks: ${dropboxResults.size} uploaded")
            } catch (e: Exception) {
                Log.e(TAG, "Dropbox upload failed: ${e.message}")
            }
        }

        // Send notifications
        if (twilioAccountSid.isNotEmpty() && twilioAuthToken.isNotEmpty() && sendgridApiKey.isNotEmpty()) {
            try {
                val notifier = ContactNotifier(context, twilioAccountSid, twilioAuthToken, sendgridApiKey)
                val notificationResults = notifier.notifyContact(
                    name = contact.name,
                    email = contact.email,
                    phone = contact.phone,
                    incidentId = incidentId,
                    locationLink = locationLink
                )
                result.notificationSuccess = notificationResults.any { it.success }
                Log.d(TAG, "Notifications: ${notificationResults.size} sent")
            } catch (e: Exception) {
                Log.e(TAG, "Notification failed: ${e.message}")
            }
        }

        result.success = result.emailSuccess || result.dropboxSuccess

        return result
    }

    private fun publishGPSToFirebase(incidentId: String, gpsFile: File) {
        try {
            val publisher = FirebaseGPSPublisher(incidentId)
            // Parse GPS CSV and publish
            gpsFile.forEachLine { line ->
                val parts = line.split(",")
                if (parts.size >= 3) {
                    val lat = parts[1].toDoubleOrNull() ?: return@forEachLine
                    val lon = parts[2].toDoubleOrNull() ?: return@forEachLine
                    val fakeLocation = android.location.Location("gps").apply {
                        latitude = lat
                        longitude = lon
                        accuracy = parts.getOrNull(3)?.toFloatOrNull() ?: 0f
                        altitude = parts.getOrNull(4)?.toDoubleOrNull() ?: 0.0
                    }
                    publisher.publishLocation(fakeLocation)
                }
            }
            Log.d(TAG, "GPS published to Firebase")
        } catch (e: Exception) {
            Log.e(TAG, "GPS publish failed: ${e.message}")
        }
    }

    data class ContactUploadResult(
        val email: String,
        var success: Boolean = false,
        var emailSuccess: Boolean = false,
        var dropboxSuccess: Boolean = false,
        var notificationSuccess: Boolean = false
    )

    data class UploadResult(
        val success: Boolean,
        val message: String,
        val incidentId: String? = null,
        val chunkCount: Int = 0,
        val contactResults: List<ContactUploadResult> = emptyList()
    )
}
