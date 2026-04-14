package com.hashmeter.ytplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar

/**
 * Google 소셜 로그인 전용 Activity
 * User-Agent에서 "; wv" 제거하여 Google 계정 선택기/OAuth 정상 작동
 */
class WebViewSignInActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    companion object {
        private const val TAG = "WebViewSignIn"
        private const val SIGN_IN_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fm.youtube.com%2F"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }
        setContentView(root)

        initWebView(root)

        webView.loadUrl(SIGN_IN_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(root: FrameLayout) {
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                // 핵심: "; wv" 제거하여 Google OAuth 제한 우회
                userAgentString = userAgentString.replace("; wv", "")
            }

            // Third-party cookies 허용 (OAuth 리다이렉트용)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = false

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    checkLoginComplete(url)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.visibility =
                        if (newProgress < 100) ProgressBar.VISIBLE else ProgressBar.GONE
                }

                // OAuth 팝업 윈도우 지원
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    val newWebView = WebView(this@WebViewSignInActivity).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            userAgentString = userAgentString.replace("; wv", "")
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                checkLoginComplete(url)
                            }
                        }
                    }
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    transport?.webView = newWebView
                    resultMsg?.sendToTarget()
                    return true
                }
            }
        }

        root.addView(webView)

        // 상단 로딩 프로그레스바
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                8 // dp가 아닌 px이지만 얇은 바로 충분
            )
            max = 100
            isIndeterminate = false
        }
        root.addView(progressBar)
    }

    /**
     * 로그인 완료 감지: m.youtube.com 도달 + SID/SSID 쿠키 존재 확인
     */
    private fun checkLoginComplete(url: String?) {
        if (url == null) return
        if (!url.contains("m.youtube.com") && !url.contains("www.youtube.com")) return

        val cookies = CookieManager.getInstance().getCookie("https://m.youtube.com") ?: ""
        if (cookies.contains("SID=") || cookies.contains("SSID=")) {
            CookieManager.getInstance().flush()
            setResult(RESULT_OK)
            finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            setResult(RESULT_CANCELED)
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
