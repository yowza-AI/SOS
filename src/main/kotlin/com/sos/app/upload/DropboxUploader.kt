package com.sos.app.upload

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * Uploads video chunks directly to contact's Dropbox account.
 * Requires OAuth2 access token (obtained during contact verification).
 */
class DropboxUploader(private val accessToken: String) {
    private val client = OkHttpClient()
    private val TAG = "DropboxUploader"

    data class UploadResult(
        val success: Boolean,
        val message: String,
        val dropboxPath: String? = null
    )

    fun uploadChunks(
        incidentId: String,
        chunks: List<File>
    ): List<UploadResult> {
        val results = mutableListOf<UploadResult>()
        val incidentFolder = "/SOS_Incidents/$incidentId"

        // Create folder
        createFolder(incidentFolder)

        // Upload each chunk
        for ((index, chunk) in chunks.withIndex()) {
            val dropboxPath = "$incidentFolder/${chunk.name}"
            val result = uploadFile(chunk, dropboxPath)
            results.add(result)
        }

        return results
    }

    private fun createFolder(path: String): Boolean {
        return try {
            val requestBody = """
            {
                "path": "$path",
                "autorename": false
            }
            """.trimIndent().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.dropboxapi.com/2/files/create_folder_v2")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful || response.code == 409 // 409 = folder already exists
        } catch (e: Exception) {
            Log.e(TAG, "Error creating folder: ${e.message}")
            false
        }
    }

    private fun uploadFile(file: File, dropboxPath: String): UploadResult {
        return try {
            if (!file.exists()) {
                return UploadResult(false, "File does not exist")
            }

            val fileBytes = file.readBytes()
            val requestBody = fileBytes.toRequestBody("application/octet-stream".toMediaType())

            val request = Request.Builder()
                .url("https://content.dropboxapi.com/2/files/upload")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Dropbox-API-Arg", """{"path":"$dropboxPath","mode":"add","autorename":true,"mute":false}""")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d(TAG, "Uploaded to Dropbox: $dropboxPath")
                UploadResult(
                    success = true,
                    message = "Uploaded to Dropbox",
                    dropboxPath = dropboxPath
                )
            } else {
                Log.e(TAG, "Failed to upload: ${response.code} ${response.message}")
                UploadResult(
                    success = false,
                    message = "Upload failed: ${response.code}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file: ${e.message}")
            UploadResult(success = false, message = "Error: ${e.message}")
        }
    }

    fun getShareLink(dropboxPath: String): String? {
        return try {
            val requestBody = """
            {
                "path": "$dropboxPath",
                "settings": {
                    "requested_visibility": "public"
                }
            }
            """.trimIndent().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.dropboxapi.com/2/sharing/create_shared_link_with_settings")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                // Parse response to extract URL (simple regex extraction)
                val body = response.body?.string() ?: return null
                val urlPattern = """"url":"([^"]+)""".toRegex()
                val match = urlPattern.find(body)
                match?.groupValues?.get(1)
            } else {
                Log.e(TAG, "Failed to get share link: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting share link: ${e.message}")
            null
        }
    }
}
