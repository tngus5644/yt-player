package com.hashmeter.ytplayer.monica.worker.condition

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.hashmeter.ytplayer.BuildConfig

/**
 * Condition: Screen must be OFF (device in sleep mode)
 * Monica should only run when the screen is turned off
 *
 * DEBUG MODE: This condition is IGNORED in debug builds to allow testing
 */
object ScreenOffCondition : Condition {

    private const val TAG = "ScreenOffCondition"

    override fun isSatisfied(context: Context): Boolean {
        // In debug mode, always return true (ignore screen state)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "🔧 DEBUG MODE: Screen check bypassed")
            return true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOff = !powerManager.isInteractive

        Log.d(TAG, "Screen state: ${if (isScreenOff) "OFF ✅" else "ON ❌"}")

        return isScreenOff
    }

    override fun getDescription(): String {
        return if (BuildConfig.DEBUG) {
            "Screen check (bypassed in DEBUG)"
        } else {
            "Screen must be OFF"
        }
    }

    override fun getStatus(context: Context): String {
        if (BuildConfig.DEBUG) {
            return "Screen: BYPASSED (DEBUG)"
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (powerManager.isInteractive) "Screen: ON" else "Screen: OFF"
    }
}
