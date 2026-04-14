package com.ytplayer.app.shorts

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.webkit.*
import com.ytplayer.app.adblock.AdBlockHelper
import io.flutter.plugin.platform.PlatformView

/**
 * YouTube Shorts 재생용 PlatformView
 * PlayerActivity의 WebView 설정과 AdBlockHelper를 재사용
 */
class ShortsPlatformView(
    context: Context,
    private val initialUrl: String
) : PlatformView {

    private val webView: WebView = createWebView(context)

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                userAgentString = userAgentString.replace("; wv", "")
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
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectAdBlockAndUiHide(view)
                }
            }

            setBackgroundColor(android.graphics.Color.BLACK)
            loadUrl(initialUrl)
        }
    }

    override fun getView(): View = webView

    fun pauseVideo() {
        webView.evaluateJavascript(
            "document.querySelectorAll('video').forEach(v => v.pause())", null
        )
    }

    fun resumeVideo() {
        webView.evaluateJavascript(
            "document.querySelectorAll('video').forEach(v => v.play())", null
        )
    }

    /**
     * 광고 차단 + YouTube 헤더/하단바 숨김 JS 인젝션
     * PlayerActivity.injectAdBlockAndUiHide() 로직 재사용 + Shorts 전용 UI 숨김
     */
    private fun injectAdBlockAndUiHide(view: WebView?) {
        view ?: return

        view.evaluateJavascript("""
            (function() {
                if (window._ytShortsAdBlockInjected) return;
                window._ytShortsAdBlockInjected = true;

                // ========== 1. CSS: 광고 + YouTube 헤더/하단바 숨김 ==========
                var style = document.createElement('style');
                style.id = 'ytplayer-shorts-css';
                style.textContent = `
                    /* YouTube 모바일 헤더/앱바 숨김 */
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

                    /* YouTube 하단 네비게이션 바 숨김 */
                    ytm-pivot-bar-renderer,
                    .pivot-bar,
                    .bottom-tab-bar,
                    ytm-bottom-sheet-renderer {
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

                    /* Shorts 페이지 전체 화면 최적화 */
                    body {
                        overflow: hidden !important;
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

    override fun dispose() {
        webView.destroy()
    }
}
