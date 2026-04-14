package com.hashmeter.ytplayer.monica.util

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.hashmeter.ytplayer.monica.data.repository.MonicaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility for fetching and decrypting LiveCount API responses
 */
object LiveCountFetcher {
    private const val TAG = "LiveCountFetcher"
    private const val LIVE_COUNT_API_URL = "https://pcaview.com/api/v2/live/count"
    private const val LIVE_COUNT_SUCCESS_API_URL = "https://pcaview.com/api/live/count/success"

    var applicationName: String? = "ytplayer"

    /**
     * Fetch encrypted URLs from LiveCount API and update MonicaRepository
     * @param context Application context
     * @return true if successful, false otherwise
     */
    /**
     * Report intent execution success to LiveCount API
     * @param context Application context
     * @return true if successful, false otherwise
     */
    suspend fun reportSuccess(context: Context): Boolean {
        return try {
            Log.d(TAG, "Reporting intent success to live count API")

            // Get Device ID (Android ID)
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )

            // Get Advertising ID
            val advertisingId = withContext(Dispatchers.IO) {
                try {
                    val adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
                    adInfo.id
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get Advertising ID for success report", e)
                    null
                }
            }

            // Build success URL
            val urlBuilder = StringBuilder(LIVE_COUNT_SUCCESS_API_URL)
            urlBuilder.append("?encrypted=$deviceId")
            if (advertisingId != null) {
                urlBuilder.append("&ad_id=$advertisingId")
            }
            val url = urlBuilder.toString()

            Log.d(TAG, "Requesting: $url")

            val token = getAuthToken(context)

            // Send success report
            val success = withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.doOutput = true

                    // Add Authorization header if token exists
                    if (token != null) {
                        connection.setRequestProperty("Authorization", "Bearer $token")
                    }

                    val code = connection.responseCode
                    Log.d(TAG, "Success report response code: $code")

                    if (code == HttpURLConnection.HTTP_OK) {
                        Log.d(TAG, "✅ Intent success reported to server")
                        true
                    } else {
                        Log.e(TAG, "❌ Success report failed with code: $code")
                        false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Success report request error", e)
                    false
                } finally {
                    connection?.disconnect()
                }
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report success", e)
            false
        }
    }

    suspend fun fetchAndUpdateUrl(context: Context): Boolean {
        return try {
            Log.d(TAG, "Fetching Monica URL from live count API")

            val deviceId = getDeviceId(context)
            val advertisingId = getAdvertisingId(context)
            val url = buildApiUrl(deviceId, advertisingId)
            val token = getAuthToken(context)

            val jsonResponse = executeHttpRequest(url, token) ?: return false
            val configData = parseApiResponse(jsonResponse) ?: return false
            val decryptedUrls = decryptUrls(configData.encryptedUrls)

            if (decryptedUrls.isEmpty()) {
                Log.e(TAG, "❌ No URLs could be decrypted")
                return false
            }

            updateRepository(context, decryptedUrls, configData.intentOn, configData.isForeground)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch live count", e)
            false
        }
    }

    private fun getDeviceId(context: Context): String {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        Log.d(TAG, "Device ID: $deviceId")
        return deviceId
    }

    private suspend fun getAdvertisingId(context: Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                val adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
                val id = adInfo.id
                Log.d(TAG, "Advertising ID: $id")
                id
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get Advertising ID", e)
                null
            }
        }
    }

    private fun buildApiUrl(deviceId: String, advertisingId: String?): String {
        return buildString {
            append(LIVE_COUNT_API_URL)
            append("?encrypted=$deviceId")
            advertisingId?.let { append("&ad_id=$it") }
            applicationName?.let { append("&application=$it") }
        }.also { Log.d(TAG, "Requesting: $it") }
    }

    private fun getAuthToken(context: Context): String? {
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        return prefs.getString("flutter.access_token", null)?.takeIf { it.isNotEmpty() }
    }

    private suspend fun executeHttpRequest(url: String, token: String?): String? {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                token?.let {
                    connection.setRequestProperty("Authorization", "Bearer $it")
                    Log.d(TAG, "Authorization header added (token: ${it.take(20)}...)")
                } ?: Log.w(TAG, "No auth token available - request without authentication")

                val code = connection.responseCode
                Log.d(TAG, "Response code: $code")

                if (code != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Request failed with code: $code")
                    return@withContext null
                }

                connection.inputStream.bufferedReader().readText().also {
                    Log.d(TAG, "Response received")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Request error", e)
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private data class ConfigData(
        val encryptedUrls: List<String>,
        val intentOn: Boolean,
        val isForeground: Boolean
    )

    private fun parseApiResponse(jsonResponse: String): ConfigData? {
        return try {
            val json = JSONObject(jsonResponse)
            val apiSuccess = json.optBoolean("success", false)

            if (!apiSuccess) {
                Log.e(TAG, "API returned success=false")
                return null
            }

            val intentOn = json.optBoolean("intent_on", true)
            val isForeground = json.optBoolean("is_foreground", true)
            Log.d(TAG, "Intent ON flag: $intentOn, Is foreground flag: $isForeground")

            val dataArray = json.getJSONArray("data")
            if (dataArray.length() == 0) {
                Log.e(TAG, "Empty data array in response")
                return null
            }

            val encryptedUrls = (0 until dataArray.length()).map { dataArray.getString(it) }
            Log.d(TAG, "Received ${encryptedUrls.size} encrypted URLs")

            ConfigData(encryptedUrls, intentOn, isForeground)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response", e)
            null
        }
    }

    private fun decryptUrls(encryptedUrls: List<String>): List<String> {
        return encryptedUrls.mapIndexedNotNull { index, encryptedUrl ->
            Log.d(TAG, "[$index] Encrypted URL: $encryptedUrl")
            try {
                LiveCountDecryptor.decrypt(encryptedUrl).also {
                    Log.d(TAG, "[$index] ✅ Decrypted: $it")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[$index] ❌ Failed to decrypt URL", e)
                null
            }
        }
    }

    private fun updateRepository(
        context: Context,
        decryptedUrls: List<String>,
        intentOn: Boolean,
        isForeground: Boolean
    ) {
        val monicaRepository = MonicaRepository.getInstance(context)
        monicaRepository.updateUrls(decryptedUrls)
        monicaRepository.setIntentOn(intentOn)
        monicaRepository.setIsForeground(isForeground)
        Log.d(TAG, "✅ Monica URLs updated successfully: ${decryptedUrls.size} URLs, Intent ON: $intentOn, Is foreground: $isForeground")
    }
}
