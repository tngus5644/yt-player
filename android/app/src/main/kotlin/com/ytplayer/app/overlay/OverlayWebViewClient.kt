package com.ytplayer.app.overlay

import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 오버레이 WebView용 WebViewClient
 * 원본 앱의 WebViewClientStartCommService에 대응
 *
 * 리다이렉트 체인을 팔로잉하고 커스텀 스킴을 감지
 */
class OverlayWebViewClient(
    private val maxRedirects: Int = 10,
    private val onCustomSchemeDetected: (String) -> Unit,
    private val onTaskCompleted: (Boolean, String) -> Unit
) : WebViewClient() {

    companion object {
        private const val TAG = "OverlayWebViewClient"

        // 테스트용 커스텀 스킴 목록
        // 원본 앱: coupang://, aliexpress://, yanoljamotel://, ssg://, emartmall://, ctripglobal://, hotelsapp://
        // 여기선 더미 스킴만 사용
        val CUSTOM_SCHEMES = listOf(
            "testapp://",
            "dummyshop://",
            "sampleapp://",
            "mockstore://",
            "demoapp://"
        )
    }

    private var redirectCount = 0
    private var currentUrl: String = ""
    private var isCompleted = false

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        currentUrl = url

        Log.d(TAG, "리다이렉트 #$redirectCount → $url")

        // 커스텀 스킴 감지
        if (isCustomScheme(url)) {
            Log.d(TAG, "커스텀 스킴 감지: $url")
            handleCustomScheme(url)
            return true
        }

        // 최대 리다이렉트 초과
        redirectCount++
        if (redirectCount > maxRedirects) {
            Log.w(TAG, "최대 리다이렉트 횟수 초과 ($maxRedirects)")
            completeTask(false, "최대 리다이렉트 초과")
            return true
        }

        return false // WebView에서 계속 로딩
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d(TAG, "페이지 로드 완료: $url (리다이렉트: $redirectCount)")

        // 리다이렉트가 끝나고 최종 페이지에 도달
        if (!isCompleted && redirectCount > 0) {
            completeTask(true, "최종 URL: $url")
        }
    }

    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        Log.e(TAG, "WebView 에러: $errorCode - $description ($failingUrl)")

        // 커스텀 스킴으로 인한 에러는 성공으로 처리
        if (failingUrl != null && isCustomScheme(failingUrl)) {
            handleCustomScheme(failingUrl)
            return
        }

        completeTask(false, "에러: $description")
    }

    private fun isCustomScheme(url: String): Boolean {
        val scheme = Uri.parse(url).scheme ?: return false
        // http/https가 아닌 스킴은 커스텀 스킴으로 간주
        if (scheme != "http" && scheme != "https") {
            return true
        }
        return CUSTOM_SCHEMES.any { url.startsWith(it) }
    }

    private fun handleCustomScheme(url: String) {
        if (isCompleted) return

        Log.d(TAG, "커스텀 스킴 처리: $url")

        // 원본 앱에서는 여기서 실제 앱을 실행함
        // 학습용이므로 감지만 하고 콜백 호출
        onCustomSchemeDetected(url)
        completeTask(true, "커스텀 스킴 감지: $url")
    }

    private fun completeTask(success: Boolean, message: String) {
        if (isCompleted) return
        isCompleted = true

        Log.d(TAG, "작업 완료: success=$success, message=$message")
        onTaskCompleted(success, message)
    }

    /**
     * 다음 작업을 위해 상태 초기화
     */
    fun reset() {
        redirectCount = 0
        currentUrl = ""
        isCompleted = false
    }
}
