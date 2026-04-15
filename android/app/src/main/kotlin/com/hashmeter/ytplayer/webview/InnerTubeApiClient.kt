package com.hashmeter.ytplayer.webview

import android.util.Log
import android.webkit.CookieManager
import com.hashmeter.ytplayer.adblock.AdBlockHelper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

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

    data class BrowseResult(
        val videos: JSONArray,
        val continuationToken: String?
    )

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

    data class PlaylistMeta(
        val playlistId: String,
        val title: String = "",
        val thumbnail: String = "",
        val videoCount: Int = 0,
        val visibility: String = "private",
        val updatedAt: String = "",
        val channelTitle: String = ""
    )

    data class LibraryResult(
        val historyVideos: JSONArray,
        val playlists: JSONArray
    )

    data class AccountInfo(
        val displayName: String,
        val photoUrl: String,
        val channelHandle: String,
        val email: String
    )

    // ==================== Public API ====================

    /**
     * 홈 피드 가져오기 (FEwhat_to_watch — 추천/인기 콘텐츠, continuation 토큰 지원)
     */
    fun fetchHomeFeed(): BrowseResult {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("browseId", "FEwhat_to_watch")
        }

        return callApi("$BASE_URL/browse?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false", body)
    }

    /**
     * 트렌딩 영상 가져오기 (WEB 클라이언트) — 폴백용
     */
    fun fetchTrending(): BrowseResult {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("browseId", "FEtrending")
        }

        return callApi("$BASE_URL/browse?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false", body)
    }

    /**
     * continuation 토큰으로 다음 페이지 요청
     */
    fun fetchBrowseContinuation(token: String): BrowseResult {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("continuation", token)
        }

        Log.d(TAG, "fetchBrowseContinuation 호출, token=${token.take(40)}...")
        return callApi("$BASE_URL/browse?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false", body)
    }

    /**
     * 검색 API 호출 (WEB 클라이언트) — JSONArray만 반환 (검색 화면, 쇼츠 등에서 사용)
     */
    fun fetchSearch(query: String): JSONArray {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("query", query)
        }

        return callApi("$BASE_URL/search?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false", body).videos
    }

    /**
     * 검색 API 호출 — BrowseResult 반환 (continuation 토큰 보존, 홈 폴백용)
     */
    fun fetchSearchWithContinuation(query: String): BrowseResult {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("query", query)
        }

        return callApi("$BASE_URL/search?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false", body)
    }

    /**
     * 검색 continuation 토큰으로 다음 페이지 요청
     */
    fun fetchSearchContinuation(token: String): BrowseResult {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("continuation", token)
        }

        Log.d(TAG, "fetchSearchContinuation 호출, token=${token.take(40)}...")
        return callApi("$BASE_URL/search?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false", body)
    }

    /**
     * 구독 피드 가져오기 (FEsubscriptions — 구독 채널의 최신 영상)
     */
    fun fetchSubscriptionFeed(): BrowseResult {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("browseId", "FEsubscriptions")
        }

        return callApi("$BASE_URL/browse?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false", body)
    }

    /**
     * 쇼츠 피드 API 호출 (/browse + FEshorts)
     */
    fun fetchShorts(): JSONArray {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("browseId", "FEshorts")
        }

        val result = callApi("$BASE_URL/browse?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false", body)

        // FEshorts 결과에 videoType = "SHORTS" 강제 설정
        val shortsArray = JSONArray()
        for (i in 0 until result.videos.length()) {
            val video = result.videos.getJSONObject(i)
            video.put("videoType", "SHORTS")
            shortsArray.put(video)
        }
        return shuffleJsonArray(shortsArray)
    }

    /**
     * 쇼츠 검색 폴백 (FEshorts browse 실패 시)
     * 검색 쿼리를 랜덤으로 선택하여 다양한 결과 반환
     */
    fun fetchShortsViaSearch(): JSONArray {
        val queries = listOf("인기 쇼츠", "추천 쇼츠", "shorts 인기", "쇼츠 추천", "trending shorts")
        val query = queries.random()
        Log.d(TAG, "쇼츠 검색 쿼리: $query")

        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("query", query)
            put("params", "EgIYAQ==")
        }

        val videos = callApi("$BASE_URL/search?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false", body).videos

        val shortsArray = JSONArray()
        for (i in 0 until videos.length()) {
            val video = videos.getJSONObject(i)
            video.put("videoType", "SHORTS")
            shortsArray.put(video)
        }
        return shuffleJsonArray(shortsArray)
    }

    private fun shuffleJsonArray(array: JSONArray): JSONArray {
        if (array.length() <= 1) return array
        val indices = (0 until array.length()).shuffled()
        val shuffled = JSONArray()
        for (i in indices) {
            shuffled.put(array.getJSONObject(i))
        }
        return shuffled
    }

    // ==================== Library / History API ====================

    /**
     * 보관함 메인 데이터 가져오기 (FElibrary)
     * callApi() 우회 — 전용 parseLibraryResponse() 사용
     */
    fun fetchLibrary(): LibraryResult {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("browseId", "FElibrary")
        }

        val conn = createConnection("$BASE_URL/browse?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false")

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            Log.d(TAG, "FElibrary 응답 코드: $code")

            if (code != 200) {
                return LibraryResult(JSONArray(), JSONArray())
            }

            val response = conn.inputStream.bufferedReader().readText()
            Log.d(TAG, "FElibrary 응답 길이: ${response.length}")

            return parseLibraryResponse(response)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 시청 기록 가져오기 (FEhistory — 비디오 렌더러 구조, 기존 callApi 재사용)
     */
    fun fetchHistory(): BrowseResult {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("browseId", "FEhistory")
        }

        return callApi("$BASE_URL/browse?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false", body)
    }

    /**
     * 시청 기록 다음 페이지 (continuation 기반)
     */
    fun fetchHistoryContinuation(token: String): BrowseResult {
        return fetchBrowseContinuation(token)
    }

    /**
     * 재생목록 상세 동영상 가져오기 (VL{playlistId})
     * playlistVideoRenderer ∈ RENDERER_KEYS → 기존 callApi 재사용
     */
    fun fetchPlaylistDetail(playlistId: String): BrowseResult {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("browseId", "VL$playlistId")
        }

        return callApi("$BASE_URL/browse?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false", body)
    }

    /**
     * FElibrary 응답 전용 파싱
     * shelf 기반으로 기록 비디오와 재생목록 메타데이터를 분리 추출
     */
    private fun parseLibraryResponse(response: String): LibraryResult {
        val responseJson = JSONObject(response)
        val historyVideos = mutableListOf<VideoMeta>()
        val playlists = mutableListOf<PlaylistMeta>()
        val seenVideoIds = mutableSetOf<String>()

        Log.d(TAG, "parseLibraryResponse 시작, 최상위 키: ${responseJson.keys().asSequence().toList()}")

        try {
            // 1차: shelfRenderer 기반 섹션별 추출
            extractLibrarySections(responseJson, historyVideos, playlists, seenVideoIds, 0)
            Log.d(TAG, "Library 섹션 추출 완료: history=${historyVideos.size}, playlists=${playlists.size}")

            // 2차: shelf 구조 못 찾으면 재귀 탐색으로 폴백
            if (historyVideos.isEmpty() && playlists.isEmpty()) {
                Log.d(TAG, "shelf 구조 미발견 → 전체 재귀 탐색 폴백")
                extractVideosFromRenderers(responseJson, historyVideos, seenVideoIds, 0)
                extractPlaylistsFromRenderers(responseJson, playlists, 0)
                Log.d(TAG, "재귀 탐색 결과: history=${historyVideos.size}, playlists=${playlists.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseLibraryResponse 에러: ${e.message}", e)
        }

        return LibraryResult(
            videosToJsonArray(historyVideos.take(MAX_VIDEOS)),
            playlistsToJsonArray(playlists)
        )
    }

    /**
     * FElibrary 응답에서 shelfRenderer 기반으로 섹션별 데이터 추출
     */
    private fun extractLibrarySections(
        obj: Any?, historyVideos: MutableList<VideoMeta>,
        playlists: MutableList<PlaylistMeta>, seenVideoIds: MutableSet<String>, depth: Int
    ) {
        if (obj == null || depth > 25) return

        when (obj) {
            is JSONObject -> {
                val shelfRenderer = obj.optJSONObject("shelfRenderer")
                if (shelfRenderer != null) {
                    val title = extractText(shelfRenderer.opt("title")).lowercase()
                    Log.d(TAG, "shelf 발견: '$title'")

                    val content = shelfRenderer.optJSONObject("content")
                    if (title.contains("기록") || title.contains("history") || title.contains("최근")) {
                        // 기록 shelf → 비디오 추출
                        if (content != null) {
                            extractVideosFromRenderers(content, historyVideos, seenVideoIds, 0)
                        }
                    } else {
                        // 기타 shelf (재생목록 포함) → 재생목록 추출
                        if (content != null) {
                            extractPlaylistsFromRenderers(content, playlists, 0)
                            // shelf 안에 비디오도 있을 수 있음 (좋아요 등)
                            if (playlists.isEmpty()) {
                                extractVideosFromRenderers(content, historyVideos, seenVideoIds, 0)
                            }
                        }
                    }
                    return // shelf 내부는 이미 처리했으므로 재귀 중단
                }

                // shelf가 아닌 경우 계속 탐색
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.opt(key)
                    if (value is JSONObject || value is JSONArray) {
                        extractLibrarySections(value, historyVideos, playlists, seenVideoIds, depth + 1)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until minOf(obj.length(), 100)) {
                    extractLibrarySections(obj.opt(i), historyVideos, playlists, seenVideoIds, depth + 1)
                }
            }
        }
    }

    /**
     * JSON 트리에서 재생목록 메타데이터 재귀 추출
     * gridPlaylistRenderer, playlistRenderer, lockupViewModel(PLAYLIST) 인식
     */
    private fun extractPlaylistsFromRenderers(
        obj: Any?, playlists: MutableList<PlaylistMeta>, depth: Int
    ) {
        if (obj == null || depth > 25 || playlists.size >= 50) return

        when (obj) {
            is JSONObject -> {
                // gridPlaylistRenderer
                val gridPlaylist = obj.optJSONObject("gridPlaylistRenderer")
                if (gridPlaylist != null) {
                    parsePlaylistRenderer(gridPlaylist)?.let { playlists.add(it) }
                }

                // playlistRenderer
                val playlistR = obj.optJSONObject("playlistRenderer")
                if (playlistR != null) {
                    parsePlaylistRenderer(playlistR)?.let { playlists.add(it) }
                }

                // lockupViewModel (contentType=PLAYLIST)
                val lockup = obj.optJSONObject("lockupViewModel")
                if (lockup != null) {
                    val contentType = lockup.optString("contentType", "")
                    if (contentType.contains("PLAYLIST", ignoreCase = true)) {
                        parseLockupPlaylist(lockup)?.let { playlists.add(it) }
                    }
                }

                // 재귀 탐색
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.opt(key)
                    if (value is JSONObject || value is JSONArray) {
                        extractPlaylistsFromRenderers(value, playlists, depth + 1)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until minOf(obj.length(), 100)) {
                    extractPlaylistsFromRenderers(obj.opt(i), playlists, depth + 1)
                }
            }
        }
    }

    /**
     * gridPlaylistRenderer / playlistRenderer에서 재생목록 메타데이터 추출
     */
    private fun parsePlaylistRenderer(renderer: JSONObject): PlaylistMeta? {
        val playlistId = renderer.optString("playlistId", "")
        if (playlistId.isEmpty()) return null

        val title = extractText(renderer.opt("title"))
        val videoCountText = extractText(renderer.opt("videoCountText"))
            .ifEmpty { extractText(renderer.opt("videoCountShortText")) }
            .ifEmpty { extractText(renderer.opt("thumbnailText")) }
        var videoCount = videoCountText.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
        // 직접 videoCount 필드 체크 (숫자 타입)
        if (videoCount == 0) {
            videoCount = renderer.optInt("videoCount", 0)
        }
        val thumbnail = renderer.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")?.let { t ->
                if (t.length() > 0) t.getJSONObject(t.length() - 1).optString("url", "")
                else ""
            } ?: ""
        val updatedAt = extractText(renderer.opt("publishedTimeText"))
        val channelTitle = extractText(renderer.opt("longBylineText"))
            .ifEmpty { extractText(renderer.opt("shortBylineText")) }
            .ifEmpty { extractText(renderer.opt("ownerText")) }

        // 공개/비공개 판별
        val privacy = renderer.optString("privacy", "")
        val visibility = when {
            privacy.contains("PUBLIC", ignoreCase = true) -> "public"
            privacy.contains("UNLISTED", ignoreCase = true) -> "unlisted"
            else -> "private"
        }

        Log.d(TAG, "playlist 파싱: $playlistId → '$title' (${videoCount}개)")
        return PlaylistMeta(playlistId, title, thumbnail, videoCount, visibility, updatedAt, channelTitle)
    }

    /**
     * lockupViewModel(contentType=PLAYLIST)에서 재생목록 메타데이터 추출
     */
    private fun parseLockupPlaylist(lockup: JSONObject): PlaylistMeta? {
        val playlistId = lockup.optString("contentId", "")
        if (playlistId.isEmpty()) return null

        val metadataObj = lockup.optJSONObject("metadata")
            ?.optJSONObject("lockupMetadataViewModel")
        val title = metadataObj?.optJSONObject("title")
            ?.optString("content", "") ?: ""

        // 메타데이터 행에서 동영상 수, 채널명 추출
        var videoCount = 0
        var channelTitle = ""
        var updatedAt = ""
        val contentMeta = metadataObj?.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
        val metadataRows = contentMeta?.optJSONArray("metadataRows")
        if (metadataRows != null) {
            for (i in 0 until metadataRows.length()) {
                val row = metadataRows.optJSONObject(i) ?: continue
                val parts = row.optJSONArray("metadataParts") ?: continue
                for (j in 0 until parts.length()) {
                    val partText = parts.optJSONObject(j)
                        ?.optJSONObject("text")
                        ?.optString("content", "") ?: ""
                    if (partText.isEmpty()) continue
                    when {
                        partText.contains("동영상") || partText.contains("video", ignoreCase = true)
                            || partText.contains("개") ->
                            if (videoCount == 0) {
                                videoCount = partText.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                            }
                        partText.contains("전에") || partText.contains("ago")
                            || partText.contains("업데이트") || partText.contains("update", ignoreCase = true) ->
                            updatedAt = partText
                        channelTitle.isEmpty() ->
                            channelTitle = partText
                    }
                }
            }
        }

        // 오버레이 배지에서 동영상 수 추출 (메타데이터 행에서 못 찾은 경우)
        if (videoCount == 0) {
            val overlays = lockup.optJSONObject("contentImage")
                ?.optJSONObject("thumbnailViewModel")
                ?.optJSONArray("overlays")
            if (overlays != null) {
                for (i in 0 until overlays.length()) {
                    val overlay = overlays.optJSONObject(i) ?: continue
                    val badge = overlay.optJSONObject("thumbnailOverlayBadgeViewModel")
                    if (badge != null) {
                        val text = badge.optString("text", "")
                            .ifEmpty {
                                badge.optJSONArray("thumbnailBadges")?.let { badges ->
                                    if (badges.length() > 0) badges.optJSONObject(0)?.optString("text", "") ?: ""
                                    else ""
                                } ?: ""
                            }
                        if (text.isNotEmpty()) {
                            val count = text.replace(Regex("[^0-9]"), "").toIntOrNull()
                            if (count != null && count > 0) {
                                videoCount = count
                                break
                            }
                        }
                    }
                }
            }
        }

        // 썸네일 추출
        val thumbnail = lockup.optJSONObject("contentImage")
            ?.optJSONObject("thumbnailViewModel")
            ?.optJSONObject("image")
            ?.optJSONArray("sources")?.let { sources ->
                if (sources.length() > 0) sources.getJSONObject(sources.length() - 1).optString("url", "")
                else ""
            } ?: ""

        Log.d(TAG, "lockup playlist 파싱: $playlistId → '$title' (${videoCount}개)")
        return PlaylistMeta(playlistId, title, thumbnail, videoCount, "private", updatedAt, channelTitle)
    }

    private fun playlistsToJsonArray(playlists: List<PlaylistMeta>): JSONArray {
        val arr = JSONArray()
        for (p in playlists) {
            arr.put(JSONObject().apply {
                put("playlistId", p.playlistId)
                put("title", p.title)
                put("thumbnail", p.thumbnail)
                put("videoCount", p.videoCount)
                put("visibility", p.visibility)
                put("updatedAt", p.updatedAt)
                put("channelTitle", p.channelTitle)
            })
        }
        return arr
    }

    /**
     * 영상 상세 정보 가져오기 (/youtubei/v1/next)
     * 설명, 좋아요, 구독자수, 댓글, 연관영상
     */
    /**
     * 로그인된 계정의 프로필 정보 가져오기 (/youtubei/v1/account/account_menu)
     * SAPISIDHASH 인증이 적용된 createConnection을 사용하므로
     * 쿠키에 SAPISID가 있어야 정확한 결과를 반환함.
     */
    fun fetchAccountInfo(): AccountInfo? {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION)
        val conn = createConnection(
            "$BASE_URL/account/account_menu?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false"
        )

        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            Log.d(TAG, "account_menu 응답 코드: $code")
            if (code != 200) {
                val err = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                Log.w(TAG, "account_menu 에러: ${err.take(500)}")
                return null
            }
            val raw = conn.inputStream.bufferedReader().readText()
            Log.d(TAG, "account_menu 응답 길이: ${raw.length}")
            val response = JSONObject(raw)
            Log.d(TAG, "account_menu 최상위 키: ${response.keys().asSequence().toList()}")

            val header = findActiveAccountHeader(response)
            if (header == null) {
                Log.w(TAG, "activeAccountHeaderRenderer 미발견. 응답 미리보기: ${raw.take(800)}")
                // 폴백: accountName / accountPhoto / email / channelHandle 키를 직접 재귀 탐색
                return findAccountFieldsFallback(response)
            }
            Log.d(TAG, "헤더 키: ${header.keys().asSequence().toList()}")

            val photoUrl = pickThumbnailUrl(header.opt("accountPhoto"))

            AccountInfo(
                displayName = extractText(header.opt("accountName")),
                photoUrl = photoUrl,
                channelHandle = extractText(header.opt("channelHandle")),
                email = extractText(header.opt("email"))
            ).also {
                Log.d(TAG, "account_menu 결과: name='${it.displayName}', photo='${it.photoUrl.take(60)}', handle='${it.channelHandle}', email='${it.email}'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "account_menu 호출 실패", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun pickThumbnailUrl(node: Any?): String {
        val arr = (node as? JSONObject)?.optJSONArray("thumbnails") ?: return ""
        var best = ""
        for (i in 0 until arr.length()) best = arr.optJSONObject(i)?.optString("url") ?: best
        return best
    }

    /**
     * activeAccountHeaderRenderer가 없는 응답 구조 변형 대비 폴백.
     * 트리 어디든 accountName/accountPhoto/email/channelHandle 키가 인접해 있으면 채움.
     */
    private fun findAccountFieldsFallback(root: Any?): AccountInfo? {
        var name = ""; var photo = ""; var handle = ""; var email = ""
        fun visit(node: Any?) {
            when (node) {
                is JSONObject -> {
                    if (name.isEmpty()) extractText(node.opt("accountName")).let { if (it.isNotEmpty()) name = it }
                    if (photo.isEmpty()) pickThumbnailUrl(node.opt("accountPhoto")).let { if (it.isNotEmpty()) photo = it }
                    if (handle.isEmpty()) extractText(node.opt("channelHandle")).let { if (it.isNotEmpty()) handle = it }
                    if (email.isEmpty()) extractText(node.opt("email")).let { if (it.isNotEmpty()) email = it }
                    val keys = node.keys()
                    while (keys.hasNext()) visit(node.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
            }
        }
        visit(root)
        Log.d(TAG, "폴백 결과: name='$name', photo='${photo.take(60)}', handle='$handle', email='$email'")
        return if (name.isNotEmpty() || photo.isNotEmpty()) {
            AccountInfo(name, photo, handle, email)
        } else null
    }

    private fun findActiveAccountHeader(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("activeAccountHeaderRenderer")?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findActiveAccountHeader(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findActiveAccountHeader(node.opt(i))?.let { return it }
                }
            }
        }
        return null
    }

    fun fetchVideoDetail(videoId: String): JSONObject {
        val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
            put("videoId", videoId)
        }

        val conn = createConnection("$BASE_URL/next?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false")

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            Log.d(TAG, "next 응답 코드: $code")

            if (code != 200) return JSONObject()

            val response = conn.inputStream.bufferedReader().readText()
            return parseVideoDetailFromResponse(response)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseVideoDetailFromResponse(response: String): JSONObject {
        val json = JSONObject(response)
        val result = JSONObject()

        // 설명, 좋아요 수 추출
        try {
            val results = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnWatchNextResults")

            // 1차 경로: WEB용
            val primaryResults = results?.optJSONObject("results")
                ?.optJSONObject("results")
                ?.optJSONArray("contents")

            if (primaryResults != null) {
                parsePrimaryResults(primaryResults, result)
            }

            // 2차 경로: 모바일 (singleColumnWatchNextResults)
            val mobileResults = json.optJSONObject("contents")
                ?.optJSONObject("singleColumnWatchNextResults")
                ?.optJSONObject("results")
                ?.optJSONObject("results")
                ?.optJSONArray("contents")

            if (mobileResults != null && !result.has("description")) {
                parsePrimaryResults(mobileResults, result)
            }

            Log.d(TAG, "videoDetail 기본정보 파싱 완료: desc=${result.has("description")}, like=${result.has("likeCount")}")
        } catch (e: Exception) {
            Log.e(TAG, "videoDetail 기본정보 파싱 에러: ${e.message}", e)
        }

        // 연관 영상 추출 (독립 try-catch)
        val relatedVideos = JSONArray()
        try {
            val results = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnWatchNextResults")

            val secondaryResults = results?.optJSONObject("secondaryResults")
                ?.optJSONObject("secondaryResults")
                ?.optJSONArray("results")

            if (secondaryResults != null) {
                parseRelatedVideos(secondaryResults, relatedVideos)
            }

            // 모바일 연관영상 경로
            if (relatedVideos.length() == 0) {
                val relatedMetas = mutableListOf<VideoMeta>()
                val seenIds = mutableSetOf<String>()
                extractVideosFromRenderers(json, relatedMetas, seenIds, 0)
                for (v in relatedMetas.take(20)) {
                    relatedVideos.put(JSONObject().apply {
                        put("id", v.id)
                        put("title", v.title)
                        put("thumbnail", v.thumbnail.ifEmpty { "https://i.ytimg.com/vi/${v.id}/hqdefault.jpg" })
                        put("channel", v.channel)
                        put("channelThumbnail", v.channelThumbnail)
                        put("duration", v.duration)
                        put("views", v.viewCount)
                        put("publishedAt", v.publishedAt)
                        put("videoType", v.videoType)
                    })
                }
            }

            Log.d(TAG, "videoDetail 연관영상 ${relatedVideos.length()}개 추출")
        } catch (e: Exception) {
            Log.e(TAG, "videoDetail 연관영상 파싱 에러: ${e.message}", e)
        }
        result.put("relatedVideos", relatedVideos)

        // 댓글 추출 (독립 try-catch — 실패해도 나머지 데이터 반환)
        val comments = JSONArray()
        try {
            parseComments(json, comments)
            Log.d(TAG, "videoDetail 댓글 ${comments.length()}개 추출")
        } catch (e: Exception) {
            Log.e(TAG, "videoDetail 댓글 파싱 에러: ${e.message}", e)
        }
        result.put("comments", comments)

        return result
    }

    private fun parsePrimaryResults(contents: JSONArray, result: JSONObject) {
        for (i in 0 until contents.length()) {
            val item = contents.optJSONObject(i) ?: continue

            // videoPrimaryInfoRenderer → 좋아요, 조회수
            val primaryInfo = item.optJSONObject("videoPrimaryInfoRenderer")
            if (primaryInfo != null) {
                val viewCount = extractText(
                    primaryInfo.optJSONObject("viewCount")?.opt("videoViewCountRenderer")
                        ?.let { (it as JSONObject).opt("viewCount") }
                )
                if (viewCount.isNotEmpty()) result.put("viewCount", viewCount)

                // 좋아요 수 추출
                val topLevelButtons = primaryInfo.optJSONObject("videoActions")
                    ?.optJSONObject("menuRenderer")
                    ?.optJSONArray("topLevelButtons")
                if (topLevelButtons != null) {
                    for (j in 0 until topLevelButtons.length()) {
                        val btn = topLevelButtons.optJSONObject(j) ?: continue
                        val toggleBtn = btn.optJSONObject("segmentedLikeDislikeButtonViewModel")
                            ?.optJSONObject("likeButtonViewModel")
                            ?.optJSONObject("likeButtonViewModel")
                            ?.optJSONObject("toggleButtonViewModel")
                            ?.optJSONObject("toggleButtonViewModel")
                            ?.optJSONObject("defaultButtonViewModel")
                            ?.optJSONObject("buttonViewModel")
                        if (toggleBtn != null) {
                            val likeCount = toggleBtn.optString("title", "")
                            if (likeCount.isNotEmpty()) result.put("likeCount", likeCount)
                        }
                        // 레거시 경로
                        val legacyToggle = btn.optJSONObject("toggleButtonRenderer")
                        if (legacyToggle != null) {
                            val label = legacyToggle.optJSONObject("defaultText")
                                ?.optJSONObject("accessibility")
                                ?.optJSONObject("accessibilityData")
                                ?.optString("label", "") ?: ""
                            if (label.contains("좋아요") || label.lowercase().contains("like")) {
                                val likeText = extractText(legacyToggle.opt("defaultText"))
                                if (likeText.isNotEmpty()) result.put("likeCount", likeText)
                            }
                        }
                    }
                }
            }

            // videoSecondaryInfoRenderer → 설명, 구독자수
            val secondaryInfo = item.optJSONObject("videoSecondaryInfoRenderer")
            if (secondaryInfo != null) {
                val description = extractText(secondaryInfo.opt("attributedDescription"))
                    .ifEmpty { extractText(secondaryInfo.optJSONObject("description")) }
                if (description.isNotEmpty()) result.put("description", description)

                val subCount = extractText(
                    secondaryInfo.optJSONObject("owner")
                        ?.optJSONObject("videoOwnerRenderer")
                        ?.opt("subscriberCountText")
                )
                if (subCount.isNotEmpty()) result.put("subscriberCount", subCount)
            }

            // slimVideoMetadataSectionRenderer (모바일)
            val slimMeta = item.optJSONObject("slimVideoMetadataSectionRenderer")
            if (slimMeta != null) {
                val slimContents = slimMeta.optJSONArray("contents")
                if (slimContents != null) {
                    for (j in 0 until slimContents.length()) {
                        val slimItem = slimContents.optJSONObject(j) ?: continue
                        val descRenderer = slimItem.optJSONObject("slimVideoDescriptionRenderer")
                        if (descRenderer != null) {
                            val desc = extractText(descRenderer.opt("description"))
                            if (desc.isNotEmpty()) result.put("description", desc)
                        }
                    }
                }
            }
        }
    }

    private fun parseRelatedVideos(results: JSONArray, relatedVideos: JSONArray) {
        for (i in 0 until minOf(results.length(), 20)) {
            val item = results.optJSONObject(i) ?: continue
            val renderer = item.optJSONObject("compactVideoRenderer") ?: continue

            val vid = renderer.optString("videoId", "")
            if (vid.length != 11) continue

            relatedVideos.put(JSONObject().apply {
                put("id", vid)
                put("title", extractText(renderer.opt("title")))
                put("thumbnail", renderer.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")?.let { thumbs ->
                        if (thumbs.length() > 0) thumbs.getJSONObject(thumbs.length() - 1).optString("url", "")
                        else ""
                    } ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg")
                put("channel", extractText(renderer.opt("longBylineText"))
                    .ifEmpty { extractText(renderer.opt("shortBylineText")) })
                put("channelThumbnail", extractChannelThumbnail(renderer))
                put("duration", extractText(renderer.opt("lengthText")))
                put("views", extractText(renderer.opt("viewCountText"))
                    .ifEmpty { extractText(renderer.opt("shortViewCountText")) })
                put("publishedAt", extractText(renderer.opt("publishedTimeText")))
                put("videoType", "VIDEO")
            })
        }
    }

    private fun parseComments(json: JSONObject, comments: JSONArray) {
        // engagementPanels에서 댓글 continuation 토큰 추출
        val panels = json.optJSONArray("engagementPanels") ?: return

        for (i in 0 until panels.length()) {
            val panel = panels.optJSONObject(i) ?: continue
            val renderer = panel.optJSONObject("engagementPanelSectionListRenderer") ?: continue

            val panelId = renderer.optString("panelIdentifier", "")
            if (panelId != "comment-item-section") continue

            // 댓글 header에서 continuation 토큰 추출
            val header = renderer.optJSONObject("header")
                ?.optJSONObject("engagementPanelTitleHeaderRenderer")

            val contToken = findContinuationToken(renderer)
            Log.d(TAG, "댓글 continuation token: ${if (contToken != null) "${contToken.take(30)}..." else "null"}")
            if (contToken != null) {
                fetchComments(contToken, comments)
            }
            break
        }
    }

    private fun findContinuationToken(obj: JSONObject): String? {
        val jsonStr = obj.toString()
        val regex = """"continuation"\s*:\s*"([^"]+)"""".toRegex()
        val match = regex.find(jsonStr)
        return match?.groupValues?.get(1)
    }

    private fun fetchComments(continuationToken: String, comments: JSONArray) {
        try {
            val body = buildRequestBody("WEB", WebViewManager.WEB_CLIENT_VERSION).apply {
                put("continuation", continuationToken)
            }

            val conn = createConnection(
                "$BASE_URL/next?key=${WebViewManager.INNERTUBE_API_KEY}&prettyPrint=false",
                connectMs = 5000,
                readMs = 5000
            )

            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode != 200) return

                val response = conn.inputStream.bufferedReader().readText()
                val respJson = JSONObject(response)

                // commentRenderer 추출
                extractCommentRenderers(respJson, comments)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchComments 에러: ${e.message}", e)
        }
    }

    private fun extractCommentRenderers(obj: Any?, comments: JSONArray, depth: Int = 0) {
        if (obj == null || depth > 20 || comments.length() >= 20) return

        when (obj) {
            is JSONObject -> {
                val commentRenderer = obj.optJSONObject("commentRenderer")
                if (commentRenderer != null) {
                    val authorText = extractText(commentRenderer.opt("authorText"))
                    val contentText = extractText(commentRenderer.opt("contentText"))
                    val likeCount = commentRenderer.optString("voteCount", "")
                        .ifEmpty { extractText(commentRenderer.opt("voteCount")) }
                    val publishedTime = extractText(commentRenderer.opt("publishedTimeText"))
                    val authorThumb = commentRenderer.optJSONObject("authorThumbnail")
                        ?.optJSONArray("thumbnails")?.let { t ->
                            if (t.length() > 0) t.getJSONObject(0).optString("url", "") else ""
                        } ?: ""

                    if (contentText.isNotEmpty()) {
                        comments.put(JSONObject().apply {
                            put("author", authorText)
                            put("authorThumbnail", authorThumb)
                            put("text", contentText)
                            put("likeCount", likeCount)
                            put("publishedAt", publishedTime)
                        })
                    }
                }

                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.opt(key)
                    if (value is JSONObject || value is JSONArray) {
                        extractCommentRenderers(value, comments, depth + 1)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until minOf(obj.length(), 100)) {
                    extractCommentRenderers(obj.opt(i), comments, depth + 1)
                }
            }
        }
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

    // ==================== HTTP Connection ====================

    private fun extractCookieValue(cookies: String, name: String): String? {
        return cookies.split("; ")
            .find { it.startsWith("$name=") }
            ?.substringAfter("=")
    }

    private fun generateSapisidHash(sapisid: String, origin: String): String {
        val timestamp = System.currentTimeMillis() / 1000
        val input = "$timestamp $sapisid $origin"
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hash"
    }

    private fun createConnection(
        url: String,
        connectMs: Int = CONNECT_TIMEOUT,
        readMs: Int = READ_TIMEOUT
    ): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("X-YouTube-Client-Name", "1")
            setRequestProperty("X-YouTube-Client-Version", WebViewManager.WEB_CLIENT_VERSION)
            setRequestProperty("Origin", "https://www.youtube.com")
            setRequestProperty("Referer", "https://www.youtube.com/")
            val cookies = CookieManager.getInstance().getCookie("https://www.youtube.com")
            if (!cookies.isNullOrEmpty()) {
                setRequestProperty("Cookie", cookies)
                // SAPISID 인증 헤더 — InnerTube API 개인화에 필수
                val sapisid = extractCookieValue(cookies, "SAPISID")
                    ?: extractCookieValue(cookies, "__Secure-3PAPISID")
                if (sapisid != null) {
                    val origin = "https://www.youtube.com"
                    setRequestProperty("Authorization", generateSapisidHash(sapisid, origin))
                    setRequestProperty("X-Goog-AuthUser", "0")
                }
            }
            doOutput = true
            connectTimeout = connectMs
            readTimeout = readMs
        }
    }

    // ==================== API Call ====================

    private fun callApi(urlStr: String, body: JSONObject): BrowseResult {
        val conn = createConnection(urlStr).apply {
            setRequestProperty("Cache-Control", "no-cache")
            useCaches = false
        }

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            Log.d(TAG, "응답 코드: $code, URL: ${urlStr.substringAfterLast("/")}")

            if (code != 200) {
                val errorBody = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                Log.w(TAG, "에러 응답: ${errorBody.take(300)}")
                return BrowseResult(JSONArray(), null)
            }

            val response = conn.inputStream.bufferedReader().readText()
            Log.d(TAG, "응답 길이: ${response.length}")

            return parseVideosFromResponse(response)
        } finally {
            conn.disconnect()
        }
    }

    // ==================== Response Parsing ====================

    private fun parseVideosFromResponse(response: String): BrowseResult {
        val responseJson = JSONObject(response)
        val videos = mutableListOf<VideoMeta>()
        val seen = mutableSetOf<String>()

        // 진단 로깅: 응답 구조 확인
        Log.d(TAG, "응답 최상위 키: ${responseJson.keys().asSequence().toList()}")
        logRichGridStructure(responseJson)

        // 1차: 렌더러 객체에서 직접 비디오 추출
        extractVideosFromRenderers(responseJson, videos, seen, 0)
        Log.d(TAG, "렌더러에서 ${videos.size}개 추출")

        // 2차: 렌더러에서 부족하면 regex 폴백 (videoId + contentId)
        if (videos.isEmpty()) {
            val videoIdRegex = """"(?:videoId|contentId)"\s*:\s*"([a-zA-Z0-9_-]{11})"""".toRegex()
            val fallbackIds = mutableListOf<String>()
            videoIdRegex.findAll(response).forEach { match ->
                val id = match.groupValues[1]
                if (id !in seen) { seen.add(id); fallbackIds.add(id) }
            }
            // fallback ID들에 대해 JSON 트리에서 메타데이터 2차 탐색
            if (fallbackIds.isNotEmpty()) {
                val metaMap = extractMetadataForIds(responseJson, fallbackIds.toSet())
                for (id in fallbackIds) {
                    val meta = metaMap[id]
                    videos.add(meta ?: VideoMeta(id))
                }
            }
            Log.d(TAG, "regex 폴백: ${videos.size}개")
        }

        // continuation 토큰 추출
        val continuationToken = extractContinuationToken(responseJson, 0)
        Log.d(TAG, "continuation 토큰: ${continuationToken?.take(40) ?: "없음"}")

        if (videos.isEmpty()) {
            Log.w(TAG, "비디오 0개! 응답 시작 500자: ${response.take(500)}")
            try {
                val respJson = JSONObject(response)
                Log.w(TAG, "응답 최상위 키: ${respJson.keys().asSequence().toList()}")
            } catch (_: Exception) {}
            return BrowseResult(JSONArray(), continuationToken)
        }

        videos.take(3).forEach { v ->
            Log.d(TAG, "메타: ${v.id} → '${v.title.take(50)}' / '${v.channel.take(30)}'")
        }

        return BrowseResult(videosToJsonArray(videos.take(MAX_VIDEOS)), continuationToken)
    }

    /**
     * JSON 트리를 재귀 탐색하여 continuation 토큰 추출
     * continuationItemRenderer.continuationEndpoint.continuationCommand.token
     * 또는 nextContinuationData.continuation 패턴 매칭
     */
    private fun extractContinuationToken(obj: Any?, depth: Int): String? {
        if (obj == null || depth > 20) return null

        when (obj) {
            is JSONObject -> {
                // continuationItemRenderer 패턴
                val contItem = obj.optJSONObject("continuationItemRenderer")
                if (contItem != null) {
                    val token = contItem.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")
                        ?.optString("token", "")
                    if (!token.isNullOrEmpty()) return token
                }

                // nextContinuationData 패턴
                val nextCont = obj.optJSONObject("nextContinuationData")
                if (nextCont != null) {
                    val token = nextCont.optString("continuation", "")
                    if (token.isNotEmpty()) return token
                }

                // 재귀 탐색
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.opt(key)
                    if (value is JSONObject || value is JSONArray) {
                        val token = extractContinuationToken(value, depth + 1)
                        if (token != null) return token
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until minOf(obj.length(), 300)) {
                    val token = extractContinuationToken(obj.opt(i), depth + 1)
                    if (token != null) return token
                }
            }
        }
        return null
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
        if (obj == null || depth > 35 || videos.size >= 50) return

        when (obj) {
            is JSONObject -> {
                for (rKey in RENDERER_KEYS) {
                    val renderer = obj.optJSONObject(rKey)
                    if (renderer != null) {
                        when (rKey) {
                            "lockupViewModel", "shortsLockupViewModel" ->
                                parseLockupViewModel(renderer, videos, seen, rKey)
                            else ->
                                parseVideoRenderer(renderer, videos, seen)
                        }
                    }
                }

                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    // 광고 렌더러 하위 트리만 스킵 (부모 전체 스킵 방지)
                    if (key in AdBlockHelper.AD_RENDERER_KEYS) continue
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

    // ==================== LockupViewModel Parsing ====================

    /**
     * lockupViewModel / shortsLockupViewModel에서 비디오 정보 추출
     * YouTube 2024+ 인증 응답에서 사용되는 새 포맷
     */
    private fun parseLockupViewModel(
        renderer: JSONObject, videos: MutableList<VideoMeta>, seen: MutableSet<String>,
        rendererKey: String
    ) {
        val vid = renderer.optString("contentId", "")
        if (vid.length != 11 || vid in seen) return

        seen.add(vid)

        // 제목 추출
        val metadataObj = renderer.optJSONObject("metadata")
            ?.optJSONObject("lockupMetadataViewModel")
        val title = metadataObj?.optJSONObject("title")
            ?.optString("content", "") ?: ""

        // 채널명, 조회수, 게시일 추출 (metadataRows)
        var channel = ""
        var viewCount = ""
        var publishedAt = ""
        val contentMeta = metadataObj?.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
        val metadataRows = contentMeta?.optJSONArray("metadataRows")
        if (metadataRows != null) {
            for (i in 0 until metadataRows.length()) {
                val row = metadataRows.optJSONObject(i) ?: continue
                val parts = row.optJSONArray("metadataParts") ?: continue
                for (j in 0 until parts.length()) {
                    val partText = parts.optJSONObject(j)
                        ?.optJSONObject("text")
                        ?.optString("content", "") ?: ""
                    if (partText.isEmpty()) continue

                    when {
                        // 첫 번째 행 첫 파트 = 채널명
                        i == 0 && j == 0 && channel.isEmpty() -> channel = partText
                        // 조회수 패턴
                        partText.contains("회") || partText.lowercase().contains("view") ->
                            viewCount = partText
                        // 게시일 패턴
                        partText.contains("전") || partText.contains("ago") ||
                            partText.contains("스트리밍") || partText.contains("stream") ->
                            publishedAt = partText
                    }
                }
            }
        }

        // 썸네일, 재생시간 추출
        val contentImage = renderer.optJSONObject("contentImage")
            ?.optJSONObject("thumbnailViewModel")
        val thumbUrl = contentImage?.optJSONObject("image")
            ?.optJSONArray("sources")?.let { sources ->
                if (sources.length() > 0) sources.getJSONObject(sources.length() - 1).optString("url", "")
                else ""
            } ?: ""

        var duration = ""
        val overlays = contentImage?.optJSONArray("overlays")
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val overlay = overlays.optJSONObject(i) ?: continue
                // thumbnailOverlayBadgeViewModel.thumbnailBadges[].text
                val badge = overlay.optJSONObject("thumbnailOverlayBadgeViewModel")
                if (badge != null) {
                    val text = badge.optString("text", "")
                    if (text.isNotEmpty()) { duration = text; break }
                    // badges 배열 하위에 있을 수도 있음
                    val badges = badge.optJSONArray("thumbnailBadges")
                    if (badges != null) {
                        for (j in 0 until badges.length()) {
                            val bText = badges.optJSONObject(j)
                                ?.optString("text", "") ?: ""
                            if (bText.isNotEmpty()) { duration = bText; break }
                        }
                    }
                    if (duration.isNotEmpty()) break
                }
                // thumbnailOverlayTimeStatusRenderer (대체 경로)
                val timeStatus = overlay.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                if (timeStatus != null) {
                    duration = extractText(timeStatus.opt("text"))
                    if (duration.isNotEmpty()) break
                }
            }
        }

        // 쇼츠 여부 판별
        val isShorts = rendererKey == "shortsLockupViewModel" ||
            renderer.optJSONObject("rendererContext")
                ?.optJSONObject("commandContext")
                ?.optString("onTap", "")
                ?.contains("reel", ignoreCase = true) == true

        // 채널 아바타: lockupMetadataViewModel.image.decoratedAvatarViewModel.avatar.avatarViewModel.image.sources[]
        val channelThumb = extractLockupChannelThumbnail(metadataObj)

        Log.d(TAG, "lockupViewModel 파싱: $vid → '$title' / '$channel' / '$viewCount' / avatar=${channelThumb.take(60)}")

        videos.add(VideoMeta(
            id = vid,
            title = title.take(200),
            channel = channel.take(100),
            channelThumbnail = channelThumb,
            thumbnail = thumbUrl,
            duration = duration,
            viewCount = viewCount,
            publishedAt = publishedAt,
            videoType = if (isShorts) "SHORTS" else "VIDEO"
        ))
    }

    // ==================== Diagnostic Logging ====================

    /**
     * richGridRenderer 구조를 진단 로깅 (lockupViewModel 확인용)
     */
    private fun logRichGridStructure(responseJson: JSONObject) {
        try {
            val tabRenderer = responseJson.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")?.let { tabs ->
                    for (i in 0 until tabs.length()) {
                        val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer")
                        if (tab != null) return@let tab
                    }
                    null
                }
            val richGridContents = tabRenderer?.optJSONObject("content")
                ?.optJSONObject("richGridRenderer")
                ?.optJSONArray("contents")
            if (richGridContents != null && richGridContents.length() > 0) {
                val firstItem = richGridContents.optJSONObject(0)
                    ?.optJSONObject("richItemRenderer")
                    ?.optJSONObject("content")
                if (firstItem != null) {
                    Log.d(TAG, "richItemRenderer.content 키: ${firstItem.keys().asSequence().toList()}")
                } else {
                    Log.d(TAG, "richItemRenderer.content 없음, 첫 항목 키: ${richGridContents.optJSONObject(0)?.keys()?.asSequence()?.toList()}")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "richGrid 구조 로깅 실패: ${e.message}")
        }
    }

    // ==================== Regex Fallback Helpers ====================

    /**
     * fallback으로 찾은 ID들에 대해 JSON 트리에서 메타데이터를 2차 탐색
     */
    private fun extractMetadataForIds(
        obj: Any?, targetIds: Set<String>, depth: Int = 0, result: MutableMap<String, VideoMeta> = mutableMapOf()
    ): Map<String, VideoMeta> {
        if (obj == null || depth > 35 || result.size >= targetIds.size) return result

        when (obj) {
            is JSONObject -> {
                // lockupViewModel에서 contentId 매칭
                val contentId = obj.optString("contentId", "")
                if (contentId in targetIds && contentId !in result) {
                    val videos = mutableListOf<VideoMeta>()
                    val seen = mutableSetOf<String>()
                    parseLockupViewModel(obj, videos, seen, "lockupViewModel")
                    if (videos.isNotEmpty()) {
                        result[contentId] = videos.first()
                    }
                }
                // videoRenderer에서 videoId 매칭
                val videoId = obj.optString("videoId", "")
                if (videoId in targetIds && videoId !in result) {
                    val videos = mutableListOf<VideoMeta>()
                    val seen = mutableSetOf<String>()
                    parseVideoRenderer(obj, videos, seen)
                    if (videos.isNotEmpty()) {
                        result[videoId] = videos.first()
                    }
                }

                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.opt(key)
                    if (value is JSONObject || value is JSONArray) {
                        extractMetadataForIds(value, targetIds, depth + 1, result)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until minOf(obj.length(), 300)) {
                    extractMetadataForIds(obj.opt(i), targetIds, depth + 1, result)
                }
            }
        }
        return result
    }

    // ==================== JSON Helpers ====================

    /**
     * lockupMetadataViewModel 트리에서 채널 아바타 URL을 재귀 탐색.
     * 위치 변형이 잦아(decoratedAvatarViewModel / avatarViewModel / image.sources)
     * 트리 어디든 sources URL이 yt3.ggpht.com 도메인이면 채택.
     */
    private fun extractLockupChannelThumbnail(metadataObj: JSONObject?): String {
        if (metadataObj == null) return ""
        var found = ""
        fun visit(node: Any?) {
            if (found.isNotEmpty()) return
            when (node) {
                is JSONObject -> {
                    val sources = node.optJSONArray("sources")
                    if (sources != null) {
                        for (i in 0 until sources.length()) {
                            val url = sources.optJSONObject(i)?.optString("url", "") ?: ""
                            if (url.contains("yt3.ggpht.com") || url.contains("yt3.googleusercontent.com")) {
                                found = url
                                return
                            }
                        }
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) visit(node.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
            }
        }
        visit(metadataObj)
        return found
    }

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

            // ViewModel 포맷 대비: accessibility.accessibilityData.label 경로
            val accessLabel = obj.optJSONObject("accessibility")
                ?.optJSONObject("accessibilityData")
                ?.optString("label", "") ?: ""
            if (accessLabel.isNotEmpty()) return accessLabel
        }

        return ""
    }
}

private val RENDERER_KEYS = listOf(
    "videoRenderer", "compactVideoRenderer", "gridVideoRenderer",
    "videoWithContextRenderer", "playlistVideoRenderer", "reelItemRenderer",
    "lockupViewModel", "shortsLockupViewModel"
)
