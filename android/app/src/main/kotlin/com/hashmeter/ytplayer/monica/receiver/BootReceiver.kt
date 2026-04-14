package com.hashmeter.ytplayer.monica.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hashmeter.ytplayer.monica.MonicaManager

/**
 * Boot receiver to restart Monica WorkManager after device reboot
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "📱 Device boot completed - Restarting Monica WorkManager")

            try {
                val config = MonicaManager.getConfig(context)

                if (config.enabled && config.url.isNotEmpty()) {
                    MonicaManager.start(context)
                    Log.d(TAG, "✅ Monica WorkManager restarted after boot")
                    Log.d(TAG, "   URL: ${config.url}")
                    Log.d(TAG, "   Interval: ${config.intervalMinutes} minutes")
                } else {
                    Log.d(TAG, "ℹ️ Monica is disabled or not configured - skipping restart")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart Monica WorkManager after boot", e)
            }
        }
    }
}
