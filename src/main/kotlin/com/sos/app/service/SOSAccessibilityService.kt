package com.sos.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.sos.app.SOSActivity
import com.sos.app.trigger.TriggerManager

class SOSAccessibilityService : AccessibilityService() {
    private lateinit var triggerManager: TriggerManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        triggerManager = TriggerManager(pressCount = 4, timeWindowMs = 2000) {
            launchSOSActivity()
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        // Detect power button (KEYCODE_POWER = 26)
        if (event.keyCode == KeyEvent.KEYCODE_POWER && event.action == KeyEvent.ACTION_DOWN) {
            triggerManager.onButtonPress()
            return true
        }

        // Also detect volume buttons as alternative triggers
        if ((event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) &&
            event.action == KeyEvent.ACTION_DOWN
        ) {
            // For now, don't consume; just log. Could add as secondary trigger.
            return false
        }

        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for button detection, but required by interface
    }

    override fun onInterrupt() {
        // Required by interface
    }

    private fun launchSOSActivity() {
        val intent = Intent(this, SOSActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }
}
