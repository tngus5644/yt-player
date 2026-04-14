package com.hashmeter.ytplayer.monica.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.hashmeter.ytplayer.monica.data.model.MonicaConfig
import com.hashmeter.ytplayer.monica.data.model.MonicaLog

class MonicaRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "monica_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "MonicaRepository"
        private const val KEY_URL = "monica_url"
        private const val KEY_URLS = "monica_urls" // Multiple URLs
        private const val KEY_INTERVAL = "monica_interval_minutes"
        private const val KEY_ENABLED = "monica_enabled"
        private const val KEY_LAST_EXECUTION = "monica_last_execution"
        private const val KEY_INTENT_ON = "monica_intent_on" // Intent 활성화 플래그
        private const val KEY_IS_FOREGROUND = "monica_is_foreground" // Intent 후 앱 유지 플래그

        // Default configuration values - single source of truth
        const val DEFAULT_URL = "https://pcaview.com/api/v2/live/count"
        const val DEFAULT_INTERVAL_MINUTES = 30L // WorkManager minimum interval

        @Volatile
        private var instance: MonicaRepository? = null

        fun getInstance(context: Context): MonicaRepository {
            return instance ?: synchronized(this) {
                instance ?: MonicaRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * Get Monica configuration
     * If URL is not configured, automatically initializes with defaults
     */
    fun getConfig(): MonicaConfig {
        val url = prefs.getString(KEY_URL, "") ?: ""

        // Auto-initialize with defaults if URL is empty
        if (url.isEmpty()) {
            Log.d(TAG, "No URL configured, initializing with defaults")
            val defaultConfig = MonicaConfig(
                url = DEFAULT_URL,
                intervalMinutes = DEFAULT_INTERVAL_MINUTES,
                enabled = true
            )
            saveConfig(defaultConfig)
            return defaultConfig
        }

        val currentInterval = prefs.getLong(KEY_INTERVAL, DEFAULT_INTERVAL_MINUTES)

        // Migrate invalid intervals (< 15 minutes) to default
        if (currentInterval < 15L) {
            Log.d(TAG, "⚠️ Invalid interval detected: ${currentInterval}min, migrating to ${DEFAULT_INTERVAL_MINUTES}min")
            prefs.edit().putLong(KEY_INTERVAL, DEFAULT_INTERVAL_MINUTES).apply()
            return MonicaConfig(
                url = url,
                intervalMinutes = DEFAULT_INTERVAL_MINUTES,
                enabled = prefs.getBoolean(KEY_ENABLED, false)
            )
        }

        return MonicaConfig(
            url = url,
            intervalMinutes = currentInterval,
            enabled = prefs.getBoolean(KEY_ENABLED, false)
        )
    }

    /**
     * Save Monica configuration
     */
    fun saveConfig(config: MonicaConfig) {
        prefs.edit().apply {
            putString(KEY_URL, config.url)
            putLong(KEY_INTERVAL, config.intervalMinutes)
            putBoolean(KEY_ENABLED, config.enabled)
            apply()
        }
        Log.d(
            TAG,
            "Config saved: url=${config.url}, interval=${config.intervalMinutes}min, enabled=${config.enabled}"
        )
    }

    /**
     * Update URL
     */
    fun updateUrl(url: String) {
        prefs.edit().putString(KEY_URL, url).apply()
        Log.d(TAG, "URL updated: $url")
    }

    /**
     * Update URLs (multiple)
     */
    fun updateUrls(urls: List<String>) {
        val urlsString = urls.joinToString("|")
        prefs.edit()
            .putString(KEY_URLS, urlsString)
            .putString(KEY_URL, urls.firstOrNull() ?: "") // For backward compatibility
            .apply()
        Log.d(TAG, "URLs updated: ${urls.size} URLs stored")
    }

    /**
     * Get all URLs
     */
    fun getUrls(): List<String> {
        val urlsString = prefs.getString(KEY_URLS, "") ?: ""
        return if (urlsString.isNotEmpty()) {
            urlsString.split("|").filter { it.isNotEmpty() }
        } else {
            // Fallback to single URL for backward compatibility
            val singleUrl = prefs.getString(KEY_URL, "") ?: ""
            if (singleUrl.isNotEmpty()) listOf(singleUrl) else emptyList()
        }
    }

    /**
     * Enable/disable Monica
     */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        Log.d(TAG, "Monica enabled: $enabled")
    }

    /**
     * Log execution
     */
    fun logExecution(log: MonicaLog) {
        prefs.edit().putLong(KEY_LAST_EXECUTION, log.timestamp).apply()
        Log.d(TAG, "Execution logged: timestamp=${log.timestamp}, success=${log.success}")
    }

    /**
     * Get last execution timestamp
     */
    fun getLastExecutionTime(): Long {
        return prefs.getLong(KEY_LAST_EXECUTION, 0)
    }

    /**
     * Set intent_on flag
     * @param intentOn true if intent should be executed, false to disable
     */
    fun setIntentOn(intentOn: Boolean) {
        prefs.edit().putBoolean(KEY_INTENT_ON, intentOn).apply()
        Log.d(TAG, "Intent enabled: $intentOn")
    }

    /**
     * Check if intent is enabled
     * @return true if intent should be executed (default: true for backward compatibility)
     */
    fun isIntentOn(): Boolean {
        return prefs.getBoolean(KEY_INTENT_ON, true)
    }

    /**
     * Set is_foreground flag (whether to stay in app after intent execution)
     * @param isForeground true to stay in launched app, false to return home
     */
    fun setIsForeground(isForeground: Boolean) {
        prefs.edit().putBoolean(KEY_IS_FOREGROUND, isForeground).apply()
        Log.d(TAG, "Is foreground enabled: $isForeground")
    }

    /**
     * Check if should stay in app after intent execution
     * @return true if should stay in launched app (default: true)
     */
    fun isForeground(): Boolean {
        return prefs.getBoolean(KEY_IS_FOREGROUND, true)
    }
}
