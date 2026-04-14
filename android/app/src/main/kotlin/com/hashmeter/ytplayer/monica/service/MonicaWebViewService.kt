package com.hashmeter.ytplayer.monica.service

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
import android.webkit.WebView
import com.hashmeter.ytplayer.monica.client.MonicaWebViewClient
import com.hashmeter.ytplayer.monica.data.model.MonicaLog
import com.hashmeter.ytplayer.monica.data.repository.MonicaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MonicaWebViewService(private val context: Context) {

    private val repository = MonicaRepository.getInstance(context)
    private var webView: WebView? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var isCleaningUp = false // Flag to prevent multiple cleanup calls
    private var cleanupJob: kotlinx.coroutines.Job? = null // Track cleanup coroutine
    private var timeoutJob: Job? = null // Track timeout coroutine

    companion object {
        private const val TAG = "MonicaWebViewService"
        private const val LOAD_TIMEOUT_MS = 10_000L // 10 seconds timeout
    }

    /**
     * Execute URL visit with invisible WebView
     * @param url The URL to visit (if null, uses configured URL)
     */
    fun executeUrlVisit(url: String? = null) {
        val config = repository.getConfig()

        if (!config.enabled) {
            Log.d(TAG, "Monica is disabled")
            return
        }

        val targetUrl = url ?: config.url
        if (targetUrl.isEmpty()) {
            Log.w(TAG, "No URL configured")
            return
        }

        Log.d(TAG, "Starting URL visit: $targetUrl")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                initializeWebView()
                loadUrl(targetUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing URL visit", e)
                logExecution(targetUrl, false, e.message)
            }
        }
    }

    private suspend fun initializeWebView() = withContext(Dispatchers.Main) {
        if (webView == null) {
            webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }

                webViewClient = MonicaWebViewClient(
                    context = context,
                    onPageFinished = { url ->
                        Log.d(TAG, "📄 Page finished: $url")

                        // Cancel timeout since page loaded successfully
                        timeoutJob?.cancel()
                        timeoutJob = null

                        logExecution(url, true, null)

                        // 기존 cleanup job 취소하고 새로운 3초 타이머 시작
                        // (여러 페이지 로드 시 마지막 페이지 로드 후에만 cleanup 실행)
                        cleanupJob?.cancel()
                        cleanupJob = CoroutineScope(Dispatchers.Main).launch {
                            delay(3000)
                            cleanup()
                        }
                    },
                    onError = { url, errorMessage ->
                        Log.e(TAG, "❌ Page error: $url - $errorMessage")

                        // Cancel timeout on error
                        timeoutJob?.cancel()
                        timeoutJob = null

                        logExecution(url, false, errorMessage)
                        cleanupJob?.cancel()
                        cleanup()
                    }
                )
            }

            // Add WebView to window as 0x0 pixel overlay (completely invisible)
            val params = WindowManager.LayoutParams(
                0, 0, // 0x0 pixel size - completely invisible
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.x = 0
            params.y = 0

            try {
                windowManager.addView(webView, params)
                Log.d(TAG, "WebView added to overlay (0x0 pixel - invisible)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add WebView overlay", e)
            }
        }
    }

    private fun loadUrl(url: String) {
        webView?.loadUrl(url)
        Log.d(TAG, "Loading URL: $url")

        // Start timeout timer
        timeoutJob?.cancel()
        timeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(LOAD_TIMEOUT_MS)
            Log.w(TAG, "⏱️ URL load timeout after ${LOAD_TIMEOUT_MS}ms: $url")
            logExecution(url, false, "Timeout after ${LOAD_TIMEOUT_MS}ms")
            cleanup()
        }
    }

    private fun cleanup() {
        if (isCleaningUp) {
            Log.d(TAG, "⚠️ Cleanup already in progress, skipping duplicate call")
            return
        }

        isCleaningUp = true
        Log.d(TAG, "🧹 Starting cleanup...")

        // Cancel all pending jobs
        cleanupJob?.cancel()
        cleanupJob = null
        timeoutJob?.cancel()
        timeoutJob = null

        webView?.let { view ->
            view.stopLoading()
            try {
                windowManager.removeView(view)
                Log.d(TAG, "✅ WebView removed from overlay")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to remove WebView overlay", e)
            }
            view.destroy()
        }
        webView = null
        isCleaningUp = false
        Log.d(TAG, "✅ WebView cleaned up successfully")
    }

    private fun logExecution(url: String, success: Boolean, errorMessage: String?) {
        val log = MonicaLog(
            timestamp = System.currentTimeMillis(),
            url = url,
            success = success,
            errorMessage = errorMessage
        )
        repository.logExecution(log)
    }
}
