package com.ytplayer.app.shorts

import android.content.Context
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

/**
 * ShortsPlatformView 팩토리
 * Flutter AndroidView에서 'shorts-webview' viewType으로 생성
 */
class ShortsPlatformViewFactory(
    private val onViewCreated: (ShortsPlatformView) -> Unit
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val params = args as? Map<*, *>
        val url = params?.get("url") as? String ?: "https://m.youtube.com/shorts"
        val view = ShortsPlatformView(context, url)
        onViewCreated(view)
        return view
    }
}
