package com.sos.app.upload

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Base64

/**
 * Sends video chunks to emergency contacts via email (SendGrid API).
 * Each chunk is a separate email attachment.
 */
class EmailUploader(private val sendgridApiKey: String) {
    private val client = OkHttpClient()
    private val TAG = "EmailUploader"

    data class EmailResult(
        val success: Boolean,
        val message: String,
        val messageId: String? = null
    )

    fun sendChunks(
        contactEmail: String,
        incidentId: String,
        chunks: List<File>,
        locationLink: String
    ): List<EmailResult> {
        val results = mutableListOf<EmailResult>()

        for ((index, chunk) in chunks.withIndex()) {
            val result = sendChunk(
                contactEmail = contactEmail,
                incidentId = incidentId,
                chunkFile = chunk,
                chunkIndex = index,
                totalChunks = chunks.size,
                locationLink = locationLink
            )
            results.add(result)
        }

        return results
    }

    private fun sendChunk(
        contactEmail: String,
        incidentId: String,
        chunkFile: File,
        chunkIndex: Int,
        totalChunks: Int,
        locationLink: String
    ): EmailResult {
        return try {
            if (!chunkFile.exists()) {
                return EmailResult(false, "File does not exist: ${chunkFile.name}")
            }

            val fileBase64 = Base64.getEncoder().encodeToString(chunkFile.readBytes())
            val subject = "🚨 Emergency SOS Alert - Incident $incidentId (Part ${chunkIndex + 1}/$totalChunks)"
            val htmlContent = buildEmailBody(incidentId, chunkIndex, totalChunks, locationLink)

            val requestBody = buildSendgridRequest(
                toEmail = contactEmail,
                subject = subject,
                htmlContent = htmlContent,
                attachmentName = chunkFile.name,
                attachmentContent = fileBase64,
                attachmentType = "video/mp4"
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.sendgrid.com/v3/mail/send")
                .addHeader("Authorization", "Bearer $sendgridApiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d(TAG, "Email sent successfully to $contactEmail: ${chunkFile.name}")
                EmailResult(
                    success = true,
                    message = "Email sent",
                    messageId = response.header("x-message-id")
                )
            } else {
                Log.e(TAG, "Failed to send email: ${response.code} ${response.message}")
                EmailResult(
                    success = false,
                    message = "SendGrid error: ${response.code} ${response.message}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email: ${e.message}")
            EmailResult(success = false, message = "Error: ${e.message}")
        }
    }

    private fun buildSendgridRequest(
        toEmail: String,
        subject: String,
        htmlContent: String,
        attachmentName: String,
        attachmentContent: String,
        attachmentType: String
    ): String {
        return """
        {
            "personalizations": [{
                "to": [{"email": "$toEmail"}],
                "subject": "$subject"
            }],
            "from": {
                "email": "alerts@sos.app",
                "name": "SOS Emergency Alert"
            },
            "content": [{
                "type": "text/html",
                "value": "$htmlContent"
            }],
            "attachments": [{
                "content": "$attachmentContent",
                "type": "$attachmentType",
                "filename": "$attachmentName",
                "disposition": "attachment"
            }]
        }
        """.trimIndent()
    }

    private fun buildEmailBody(
        incidentId: String,
        chunkIndex: Int,
        totalChunks: Int,
        locationLink: String
    ): String {
        return """
        <html>
            <body style="font-family: Arial, sans-serif; color: #333;">
                <h2 style="color: #d32f2f;">🚨 Emergency SOS Alert</h2>
                <p><strong>Incident ID:</strong> $incidentId</p>
                <p><strong>Video Part:</strong> ${chunkIndex + 1} of $totalChunks</p>
                <p><strong>Timestamp:</strong> ${System.currentTimeMillis()}</p>

                <h3>Actions:</h3>
                <ul>
                    <li><a href="$locationLink">View Live Location &amp; Incident Status</a></li>
                    <li>Download this email attachment for video evidence</li>
                    <li><a href="tel:911">Call 911 immediately</a></li>
                </ul>

                <p style="color: #666; font-size: 12px;">
                    This is an automated emergency alert.
                    If you received this in error, please disregard.
                </p>
            </body>
        </html>
        """.trimIndent().replace("\n", "").replace("\"", "\\\"")
    }
}
