package com.ytplayer.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
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
import com.ytplayer.app.adblock.AdBlockHelper

/**
 * 영상 재생 Activity
 * 별도 Activity로 띄워서 PiP 모드 지원
 */
class PlayerActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var fullscreenContainer: FrameLayout
    private var backBtn: ImageButton? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fullscreenContainer = FrameLayout(this)
        fullscreenContainer.setBackgroundColor(Color.BLACK)
        setContentView(fullscreenContainer)

        // 전체화면 몰입 모드 (setContentView 이후에 호출해야 DecorView가 초기화됨)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        initWebView()

        val videoUrl = intent.getStringExtra("video_url") ?: return finish()
        webView.loadUrl(videoUrl)
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

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectAdBlockAndUiHide(view)
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
                }

                override fun onHideCustomView() {
                    customView?.let { fullscreenContainer.removeView(it) }
                    webView.visibility = View.VISIBLE
                    customViewCallback?.onCustomViewHidden()
                    customView = null
                    customViewCallback = null
                }
            }
        }

        fullscreenContainer.addView(webView)

        // 뒤로가기 버튼 오버레이
        val density = resources.displayMetrics.density
        val btnSize = (40 * density).toInt()
        backBtn = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = (16 * density).toInt()
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
                    canvas.drawLine(w / 2 + s * 0.3f, h / 2 - s, w / 2 - s * 0.7f, h / 2, paint)
                    canvas.drawLine(w / 2 - s * 0.7f, h / 2, w / 2 + s * 0.3f, h / 2 + s, paint)
                }
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: ColorFilter?) {}
                @Deprecated("Deprecated in Java")
                override fun getOpacity() = PixelFormat.TRANSLUCENT
            })
            scaleType = ImageView.ScaleType.CENTER
            val pad = (6 * density).toInt()
            setPadding(pad, pad, pad, pad)
            setOnClickListener { finish() }
            elevation = 8 * density
        }
        fullscreenContainer.addView(backBtn)
    }

    /**
     * 광고 차단 + YouTube 헤더 숨김 JS 인젝션
     */
    private fun injectAdBlockAndUiHide(view: WebView?) {
        view ?: return

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
                    .watch-below-the-player,
                    .slim-video-metadata-header,
                    .compact-link-icon,
                    ytm-pivot-bar-renderer {
                        display: none !important;
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
            })();
        """.trimIndent(), null)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipMode()
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode) {
            // PIP 진입: 뒤로가기 버튼 숨기고 비디오를 전체 화면으로
            backBtn?.visibility = View.GONE
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
                    }
                })();
            """.trimIndent(), null)
        } else {
            // PIP 복귀: 뒤로가기 버튼 복원, 비디오 스타일 원래대로
            backBtn?.visibility = View.VISIBLE
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
        webView.destroy()
        super.onDestroy()
    }
}
