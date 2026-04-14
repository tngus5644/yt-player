package com.hashmeter.ytplayer.monica.worker

import android.content.Context
import android.util.Log
import com.hashmeter.ytplayer.BuildConfig
import com.hashmeter.ytplayer.monica.worker.condition.Condition
import com.hashmeter.ytplayer.monica.worker.condition.ScreenOffCondition
import com.hashmeter.ytplayer.monica.worker.condition.UsbDisconnectedCondition

/**
 * Worker execution condition checker
 * Determines whether Monica worker should execute based on device state
 */
object WorkerCondition {

    private const val TAG = "WorkerCondition"

    // List of all conditions that must be satisfied
    private val conditions: List<Condition> = listOf(
        ScreenOffCondition,
        UsbDisconnectedCondition
    )

    /**
     * Check if all conditions are met for Monica execution
     * In DEBUG mode, all conditions are bypassed
     * @return Pair<Boolean, String> - (shouldExecute, reason)
     */
    fun checkConditions(context: Context): Pair<Boolean, String> {
        // DEBUG 모드에서는 모든 조건 무시
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "🔧 DEBUG MODE: All conditions bypassed")
            return true to "DEBUG mode - conditions skipped"
        }

        Log.d(TAG, "Checking ${conditions.size} conditions...")

        for (condition in conditions) {
            if (!condition.isSatisfied(context)) {
                val reason = condition.getDescription()
                Log.d(TAG, "❌ Condition failed: $reason")
                return false to reason
            }
        }

        Log.d(TAG, "✅ All conditions satisfied")
        return true to "All conditions met"
    }

    /**
     * Get detailed condition status for logging
     */
    fun getDetailedStatus(context: Context): String {
        return conditions.joinToString(", ") { it.getStatus(context) }
    }

    /**
     * Get list of all condition descriptions
     */
    fun getAllConditions(): List<String> {
        return conditions.map { it.getDescription() }
    }
}
