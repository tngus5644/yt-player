package com.ytplayer.app.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import io.flutter.plugin.common.MethodChannel
import com.ytplayer.app.channel.DataEventChannel
import com.ytplayer.app.webview.bridges.VideoListBridge
import com.ytplayer.app.webview.bridges.AuthBridge
import com.ytplayer.app.PlayerActivity
import com.ytplayer.app.WebViewSignInActivity
import com.ytplayer.app.adblock.AdBlockHelper
import org.json.JSONObject
import org.json.JSONArray

/**
 * 숨겨진 WebView 관리자
 * YouTube 모바일 사이트를 로드하고, ytInitialData JSON을 파싱해서 영상 데이터를 추출
 *
 * 전략:
 * 1차) window.ytInitialData (YouTube가 페이지에 내장하는 JSON 데이터) 파싱
 * 2차) script 태그에서 ytInitialData JSON 추출
 * 3차) DOM 셀렉터 기반 스크래핑 (fallback)
 */
class WebViewManager(
    private val activity: Activity,
    private val dataEventChannel: DataEventChannel
) {

    companion object {
        private const val TAG = "YTPlayerWebView"
        private const val EXTRACT_INITIAL_DELAY_MS = 5000L
        private const val EXTRACT_RETRY_DELAY_MS = 5000L
        private const val EXTRACT_MAX_RETRIES = 3
        const val SIGN_IN_REQUEST_CODE = 2001
        internal const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        internal const val WEB_CLIENT_VERSION = "2.20260220.00.00"
        internal const val ANDROID_CLIENT_VERSION = "19.02.39"
    }

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences("ytplayer_prefs", Context.MODE_PRIVATE)
    private val apiClient = InnerTubeApiClient()

    private var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    private var signInResult: MethodChannel.Result? = null
    @Volatile private var retryCount = 0
    @Volatile private var dataReceived = false
    @Volatile private var consentHandled = false
    @Volatile private var triedTrending = false

    init {
        Log.d(TAG, "========= WebViewManager 생성됨 =========")
        mainHandler.post { initWebView() }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun initWebView() {
        Log.d(TAG, "initWebView 시작")

        val density = activity.resources.displayMetrics.density
        val widthPx = (360 * density).toInt()
        val heightPx = (800 * density).toInt()

        webView = WebView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Flutter 뷰 뒤(index 0)에 배치되므로 유저에게 안 보임
            // VISIBLE + 정상 위치 → YouTube JS의 IntersectionObserver 정상 작동
            visibility = View.VISIBLE

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                // 시스템 WebView 버전과 일치시킴 (Chrome/144.0.7559.132)
                userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S928N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.7559.132 Mobile Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            // 쿠키 허용
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            addJavascriptInterface(
                VideoListBridge(dataEventChannel),
                "YTPlayer"
            )
            addJavascriptInterface(
                AuthBridge(dataEventChannel) { token ->
                    accessToken = token
                    signInResult?.success(JSONObject().apply {
                        put("accessToken", token)
                    }.toString())
                    signInResult = null
                },
                "YTPlayerAuth"
            )

            webViewClient = createWebViewClient()

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        Log.d(TAG, "JS [${it.messageLevel()}] ${it.message()}")
                    }
                    return true
                }
            }
        }

        val rootView = activity.window.decorView as ViewGroup
        rootView.addView(webView, 0)
        Log.d(TAG, "WebView 추가됨 (MATCH_PARENT, Flutter 뒤 VISIBLE)")
    }

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "▶ 로딩 시작: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "✓ 로딩 완료: $url")
                url ?: return

                // consent.youtube.com이면 동의 자동 처리
                if (url.contains("consent.youtube.com") || url.contains("consent.google.com")) {
                    handleConsentPage()
                    return
                }

                // YouTube 페이지 처리
                when {
                    url.contains("m.youtube.com") && !url.contains("/shorts/") && !url.contains("/feed/subscriptions") -> {
                        // API가 이미 데이터를 가져왔으면 WebView 추출 불필요
                        if (dataReceived) {
                            Log.d(TAG, "API 데이터 이미 수신됨 → WebView 추출 스킵")
                            return
                        }

                        retryCount = 0

                        // 페이지 로드 후 스크롤 트리거 (lazy loading 강제)
                        mainHandler.postDelayed({
                            webView?.evaluateJavascript(
                                "window.scrollTo(0, 500); setTimeout(function(){ window.scrollTo(0, 0); }, 500);",
                                null
                            )
                        }, 2000)

                        // 먼저 동의 다이얼로그 체크 후 데이터 추출
                        mainHandler.postDelayed({
                            checkAndHandleConsentThenExtract()
                        }, EXTRACT_INITIAL_DELAY_MS)
                    }
                    url.contains("m.youtube.com/feed/subscriptions") -> {
                        retryCount = 0
                        mainHandler.postDelayed({
                            extractSubscriptions()
                        }, EXTRACT_INITIAL_DELAY_MS)
                    }
                }
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

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                Log.d(TAG, "네비게이션: $url")
                return false
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    Log.e(TAG, "에러: ${error?.errorCode} - ${error?.description} (${request.url})")
                }
            }
        }
    }

    // ==================== Consent 처리 ====================

    private fun handleConsentPage() {
        Log.d(TAG, "동의 페이지 감지 → 자동 수락 시도")
        val script = """
            (function() {
                // "Accept all" / "모두 동의" 버튼 찾기
                var buttons = document.querySelectorAll('button');
                for (var i = 0; i < buttons.length; i++) {
                    var text = buttons[i].textContent.toLowerCase();
                    if (text.indexOf('accept') !== -1 || text.indexOf('agree') !== -1 ||
                        text.indexOf('동의') !== -1 || text.indexOf('수락') !== -1 ||
                        text.indexOf('continue') !== -1) {
                        console.log('[YTPlayer] 동의 버튼 클릭: ' + buttons[i].textContent);
                        buttons[i].click();
                        return 'clicked';
                    }
                }
                // form submit 방식
                var forms = document.querySelectorAll('form[action*="consent"]');
                if (forms.length > 0) {
                    console.log('[YTPlayer] 동의 폼 서브밋');
                    forms[0].submit();
                    return 'submitted';
                }
                return 'not_found';
            })();
        """.trimIndent()

        mainHandler.post {
            webView?.evaluateJavascript(script) { result ->
                Log.d(TAG, "동의 처리 결과: $result")
            }
        }
    }

    private fun checkAndHandleConsentThenExtract() {
        // 인라인 동의 다이얼로그 확인 (YouTube 페이지 내 동의 팝업)
        val consentScript = """
            (function() {
                // YouTube 인라인 동의 다이얼로그 확인
                var consentDialog = document.querySelector('tp-yt-paper-dialog, ytd-consent-bump-v2-lightbox, [class*="consent"]');
                if (consentDialog) {
                    var btn = consentDialog.querySelector('button[aria-label*="Accept"], button[aria-label*="agree"], [class*="accept"], [class*="agree"]');
                    if (btn) {
                        console.log('[YTPlayer] 인라인 동의 버튼 클릭');
                        btn.click();
                        return 'consent_clicked';
                    }
                }
                return 'no_consent';
            })();
        """.trimIndent()

        mainHandler.post {
            webView?.evaluateJavascript(consentScript) { result ->
                Log.d(TAG, "인라인 동의 확인: $result")
                // 동의 처리 후 데이터 추출 시작
                if (!dataReceived) {
                    extractVideoData()
                }
            }
        }
    }

    // ==================== 데이터 추출 ====================

    /**
     * YouTube 페이지에서 영상 데이터 추출
     *
     * YouTube 최신 아키텍처:
     * 1) 초기 페이지 로드 → 셸(검색바, 구조)만 포함, 영상 데이터 없음
     * 2) richGridRenderer.contents에 continuation 토큰만 있음
     * 3) continuation 토큰으로 browse API를 호출해야 실제 영상 데이터 획득
     *
     * 전략:
     * 1차) DOM에서 이미 렌더된 비디오 링크 추출 (WebView VISIBLE이므로 가능할 수 있음)
     * 2차) ytInitialData에서 continuation 토큰 추출 → browse API 호출
     * 3차) DOM 재시도 (YouTube JS가 콘텐츠를 로드할 시간 부여)
     */
    private fun extractVideoData() {
        if (dataReceived) return

        val script = """
            (function() {
                try {
                    // ====== 1차: DOM에서 비디오 링크 추출 ======
                    var videos = [];
                    var seenIds = {};

                    // YouTube 모바일 페이지의 비디오 링크들
                    var links = document.querySelectorAll('a[href*="/watch?v="], a[href*="/shorts/"]');
                    console.log('[YTPlayer] DOM 비디오 링크 수: ' + links.length);

                    for (var i = 0; i < links.length && videos.length < 50; i++) {
                        var href = links[i].getAttribute('href') || '';
                        var vid = null;
                        var isShorts = false;

                        var watchMatch = href.match(/\/watch\?v=([a-zA-Z0-9_-]{11})/);
                        var shortsMatch = href.match(/\/shorts\/([a-zA-Z0-9_-]{11})/);

                        if (watchMatch) vid = watchMatch[1];
                        else if (shortsMatch) { vid = shortsMatch[1]; isShorts = true; }

                        if (vid && !seenIds[vid]) {
                            seenIds[vid] = true;
                            // 근처 요소에서 제목/채널 추출 시도
                            var parent = links[i].closest('ytm-rich-item-renderer, ytm-video-with-context-renderer, ytm-compact-video-renderer, ytm-reel-item-renderer') || links[i].parentElement;
                            var title = '';
                            var channel = '';
                            var duration = '';

                            if (parent) {
                                // aria-label이 가장 풍부한 정보 포함
                                var ariaEl = parent.querySelector('[aria-label]');
                                if (ariaEl) {
                                    var label = ariaEl.getAttribute('aria-label') || '';
                                    if (label.length > 5) title = label.split(' 게시자:')[0].split(' by ')[0];
                                }
                                if (!title) {
                                    var titleEl = parent.querySelector('h3, .media-item-headline, [class*="title"], .compact-media-item-headline');
                                    if (titleEl) title = titleEl.textContent.trim();
                                }
                                var channelEl = parent.querySelector('.ytm-badge-and-byline-renderer, [class*="byline"], [class*="channel"]');
                                if (channelEl) channel = channelEl.textContent.trim().split('·')[0].trim();

                                var durEl = parent.querySelector('.badge-shape-wiz__text, [class*="duration"], [class*="time-status"]');
                                if (durEl) duration = durEl.textContent.trim();
                            }

                            videos.push({
                                id: vid,
                                title: (title || 'Video ' + vid).substring(0, 200),
                                thumbnail: 'https://i.ytimg.com/vi/' + vid + '/hqdefault.jpg',
                                channel: (channel || '').substring(0, 100),
                                duration: duration,
                                videoType: isShorts ? 'SHORTS' : 'VIDEO'
                            });
                        }
                    }

                    if (videos.length > 0) {
                        console.log('[YTPlayer] ★ DOM에서 ' + videos.length + '개 추출! 첫번째: ' + videos[0].title);
                        YTPlayer.onVideoListReceived(JSON.stringify({ type: 'home', videoList: videos }));
                        return JSON.stringify({source: 'dom', count: videos.length});
                    }

                    // ====== 2차: ytInitialData 진단 + continuation 토큰 추출 ======
                    var data = window.ytInitialData;
                    if (!data) {
                        console.log('[YTPlayer] ytInitialData 없음');
                        // HTML에서 videoId 확인
                        var html = document.documentElement.innerHTML;
                        console.log('[YTPlayer] HTML 길이: ' + html.length);
                        var watchCount = (html.match(/watch\?v=/g) || []).length;
                        console.log('[YTPlayer] HTML watch?v= 개수: ' + watchCount);
                        return JSON.stringify({source: 'none', count: 0, htmlLen: html.length, watchLinks: watchCount});
                    }

                    // ytInitialData 구조 진단
                    try {
                        var str = JSON.stringify(data);
                        console.log('[YTPlayer] ytInitialData JSON 길이: ' + str.length);
                        var vidCount = (str.match(/"videoId"/g) || []).length;
                        console.log('[YTPlayer] ytInitialData "videoId" 출현 횟수: ' + vidCount);
                        // "token" 출현 횟수
                        var tokCount = (str.match(/"token"/g) || []).length;
                        console.log('[YTPlayer] ytInitialData "token" 출현 횟수: ' + tokCount);

                        // richGridRenderer.contents 직접 접근
                        var rgr = data.contents && data.contents.singleColumnBrowseResultsRenderer &&
                                  data.contents.singleColumnBrowseResultsRenderer.tabs &&
                                  data.contents.singleColumnBrowseResultsRenderer.tabs[0] &&
                                  data.contents.singleColumnBrowseResultsRenderer.tabs[0].tabRenderer &&
                                  data.contents.singleColumnBrowseResultsRenderer.tabs[0].tabRenderer.content &&
                                  data.contents.singleColumnBrowseResultsRenderer.tabs[0].tabRenderer.content.richGridRenderer;
                        if (rgr) {
                            console.log('[YTPlayer] richGridRenderer.contents 길이: ' + (rgr.contents ? rgr.contents.length : 'null'));
                            if (rgr.contents) {
                                for (var ci = 0; ci < rgr.contents.length; ci++) {
                                    var itemKeys = Object.keys(rgr.contents[ci]);
                                    console.log('[YTPlayer] richGridRenderer.contents[' + ci + '] 키: ' + itemKeys.join(', '));
                                    // richSectionRenderer 내용 덤프 (로그인 프롬프트인지 확인)
                                    if (rgr.contents[ci].richSectionRenderer) {
                                        var rsr = rgr.contents[ci].richSectionRenderer;
                                        var rsrJson = JSON.stringify(rsr);
                                        console.log('[YTPlayer] richSectionRenderer[' + ci + '] JSON 길이: ' + rsrJson.length);
                                        console.log('[YTPlayer] richSectionRenderer[' + ci + '] 키: ' + Object.keys(rsr).join(', '));
                                        // 내용 앞부분 500자 덤프
                                        console.log('[YTPlayer] richSectionRenderer[' + ci + '] 샘플: ' + rsrJson.substring(0, 500));
                                        // 중간 부분도 확인 (로그인 프롬프트 키워드 탐색)
                                        if (rsrJson.indexOf('signIn') !== -1 || rsrJson.indexOf('login') !== -1 || rsrJson.indexOf('Sign in') !== -1) {
                                            console.log('[YTPlayer] ★ richSectionRenderer[' + ci + '] 에 로그인 관련 키워드 발견!');
                                        }
                                        if (rsrJson.indexOf('videoId') !== -1) {
                                            console.log('[YTPlayer] ★ richSectionRenderer[' + ci + '] 에 videoId 발견!');
                                        }
                                    }
                                    // continuationItemRenderer가 있다면 내부 구조 덤프
                                    if (rgr.contents[ci].continuationItemRenderer) {
                                        var cir = rgr.contents[ci].continuationItemRenderer;
                                        console.log('[YTPlayer] continuationItemRenderer 발견! 키: ' + Object.keys(cir).join(', '));
                                        console.log('[YTPlayer] continuationItemRenderer JSON: ' + JSON.stringify(cir).substring(0, 500));
                                    }
                                }
                            }
                        } else {
                            console.log('[YTPlayer] richGridRenderer 없음. contents 키: ' + (data.contents ? Object.keys(data.contents).join(', ') : 'null'));
                        }
                    } catch(e) {
                        console.log('[YTPlayer] 진단 에러: ' + e.message);
                    }

                    // continuation 토큰 찾기
                    var contToken = null;
                    function findCont(obj, depth) {
                        if (!obj || typeof obj !== 'object' || depth > 15 || contToken) return;
                        // continuationItemRenderer 내부의 토큰
                        if (obj.continuationItemRenderer) {
                            try {
                                contToken = obj.continuationItemRenderer.continuationEndpoint.continuationCommand.token;
                                console.log('[YTPlayer] continuation 토큰 발견 (continuationItemRenderer)');
                            } catch(e) {}
                        }
                        // 또는 직접 continuation 배열
                        if (obj.continuations && Array.isArray(obj.continuations)) {
                            for (var i = 0; i < obj.continuations.length; i++) {
                                var c = obj.continuations[i];
                                if (c.nextContinuationData && c.nextContinuationData.continuation) {
                                    contToken = c.nextContinuationData.continuation;
                                    console.log('[YTPlayer] continuation 토큰 발견 (nextContinuationData)');
                                }
                                if (c.reloadContinuationData && c.reloadContinuationData.continuation) {
                                    contToken = c.reloadContinuationData.continuation;
                                    console.log('[YTPlayer] continuation 토큰 발견 (reloadContinuationData)');
                                }
                            }
                        }
                        // browseEndpoint의 continuation
                        if (obj.token && typeof obj.token === 'string' && obj.token.length > 20) {
                            if (obj.request === 'CONTINUATION_REQUEST_TYPE_BROWSE' ||
                                (obj.continuation && typeof obj.continuation === 'string')) {
                                contToken = obj.token;
                                console.log('[YTPlayer] continuation 토큰 발견 (token 직접)');
                            }
                        }
                        if (contToken) return;
                        if (Array.isArray(obj)) {
                            for (var i = 0; i < obj.length; i++) findCont(obj[i], depth+1);
                        } else {
                            var keys = Object.keys(obj);
                            for (var i = 0; i < keys.length; i++) {
                                if (obj[keys[i]] && typeof obj[keys[i]] === 'object') findCont(obj[keys[i]], depth+1);
                            }
                        }
                    }
                    findCont(data, 0);

                    if (!contToken) {
                        console.log('[YTPlayer] continuation 토큰 없음!');
                        return JSON.stringify({source: 'no_continuation', count: 0});
                    }

                    console.log('[YTPlayer] continuation 토큰: ' + contToken.substring(0, 60) + '...');

                    // ====== 3차: continuation 토큰으로 browse API 호출 ======
                    var apiKey = '';
                    var clientVersion = '2.20260217.01.00';
                    if (typeof ytcfg !== 'undefined' && ytcfg.get) {
                        apiKey = ytcfg.get('INNERTUBE_API_KEY') || '';
                        clientVersion = ytcfg.get('INNERTUBE_CLIENT_VERSION') || clientVersion;
                    }

                    var url = 'https://m.youtube.com/youtubei/v1/browse';
                    if (apiKey) url += '?key=' + apiKey;

                    console.log('[YTPlayer-CONT] continuation API 호출 시작');

                    fetch(url, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'X-YouTube-Client-Name': '2',
                            'X-YouTube-Client-Version': clientVersion
                        },
                        body: JSON.stringify({
                            context: {
                                client: {
                                    clientName: 'MWEB',
                                    clientVersion: clientVersion,
                                    hl: 'ko',
                                    gl: 'KR',
                                    platform: 'MOBILE',
                                    userAgent: navigator.userAgent
                                }
                            },
                            continuation: contToken
                        }),
                        credentials: 'include'
                    })
                    .then(function(resp) {
                        console.log('[YTPlayer-CONT] 응답 상태: ' + resp.status);
                        return resp.json();
                    })
                    .then(function(contData) {
                        console.log('[YTPlayer-CONT] 응답 키: ' + Object.keys(contData).join(', '));

                        // JSON stringify + regex로 videoId 추출
                        var jsonStr = JSON.stringify(contData);
                        var videoIdRegex = /"videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"/g;
                        var match;
                        var allIds = [];
                        var seen = {};
                        while ((match = videoIdRegex.exec(jsonStr)) !== null) {
                            if (!seen[match[1]]) { seen[match[1]] = true; allIds.push(match[1]); }
                        }
                        console.log('[YTPlayer-CONT] ★ videoId 개수: ' + allIds.length);

                        if (allIds.length > 0) {
                            // 메타데이터 수집
                            var metaMap = {};
                            function findMeta(obj, d) {
                                if (!obj || typeof obj !== 'object' || d > 25) return;
                                if (obj.videoId && typeof obj.videoId === 'string' && seen[obj.videoId]) {
                                    if (!metaMap[obj.videoId]) metaMap[obj.videoId] = {};
                                    var m = metaMap[obj.videoId];
                                    function exT(s) {
                                        if (!s) return '';
                                        if (typeof s === 'string') return s;
                                        if (s.simpleText) return s.simpleText;
                                        if (s.content && typeof s.content === 'string') return s.content;
                                        if (s.runs) return s.runs.map(function(r){return r.text||'';}).join('');
                                        if (s.accessibility && s.accessibility.accessibilityData) return s.accessibility.accessibilityData.label || '';
                                        return '';
                                    }
                                    var t = exT(obj.title || obj.headline || obj.text);
                                    if (t && (!m.title || t.length > m.title.length)) m.title = t;
                                    var c = exT(obj.longBylineText || obj.shortBylineText || obj.ownerText || obj.subtitle);
                                    if (c && (!m.channel || c.length > m.channel.length)) m.channel = c;
                                    var dur = exT(obj.lengthText || obj.timestampText);
                                    if (dur) m.duration = dur;
                                    if (obj.navigationEndpoint && obj.navigationEndpoint.reelWatchEndpoint) m.isShorts = true;
                                }
                                if (Array.isArray(obj)) { for (var i=0;i<Math.min(obj.length,300);i++) findMeta(obj[i],d+1); }
                                else { var ks=Object.keys(obj); for (var i=0;i<ks.length;i++) { if(obj[ks[i]]&&typeof obj[ks[i]]==='object') findMeta(obj[ks[i]],d+1); } }
                            }
                            findMeta(contData, 0);

                            var vids = [];
                            for (var i = 0; i < allIds.length && vids.length < 50; i++) {
                                var meta = metaMap[allIds[i]] || {};
                                vids.push({
                                    id: allIds[i],
                                    title: (meta.title || 'Video ' + allIds[i]).substring(0, 200),
                                    thumbnail: 'https://i.ytimg.com/vi/' + allIds[i] + '/hqdefault.jpg',
                                    channel: (meta.channel || '').substring(0, 100),
                                    duration: meta.duration || '',
                                    videoType: meta.isShorts ? 'SHORTS' : 'VIDEO'
                                });
                            }
                            console.log('[YTPlayer-CONT] ★★★ 최종 추출: ' + vids.length + '개, 첫 영상: ' + vids[0].title);
                            YTPlayer.onVideoListReceived(JSON.stringify({ type: 'home', videoList: vids }));
                        } else {
                            // 구조 덤프
                            var contKeys = Object.keys(contData);
                            contKeys.forEach(function(k) {
                                if (contData[k] && typeof contData[k] === 'object') {
                                    var info = Array.isArray(contData[k]) ? '[array:'+contData[k].length+']' : '{'+Object.keys(contData[k]).slice(0,6).join(',')+'}';
                                    console.log('[YTPlayer-CONT] ' + k + ': ' + info);
                                }
                            });
                            console.log('[YTPlayer-CONT] JSON 길이: ' + jsonStr.length + '자');
                            console.log('[YTPlayer-CONT] JSON 샘플: ' + jsonStr.substring(0, 500));
                            YTPlayer.onError('CONT_NO_VIDEOS');
                        }
                    })
                    .catch(function(err) {
                        console.log('[YTPlayer-CONT] fetch 에러: ' + err.message);
                        YTPlayer.onError('CONT_FETCH_ERROR: ' + err.message);
                    });

                    return JSON.stringify({source: 'continuation', count: 0, token: contToken.substring(0, 30)});
                } catch(e) {
                    console.log('[YTPlayer] 추출 에러: ' + e.message);
                    return JSON.stringify({source: 'error', error: e.message, count: 0});
                }
            })();
        """.trimIndent()

        mainHandler.post {
            webView?.evaluateJavascript(script) { result ->
                Log.d(TAG, "추출 결과: $result")

                // DOM이나 continuation에서 비동기 처리 대기
                mainHandler.postDelayed({
                    if (!dataReceived) {
                        retryCount++
                        if (retryCount < EXTRACT_MAX_RETRIES) {
                            Log.d(TAG, "재시도 #$retryCount (DOM + continuation)")
                            extractVideoData()
                        } else if (!triedTrending) {
                            // 홈 피드 실패 → 트렌딩 페이지로 폴백
                            Log.w(TAG, "★ 홈 피드 실패! → /feed/trending 폴백 시도")
                            triedTrending = true
                            retryCount = 0
                            dataReceived = false
                            mainHandler.post {
                                webView?.loadUrl("https://m.youtube.com/feed/trending")
                            }
                        } else {
                            Log.w(TAG, "★ WebView 추출 모두 실패! → 직접 InnerTube API 호출")
                            fetchVideosDirectApi()
                        }
                    }
                }, 5000)
            }
        }
    }

    // ==================== 구독 추출 ====================

    private fun extractSubscriptions() {
        val script = """
            (function() {
                try {
                    var channels = [];
                    var data = window.ytInitialData;
                    if (!data) return 0;

                    function extractText(obj) {
                        if (!obj) return '';
                        if (typeof obj === 'string') return obj;
                        if (obj.simpleText) return obj.simpleText;
                        if (obj.runs) return obj.runs.map(function(r) { return r.text || ''; }).join('');
                        return '';
                    }

                    function walk(obj, d) {
                        if (!obj || typeof obj !== 'object' || d > 12) return;
                        if (obj.gridChannelRenderer || obj.channelRenderer || obj.compactChannelRenderer) {
                            var r = obj.gridChannelRenderer || obj.channelRenderer || obj.compactChannelRenderer;
                            channels.push({
                                id: r.channelId || '',
                                title: extractText(r.title),
                                thumbnail: (r.thumbnail && r.thumbnail.thumbnails && r.thumbnail.thumbnails.length > 0) ? r.thumbnail.thumbnails[r.thumbnail.thumbnails.length - 1].url : '',
                                handle: extractText(r.subscriberCountText),
                                isLive: false,
                                hasNew: false
                            });
                        }
                        if (Array.isArray(obj)) { for (var i=0;i<obj.length;i++) walk(obj[i],d+1); }
                        else { var keys = Object.keys(obj); for (var i=0;i<keys.length;i++) { var v=obj[keys[i]]; if(v&&typeof v==='object') walk(v,d+1); } }
                    }

                    walk(data, 0);
                    console.log('[YTPlayer] 구독 채널: ' + channels.length);
                    if (channels.length > 0) {
                        YTPlayer.onSubscriptionListReceived(JSON.stringify({ channelList: channels }));
                    }
                    return channels.length;
                } catch(e) {
                    console.log('[YTPlayer] 구독 추출 에러: ' + e.message);
                    return -1;
                }
            })();
        """.trimIndent()

        mainHandler.post {
            webView?.evaluateJavascript(script) { result ->
                Log.d(TAG, "구독 추출: $result")
            }
        }
    }

    // ==================== Public Methods ====================

    fun loadHomeFeed() {
        Log.d(TAG, "★★★ loadHomeFeed() 호출됨 ★★★")
        dataReceived = false
        retryCount = 0
        triedTrending = false

        // 직접 InnerTube API 호출 (최우선 — 즉시 시작, WebView 대기 불필요)
        fetchVideosDirectApi()

        // WebView도 병렬 로드 (로그인 유저용 — dataReceived가 true면 추출 스킵됨)
        mainHandler.post {
            webView?.loadUrl("https://m.youtube.com/")
        }
    }

    fun search(query: String) {
        fetchSearchApi(query)
    }

    fun loadSubscriptions() {
        mainHandler.post {
            webView?.loadUrl("https://m.youtube.com/feed/subscriptions")
        }
    }

    fun loadShorts() {
        fetchShortsApi()
    }

    fun loadProfile() {
        mainHandler.post {
            webView?.loadUrl("https://m.youtube.com/feed/library")
        }
    }

    fun loadDashboard() {
        mainHandler.post {
            webView?.loadUrl("https://youplayer.co.kr/app_dashboard")
        }
    }

    fun playVideo(url: String) {
        val intent = Intent(activity, PlayerActivity::class.java).apply {
            putExtra("video_url", url)
        }
        activity.startActivity(intent)
    }

    fun startSignIn(result: MethodChannel.Result) {
        // 소셜 로그인 전용 Activity 열기 (User-Agent에서 ; wv 제거)
        val intent = Intent(activity, WebViewSignInActivity::class.java)
        @Suppress("DEPRECATION")
        activity.startActivityForResult(intent, SIGN_IN_REQUEST_CODE)
        result.success(null)
    }

    /**
     * WebViewSignInActivity 결과 처리
     */
    fun onSignInResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            val data = JSONObject().apply {
                put("isLogin", true)
            }
            dataEventChannel.sendEvent("loginState", data)
        }
    }

    fun signOut() {
        accessToken = null
        CookieManager.getInstance().removeAllCookies(null)
        mainHandler.post {
            webView?.clearCache(true)
        }
        val data = JSONObject().apply {
            put("isLogin", false)
        }
        dataEventChannel.sendEvent("loginState", data)
    }

    fun isLoggedIn(): Boolean {
        // 쿠키 기반 로그인 확인
        val cookies = CookieManager.getInstance().getCookie("https://m.youtube.com") ?: ""
        return cookies.contains("SID=") || cookies.contains("SSID=") || !accessToken.isNullOrEmpty()
    }

    fun scrollBottom() {
        mainHandler.post {
            webView?.evaluateJavascript(
                "window.scrollTo(0, document.body.scrollHeight);",
                null
            )
            // 스크롤 후 새 콘텐츠 로드 대기 → 추가 데이터 추출
            mainHandler.postDelayed({
                extractMoreVideos()
            }, 2500)
        }
    }

    private fun extractMoreVideos() {
        // 스크롤 후 새로 렌더된 videoId를 HTML에서 추출
        val script = """
            (function() {
                var videos = [];
                var html = document.documentElement.innerHTML;
                var pattern = /"videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"/g;
                var seenIds = {};
                var match;
                while ((match = pattern.exec(html)) !== null && videos.length < 50) {
                    var vid = match[1];
                    if (!seenIds[vid]) {
                        seenIds[vid] = true;
                        videos.push({ id: vid, title: vid, thumbnail: 'https://i.ytimg.com/vi/'+vid+'/hqdefault.jpg', channel: '', duration: '', videoType: 'VIDEO' });
                    }
                }
                if (videos.length > 0) {
                    YTPlayer.onVideoListMoreReceived(JSON.stringify({ type: 'home', videoList: videos }));
                }
                return videos.length;
            })();
        """.trimIndent()

        mainHandler.post {
            webView?.evaluateJavascript(script) { result ->
                Log.d(TAG, "추가 영상 추출: $result")
            }
        }
    }

    // ==================== InnerTube API 위임 ====================

    private fun fetchVideosDirectApi() {
        if (dataReceived) return
        Log.d(TAG, "★ 직접 InnerTube API 호출 시작")

        Thread {
            try {
                // 1차: 트렌딩
                var videos = apiClient.fetchTrending()

                if (videos.length() > 0) {
                    Log.d(TAG, "★★★ API 트렌딩 성공: ${videos.length()}개")
                    dataReceived = true
                    sendVideoEvent("videoList", "home", videos)
                    return@Thread
                }

                if (dataReceived) return@Thread
                Log.d(TAG, "API 트렌딩 0개 → 검색으로 폴백")

                // 2차: 검색
                videos = apiClient.fetchSearch("인기 동영상")

                if (videos.length() > 0) {
                    Log.d(TAG, "★★★ API 검색 성공: ${videos.length()}개")
                    dataReceived = true
                    sendVideoEvent("videoList", "home", videos)
                    return@Thread
                }

                Log.w(TAG, "★ 모든 직접 API 호출도 실패!")
                sendVideoEvent("videoList", "home", JSONArray())
            } catch (e: Exception) {
                Log.e(TAG, "fetchVideosDirectApi 에러: ${e.message}", e)
                sendVideoEvent("videoList", "home", JSONArray())
            }
        }.start()
    }

    private fun fetchSearchApi(query: String) {
        Log.d(TAG, "★ 검색 API 호출: $query")

        Thread {
            try {
                val videos = apiClient.fetchSearch(query)
                Log.d(TAG, "★★★ 검색 API 성공: ${videos.length()}개")
                mainHandler.post {
                    dataEventChannel.sendEvent("searchResults", JSONObject().apply {
                        put("videoList", videos)
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchSearchApi 에러: ${e.message}", e)
                mainHandler.post {
                    dataEventChannel.sendEvent("searchResults", JSONObject().apply {
                        put("videoList", JSONArray())
                    })
                }
            }
        }.start()
    }

    private fun fetchShortsApi() {
        Log.d(TAG, "★ 쇼츠 API 호출 시작")

        Thread {
            try {
                val shortsArray = apiClient.fetchShorts()
                Log.d(TAG, "★★★ 쇼츠 API 성공: ${shortsArray.length()}개")
                mainHandler.post {
                    dataEventChannel.sendEvent("shortsList", JSONObject().apply {
                        put("videoList", shortsArray)
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchShortsApi 에러: ${e.message}", e)
                mainHandler.post {
                    dataEventChannel.sendEvent("shortsList", JSONObject().apply {
                        put("videoList", JSONArray())
                    })
                }
            }
        }.start()
    }

    private fun sendVideoEvent(eventType: String, type: String, videos: JSONArray) {
        mainHandler.post {
            dataEventChannel.sendEvent(eventType, JSONObject().apply {
                put("type", type)
                put("videoList", videos)
            })
        }
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            webView?.let { wv ->
                wv.stopLoading()
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
            webView = null
        }
    }
}
