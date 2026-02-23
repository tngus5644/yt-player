package com.ytplayer.app.channel

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.EventChannel
import org.json.JSONObject

/**
 * Native → Flutter 데이터 스트림 채널
 * WebView에서 스크래핑한 데이터를 Flutter로 전달
 */
class DataEventChannel : EventChannel.StreamHandler {

    private var eventSink: EventChannel.EventSink? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    /**
     * Flutter에 이벤트 전송
     */
    fun sendEvent(type: String, data: JSONObject? = null) {
        mainHandler.post {
            val event = JSONObject().apply {
                put("type", type)
                if (data != null) put("data", data)
            }
            eventSink?.success(event.toString())
        }
    }

    /**
     * Flutter에 에러 전송
     */
    fun sendError(code: String, message: String) {
        mainHandler.post {
            eventSink?.error(code, message, null)
        }
    }
}
