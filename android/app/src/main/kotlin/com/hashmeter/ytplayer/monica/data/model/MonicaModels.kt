package com.hashmeter.ytplayer.monica.data.model

import com.google.gson.annotations.SerializedName

/**
 * Monica URL Configuration
 */
data class MonicaConfig(
    val url: String,
    val intervalMinutes: Long = 15, // Default: 15 minutes (WorkManager minimum)
    val enabled: Boolean = true
)

/**
 * Monica API Response
 */
data class MonicaApiResponse(
    val success: Boolean,
    val message: String? = null,
    val data: MonicaData? = null
)

data class MonicaData(
    val url: String,
    @SerializedName("interval_minutes")
    val intervalMinutes: Long? = null
)

/**
 * Monica execution log
 */
data class MonicaLog(
    val timestamp: Long,
    val url: String,
    val success: Boolean,
    val errorMessage: String? = null
)
