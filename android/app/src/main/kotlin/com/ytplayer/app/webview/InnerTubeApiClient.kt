package com.ytplayer.app.webview

import android.util.Log
import com.ytplayer.app.adblock.AdBlockHelper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * YouTube InnerTube API 클라이언트
 * WebViewManager에서 분리된 API 호출 및 비디오 파싱 로직
 */
class InnerTubeApiClient {

    companion object {
        private const val TAG = "InnerTubeApi"
        private const val BASE_URL = "https://www.youtube.com/youtubei/v1"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"
        private const val MAX_VIDEOS = 30
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 15000
    }

    data class VideoMeta(
        val id: String,
        val title: String = "",
        val channel: String = "",
        val channelThumbnail: String = "",
        val thumbnail: String = "",
        val duration: String = "",
        val viewCount: String = "",
        val publishedAt: String = "",
        val videoType: String = "VIDEO"
    )

    // ==================== Public API ====================

    /**
     * 트렌딩 영상 가져오기 (ANDROID 클라이언트)
     */
    fun fetchTrending(): JSONArray {
        val body = buildRequestBody("ANDROID", WebViewManager.ANDROID_CLIENT_VERSION) {
            put("androidSdkVersion", 30)
        }.apply {
            put("browseId", "FEtrending")
        }

        return callApi("$BASE_URL/browse?key=${WebViewManager.INNERTUBE_API_KEY}", body)
    }

    /**
     * 검색 API 호출 (WEB 클라이언트)
     */
    fun fetchSearch(query: String): JSONArray {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("query", query)
        }

        return callApi("$BASE_URL/search?key=${WebViewManager.INNERTUBE_API_KEY}", body)
    }

    /**
     * 쇼츠 검색 API 호출
     */
    fun fetchShorts(): JSONArray {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("query", "인기 쇼츠")
            put("params", "EgIYAQ==")
        }

        val videos = callApi("$BASE_URL/search?key=${WebViewManager.INNERTUBE_API_KEY}", body)

        // 쇼츠용으로 videoType 설정
        val shortsArray = JSONArray()
        for (i in 0 until videos.length()) {
            val video = videos.getJSONObject(i)
            video.put("videoType", "SHORTS")
            shortsArray.put(video)
        }
        return shortsArray
    }

    // ==================== Request Builder ====================

    private fun buildRequestBody(
        clientName: String,
        clientVersion: String,
        extraClientProps: JSONObject.() -> Unit = {}
    ): JSONObject {
        return JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", clientName)
                    put("clientVersion", clientVersion)
                    put("hl", "ko")
                    put("gl", "KR")
                    extraClientProps()
                })
            })
        }
    }

    // ==================== API Call ====================

    private fun callApi(urlStr: String, body: JSONObject): JSONArray {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
        }

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            Log.d(TAG, "응답 코드: $code, URL: ${urlStr.substringAfterLast("/")}")

            if (code != 200) {
                val errorBody = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                Log.w(TAG, "에러 응답: ${errorBody.take(300)}")
                return JSONArray()
            }

            val response = conn.inputStream.bufferedReader().readText()
            Log.d(TAG, "응답 길이: ${response.length}")

            return parseVideosFromResponse(response)
        } finally {
            conn.disconnect()
        }
    }

    // ==================== Response Parsing ====================

    private fun parseVideosFromResponse(response: String): JSONArray {
        val responseJson = JSONObject(response)
        val videos = mutableListOf<VideoMeta>()
        val seen = mutableSetOf<String>()

        // 1차: 렌더러 객체에서 직접 비디오 추출
        extractVideosFromRenderers(responseJson, videos, seen, 0)
        Log.d(TAG, "렌더러에서 ${videos.size}개 추출")

        // 2차: 렌더러에서 부족하면 regex 폴백
        if (videos.isEmpty()) {
            val videoIdRegex = """"videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""".toRegex()
            videoIdRegex.findAll(response).forEach { match ->
                val id = match.groupValues[1]
                if (id !in seen) { seen.add(id); videos.add(VideoMeta(id)) }
            }
            Log.d(TAG, "regex 폴백: ${videos.size}개")
        }

        if (videos.isEmpty()) {
            Log.d(TAG, "응답 샘플: ${response.take(500)}")
            return JSONArray()
        }

        videos.take(3).forEach { v ->
            Log.d(TAG, "메타: ${v.id} → '${v.title.take(50)}' / '${v.channel.take(30)}'")
        }

        return videosToJsonArray(videos.take(MAX_VIDEOS))
    }

    private fun videosToJsonArray(videos: List<VideoMeta>): JSONArray {
        val arr = JSONArray()
        for (v in videos) {
            arr.put(JSONObject().apply {
                put("id", v.id)
                put("title", v.title.ifEmpty { "Video ${v.id}" })
                put("thumbnail", v.thumbnail.ifEmpty { "https://i.ytimg.com/vi/${v.id}/hqdefault.jpg" })
                put("channel", v.channel)
                put("channelThumbnail", v.channelThumbnail)
                put("duration", v.duration)
                put("views", v.viewCount)
                put("publishedAt", v.publishedAt)
                put("videoType", v.videoType)
            })
        }
        Log.d(TAG, "최종: ${arr.length()}개")
        return arr
    }

    // ==================== Video Extraction ====================

    /**
     * JSON 트리를 재귀 탐색하여 videoRenderer 등에서 비디오 정보 직접 추출
     */
    fun extractVideosFromRenderers(
        obj: Any?, videos: MutableList<VideoMeta>, seen: MutableSet<String>, depth: Int
    ) {
        if (obj == null || depth > 25 || videos.size >= 50) return

        when (obj) {
            is JSONObject -> {
                // 광고 렌더러 스킵
                for (adKey in AdBlockHelper.AD_RENDERER_KEYS) {
                    if (obj.has(adKey)) return
                }

                for (rKey in RENDERER_KEYS) {
                    val renderer = obj.optJSONObject(rKey)
                    if (renderer != null) {
                        parseVideoRenderer(renderer, videos, seen)
                    }
                }

                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.opt(key)
                    if (value is JSONObject || value is JSONArray) {
                        extractVideosFromRenderers(value, videos, seen, depth + 1)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until minOf(obj.length(), 300)) {
                    extractVideosFromRenderers(obj.opt(i), videos, seen, depth + 1)
                }
            }
        }
    }

    private fun parseVideoRenderer(
        renderer: JSONObject, videos: MutableList<VideoMeta>, seen: MutableSet<String>
    ) {
        val vid = renderer.optString("videoId", "")
        if (vid.length != 11 || vid in seen) return

        seen.add(vid)
        val title = extractText(renderer.opt("title"))
            .ifEmpty { extractText(renderer.opt("headline")) }
        val channel = extractText(renderer.opt("longBylineText"))
            .ifEmpty { extractText(renderer.opt("shortBylineText")) }
            .ifEmpty { extractText(renderer.opt("ownerText")) }
        val channelThumb = extractChannelThumbnail(renderer)
        val thumbUrl = renderer.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")?.let { thumbs ->
                if (thumbs.length() > 0)
                    thumbs.getJSONObject(thumbs.length() - 1).optString("url", "")
                else ""
            } ?: ""
        val duration = extractText(renderer.opt("lengthText"))
        val viewCount = extractText(renderer.opt("viewCountText"))
            .ifEmpty { extractText(renderer.opt("shortViewCountText")) }
        val published = extractText(renderer.opt("publishedTimeText"))
        val isShorts = renderer.optJSONObject("navigationEndpoint")
            ?.has("reelWatchEndpoint") == true

        videos.add(VideoMeta(
            id = vid,
            title = title.take(200),
            channel = channel.take(100),
            channelThumbnail = channelThumb,
            thumbnail = thumbUrl,
            duration = duration,
            viewCount = viewCount,
            publishedAt = published,
            videoType = if (isShorts) "SHORTS" else "VIDEO"
        ))
    }

    // ==================== JSON Helpers ====================

    private fun extractChannelThumbnail(renderer: JSONObject): String {
        val ctsr = renderer.optJSONObject("channelThumbnailSupportedRenderers")
        if (ctsr != null) {
            val thumbs = ctsr.optJSONObject("channelThumbnailWithLinkRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            if (thumbs != null && thumbs.length() > 0) {
                return thumbs.getJSONObject(0).optString("url", "")
            }
        }
        val ct = renderer.optJSONObject("channelThumbnail")
        if (ct != null) {
            val thumbs = ct.optJSONArray("thumbnails")
            if (thumbs != null && thumbs.length() > 0) {
                return thumbs.getJSONObject(0).optString("url", "")
            }
        }
        return ""
    }

    /**
     * YouTube JSON 텍스트 객체에서 문자열 추출
     * {"simpleText": "..."} 또는 {"runs": [{"text": "..."}]}
     */
    fun extractText(obj: Any?): String {
        if (obj == null) return ""
        if (obj is String) return obj

        if (obj is JSONObject) {
            val simple = obj.optString("simpleText", "")
            if (simple.isNotEmpty()) return simple

            val runs = obj.optJSONArray("runs")
            if (runs != null && runs.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until runs.length()) {
                    sb.append(runs.getJSONObject(i).optString("text", ""))
                }
                return sb.toString()
            }

            val content = obj.optString("content", "")
            if (content.isNotEmpty()) return content
        }

        return ""
    }
}

private val RENDERER_KEYS = listOf(
    "videoRenderer", "compactVideoRenderer", "gridVideoRenderer",
    "videoWithContextRenderer", "playlistVideoRenderer", "reelItemRenderer"
)
