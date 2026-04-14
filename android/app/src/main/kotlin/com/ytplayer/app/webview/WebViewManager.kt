package com.ytplayer.app.webview

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import io.flutter.plugin.common.MethodChannel
import com.ytplayer.app.BuildConfig
import com.ytplayer.app.channel.DataEventChannel
import com.ytplayer.app.PlayerActivity
import com.ytplayer.app.WebViewSignInActivity
import com.ytplayer.app.adblock.AdBlockHelper
import org.json.JSONObject

/**
 * 숨겨진 WebView 관리자 (조율 클래스)
 * WebViewConfig, PageDataExtractor, HomeFeedManager에 책임을 위임
 */
class WebViewManager(
    private val activity: Activity,
    private val dataEventChannel: DataEventChannel
) {

    companion object {
        private const val TAG = "YTPlayerWebView"
        private const val EXTRACT_INITIAL_DELAY_MS = 5000L
        private const val EXTRACT_RETRY_DELAY_MS = 5000L
        private const val EXTRACT_MAX_RETRIES = 3
        const val SIGN_IN_REQUEST_CODE = 2001
        internal val INNERTUBE_API_KEY: String = BuildConfig.INNERTUBE_API_KEY
        internal const val WEB_CLIENT_VERSION = "2.20260206.01.00"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val apiClient = InnerTubeApiClient()
    private val config = WebViewConfig(activity, dataEventChannel)
    private val extractor = PageDataExtractor()
    private val feedManager = HomeFeedManager(apiClient, dataEventChannel)
    private val libraryManager = LibraryManager(apiClient, dataEventChannel)

    @Volatile private var retryCount = 0
    @Volatile private var triedTrending = false
    @Volatile private var consentHandled = false

    private val dataReceived: Boolean get() = feedManager.dataReceived

    init {
        Log.d(TAG, "========= WebViewManager 생성됨 =========")
        mainHandler.post {
            config.initWebView(createWebViewClient())
        }
    }

    // ==================== WebViewClient ====================

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "▶ 로딩 시작: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "✓ 로딩 완료: $url")
                url ?: return

                if (url.contains("consent.youtube.com") || url.contains("consent.google.com")) {
                    extractor.handleConsentPage(config.webView)
                    return
                }

                when {
                    url.contains("m.youtube.com") && !url.contains("/shorts/") && !url.contains("/feed/subscriptions") -> {
                        if (dataReceived) {
                            Log.d(TAG, "API 데이터 이미 수신됨 → WebView 추출 스킵")
                            return
                        }
                        retryCount = 0

                        mainHandler.postDelayed({
                            config.webView?.evaluateJavascript(
                                "window.scrollTo(0, 500); setTimeout(function(){ window.scrollTo(0, 0); }, 500);",
                                null
                            )
                        }, 2000)

                        mainHandler.postDelayed({
                            extractor.checkAndHandleConsentThenExtract(
                                config.webView,
                                dataReceived
                            ) { extractVideoDataWithRetry() }
                        }, EXTRACT_INITIAL_DELAY_MS)
                    }
                    url.contains("m.youtube.com/feed/subscriptions") -> {
                        retryCount = 0
                        mainHandler.postDelayed({
                            extractor.extractSubscriptions(config.webView)
                        }, EXTRACT_INITIAL_DELAY_MS)
                    }
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (AdBlockHelper.shouldBlockRequest(request)) {
                    return AdBlockHelper.createEmptyResponse()
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                Log.d(TAG, "네비게이션: $url")
                return false
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    Log.e(TAG, "에러: ${error?.errorCode} - ${error?.description} (${request.url})")
                }
            }
        }
    }

    // ==================== Video Data Extraction with Retry ====================

    private fun extractVideoDataWithRetry() {
        if (dataReceived) return

        extractor.extractVideoData(config.webView) {}

        mainHandler.postDelayed({
            if (!dataReceived) {
                retryCount++
                if (retryCount < EXTRACT_MAX_RETRIES) {
                    Log.d(TAG, "재시도 #$retryCount (DOM + continuation)")
                    extractVideoDataWithRetry()
                } else if (!triedTrending) {
                    Log.w(TAG, "★ 홈 피드 실패! → /feed/trending 폴백 시도")
                    triedTrending = true
                    retryCount = 0
                    feedManager.dataReceived = false
                    mainHandler.post {
                        config.webView?.loadUrl("https://m.youtube.com/feed/trending")
                    }
                } else {
                    Log.w(TAG, "★ WebView 추출 모두 실패! → 직접 InnerTube API 호출")
                    feedManager.fetchVideosDirectApi()
                }
            }
        }, EXTRACT_RETRY_DELAY_MS)
    }

    // ==================== Public Methods ====================

    fun loadHomeFeed(isRefresh: Boolean = false) {
        Log.d(TAG, "★★★ loadHomeFeed() 호출됨 (isRefresh=$isRefresh) ★★★")
        feedManager.resetForRefresh()
        feedManager.dataReceived = false
        retryCount = 0
        triedTrending = false

        feedManager.fetchVideosDirectApi()

        if (!isRefresh) {
            mainHandler.post {
                config.webView?.loadUrl("https://m.youtube.com/")
            }
        }
    }

    fun search(query: String) {
        feedManager.fetchSearchApi(query)
    }

    fun loadSubscriptions() {
        // API 방식으로 구독 피드 로드 (WebView DOM 추출은 채널 목록용으로 유지)
        feedManager.fetchSubscriptionFeedApi()
        mainHandler.post {
            config.webView?.loadUrl("https://m.youtube.com/feed/subscriptions")
        }
    }

    fun loadShorts() {
        feedManager.fetchShortsApi()
    }

    fun loadLibrary() {
        Log.d(TAG, "★★★ loadLibrary() 호출됨 ★★★")
        libraryManager.fetchLibraryApi()
    }

    fun loadHistory() {
        Log.d(TAG, "★★★ loadHistory() 호출됨 ★★★")
        libraryManager.fetchHistoryApi()
    }

    fun loadHistoryContinuation() {
        libraryManager.fetchHistoryContinuation()
    }

    fun loadPlaylistDetail(playlistId: String) {
        Log.d(TAG, "★★★ loadPlaylistDetail($playlistId) 호출됨 ★★★")
        libraryManager.fetchPlaylistDetailApi(playlistId)
    }

    fun loadDashboard() {
        mainHandler.post {
            config.webView?.loadUrl("https://youplayer.co.kr/app_dashboard")
        }
    }

    @Volatile private var lastPlayTime = 0L

    fun playVideo(url: String) {
        val now = System.currentTimeMillis()
        if (now - lastPlayTime < 1000) return
        lastPlayTime = now

        val intent = Intent(activity, PlayerActivity::class.java).apply {
            putExtra("video_url", url)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        activity.startActivity(intent)
    }

    fun startSignIn(result: MethodChannel.Result) {
        val intent = Intent(activity, WebViewSignInActivity::class.java)
        @Suppress("DEPRECATION")
        activity.startActivityForResult(intent, SIGN_IN_REQUEST_CODE)
        result.success(null)
    }

    fun onSignInResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            CookieManager.getInstance().flush()
            val data = JSONObject().apply { put("isLogin", true) }
            dataEventChannel.sendEvent("loginState", data)
        }
    }

    fun signOut() {
        config.clearSession()
        mainHandler.post { config.webView?.clearCache(true) }
        val data = JSONObject().apply { put("isLogin", false) }
        dataEventChannel.sendEvent("loginState", data)
    }

    fun isLoggedIn(): Boolean = config.isLoggedIn()

    fun getVideoDetail(videoId: String): JSONObject {
        return apiClient.fetchVideoDetail(videoId)
    }

    fun scrollBottom() {
        mainHandler.post {
            config.webView?.evaluateJavascript(
                "window.scrollTo(0, document.body.scrollHeight);",
                null
            )
            mainHandler.postDelayed({
                extractor.extractMoreVideos(config.webView)
            }, 2500)
        }
    }

    fun loadMoreHomeFeed() {
        feedManager.loadMoreHomeFeed()
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        feedManager.destroy()
        libraryManager.destroy()
        mainHandler.post { config.destroy() }
    }
}
