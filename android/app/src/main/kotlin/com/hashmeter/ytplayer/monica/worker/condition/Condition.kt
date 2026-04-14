package com.hashmeter.ytplayer.monica.worker.condition

import android.content.Context

/**
 * Interface for worker execution conditions
 */
interface Condition {
    /**
     * Check if this condition is satisfied
     * @return true if condition is met, false otherwise
     */
    fun isSatisfied(context: Context): Boolean

    /**
     * Get human-readable description of this condition
     */
    fun getDescription(): String

    /**
     * Get current status of this condition for logging
     */
    fun getStatus(context: Context): String
}
