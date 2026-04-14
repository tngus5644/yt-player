package com.hashmeter.ytplayer.webview.bridges

import android.webkit.JavascriptInterface
import com.hashmeter.ytplayer.channel.DataEventChannel
import org.json.JSONObject

/**
 * 영상 목록 JS 브릿지
 * WebView에서 스크래핑한 영상 데이터를 Flutter로 전달
 */
class VideoListBridge(
    private val dataEventChannel: DataEventChannel
) {

    @JavascriptInterface
    fun onVideoListReceived(jsonString: String) {
        try {
            val data = JSONObject(jsonString)
            dataEventChannel.sendEvent("videoList", data)
        } catch (e: Exception) {
            dataEventChannel.sendError("VIDEO_LIST_ERROR", e.message ?: "Unknown error")
        }
    }

    @JavascriptInterface
    fun onVideoListMoreReceived(jsonString: String) {
        try {
            val data = JSONObject(jsonString)
            dataEventChannel.sendEvent("videoListMore", data)
        } catch (e: Exception) {
            dataEventChannel.sendError("VIDEO_LIST_MORE_ERROR", e.message ?: "Unknown error")
        }
    }

    @JavascriptInterface
    fun onSubscriptionListReceived(jsonString: String) {
        try {
            val data = JSONObject(jsonString)
            dataEventChannel.sendEvent("subscriptions", data)
        } catch (e: Exception) {
            dataEventChannel.sendError("SUBSCRIPTION_ERROR", e.message ?: "Unknown error")
        }
    }

    @JavascriptInterface
    fun onError(message: String) {
        dataEventChannel.sendError("JS_ERROR", message)
    }
}
