package com.sos.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sos.app.data.ContactStorage

class SetupActivity : AppCompatActivity() {
    private lateinit var contactStorage: ContactStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        contactStorage = ContactStorage(this)

        val contactNameInput = findViewById<EditText>(R.id.contact_name)
        val contactEmailInput = findViewById<EditText>(R.id.contact_email)
        val saveButton = findViewById<Button>(R.id.save_button)
        val contactsContainer = findViewById<LinearLayout>(R.id.contacts_list)

        saveButton.setOnClickListener {
            val name = contactNameInput.text.toString().trim()
            val email = contactEmailInput.text.toString().trim()

            if (name.isNotEmpty() && email.isNotEmpty()) {
                contactStorage.addContact(name, email)
                contactNameInput.text.clear()
                contactEmailInput.text.clear()
                updateContactsList(contactsContainer)
            }
        }

        updateContactsList(contactsContainer)
    }

    private fun updateContactsList(container: LinearLayout) {
        container.removeAllViews()
        val contacts = contactStorage.getContacts()

        if (contacts.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No contacts added yet"
                setTextColor(android.graphics.Color.GRAY)
            }
            container.addView(emptyView)
        } else {
            for (contact in contacts) {
                val contactView = TextView(this).apply {
                    text = "${contact.name} (${contact.email})\n${if (contact.isVerified) "✓ Verified" else "⏳ Pending verification"}"
                    setTextColor(android.graphics.Color.BLACK)
                    setPadding(8, 8, 8, 8)
                }
                container.addView(contactView)
            }
        }
    }
}
