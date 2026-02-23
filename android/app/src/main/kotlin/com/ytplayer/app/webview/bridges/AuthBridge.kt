package com.ytplayer.app.webview.bridges

import android.webkit.JavascriptInterface
import com.ytplayer.app.channel.DataEventChannel
import org.json.JSONObject

/**
 * 인증 JS 브릿지
 * WebView 로그인 완료 시 토큰을 Flutter로 전달
 */
class AuthBridge(
    private val dataEventChannel: DataEventChannel,
    private val onTokenReceived: (String) -> Unit
) {

    @JavascriptInterface
    fun signIn(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val token = json.optString("accessToken", "")

            if (token.isNotEmpty()) {
                onTokenReceived(token)

                val data = JSONObject().apply {
                    put("isLogin", true)
                    put("accessToken", token)
                }
                dataEventChannel.sendEvent("loginState", data)
            }
        } catch (e: Exception) {
            dataEventChannel.sendError("AUTH_ERROR", e.message ?: "Unknown error")
        }
    }

    @JavascriptInterface
    fun signOut() {
        onTokenReceived("")
        val data = JSONObject().apply {
            put("isLogin", false)
        }
        dataEventChannel.sendEvent("loginState", data)
    }
}
