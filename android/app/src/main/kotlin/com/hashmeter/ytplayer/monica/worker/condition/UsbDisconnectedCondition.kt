package com.hashmeter.ytplayer.monica.worker.condition

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.hashmeter.ytplayer.BuildConfig

/**
 * Condition: USB must be DISCONNECTED
 * Monica should not run when device is connected via USB
 *
 * DEBUG MODE: This condition is IGNORED in debug builds to allow testing
 */
object UsbDisconnectedCondition : Condition {

    private const val TAG = "UsbDisconnectedCondition"

    override fun isSatisfied(context: Context): Boolean {
        // In debug mode, always return true (ignore USB connection)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "🔧 DEBUG MODE: USB check bypassed")
            return true
        }

        val isUsbConnected = isUsbConnected(context)

        Log.d(TAG, "USB state: ${if (isUsbConnected) "CONNECTED ❌" else "DISCONNECTED ✅"}")

        return !isUsbConnected
    }

    override fun getDescription(): String {
        return if (BuildConfig.DEBUG) {
            "USB check (bypassed in DEBUG)"
        } else {
            "USB must be disconnected"
        }
    }

    override fun getStatus(context: Context): String {
        if (BuildConfig.DEBUG) {
            return "USB: BYPASSED (DEBUG)"
        }

        val batteryStatus: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1

        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB: CONNECTED"
            BatteryManager.BATTERY_PLUGGED_AC -> "USB: DISCONNECTED (AC)"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "USB: DISCONNECTED (Wireless)"
            else -> "USB: DISCONNECTED (Battery)"
        }
    }

    /**
     * Check if device is connected via USB
     */
    private fun isUsbConnected(context: Context): Boolean {
        val batteryStatus: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1

        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> {
                Log.d(TAG, "📱 Charging type: USB")
                true
            }
            BatteryManager.BATTERY_PLUGGED_AC -> {
                Log.d(TAG, "🔌 Charging type: AC Adapter")
                false
            }
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> {
                Log.d(TAG, "📶 Charging type: Wireless")
                false
            }
            else -> {
                Log.d(TAG, "🔋 Not charging (Battery only)")
                false
            }
        }
    }
}
