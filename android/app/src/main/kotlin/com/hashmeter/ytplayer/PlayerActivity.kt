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
import android.view.WindowManager
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
    private var topBar: FrameLayout? = null
    private var titleText: TextView? = null
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
        initTopBar()
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
        val density = resources.displayMetrics.density
        val topInset = (48 * density).toInt()
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { topMargin = topInset }

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
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    val cleaned = title
                        ?.replace(Regex("\\s*-?\\s*YouTube\\s*$", RegexOption.IGNORE_CASE), "")
                        ?.trim()
                        ?: ""
                    titleText?.text = cleaned
                }

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
    }

    /**
     * 상단 AppBar: 뒤로가기 + 영상 제목 + 공유 + PiP
     */
    private fun initTopBar() {
        val density = resources.displayMetrics.density
        val statusH = getStatusBarHeight()
        val barH = (48 * density).toInt()
        val btn = (40 * density).toInt()

        topBar = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                statusH + barH
            ).apply { gravity = Gravity.TOP }
            setPadding(0, statusH, 0, 0)
            setBackgroundColor(Color.argb(140, 0, 0, 0))
            elevation = 8 * density
        }

        val backBtn = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(btn, btn).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                leftMargin = (4 * density).toInt()
            }
            background = null
            setImageDrawable(strokeIcon { canvas, w, h, paint ->
                val s = minOf(w, h) * 0.22f
                canvas.drawLine(w / 2 + s * 0.6f, h / 2 - s, w / 2 - s * 0.4f, h / 2, paint)
                canvas.drawLine(w / 2 - s * 0.4f, h / 2, w / 2 + s * 0.6f, h / 2 + s, paint)
            })
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener { finish() }
        }

        titleText = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                leftMargin = btn + (8 * density).toInt()
                rightMargin = btn * 2 + (8 * density).toInt()
            }
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            text = ""
        }

        val screenOffBtn = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(btn, btn).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                rightMargin = btn + (4 * density).toInt()
            }
            background = null
            setImageDrawable(strokeIcon { canvas, w, h, paint ->
                val s = minOf(w, h) * 0.22f
                // 달/전원 모양: 원호 + 위쪽 작은 선
                canvas.drawArc(w / 2 - s, h / 2 - s, w / 2 + s, h / 2 + s, 60f, 240f, false, paint)
                canvas.drawLine(w / 2, h / 2 - s, w / 2, h / 2 - s * 0.4f, paint)
            })
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener { enableScreenOffMode() }
        }

        pipBtn = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(btn, btn).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                rightMargin = (4 * density).toInt()
            }
            background = null
            setImageDrawable(strokeIcon { canvas, w, h, paint ->
                val s = minOf(w, h) * 0.22f
                // 아래 방향 쉐브론
                canvas.drawLine(w / 2 - s, h / 2 - s * 0.4f, w / 2, h / 2 + s * 0.6f, paint)
                canvas.drawLine(w / 2, h / 2 + s * 0.6f, w / 2 + s, h / 2 - s * 0.4f, paint)
            })
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener { enterPipMode() }
        }

        topBar?.apply {
            addView(backBtn)
            addView(titleText)
            addView(screenOffBtn)
            addView(pipBtn)
        }
        fullscreenContainer.addView(topBar)
    }

    private fun strokeIcon(draw: (Canvas, Float, Float, Paint) -> Unit): Drawable {
        val density = resources.displayMetrics.density
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2.5f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            override fun draw(canvas: Canvas) {
                draw(canvas, bounds.width().toFloat(), bounds.height().toFloat(), paint)
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: ColorFilter?) {}
            @Deprecated("Deprecated in Java")
            override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

    private var screenOffOverlay: FrameLayout? = null

    /**
     * 화면 끄기 모드: 화면 밝기를 최소로 낮추고 검은 오버레이를 띄워
     * 영상 화면을 끈 채로 오디오만 계속 재생되도록 한다. 오버레이 탭 시 복원.
     */
    private fun enableScreenOffMode() {
        if (screenOffOverlay != null) return

        window.attributes = window.attributes.apply {
            screenBrightness = 0.01f
        }

        val density = resources.displayMetrics.density
        screenOffOverlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            elevation = 100 * density
            isClickable = true
            isFocusable = true
            setOnClickListener { disableScreenOffMode() }

            addView(TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER }
                setTextColor(Color.argb(140, 255, 255, 255))
                textSize = 14f
                text = "탭하여 화면 켜기"
            })
        }
        fullscreenContainer.addView(screenOffOverlay)
    }

    private fun disableScreenOffMode() {
        screenOffOverlay?.let { fullscreenContainer.removeView(it) }
        screenOffOverlay = null
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
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

                // ========== 자동 음소거 해제 ==========
                function unmuteAll() {
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        try {
                            videos[i].muted = false;
                            videos[i].volume = 1.0;
                        } catch (e) {}
                    }
                    var labels = ['음소거 해제', 'unmute', 'tap to unmute'];
                    var candidates = document.querySelectorAll(
                        'button, [role="button"], .ytp-unmute, .ytp-unmute-button'
                    );
                    for (var j = 0; j < candidates.length; j++) {
                        var el = candidates[j];
                        var aria = (el.getAttribute('aria-label') || '').toLowerCase();
                        var text = (el.textContent || '').toLowerCase();
                        for (var k = 0; k < labels.length; k++) {
                            if (aria.indexOf(labels[k]) !== -1 || text.indexOf(labels[k]) !== -1) {
                                try { el.click(); } catch (e) {}
                                break;
                            }
                        }
                    }
                }
                setInterval(unmuteAll, 500);
                document.addEventListener('play', unmuteAll, true);

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

    private fun isLoggedIn(): Boolean {
        val cookies = android.webkit.CookieManager.getInstance()
            .getCookie("https://www.youtube.com") ?: ""
        return cookies.contains("SID=") ||
            cookies.contains("SSID=") ||
            cookies.contains("__Secure-1PSID=")
    }

    private fun enterPipMode() {
        if (!isLoggedIn()) {
            // 로그인 안 됨: YTPlayer 탭으로 이동 후 Activity 종료
            val intent = android.content.Intent(this, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "ytplayer")
            }
            startActivity(intent)
            finish()
            return
        }
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
            topBar?.visibility = View.GONE
            (webView.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.topMargin = 0
                webView.layoutParams = it
            }
        } else {
            topBar?.visibility = View.VISIBLE
            val density = resources.displayMetrics.density
            (webView.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.topMargin = (48 * density).toInt()
                webView.layoutParams = it
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (!isInPictureInPictureMode) {
            webView.onPause()
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
