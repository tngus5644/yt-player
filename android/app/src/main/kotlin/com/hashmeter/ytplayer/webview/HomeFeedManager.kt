package com.hashmeter.ytplayer.webview

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hashmeter.ytplayer.channel.DataEventChannel
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * 홈피드 로드, 페이지네이션, 폴백 체인 담당
 * Thread 대신 Coroutine 사용
 */
class HomeFeedManager(
    private val apiClient: InnerTubeApiClient,
    private val dataEventChannel: DataEventChannel
) {
    companion object {
        private const val TAG = "YTPlayerWebView"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private enum class PaginationSource { BROWSE, SEARCH }

    @Volatile var dataReceived = false
    @Volatile private var homeContinuationToken: String? = null
    @Volatile private var searchContinuationToken: String? = null
    @Volatile private var paginationSource = PaginationSource.BROWSE

    /**
     * InnerTube API로 직접 홈피드를 가져오는 폴백 체인
     * 1차: 홈 피드 → 2차: 트렌딩 → 3차: 검색
     */
    fun fetchVideosDirectApi() {
        if (dataReceived) return
        Log.d(TAG, "★ 직접 InnerTube API 호출 시작")

        scope.launch {
            try {
                // 1차: 홈 피드
                val homeResult = runCatching {
                    withContext(Dispatchers.IO) { apiClient.fetchHomeFeed() }
                }
                homeResult.getOrNull()?.let { result ->
                    if (result.videos.length() > 0) {
                        Log.d(TAG, "★★★ API 홈피드 성공: ${result.videos.length()}개")
                        homeContinuationToken = result.continuationToken
                        paginationSource = PaginationSource.BROWSE
                        dataReceived = true
                        sendVideoEvent("videoList", "home", result.videos)
                        return@launch
                    }
                }
                homeResult.exceptionOrNull()?.let {
                    Log.w(TAG, "API 홈피드 실패: ${it.message} → 트렌딩으로 폴백")
                }

                if (dataReceived) return@launch

                // 2차: 트렌딩
                val trendingResult = runCatching {
                    withContext(Dispatchers.IO) { apiClient.fetchTrending() }
                }
                trendingResult.getOrNull()?.let { result ->
                    if (result.videos.length() > 0) {
                        Log.d(TAG, "★★★ API 트렌딩 성공: ${result.videos.length()}개")
                        homeContinuationToken = result.continuationToken
                        paginationSource = PaginationSource.BROWSE
                        dataReceived = true
                        sendVideoEvent("videoList", "home", result.videos)
                        return@launch
                    }
                }
                trendingResult.exceptionOrNull()?.let {
                    Log.w(TAG, "API 트렌딩 실패: ${it.message} → 검색으로 폴백")
                }

                if (dataReceived) return@launch

                // 3차: 검색
                val searchResult = withContext(Dispatchers.IO) {
                    apiClient.fetchSearchWithContinuation("인기 동영상")
                }
                if (searchResult.videos.length() > 0) {
                    Log.d(TAG, "★★★ API 검색 성공: ${searchResult.videos.length()}개")
                    searchContinuationToken = searchResult.continuationToken
                    paginationSource = PaginationSource.SEARCH
                    dataReceived = true
                    sendVideoEvent("videoList", "home", searchResult.videos)
                } else {
                    Log.w(TAG, "★ 모든 직접 API 호출도 실패!")
                    sendVideoEvent("videoList", "home", JSONArray())
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchVideosDirectApi 에러: ${e.message}", e)
                sendVideoEvent("videoList", "home", JSONArray())
            }
        }
    }

    /**
     * 홈피드 다음 페이지 로드 (continuation 기반)
     */
    fun loadMoreHomeFeed() {
        when (paginationSource) {
            PaginationSource.BROWSE -> {
                val token = homeContinuationToken
                if (token == null) {
                    Log.d(TAG, "loadMoreHomeFeed: browse continuation 소진 → 검색으로 전환")
                    loadMoreViaSearch()
                    return
                }

                Log.d(TAG, "★ loadMoreHomeFeed: browse continuation 요청 시작")
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            apiClient.fetchBrowseContinuation(token)
                        }
                        homeContinuationToken = result.continuationToken
                        Log.d(TAG, "★★★ loadMoreHomeFeed(browse) 성공: ${result.videos.length()}개")
                        sendVideoEvent("videoListMore", "home", result.videos)
                    } catch (e: Exception) {
                        Log.e(TAG, "loadMoreHomeFeed(browse) 에러: ${e.message}", e)
                        sendVideoEvent("videoListMore", "home", JSONArray())
                    }
                }
            }
            PaginationSource.SEARCH -> {
                val token = searchContinuationToken
                if (token == null) {
                    Log.d(TAG, "loadMoreHomeFeed: search continuation 소진 → 빈 응답")
                    sendVideoEvent("videoListMore", "home", JSONArray())
                    return
                }

                Log.d(TAG, "★ loadMoreHomeFeed: search continuation 요청 시작")
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            apiClient.fetchSearchContinuation(token)
                        }
                        searchContinuationToken = result.continuationToken
                        Log.d(TAG, "★★★ loadMoreHomeFeed(search) 성공: ${result.videos.length()}개")
                        sendVideoEvent("videoListMore", "home", result.videos)
                    } catch (e: Exception) {
                        Log.e(TAG, "loadMoreHomeFeed(search) 에러: ${e.message}", e)
                        sendVideoEvent("videoListMore", "home", JSONArray())
                    }
                }
            }
        }
    }

    /**
     * 검색 API 호출
     */
    fun fetchSearchApi(query: String) {
        Log.d(TAG, "★ 검색 API 호출: $query")
        scope.launch {
            try {
                val videos = withContext(Dispatchers.IO) { apiClient.fetchSearch(query) }
                Log.d(TAG, "★★★ 검색 API 성공: ${videos.length()}개")
                dataEventChannel.sendEvent("searchResults", JSONObject().apply {
                    put("videoList", videos)
                })
            } catch (e: Exception) {
                Log.e(TAG, "fetchSearchApi 에러: ${e.message}", e)
                dataEventChannel.sendEvent("searchResults", JSONObject().apply {
                    put("videoList", JSONArray())
                })
            }
        }
    }

    /**
     * 쇼츠 API 호출 (FEshorts browse → 검색 폴백)
     */
    fun fetchShortsApi() {
        Log.d(TAG, "★ 쇼츠 API 호출 시작")
        scope.launch {
            try {
                val shortsArray = withContext(Dispatchers.IO) { apiClient.fetchShorts() }
                if (shortsArray.length() > 0) {
                    Log.d(TAG, "★★★ 쇼츠 browse 성공: ${shortsArray.length()}개")
                    dataEventChannel.sendEvent("shortsList", JSONObject().apply {
                        put("videoList", shortsArray)
                    })
                    return@launch
                }
                Log.w(TAG, "FEshorts 결과 없음 → 검색 폴백")
                fallbackToShortsSearch()
            } catch (e: Exception) {
                Log.e(TAG, "fetchShortsApi 에러: ${e.message} → 검색 폴백", e)
                try {
                    fallbackToShortsSearch()
                } catch (e2: Exception) {
                    Log.e(TAG, "쇼츠 검색 폴백도 실패: ${e2.message}", e2)
                    dataEventChannel.sendEvent("shortsList", JSONObject().apply {
                        put("videoList", JSONArray())
                    })
                }
            }
        }
    }

    private suspend fun fallbackToShortsSearch() {
        val shortsArray = withContext(Dispatchers.IO) { apiClient.fetchShortsViaSearch() }
        Log.d(TAG, "★★★ 쇼츠 검색 폴백 성공: ${shortsArray.length()}개")
        dataEventChannel.sendEvent("shortsList", JSONObject().apply {
            put("videoList", shortsArray)
        })
    }

    /**
     * 구독 피드 API 호출
     */
    fun fetchSubscriptionFeedApi() {
        Log.d(TAG, "★ 구독 피드 API 호출 시작")
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { apiClient.fetchSubscriptionFeed() }
                Log.d(TAG, "★★★ 구독 피드 API 성공: ${result.videos.length()}개")

                // 구독 피드 영상에서 고유 채널 목록 추출하여 subscriptions 이벤트 전송
                val channelList = extractChannelsFromVideos(result.videos)
                if (channelList.length() > 0) {
                    Log.d(TAG, "★ 구독 피드에서 채널 ${channelList.length()}개 추출")
                    mainHandler.post {
                        dataEventChannel.sendEvent("subscriptions", JSONObject().apply {
                            put("channelList", channelList)
                        })
                    }
                }

                mainHandler.post {
                    dataEventChannel.sendEvent("subscriptionFeed", JSONObject().apply {
                        put("type", "subscriptions")
                        put("videoList", result.videos)
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchSubscriptionFeedApi 에러: ${e.message}", e)
                mainHandler.post {
                    dataEventChannel.sendEvent("subscriptionFeed", JSONObject().apply {
                        put("type", "subscriptions")
                        put("videoList", JSONArray())
                    })
                }
            }
        }
    }

    fun resetForRefresh() {
        dataReceived = false
        homeContinuationToken = null
        searchContinuationToken = null
        paginationSource = PaginationSource.BROWSE
    }

    fun destroy() {
        scope.cancel()
    }

    private fun loadMoreViaSearch() {
        Log.d(TAG, "★ loadMoreViaSearch: 검색 기반 페이지네이션 시작")
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiClient.fetchSearchWithContinuation("인기 동영상")
                }
                searchContinuationToken = result.continuationToken
                paginationSource = PaginationSource.SEARCH
                Log.d(TAG, "★★★ loadMoreViaSearch 성공: ${result.videos.length()}개")
                sendVideoEvent("videoListMore", "home", result.videos)
            } catch (e: Exception) {
                Log.e(TAG, "loadMoreViaSearch 에러: ${e.message}", e)
                sendVideoEvent("videoListMore", "home", JSONArray())
            }
        }
    }

    private fun extractChannelsFromVideos(videos: JSONArray): JSONArray {
        val channelList = JSONArray()
        val seen = linkedSetOf<String>()

        for (i in 0 until videos.length()) {
            val video = videos.optJSONObject(i) ?: continue
            val channelName = video.optString("channel", "")
            var channelThumb = video.optString("channelThumbnail", "")

            if (channelName.isEmpty() || channelName in seen) continue
            seen.add(channelName)

            // protocol-relative URL 처리
            if (channelThumb.startsWith("//")) {
                channelThumb = "https:$channelThumb"
            }

            channelList.put(JSONObject().apply {
                put("id", channelName)
                put("title", channelName)
                put("thumbnail", channelThumb)
                put("handle", "")
                put("isLive", false)
                put("hasNew", false)
            })
        }
        return channelList
    }

    private fun sendVideoEvent(eventType: String, type: String, videos: JSONArray) {
        mainHandler.post {
            dataEventChannel.sendEvent(eventType, JSONObject().apply {
                put("type", type)
                put("videoList", videos)
            })
        }
    }
}
