package com.hashmeter.ytplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.hashmeter.ytplayer.adblock.AdBlockHelper

/**
 * 영상 재생 Activity
 * 별도 Activity로 띄워서 PiP 모드 지원
 */
class PlayerActivity : Activity() {

    companion object {
        var currentInstance: PlayerActivity? = null

        fun finishIfRunning() {
            currentInstance?.finishAndRemoveTask()
            currentInstance = null
        }
    }

    private lateinit var webView: WebView
    private lateinit var fullscreenContainer: FrameLayout
    private var pipBtn: ImageButton? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // 로딩/에러 UI
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var errorOverlay: FrameLayout
    private val timeoutHandler = Handler(Looper.getMainLooper())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 기존 PIP PlayerActivity가 있으면 종료
        currentInstance?.finishAndRemoveTask()
        currentInstance = this

        fullscreenContainer = FrameLayout(this)
        fullscreenContainer.setBackgroundColor(Color.BLACK)
        setContentView(fullscreenContainer)

        // 상태바 투명 표시 + 네비게이션바만 숨김
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.navigationBars())  // 네비게이션바만 숨김
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
            // SYSTEM_UI_FLAG_FULLSCREEN 제거 → 상태바 표시
        }

        initWebView()
        initLoadingOverlay()
        initErrorOverlay()

        // API 31+: 홈 버튼 시 자동 PIP 진입 (부드러운 전환)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(true)
                    .build()
            )
        }

        val videoUrl = intent.getStringExtra("video_url") ?: return finish()
        webView.loadUrl(videoUrl)

        // 15초 타임아웃 보호
        timeoutHandler.postDelayed({
            if (loadingOverlay.visibility == View.VISIBLE) {
                loadingOverlay.visibility = View.GONE
                errorOverlay.visibility = View.VISIBLE
            }
        }, 15000)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                userAgentString = settings.userAgentString.replace("; wv", "")
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return false
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (AdBlockHelper.shouldBlockRequest(request)) {
                        return AdBlockHelper.createEmptyResponse()
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    loadingOverlay.visibility = View.VISIBLE
                    errorOverlay.visibility = View.GONE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    timeoutHandler.removeCallbacksAndMessages(null)
                    loadingOverlay.visibility = View.GONE
                    injectAdBlockAndUiHide(view)
                    // Shorts면 PiP 버튼 숨김
                    pipBtn?.visibility = if (url?.contains("/shorts/") == true) View.GONE else View.VISIBLE
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        loadingOverlay.visibility = View.GONE
                        errorOverlay.visibility = View.VISIBLE
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(
                    view: android.view.View?,
                    callback: CustomViewCallback?
                ) {
                    customView = view
                    customViewCallback = callback
                    fullscreenContainer.addView(view)
                    webView.visibility = View.GONE
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }

                override fun onHideCustomView() {
                    customView?.let { fullscreenContainer.removeView(it) }
                    webView.visibility = View.VISIBLE
                    customViewCallback?.onCustomViewHidden()
                    customView = null
                    customViewCallback = null
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }

        fullscreenContainer.addView(webView)

        // PiP 전환 버튼 오버레이 (아래 쉐브론 ∨)
        val density = resources.displayMetrics.density
        val btnSize = (40 * density).toInt()
        pipBtn = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = getStatusBarHeight() + (8 * density).toInt()
                leftMargin = (12 * density).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(160, 0, 0, 0))
            }
            setImageDrawable(object : Drawable() {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f * density
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                override fun draw(canvas: Canvas) {
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()
                    val s = minOf(w, h) * 0.22f
                    // 아래 방향 쉐브론: ∨
                    canvas.drawLine(w / 2 - s, h / 2 - s * 0.4f, w / 2, h / 2 + s * 0.6f, paint)
                    canvas.drawLine(w / 2, h / 2 + s * 0.6f, w / 2 + s, h / 2 - s * 0.4f, paint)
                }
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: ColorFilter?) {}
                @Deprecated("Deprecated in Java")
                override fun getOpacity() = PixelFormat.TRANSLUCENT
            })
            scaleType = ImageView.ScaleType.CENTER
            val pad = (6 * density).toInt()
            setPadding(pad, pad, pad, pad)
            setOnClickListener { enterPipMode() }
            elevation = 8 * density
        }
        fullscreenContainer.addView(pipBtn)
    }

    /**
     * 로딩 오버레이: 반투명 검은 배경 + ProgressBar + 텍스트
     */
    private fun initLoadingOverlay() {
        val density = resources.displayMetrics.density
        loadingOverlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.argb(200, 0, 0, 0))
            visibility = View.VISIBLE

            // 중앙 ProgressBar
            val spinner = ProgressBar(this@PlayerActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (48 * density).toInt(), (48 * density).toInt()
                ).apply {
                    gravity = Gravity.CENTER
                    bottomMargin = (16 * density).toInt()
                }
                isIndeterminate = true
            }
            addView(spinner)

            // "영상을 불러오는 중..." 텍스트
            val label = TextView(this@PlayerActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                    topMargin = (32 * density).toInt()
                }
                text = "영상을 불러오는 중..."
                setTextColor(Color.WHITE)
                textSize = 14f
            }
            addView(label)
        }
        fullscreenContainer.addView(loadingOverlay)
    }

    /**
     * 에러 오버레이: 아이콘 + 메시지 + 다시 시도 버튼
     */
    private fun initErrorOverlay() {
        val density = resources.displayMetrics.density
        errorOverlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.argb(220, 0, 0, 0))
            visibility = View.GONE

            // 에러 아이콘 (X 마크를 Canvas로)
            val iconView = object : View(this@PlayerActivity) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(200, 255, 255, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * density
                    strokeCap = Paint.Cap.ROUND
                }
                override fun onDraw(canvas: Canvas) {
                    val cx = width / 2f
                    val cy = height / 2f
                    val r = minOf(width, height) * 0.3f
                    // 원
                    canvas.drawCircle(cx, cy, r, paint)
                    // X
                    val s = r * 0.5f
                    canvas.drawLine(cx - s, cy - s, cx + s, cy + s, paint)
                    canvas.drawLine(cx + s, cy - s, cx - s, cy + s, paint)
                }
            }.apply {
                layoutParams = FrameLayout.LayoutParams(
                    (56 * density).toInt(), (56 * density).toInt()
                ).apply {
                    gravity = Gravity.CENTER
                    bottomMargin = (56 * density).toInt()
                }
            }
            addView(iconView)

            // "영상을 불러올 수 없습니다" 텍스트
            val msgLabel = TextView(this@PlayerActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                }
                text = "영상을 불러올 수 없습니다"
                setTextColor(Color.WHITE)
                textSize = 15f
            }
            addView(msgLabel)

            // "다시 시도" 버튼
            val retryBtn = TextView(this@PlayerActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                    topMargin = (40 * density).toInt()
                }
                text = "다시 시도"
                setTextColor(Color.WHITE)
                textSize = 14f
                val pad = (16 * density).toInt()
                setPadding(pad * 2, pad, pad * 2, pad)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24 * density
                    setStroke((1.5f * density).toInt(), Color.argb(180, 255, 255, 255))
                }
                setOnClickListener {
                    errorOverlay.visibility = View.GONE
                    loadingOverlay.visibility = View.VISIBLE
                    timeoutHandler.postDelayed({
                        if (loadingOverlay.visibility == View.VISIBLE) {
                            loadingOverlay.visibility = View.GONE
                            errorOverlay.visibility = View.VISIBLE
                        }
                    }, 15000)
                    webView.reload()
                }
            }
            addView(retryBtn)
        }
        fullscreenContainer.addView(errorOverlay)
    }


    /**
     * 광고 차단 + YouTube 헤더 숨김 JS 인젝션
     */
    private fun injectAdBlockAndUiHide(view: WebView?) {
        view ?: return

        val density = resources.displayMetrics.density
        val statusBarDp = (getStatusBarHeight() / density).toInt()

        view.evaluateJavascript("""
            (function() {
                if (window._ytAdBlockInjected) return;
                window._ytAdBlockInjected = true;

                // ========== 1. CSS: 광고 + YouTube 헤더 숨김 ==========
                var style = document.createElement('style');
                style.id = 'ytplayer-adblock-css';
                style.textContent = `
                    /* YouTube 모바일 헤더/앱바 숨김 (display:none만 사용) */
                    header,
                    #header,
                    ytm-mobile-topbar-renderer,
                    .mobile-topbar-header-content,
                    .topbar-menu-button-avatar-button,
                    ytm-searchbox,
                    .tab-title-bar,
                    .compact-link-icon,
                    ytm-pivot-bar-renderer {
                        display: none !important;
                    }

                    /* 헤더 숨김 후 남은 상단 여백 제거 (상태바 높이 보존) */
                    html {
                        --ytm-toolbar-height: ${statusBarDp}px !important;
                        --ytm-toolbar-offset: ${statusBarDp}px !important;
                        --ytm-header-height: 0px !important;
                    }

                    #app {
                        padding-top: unset !important;
                    }

                    #header-bar {
                        display: none !important;
                    }

                    #player-container-id {
                        top: ${statusBarDp}px !important;
                    }

                    .watch-below-the-player {
                        position: relative !important;
                        top: auto !important;
                    }

                    ytm-engagement-panel {
                        position: relative !important;
                        top: auto !important;
                    }

                    ytm-related-chip-cloud-renderer.chips-visible {
                        position: relative !important;
                        top: auto !important;
                    }

                    /* 광고 요소 숨김 */
                    ytm-promoted-sparkles-web-renderer,
                    ytm-promoted-video-renderer,
                    ytm-companion-ad-renderer,
                    ytm-statement-banner-renderer,
                    ytm-brand-video-shelf-renderer,
                    ytm-in-feed-ad-layout-renderer,
                    ytm-ad-slot-renderer,
                    #masthead-ad,
                    .video-ads,
                    #player-ads,
                    .ytp-ad-module,
                    .ytp-ad-overlay-container,
                    .ytp-ad-overlay-slot,
                    .ytp-ad-text-overlay,
                    .ytp-ad-image-overlay,
                    .ad-container, .ad-div,
                    #merch-shelf, ytd-merch-shelf-renderer,
                    .ytp-ad-skip-button-container {
                        display: none !important;
                    }
                    .ad-showing .ytp-ad-text {
                        display: none !important;
                    }
                `;
                document.head.appendChild(style);

                // ========== 2. JS: 광고 자동 스킵 ==========
                function skipAds() {
                    var skipBtns = document.querySelectorAll(
                        '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, ' +
                        '.ytp-skip-ad-button, button[class*="skip-button"]'
                    );
                    for (var i = 0; i < skipBtns.length; i++) {
                        try { skipBtns[i].click(); } catch(e) {}
                    }

                    var closeBtns = document.querySelectorAll(
                        '.ytp-ad-overlay-close-button, [class*="ad-close"]'
                    );
                    for (var i = 0; i < closeBtns.length; i++) {
                        try { closeBtns[i].click(); } catch(e) {}
                    }

                    if (document.querySelector('.ad-showing')) {
                        var v = document.querySelector('video');
                        if (v && v.duration && isFinite(v.duration) && v.duration > 0) {
                            v.currentTime = v.duration;
                        }
                    }
                }

                setInterval(skipAds, 500);

                var observer = new MutationObserver(function() { skipAds(); });
                observer.observe(document.documentElement, {
                    childList: true, subtree: true
                });

                // ========== 3. 영상 정보 영역 레이아웃 동적 조정 ==========
                function adjustLayout() {
                    var player = document.querySelector('#player-container-id');
                    var below = document.querySelector('.watch-below-the-player');
                    if (!player || !below) return false;

                    var playerBottom = player.getBoundingClientRect().bottom;
                    var belowTop = below.getBoundingClientRect().top;
                    if (playerBottom <= 0) return false;

                    var overlap = playerBottom - belowTop;
                    if (overlap > 0) {
                        below.style.setProperty('margin-top', overlap + 'px', 'important');
                    }
                    return true;
                }

                var retryCount = 0;
                function tryAdjust() {
                    if (adjustLayout() || retryCount >= 10) return;
                    retryCount++;
                    setTimeout(tryAdjust, 500);
                }
                setTimeout(tryAdjust, 300);

                if (typeof ResizeObserver !== 'undefined') {
                    var debounceTimer;
                    var ro = new ResizeObserver(function() {
                        clearTimeout(debounceTimer);
                        debounceTimer = setTimeout(adjustLayout, 100);
                    });
                    var playerEl = document.querySelector('#player-container-id');
                    if (playerEl) ro.observe(playerEl);
                }

                // SPA 내비게이션 대응
                document.addEventListener('yt-navigate-finish', function() {
                    retryCount = 0;
                    setTimeout(tryAdjust, 300);
                });
            })();
        """.trimIndent(), null)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isShowingShorts()) {
            enterPipMode()
        }
    }

    private fun isShowingShorts(): Boolean {
        val url = webView.url ?: return false
        return url.contains("/shorts/")
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isShowingShorts()) {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))

            // API 31+: 비디오 영역에서 부드러운 전환 애니메이션
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val videoHeight = (webView.width * 9f / 16f).toInt()
                val videoRect = Rect(0, getStatusBarHeight(), webView.width, getStatusBarHeight() + videoHeight)
                builder.setSourceRectHint(videoRect)
            }

            enterPictureInPictureMode(builder.build())
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode) {
            // PIP 진입: PiP 버튼 숨기고 비디오를 전체 화면으로
            pipBtn?.visibility = View.GONE
            webView.evaluateJavascript("""
                (function() {
                    var video = document.querySelector('video');
                    if (video) {
                        video.style.cssText = 'position:fixed !important;top:0 !important;left:0 !important;width:100vw !important;height:100vh !important;z-index:999999 !important;object-fit:contain !important;background:black !important;';
                        document.body.style.overflow = 'hidden';
                        // 다른 UI 요소 숨김
                        var style = document.createElement('style');
                        style.id = 'ytplayer-pip-style';
                        style.textContent = 'ytm-app, .player-controls-background, .watch-below-the-player, #secondary, .slim-video-metadata-header { display:none !important; } body { background:black !important; overflow:hidden !important; }';
                        document.head.appendChild(style);
                        // PiP 진입 시 음소거 상태 보존 후 재생 재개
                        var wasMuted = video.muted;
                        video.play().then(function() {
                            video.muted = wasMuted;
                        }).catch(function(e) { console.log('PiP play failed:', e); });
                    }
                })();
            """.trimIndent(), null)
        } else {
            // PIP 복귀: PiP 버튼 복원, 비디오 스타일 원래대로
            pipBtn?.visibility = View.VISIBLE
            webView.evaluateJavascript("""
                (function() {
                    var video = document.querySelector('video');
                    if (video) {
                        video.style.cssText = '';
                        document.body.style.overflow = '';
                    }
                    var pipStyle = document.getElementById('ytplayer-pip-style');
                    if (pipStyle) pipStyle.remove();
                })();
            """.trimIndent(), null)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (customView != null) {
            webView.webChromeClient?.onHideCustomView()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (currentInstance == this) currentInstance = null
        timeoutHandler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }
}
