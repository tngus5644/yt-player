package com.ytplayer.app.webview

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ytplayer.app.channel.DataEventChannel
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * 보관함/기록/재생목록 API 관리
 * HomeFeedManager와 분리하여 단일 책임 유지
 */
class LibraryManager(
    private val apiClient: InnerTubeApiClient,
    private val dataEventChannel: DataEventChannel
) {
    companion object {
        private const val TAG = "YTPlayerLibrary"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var historyContinuationToken: String? = null

    /**
     * 보관함 메인 데이터 로드 (FElibrary → 폴백: FEhistory)
     * 이벤트: "libraryData" { historyVideos: [...], playlists: [...] }
     */
    fun fetchLibraryApi() {
        Log.d(TAG, "★ 보관함 API 호출 시작")
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { apiClient.fetchLibrary() }
                Log.d(TAG, "★★★ 보관함 API 성공: history=${result.historyVideos.length()}, playlists=${result.playlists.length()}")

                // 빈 결과 시 FEhistory 폴백
                if (result.historyVideos.length() == 0 && result.playlists.length() == 0) {
                    Log.w(TAG, "FElibrary 빈 결과 → FEhistory 폴백")
                    fallbackToHistory()
                    return@launch
                }

                sendLibraryEvent(result.historyVideos, result.playlists)
            } catch (e: Exception) {
                Log.e(TAG, "fetchLibraryApi 에러: ${e.message} → FEhistory 폴백", e)
                try {
                    fallbackToHistory()
                } catch (e2: Exception) {
                    Log.e(TAG, "FEhistory 폴백도 실패: ${e2.message}", e2)
                    sendLibraryEvent(JSONArray(), JSONArray())
                }
            }
        }
    }

    private suspend fun fallbackToHistory() {
        val historyResult = withContext(Dispatchers.IO) { apiClient.fetchHistory() }
        Log.d(TAG, "★★★ FEhistory 폴백 성공: ${historyResult.videos.length()}개")
        sendLibraryEvent(historyResult.videos, JSONArray())
    }

    private fun sendLibraryEvent(historyVideos: JSONArray, playlists: JSONArray) {
        mainHandler.post {
            dataEventChannel.sendEvent("libraryData", JSONObject().apply {
                put("historyVideos", historyVideos)
                put("playlists", playlists)
            })
        }
    }

    /**
     * 시청 기록 상세 로드 (FEhistory)
     * 이벤트: "historyList" { videoList: [...] }
     */
    fun fetchHistoryApi() {
        Log.d(TAG, "★ 시청 기록 API 호출 시작")
        historyContinuationToken = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { apiClient.fetchHistory() }
                historyContinuationToken = result.continuationToken
                Log.d(TAG, "★★★ 시청 기록 API 성공: ${result.videos.length()}개, continuation=${result.continuationToken != null}")
                mainHandler.post {
                    dataEventChannel.sendEvent("historyList", JSONObject().apply {
                        put("videoList", result.videos)
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchHistoryApi 에러: ${e.message}", e)
                mainHandler.post {
                    dataEventChannel.sendEvent("historyList", JSONObject().apply {
                        put("videoList", JSONArray())
                    })
                }
            }
        }
    }

    /**
     * 시청 기록 다음 페이지 (continuation 기반)
     * 이벤트: "historyListMore" { videoList: [...] }
     */
    fun fetchHistoryContinuation() {
        val token = historyContinuationToken
        if (token == null) {
            Log.d(TAG, "시청 기록 continuation 토큰 없음")
            mainHandler.post {
                dataEventChannel.sendEvent("historyListMore", JSONObject().apply {
                    put("videoList", JSONArray())
                })
            }
            return
        }

        Log.d(TAG, "★ 시청 기록 continuation 호출")
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { apiClient.fetchHistoryContinuation(token) }
                historyContinuationToken = result.continuationToken
                Log.d(TAG, "★★★ 시청 기록 continuation 성공: ${result.videos.length()}개")
                mainHandler.post {
                    dataEventChannel.sendEvent("historyListMore", JSONObject().apply {
                        put("videoList", result.videos)
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchHistoryContinuation 에러: ${e.message}", e)
                mainHandler.post {
                    dataEventChannel.sendEvent("historyListMore", JSONObject().apply {
                        put("videoList", JSONArray())
                    })
                }
            }
        }
    }

    /**
     * 재생목록 상세 동영상 로드 (VL{playlistId})
     * 이벤트: "playlistDetail" { playlistId: "...", videoList: [...] }
     */
    fun fetchPlaylistDetailApi(playlistId: String) {
        Log.d(TAG, "★ 재생목록 상세 API 호출: $playlistId")
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { apiClient.fetchPlaylistDetail(playlistId) }
                Log.d(TAG, "★★★ 재생목록 상세 API 성공: ${result.videos.length()}개")
                mainHandler.post {
                    dataEventChannel.sendEvent("playlistDetail", JSONObject().apply {
                        put("playlistId", playlistId)
                        put("videoList", result.videos)
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchPlaylistDetailApi 에러: ${e.message}", e)
                mainHandler.post {
                    dataEventChannel.sendEvent("playlistDetail", JSONObject().apply {
                        put("playlistId", playlistId)
                        put("videoList", JSONArray())
                    })
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }
}
