package com.sos.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sos.app.service.SOSForegroundService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val titleView = findViewById<TextView>(R.id.title)
        val descView = findViewById<TextView>(R.id.description)
        val setupButton = findViewById<Button>(R.id.setup_button)
        val testButton = findViewById<Button>(R.id.test_button)

        titleView.text = "Emergency SOS"
        descView.text = "Press 4x power button or use gesture to activate"

        setupButton.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        testButton.setOnClickListener {
            // Launch SOS activity for testing
            startActivity(Intent(this, SOSActivity::class.java))
        }

        // Start foreground service
        startSOSService()
    }

    private fun startSOSService() {
        val intent = Intent(this, SOSForegroundService::class.java)
        startService(intent)
    }
}
