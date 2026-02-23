package com.ytplayer.app.webview.bridges

import android.webkit.JavascriptInterface
import com.ytplayer.app.channel.DataEventChannel
import org.json.JSONObject

/**
 * 플레이어 JS 브릿지
 * 영상 재생 이벤트를 Flutter로 전달
 */
class PlayerBridge(
    private val dataEventChannel: DataEventChannel
) {

    @JavascriptInterface
    fun onPlayStateChanged(jsonString: String) {
        try {
            val data = JSONObject(jsonString)
            dataEventChannel.sendEvent("playState", data)
        } catch (e: Exception) {
            dataEventChannel.sendError("PLAY_STATE_ERROR", e.message ?: "Unknown error")
        }
    }

    @JavascriptInterface
    fun onWatchHistoryUpdate(jsonString: String) {
        try {
            val data = JSONObject(jsonString)
            dataEventChannel.sendEvent("watchHistory", data)
        } catch (e: Exception) {
            dataEventChannel.sendError("WATCH_HISTORY_ERROR", e.message ?: "Unknown error")
        }
    }

    @JavascriptInterface
    fun onFullscreenChanged(jsonString: String) {
        try {
            val data = JSONObject(jsonString)
            dataEventChannel.sendEvent("fullscreenChanged", data)
        } catch (e: Exception) {
            dataEventChannel.sendError("FULLSCREEN_ERROR", e.message ?: "Unknown error")
        }
    }

    @JavascriptInterface
    fun onRewardEarned(jsonString: String) {
        try {
            val data = JSONObject(jsonString)
            dataEventChannel.sendEvent("rewardEarned", data)
        } catch (e: Exception) {
            dataEventChannel.sendError("REWARD_ERROR", e.message ?: "Unknown error")
        }
    }
}
