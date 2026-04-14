package com.ytplayer.app.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import com.ytplayer.app.adblock.AdBlockHelper
import com.ytplayer.app.webview.bridges.VideoListBridge
import com.ytplayer.app.webview.bridges.AuthBridge
import com.ytplayer.app.channel.DataEventChannel
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

/**
 * WebView 초기화, 설정, 쿠키 관리 담당
 */
class WebViewConfig(
    private val activity: Activity,
    private val dataEventChannel: DataEventChannel
) {
    companion object {
        private const val TAG = "YTPlayerWebView"
    }

    var webView: WebView? = null
        private set

    var accessToken: String?
        get() = activity.getSharedPreferences("ytplayer_prefs", android.content.Context.MODE_PRIVATE)
            .getString("access_token", null)
        set(value) = activity.getSharedPreferences("ytplayer_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("access_token", value).apply()

    var signInResult: MethodChannel.Result? = null

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun initWebView(webViewClient: WebViewClient) {
        Log.d(TAG, "initWebView 시작")

        webView = WebView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.VISIBLE

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S928N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.7559.132 Mobile Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            addJavascriptInterface(
                VideoListBridge(dataEventChannel),
                "YTPlayer"
            )
            addJavascriptInterface(
                AuthBridge(dataEventChannel) { token ->
                    accessToken = token
                    signInResult?.success(JSONObject().apply {
                        put("accessToken", token)
                    }.toString())
                    signInResult = null
                },
                "YTPlayerAuth"
            )

            this.webViewClient = webViewClient

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        Log.d(TAG, "JS [${it.messageLevel()}] ${it.message()}")
                    }
                    return true
                }
            }
        }

        CookieManager.getInstance().flush()

        val rootView = activity.window.decorView as ViewGroup
        rootView.addView(webView, 0)
        Log.d(TAG, "WebView 추가됨 (MATCH_PARENT, Flutter 뒤 VISIBLE)")
    }

    fun isLoggedIn(): Boolean {
        val cookies = CookieManager.getInstance().getCookie("https://m.youtube.com") ?: ""
        return cookies.contains("SID=") || cookies.contains("SSID=") || !accessToken.isNullOrEmpty()
    }

    fun clearSession() {
        accessToken = null
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        webView?.clearCache(true)
    }

    fun destroy() {
        webView?.let { wv ->
            wv.stopLoading()
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        webView = null
    }
}
