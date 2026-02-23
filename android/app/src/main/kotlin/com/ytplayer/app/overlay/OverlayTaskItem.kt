package com.ytplayer.app.overlay

/**
 * 오버레이 WebView가 처리할 작업 항목
 * 원본 앱의 ListsItem 모델에 대응
 *
 * [주의] 더미 URL 전용 - 실제 제휴 링크 사용 금지
 */
data class OverlayTaskItem(
    val idx: Int,
    val title: String,
    val url: String,          // 로드할 URL (더미)
    val targetPackage: String  // 감지할 대상 패키지명 (테스트용)
)

/**
 * 서버 응답 모델 (더미)
 * 원본 앱의 LiveCheckData에 대응
 */
data class OverlayTaskConfig(
    val taskId: String,
    val items: List<OverlayTaskItem>,
    val loopCount: Int,       // 반복 횟수
    val delayMs: Long,        // 작업 간 딜레이 (ms)
    val maxRedirects: Int     // 최대 리다이렉트 횟수
)

/**
 * 더미 테스트 데이터 생성기
 */
object DummyTaskProvider {

    /**
     * 테스트용 더미 작업 목록 생성
     * 실제 제휴 링크 없이 httpbin.org 등의 테스트 서비스 사용
     */
    fun createDummyConfig(): OverlayTaskConfig {
        val items = listOf(
            OverlayTaskItem(
                idx = 1,
                title = "테스트 리다이렉트 1",
                url = "https://httpbin.org/redirect/3",
                targetPackage = "com.test.dummy1"
            ),
            OverlayTaskItem(
                idx = 2,
                title = "테스트 HTML 페이지",
                url = "https://httpbin.org/html",
                targetPackage = "com.test.dummy2"
            ),
            OverlayTaskItem(
                idx = 3,
                title = "테스트 딜레이 응답",
                url = "https://httpbin.org/delay/2",
                targetPackage = "com.test.dummy3"
            ),
            OverlayTaskItem(
                idx = 4,
                title = "테스트 상태코드",
                url = "https://httpbin.org/status/302",
                targetPackage = "com.test.dummy4"
            ),
            OverlayTaskItem(
                idx = 5,
                title = "테스트 커스텀 스킴",
                url = "https://httpbin.org/redirect-to?url=testapp%3A%2F%2Fopen%3Fid%3D123",
                targetPackage = "com.test.dummy5"
            )
        )

        return OverlayTaskConfig(
            taskId = "dummy_${System.currentTimeMillis()}",
            items = items,
            loopCount = 1,
            delayMs = 3000L,
            maxRedirects = 10
        )
    }
}
