package com.ytplayer.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import com.ytplayer.app.channel.WebViewMethodChannel
import com.ytplayer.app.channel.DataEventChannel
import com.ytplayer.app.overlay.OverlayWebViewService
import com.ytplayer.app.shorts.ShortsPlatformView
import com.ytplayer.app.shorts.ShortsPlatformViewFactory
import com.ytplayer.app.webview.WebViewManager

class MainActivity : FlutterActivity() {

    private lateinit var webViewMethodChannel: WebViewMethodChannel
    private lateinit var dataEventChannel: DataEventChannel
    private var currentShortsView: ShortsPlatformView? = null

    companion object {
        private const val TAG = "MainActivity"
        const val METHOD_CHANNEL = "com.ytplayer/webview"
        const val EVENT_CHANNEL = "com.ytplayer/data"
        const val PLAYER_CHANNEL = "com.ytplayer/player"
        const val OVERLAY_CHANNEL = "com.ytplayer/overlay"
        const val SHORTS_CHANNEL = "com.ytplayer/shorts"
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1234
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Shorts PlatformView 등록
        flutterEngine.platformViewsController.registry.registerViewFactory(
            "shorts-webview",
            ShortsPlatformViewFactory { view -> currentShortsView = view }
        )

        // 데이터 이벤트 채널 (Native → Flutter 스트림)
        dataEventChannel = DataEventChannel()
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
            .setStreamHandler(dataEventChannel)

        // WebView 메서드 채널 (Flutter → Native 요청)
        webViewMethodChannel = WebViewMethodChannel(this, dataEventChannel)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL)
            .setMethodCallHandler(webViewMethodChannel)

        // 플레이어 채널
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, PLAYER_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startPip" -> result.success(false)
                    "isPipSupported" -> result.success(Build.VERSION.SDK_INT >= 26)
                    "shareVideo" -> {
                        val videoId = call.argument<String>("videoId") ?: ""
                        val title = call.argument<String>("title") ?: ""
                        shareVideo(videoId, title)
                        result.success(null)
                    }
                    "closePlayer" -> result.success(null)
                    else -> result.notImplemented()
                }
            }

        // 오버레이 서비스 채널
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, OVERLAY_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startOverlayService" -> {
                        if (checkOverlayPermission()) {
                            startOverlayService()
                            result.success(true)
                        } else {
                            requestOverlayPermission()
                            result.success(false)
                        }
                    }
                    "stopOverlayService" -> {
                        stopOverlayService()
                        result.success(true)
                    }
                    "isOverlayServiceRunning" -> {
                        result.success(OverlayWebViewService.isRunning)
                    }
                    "hasOverlayPermission" -> {
                        result.success(checkOverlayPermission())
                    }
                    "requestOverlayPermission" -> {
                        requestOverlayPermission()
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }

        // 쇼츠 제어 채널 (Flutter → Native: pause/resume)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SHORTS_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "pauseShorts" -> {
                        currentShortsView?.pauseVideo()
                        result.success(null)
                    }
                    "resumeShorts" -> {
                        currentShortsView?.resumeVideo()
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    // ==================== Activity Result 처리 ====================

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            WebViewManager.SIGN_IN_REQUEST_CODE -> {
                webViewMethodChannel.webViewManager.onSignInResult(resultCode)
            }
        }
    }

    // ==================== 오버레이 서비스 제어 ====================

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    private fun startOverlayService() {
        Log.d(TAG, "오버레이 서비스 시작")
        val intent = Intent(this, OverlayWebViewService::class.java).apply {
            action = OverlayWebViewService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopOverlayService() {
        Log.d(TAG, "오버레이 서비스 중지")
        val intent = Intent(this, OverlayWebViewService::class.java).apply {
            action = OverlayWebViewService.ACTION_STOP
        }
        startService(intent)
    }

    // ==================== 기타 ====================

    private fun shareVideo(videoId: String, title: String) {
        val shareUrl = "https://youplayer.co.kr/s/ytb/$videoId"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$title\n$shareUrl")
        }
        startActivity(Intent.createChooser(intent, "공유"))
    }
}
