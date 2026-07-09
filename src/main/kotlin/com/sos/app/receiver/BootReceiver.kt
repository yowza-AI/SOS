package com.sos.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sos.app.service.SOSForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            // Re-arm the SOS service after device reboot
            val serviceIntent = Intent(context, SOSForegroundService::class.java)
            context.startService(serviceIntent)
        }
    }
}
