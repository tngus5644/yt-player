package com.ytplayer.app.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URISyntaxException

/**
 * 오버레이 WebView용 WebViewClient
 * 원본 앱의 WebViewClientStartCommService에 대응
 *
 * 리다이렉트 체인을 팔로잉하고 커스텀 스킴을 감지
 */
class OverlayWebViewClient(
    private val context: Context,
    private val maxRedirects: Int = 10,
    private val onLinkProcessed: (LinkResult) -> Unit
) : WebViewClient() {

    /**
     * 링크 처리 결과 (원본: CommProcess.LinkResult)
     */
    sealed class LinkResult {
        object Success : LinkResult()
        object Fail : LinkResult()
    }

    companion object {
        private const val TAG = "OverlayWebViewClient"

        // 커스텀 스킴 → 패키지명 매핑 (원본 WebViewClientStartCommService 동일)
        private val SCHEME_PACKAGE_MAP = mapOf(
            "coupang://" to "com.coupang.mobile",
            "aliexpress://" to "com.alibaba.aliexpresshd",
            "yanoljamotel://" to "com.cultsotry.yanolja.nativeapp",
            "ssg://" to "kr.co.ssg",
            "emartmall://" to "kr.co.emart.emartmall",
            "ctripglobal://" to "ctrip.english",
            "hotelsapp://" to "com.hcom.android"
        )

        private const val SCHEME_INTENT = "intent://"
    }

    private var redirectCount = 0
    private var isProcessed = false
    private var isStopProcess = false

    fun setStopProcess(stop: Boolean) {
        isStopProcess = stop
    }

    /**
     * 콜백 중복 호출 방지 (원본: safeProcess)
     * 한 페이지 로드당 1번만 콜백 호출
     */
    private fun safeProcess(result: LinkResult) {
        if (isProcessed) return
        isProcessed = true
        onLinkProcessed(result)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d(TAG, "onPageStarted: $url")
        redirectCount = 0
        isProcessed = false
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        Log.d(TAG, "onPageFinished: $url")
        super.onPageFinished(view, url)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        Log.d(TAG, "shouldOverrideUrlLoading: $url, Redirect Count: $redirectCount")

        // 최대 리다이렉트 초과
        if (redirectCount > maxRedirects) {
            Log.e(TAG, "Max redirect count exceeded for: $url")
            safeProcess(LinkResult.Fail)
            return true
        }

        // 중지 플래그
        if (isStopProcess) {
            return false
        }

        // 커스텀 스킴 매칭 (원본 동일 순서)
        for ((scheme, pkg) in SCHEME_PACKAGE_MAP) {
            if (url.startsWith(scheme)) {
                return handleCustomScheme(url, pkg)
            }
        }

        // intent:// 스킴 처리
        if (url.startsWith(SCHEME_INTENT)) {
            return handleIntentUri(view, url)
        }

        redirectCount++
        return false
    }

    /**
     * 커스텀 스킴 처리 (원본 handleCustomScheme 동일)
     * Intent.ACTION_VIEW로 대상 앱 패키지를 직접 실행
     */
    private fun handleCustomScheme(url: String, targetPackage: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(targetPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            checkAndStartActivity(intent)
            safeProcess(LinkResult.Success)
            true
        } catch (e: Exception) {
            Log.e(TAG, "handleCustomScheme 실패: $url", e)
            safeProcess(LinkResult.Fail)
            true
        }
    }

    /**
     * intent:// URI 처리 (원본 handleIntentUri 동일)
     * intent:// 스킴을 파싱하여 대상 앱 실행, 없으면 fallback URL 로드
     */
    private fun handleIntentUri(view: WebView, url: String): Boolean {
        return try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(view.context.packageManager) != null) {
                checkAndStartActivity(intent)
                safeProcess(LinkResult.Success)
                true
            } else {
                // 대상 앱 미설치 시 fallback URL 로드
                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                if (!fallbackUrl.isNullOrEmpty()) {
                    view.loadUrl(fallbackUrl)
                }
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleIntentUri 실패: $url", e)
            safeProcess(LinkResult.Fail)
            false
        }
    }

    /**
     * 화면 상태 확인 후 Activity 실행 (원본 checkAndStartActivity 동일)
     * 화면이 꺼져 있으면 앱 실행을 억제
     */
    private fun checkAndStartActivity(intent: Intent) {
        if (ScreenStateReceiver.isScreenOn) {
            context.startActivity(intent)
        } else {
            Log.d(TAG, "Screen off - suppressed startActivity for ${intent.data}")
        }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            safeProcess(LinkResult.Fail)
        }
    }

    /**
     * 다음 작업을 위해 상태 초기화
     */
    fun reset() {
        redirectCount = 0
        isProcessed = false
    }
}
