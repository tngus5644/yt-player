package com.hashmeter.ytplayer.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 화면 on/off 상태 감지 리시버
 * 원본 앱의 DeviceControllerKt.isProgressBeforeCheckScreenOnOff()에 대응
 *
 * 화면이 꺼져 있을 때는 오버레이 작업을 일시 중지
 */
class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenStateReceiver"
        var isScreenOn = true
            private set

        private var listener: ScreenStateListener? = null

        fun setListener(l: ScreenStateListener?) {
            listener = l
        }
    }

    interface ScreenStateListener {
        fun onScreenOn()
        fun onScreenOff()
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "화면 켜짐")
                isScreenOn = true
                listener?.onScreenOn()
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "화면 꺼짐")
                isScreenOn = false
                listener?.onScreenOff()
            }
        }
    }
}
