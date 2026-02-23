package com.ytplayer.app.channel

import android.app.Activity
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import com.ytplayer.app.webview.WebViewManager

/**
 * Flutter → Native 메서드 채널 핸들러
 * Flutter에서 요청하면 숨겨진 WebView를 제어
 */
class WebViewMethodChannel(
    private val activity: Activity,
    private val dataEventChannel: DataEventChannel
) : MethodChannel.MethodCallHandler {

    val webViewManager by lazy {
        WebViewManager(activity, dataEventChannel)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "loadHomeFeed" -> {
                webViewManager.loadHomeFeed()
                result.success(null)
            }
            "search" -> {
                val query = call.argument<String>("query") ?: ""
                webViewManager.search(query)
                result.success(null)
            }
            "loadSubscriptions" -> {
                webViewManager.loadSubscriptions()
                result.success(null)
            }
            "loadShorts" -> {
                webViewManager.loadShorts()
                result.success(null)
            }
            "loadProfile" -> {
                webViewManager.loadProfile()
                result.success(null)
            }
            "loadDashboard" -> {
                webViewManager.loadDashboard()
                result.success(null)
            }
            "playVideo" -> {
                val url = call.argument<String>("url") ?: ""
                webViewManager.playVideo(url)
                result.success(null)
            }
            "startSignIn" -> {
                webViewManager.startSignIn(result)
            }
            "signOut" -> {
                webViewManager.signOut()
                result.success(null)
            }
            "isLoggedIn" -> {
                result.success(webViewManager.isLoggedIn())
            }
            "scrollBottom" -> {
                webViewManager.scrollBottom()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }
}
