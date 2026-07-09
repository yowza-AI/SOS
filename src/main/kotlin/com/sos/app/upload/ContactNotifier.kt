package com.sos.app.upload

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Sends notifications to emergency contacts via multiple channels.
 * SMS (Twilio), Email (SendGrid), Push (FCM).
 */
class ContactNotifier(
    private val context: Context,
    private val twilioAccountSid: String,
    private val twilioAuthToken: String,
    private val sendgridApiKey: String
) {
    private val client = OkHttpClient()
    private val TAG = "ContactNotifier"

    data class NotificationResult(
        val channel: String,
        val success: Boolean,
        val message: String
    )

    fun notifyContact(
        name: String,
        email: String,
        phone: String,
        incidentId: String,
        locationLink: String
    ): List<NotificationResult> {
        val results = mutableListOf<NotificationResult>()

        // Send SMS
        if (phone.isNotEmpty()) {
            results.add(sendSMS(phone, name, incidentId, locationLink))
        }

        // Send Email
        if (email.isNotEmpty()) {
            results.add(sendEmail(email, name, incidentId, locationLink))
        }

        // Send Push (if contact has FCM token registered)
        results.add(sendPush(email, name, incidentId, locationLink))

        return results
    }

    private fun sendSMS(
        phoneNumber: String,
        contactName: String,
        incidentId: String,
        locationLink: String
    ): NotificationResult {
        return try {
            val messageBody = "🚨 EMERGENCY ALERT from SOS App\n\n" +
                    "Your contact $contactName has activated an emergency alert.\n\n" +
                    "View live location: $locationLink\n\n" +
                    "Incident ID: $incidentId\n\n" +
                    "Call 911 immediately if necessary."

            val requestBody = "From=%2B1234567890&To=%2B$phoneNumber&Body=${
                java.net.URLEncoder.encode(messageBody, "UTF-8")
            }".toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val request = Request.Builder()
                .url("https://api.twilio.com/2010-04-01/Accounts/$twilioAccountSid/Messages.json")
                .addHeader("Authorization", "Basic ${java.util.Base64.getEncoder().encodeToString("$twilioAccountSid:$twilioAuthToken".toByteArray())}")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d(TAG, "SMS sent to $phoneNumber")
                NotificationResult("SMS", true, "Message sent")
            } else {
                Log.e(TAG, "Failed to send SMS: ${response.code}")
                NotificationResult("SMS", false, "Failed: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS: ${e.message}")
            NotificationResult("SMS", false, "Error: ${e.message}")
        }
    }

    private fun sendEmail(
        email: String,
        contactName: String,
        incidentId: String,
        locationLink: String
    ): NotificationResult {
        return try {
            val htmlContent = """
            <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <h2 style="color: #d32f2f;">🚨 Emergency SOS Alert</h2>
                    <p>Your contact <strong>$contactName</strong> has activated an emergency alert.</p>
                    <p><strong>Incident ID:</strong> $incidentId</p>
                    <p><strong>Alert Time:</strong> ${System.currentTimeMillis()}</p>

                    <h3>Actions:</h3>
                    <p><a href="$locationLink" style="background-color: #d32f2f; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">View Live Location &amp; Download Video</a></p>

                    <p style="color: #666; font-size: 12px;">
                        Contact the person directly or contact emergency services if you believe they are in danger.
                    </p>
                </body>
            </html>
            """.trimIndent().replace("\n", "").replace("\"", "\\\"")

            val requestBody = """
            {
                "personalizations": [{
                    "to": [{"email": "$email"}],
                    "subject": "🚨 Emergency SOS Alert - Incident $incidentId"
                }],
                "from": {
                    "email": "alerts@sos.app",
                    "name": "SOS Emergency Alert"
                },
                "content": [{
                    "type": "text/html",
                    "value": "$htmlContent"
                }]
            }
            """.trimIndent().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.sendgrid.com/v3/mail/send")
                .addHeader("Authorization", "Bearer $sendgridApiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d(TAG, "Email sent to $email")
                NotificationResult("Email", true, "Message sent")
            } else {
                Log.e(TAG, "Failed to send email: ${response.code}")
                NotificationResult("Email", false, "Failed: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email: ${e.message}")
            NotificationResult("Email", false, "Error: ${e.message}")
        }
    }

    private fun sendPush(
        email: String,
        contactName: String,
        incidentId: String,
        locationLink: String
    ): NotificationResult {
        return try {
            // TODO: Query FCM token for contact (requires contact to have app installed)
            // For now, return placeholder
            NotificationResult("Push", false, "Contact app not installed or token not available")
        } catch (e: Exception) {
            NotificationResult("Push", false, "Error: ${e.message}")
        }
    }
}
