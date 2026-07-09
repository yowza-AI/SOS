package com.sos.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.UUID

/**
 * Local storage for emergency contacts using SharedPreferences.
 */
class ContactStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sos_contacts", Context.MODE_PRIVATE)
    private val TAG = "ContactStorage"

    fun addContact(name: String, email: String, phone: String = ""): Contact {
        val contact = Contact(
            id = UUID.randomUUID().toString(),
            name = name,
            email = email,
            phone = phone,
            isVerified = false
        )
        prefs.edit().putString("contact_${contact.id}", contact.toJSON()).apply()
        Log.d(TAG, "Contact added: ${contact.id}")
        return contact
    }

    fun getContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        for ((key, value) in prefs.all) {
            if (key.startsWith("contact_") && value is String) {
                try {
                    contacts.add(parseContact(value))
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing contact: ${e.message}")
                }
            }
        }
        return contacts
    }

    fun getVerifiedContacts(): List<Contact> {
        return getContacts().filter { it.isVerified }
    }

    fun getContact(id: String): Contact? {
        val json = prefs.getString("contact_$id", null) ?: return null
        return try {
            parseContact(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing contact: ${e.message}")
            null
        }
    }

    fun markVerified(id: String) {
        val contact = getContact(id) ?: return
        val verified = contact.copy(isVerified = true, verifiedAt = System.currentTimeMillis())
        prefs.edit().putString("contact_$id", verified.toJSON()).apply()
        Log.d(TAG, "Contact marked verified: $id")
    }

    fun deleteContact(id: String) {
        prefs.edit().remove("contact_$id").apply()
        Log.d(TAG, "Contact deleted: $id")
    }

    fun deleteAll() {
        prefs.edit().clear().apply()
        Log.d(TAG, "All contacts deleted")
    }

    private fun parseContact(json: String): Contact {
        // Simple JSON parsing (for production, use proper JSON library)
        val id = extractField(json, "id")
        val name = extractField(json, "name")
        val email = extractField(json, "email")
        val phone = extractField(json, "phone")
        val isVerified = extractField(json, "isVerified").toBoolean()
        val verifiedAt = extractField(json, "verifiedAt").toLong()
        val createdAt = extractField(json, "createdAt").toLong()

        return Contact(
            id = id,
            name = name,
            email = email,
            phone = phone,
            isVerified = isVerified,
            verifiedAt = verifiedAt,
            createdAt = createdAt
        )
    }

    private fun extractField(json: String, fieldName: String): String {
        val pattern = "\"$fieldName\"\\s*:\\s*\"?([^,}]*)\"?"
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)?.trim('"') ?: ""
    }
}
