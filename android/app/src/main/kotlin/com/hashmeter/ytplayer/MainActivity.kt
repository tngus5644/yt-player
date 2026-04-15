package com.hashmeter.ytplayer

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
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import com.hashmeter.ytplayer.channel.WebViewMethodChannel
import com.hashmeter.ytplayer.channel.DataEventChannel
import com.hashmeter.ytplayer.overlay.OverlayWebViewService
import com.hashmeter.ytplayer.shorts.ShortsPlatformView
import com.hashmeter.ytplayer.shorts.ShortsPlatformViewFactory
import com.hashmeter.ytplayer.webview.WebViewManager

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
        handleNavigateIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigateIntent(intent)
    }

    /**
     * 다른 Activity(예: PlayerActivity 로그인 안 된 상태)에서 보낸 탭 이동 요청 처리.
     * Intent extra "navigate_to" 값을 Flutter 측 이벤트로 전달.
     */
    private fun handleNavigateIntent(intent: Intent?) {
        val target = intent?.getStringExtra("navigate_to") ?: return
        intent.removeExtra("navigate_to")
        // Flutter 엔진이 리스너를 구독할 시간을 주기 위해 짧은 지연 후 전송
        Handler(Looper.getMainLooper()).postDelayed({
            dataEventChannel.sendEvent("navigateTab", JSONObject().apply {
                put("tab", target)
            })
        }, 400)
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
