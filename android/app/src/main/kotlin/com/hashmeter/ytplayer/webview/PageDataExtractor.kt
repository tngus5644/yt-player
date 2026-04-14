package com.hashmeter.ytplayer.webview

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView

/**
 * JS injection, ytInitialData 파싱, 구독 목록 추출 담당
 */
class PageDataExtractor {

    companion object {
        private const val TAG = "YTPlayerWebView"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * YouTube 페이지에서 영상 데이터 추출 (DOM + ytInitialData + continuation)
     */
    fun extractVideoData(webView: WebView?, onNoData: () -> Unit) {
        val script = buildExtractionScript()

        mainHandler.post {
            webView?.evaluateJavascript(script) { result ->
                Log.d(TAG, "추출 결과: $result")
            }
        }
    }

    /**
     * Consent 페이지 자동 처리
     */
    fun handleConsentPage(webView: WebView?) {
        Log.d(TAG, "동의 페이지 감지 → 자동 수락 시도")
        val script = """
            (function() {
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

    /**
     * 인라인 동의 다이얼로그 확인 후 데이터 추출
     */
    fun checkAndHandleConsentThenExtract(webView: WebView?, dataReceived: Boolean, onExtract: () -> Unit) {
        val consentScript = """
            (function() {
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
                if (!dataReceived) {
                    onExtract()
                }
            }
        }
    }

    /**
     * 구독 채널 목록 추출
     */
    fun extractSubscriptions(webView: WebView?) {
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

    /**
     * 스크롤 후 추가 영상 추출
     */
    fun extractMoreVideos(webView: WebView?) {
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

    private fun buildExtractionScript(): String {
        // 기존 extractVideoData의 대규모 JS 스크립트 (변경 없이 유지)
        return """
            (function() {
                try {
                    var videos = [];
                    var seenIds = {};
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
                            var parent = links[i].closest('ytm-rich-item-renderer, ytm-video-with-context-renderer, ytm-compact-video-renderer, ytm-reel-item-renderer') || links[i].parentElement;
                            var title = '';
                            var channel = '';
                            var duration = '';
                            if (parent) {
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
                        console.log('[YTPlayer] DOM에서 ' + videos.length + '개 추출');
                        YTPlayer.onVideoListReceived(JSON.stringify({ type: 'home', videoList: videos }));
                        return JSON.stringify({source: 'dom', count: videos.length});
                    }
                    var data = window.ytInitialData;
                    if (!data) {
                        return JSON.stringify({source: 'none', count: 0});
                    }
                    var contToken = null;
                    function findCont(obj, depth) {
                        if (!obj || typeof obj !== 'object' || depth > 15 || contToken) return;
                        if (obj.continuationItemRenderer) {
                            try { contToken = obj.continuationItemRenderer.continuationEndpoint.continuationCommand.token; } catch(e) {}
                        }
                        if (obj.continuations && Array.isArray(obj.continuations)) {
                            for (var i = 0; i < obj.continuations.length; i++) {
                                var c = obj.continuations[i];
                                if (c.nextContinuationData && c.nextContinuationData.continuation) contToken = c.nextContinuationData.continuation;
                                if (c.reloadContinuationData && c.reloadContinuationData.continuation) contToken = c.reloadContinuationData.continuation;
                            }
                        }
                        if (obj.token && typeof obj.token === 'string' && obj.token.length > 20) {
                            if (obj.request === 'CONTINUATION_REQUEST_TYPE_BROWSE' || (obj.continuation && typeof obj.continuation === 'string')) contToken = obj.token;
                        }
                        if (contToken) return;
                        if (Array.isArray(obj)) { for (var i = 0; i < obj.length; i++) findCont(obj[i], depth+1); }
                        else { var keys = Object.keys(obj); for (var i = 0; i < keys.length; i++) { if (obj[keys[i]] && typeof obj[keys[i]] === 'object') findCont(obj[keys[i]], depth+1); } }
                    }
                    findCont(data, 0);
                    if (!contToken) return JSON.stringify({source: 'no_continuation', count: 0});

                    var apiKey = '';
                    var clientVersion = '2.20260217.01.00';
                    if (typeof ytcfg !== 'undefined' && ytcfg.get) {
                        apiKey = ytcfg.get('INNERTUBE_API_KEY') || '';
                        clientVersion = ytcfg.get('INNERTUBE_CLIENT_VERSION') || clientVersion;
                    }
                    var url = 'https://m.youtube.com/youtubei/v1/browse';
                    if (apiKey) url += '?key=' + apiKey;

                    fetch(url, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json', 'X-YouTube-Client-Name': '2', 'X-YouTube-Client-Version': clientVersion },
                        body: JSON.stringify({ context: { client: { clientName: 'MWEB', clientVersion: clientVersion, hl: 'ko', gl: 'KR', platform: 'MOBILE', userAgent: navigator.userAgent } }, continuation: contToken }),
                        credentials: 'include'
                    })
                    .then(function(resp) { return resp.json(); })
                    .then(function(contData) {
                        var jsonStr = JSON.stringify(contData);
                        var videoIdRegex = /"videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"/g;
                        var match;
                        var allIds = [];
                        var seen = {};
                        while ((match = videoIdRegex.exec(jsonStr)) !== null) {
                            if (!seen[match[1]]) { seen[match[1]] = true; allIds.push(match[1]); }
                        }
                        if (allIds.length > 0) {
                            var metaMap = {};
                            function findMeta(obj, d) {
                                if (!obj || typeof obj !== 'object' || d > 25) return;
                                if (obj.videoId && typeof obj.videoId === 'string' && seen[obj.videoId]) {
                                    if (!metaMap[obj.videoId]) metaMap[obj.videoId] = {};
                                    var m = metaMap[obj.videoId];
                                    function exT(s) { if (!s) return ''; if (typeof s==='string') return s; if (s.simpleText) return s.simpleText; if (s.content&&typeof s.content==='string') return s.content; if (s.runs) return s.runs.map(function(r){return r.text||'';}).join(''); return ''; }
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
                                vids.push({ id: allIds[i], title: (meta.title || 'Video ' + allIds[i]).substring(0, 200), thumbnail: 'https://i.ytimg.com/vi/' + allIds[i] + '/hqdefault.jpg', channel: (meta.channel || '').substring(0, 100), duration: meta.duration || '', videoType: meta.isShorts ? 'SHORTS' : 'VIDEO' });
                            }
                            YTPlayer.onVideoListReceived(JSON.stringify({ type: 'home', videoList: vids }));
                        } else {
                            YTPlayer.onError('CONT_NO_VIDEOS');
                        }
                    })
                    .catch(function(err) { YTPlayer.onError('CONT_FETCH_ERROR: ' + err.message); });
                    return JSON.stringify({source: 'continuation', count: 0});
                } catch(e) {
                    return JSON.stringify({source: 'error', error: e.message, count: 0});
                }
            })();
        """.trimIndent()
    }
}
