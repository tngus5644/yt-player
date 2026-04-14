package com.hashmeter.ytplayer.adblock

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

/**
 * YouTube 광고 차단 헬퍼
 * PlayerActivity, WebViewManager에서 공통으로 사용
 */
object AdBlockHelper {

    // ==================== 네트워크 차단 ====================

    private val AD_HOSTS = setOf(
        "googlesyndication.com",
        "doubleclick.net",
        "googleadservices.com",
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "www.googletagservices.com",
        "ad.doubleclick.net",
        "static.doubleclick.net",
        "s0.2mdn.net",
        "cdn.ampproject.org",
        "imasdk.googleapis.com",
    )

    private val AD_PATH_PATTERNS = listOf(
        "/pagead/",
        "/ptracking",
        "/api/stats/ads",
        "/get_midroll_info",
        "/ad_break",
        "/youtubei/v1/player/ad_break",
        "google_ads",
        "/pcs/activeview",
        "/generate_204",
        "/log_interaction",
    )

    /**
     * 광고 관련 네트워크 요청인지 확인
     */
    fun shouldBlockRequest(request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val host = request.url?.host ?: ""

        for (adHost in AD_HOSTS) {
            if (host.contains(adHost)) return true
        }

        for (pattern in AD_PATH_PATTERNS) {
            if (url.contains(pattern)) return true
        }

        return false
    }

    /**
     * 빈 응답 반환 (차단된 요청용)
     */
    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain", "UTF-8",
            "".byteInputStream()
        )
    }

    // ==================== InnerTube 광고 렌더러 ====================

    /**
     * InnerTube API 응답에서 필터링할 광고 렌더러 키
     */
    val AD_RENDERER_KEYS = setOf(
        "adSlotRenderer",
        "promotedSparklesWebRenderer",
        "promotedSparklesTextSearchRenderer",
        "promotedVideoRenderer",
        "searchPyvRenderer",
        "bannerPromoRenderer",
        "statementBannerRenderer",
        "brandVideoShelfRenderer",
        "brandVideoSingletonRenderer",
        "actionCompanionAdRenderer",
        "instreamVideoAdRenderer",
        "linearAdSequenceRenderer",
        "mastHeadRenderer",
        "primeMastHeadRenderer",
        "inFeedAdLayoutRenderer",
        "adIntroRenderer",
    )

    // ==================== CSS 광고 숨김 ====================

    /**
     * YouTube 광고 요소를 숨기는 CSS
     */
    val AD_HIDE_CSS = """
        /* 비디오 플레이어 광고 */
        .ytp-ad-module,
        .ytp-ad-overlay-container,
        .ytp-ad-overlay-slot,
        .ytp-ad-text-overlay,
        .ytp-ad-skip-button-container,
        .ytp-ad-player-overlay,
        .ytp-ad-player-overlay-layout,
        .ytp-ad-image-overlay,
        .video-ads,
        #player-ads,
        #masthead-ad,

        /* 모바일 광고 */
        ytm-promoted-sparkles-web-renderer,
        ytm-promoted-video-renderer,
        ytm-companion-ad-renderer,
        ytm-brand-video-shelf-renderer,
        ytm-brand-video-singleton-renderer,
        ytm-statement-banner-renderer,
        ytm-in-feed-ad-layout-renderer,
        ytm-ad-slot-renderer,

        /* 피드/검색 광고 */
        .ytd-promoted-sparkles-web-renderer,
        .ytd-promoted-video-renderer,
        .ytd-ad-slot-renderer,
        .ytd-banner-promo-renderer,
        .ytd-statement-banner-renderer,
        .ytd-in-feed-ad-layout-renderer,

        /* 일반 광고 컨테이너 */
        .ad-container,
        .ad-div,
        .masthead-ad-control,
        .sparkles-light-cta,
        [layout="companion-ad"],
        [target-id="companion-ad"],
        #merch-shelf,
        ytd-merch-shelf-renderer,

        /* 배너/프로모 */
        ytd-banner-promo-renderer-background,
        .ytd-rich-item-renderer[is-ad],
        .ytm-rich-item-renderer[is-ad] {
            display: none !important;
        }

        /* 광고 재생 중 비디오 숨기고 빠르게 스킵 유도 */
        .ad-showing .html5-video-container {
            height: 0 !important;
            overflow: hidden !important;
        }
        .ad-showing .ytp-ad-text {
            display: none !important;
        }
    """.trimIndent()

    // ==================== JS 광고 자동 스킵 ====================

    /**
     * 광고 자동 스킵 + 오버레이 닫기 + 광고 빠르게 넘기기
     */
    val AD_SKIP_JS = """
        (function() {
            if (window._ytAdBlocker) return;
            window._ytAdBlocker = true;

            function skipAd() {
                // 1. 스킵 버튼 클릭
                var skipBtns = document.querySelectorAll(
                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, ' +
                    '.ytp-skip-ad-button, .ytp-ad-skip-button-slot, ' +
                    'button[class*="skip-button"], ' +
                    '.ytp-ad-skip-button-container button'
                );
                for (var i = 0; i < skipBtns.length; i++) {
                    try { skipBtns[i].click(); } catch(e) {}
                }

                // 2. 오버레이 광고 닫기
                var closeBtns = document.querySelectorAll(
                    '.ytp-ad-overlay-close-button, ' +
                    '.ytp-ad-overlay-close-container button, ' +
                    '[class*="ad-close"], .ytp-ad-button-icon'
                );
                for (var i = 0; i < closeBtns.length; i++) {
                    try { closeBtns[i].click(); } catch(e) {}
                }

                // 3. 광고 재생 중이면 빠르게 넘기기
                var adShowing = document.querySelector('.ad-showing');
                if (adShowing) {
                    var video = document.querySelector('video');
                    if (video) {
                        if (video.duration && isFinite(video.duration) && video.duration > 0) {
                            video.currentTime = video.duration;
                        }
                        video.playbackRate = 16;
                    }
                }
            }

            // 500ms마다 체크
            setInterval(skipAd, 500);

            // DOM 변경 감지 (광고 동적 삽입 대응)
            var target = document.body || document.documentElement;
            if (target) {
                var observer = new MutationObserver(function() { skipAd(); });
                observer.observe(target, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['class']
                });
            }
        })();
    """.trimIndent()
}
