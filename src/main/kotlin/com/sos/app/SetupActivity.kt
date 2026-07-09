package com.sos.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val contactNameInput = findViewById<EditText>(R.id.contact_name)
        val contactEmailInput = findViewById<EditText>(R.id.contact_email)
        val saveButton = findViewById<Button>(R.id.save_button)
        val contactsView = findViewById<TextView>(R.id.contacts_list)

        saveButton.setOnClickListener {
            val name = contactNameInput.text.toString()
            val email = contactEmailInput.text.toString()
            if (name.isNotEmpty() && email.isNotEmpty()) {
                // TODO: Save contact to SharedPreferences or Room database
                contactNameInput.text.clear()
                contactEmailInput.text.clear()
                // TODO: Update contacts list view
            }
        }
    }
}
