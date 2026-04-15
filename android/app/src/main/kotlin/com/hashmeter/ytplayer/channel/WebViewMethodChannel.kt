package com.hashmeter.ytplayer.channel

import android.app.Activity
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import com.hashmeter.ytplayer.webview.WebViewManager
import kotlinx.coroutines.*

/**
 * Flutter → Native 메서드 채널 핸들러
 * Flutter에서 요청하면 숨겨진 WebView를 제어
 */
class WebViewMethodChannel(
    private val activity: Activity,
    private val dataEventChannel: DataEventChannel
) : MethodChannel.MethodCallHandler {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val webViewManager by lazy {
        WebViewManager(activity, dataEventChannel)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "loadHomeFeed" -> {
                val isRefresh = call.argument<Boolean>("isRefresh") ?: false
                webViewManager.loadHomeFeed(isRefresh)
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
            "loadLibrary" -> {
                webViewManager.loadLibrary()
                result.success(null)
            }
            "loadHistory" -> {
                webViewManager.loadHistory()
                result.success(null)
            }
            "loadHistoryContinuation" -> {
                webViewManager.loadHistoryContinuation()
                result.success(null)
            }
            "loadPlaylistDetail" -> {
                val playlistId = call.argument<String>("playlistId") ?: ""
                if (playlistId.isEmpty()) {
                    result.error("INVALID_ARGS", "playlistId is required", null)
                    return
                }
                webViewManager.loadPlaylistDetail(playlistId)
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
            "loadMoreHomeFeed" -> {
                webViewManager.loadMoreHomeFeed()
                result.success(null)
            }
            "getAccountInfo" -> {
                scope.launch {
                    try {
                        val info = withContext(Dispatchers.IO) {
                            webViewManager.getAccountInfo()
                        }
                        if (info == null) {
                            result.success(null)
                        } else {
                            result.success(mapOf(
                                "displayName" to info.displayName,
                                "photoUrl" to info.photoUrl,
                                "channelHandle" to info.channelHandle,
                                "email" to info.email,
                            ))
                        }
                    } catch (e: Exception) {
                        result.error("API_ERROR", e.message, null)
                    }
                }
            }
            "getVideoDetail" -> {
                val videoId = call.argument<String>("videoId") ?: ""
                if (videoId.isEmpty()) {
                    result.error("INVALID_ARGS", "videoId is required", null)
                    return
                }
                scope.launch {
                    try {
                        val detail = withContext(Dispatchers.IO) {
                            webViewManager.getVideoDetail(videoId)
                        }
                        result.success(detail.toString())
                    } catch (e: Exception) {
                        result.error("API_ERROR", e.message, null)
                    }
                }
            }
            else -> result.notImplemented()
        }
    }
}
