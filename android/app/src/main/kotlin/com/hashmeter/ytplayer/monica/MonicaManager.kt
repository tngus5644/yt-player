package com.hashmeter.ytplayer.monica

import android.content.Context
import android.util.Log
import com.hashmeter.ytplayer.monica.data.model.MonicaConfig
import com.hashmeter.ytplayer.monica.data.repository.MonicaRepository
import com.hashmeter.ytplayer.monica.worker.MonicaWorker

/**
 * Monica Manager - Facade for managing Monica background URL visits
 *
 * Usage:
 * ```
 * // Initialize Monica with URL and interval
 * MonicaManager.initialize(context, "https://example.com", intervalMinutes = 60)
 *
 * // Start Monica
 * MonicaManager.start(context)
 *
 * // Stop Monica
 * MonicaManager.stop(context)
 *
 * // Execute immediately (one-time)
 * MonicaManager.executeNow(context)
 * ```
 */
object MonicaManager {

    private const val TAG = "MonicaManager"

    /**
     * Initialize Monica with configuration
     */
    fun initialize(
        context: Context,
        url: String,
        intervalMinutes: Long = 15,
        enabled: Boolean = true
    ) {
        val appContext = context.applicationContext
        val repository = MonicaRepository.getInstance(appContext)
        val config = MonicaConfig(
            url = url,
            intervalMinutes = intervalMinutes,
            enabled = enabled
        )
        repository.saveConfig(config)

        Log.d(TAG, "Monica initialized: url=$url, interval=${intervalMinutes}min, enabled=$enabled")

        if (enabled) {
            start(appContext)
        }
    }

    /**
     * Start Monica periodic execution using WorkManager with WakeLock
     */
    fun start(context: Context) {
        val appContext = context.applicationContext
        val repository = MonicaRepository.getInstance(appContext)
        val config = repository.getConfig()

        if (config.url.isEmpty()) {
            Log.w(TAG, "Cannot start Monica: no URL configured")
            return
        }

        repository.setEnabled(true)

        // Schedule WorkManager with WakeLock for battery-efficient periodic execution
        MonicaWorker.schedule(appContext, config.intervalMinutes)

        Log.d(TAG, "Monica started with ${config.intervalMinutes}min interval")
    }

    /**
     * Stop Monica periodic execution
     */
    fun stop(context: Context) {
        val appContext = context.applicationContext
        val repository = MonicaRepository.getInstance(appContext)
        repository.setEnabled(false)

        // Stop WorkManager
        MonicaWorker.cancel(appContext)

        Log.d(TAG, "Monica stopped")
    }

    /**
     * Execute Monica immediately (one-time)
     */
    fun executeNow(context: Context) {
        val appContext = context.applicationContext
        MonicaWorker.executeNow(appContext)
        Log.d(TAG, "Monica immediate execution requested")
    }

    /**
     * Get current configuration
     */
    fun getConfig(context: Context): MonicaConfig {
        return MonicaRepository.getInstance(context.applicationContext).getConfig()
    }

    /**
     * Check if Monica is enabled
     */
    fun isEnabled(context: Context): Boolean {
        return MonicaRepository.getInstance(context.applicationContext).getConfig().enabled
    }

    /**
     * Get last execution timestamp
     */
    fun getLastExecutionTime(context: Context): Long {
        return MonicaRepository.getInstance(context.applicationContext).getLastExecutionTime()
    }
}
