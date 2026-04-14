# Flutter
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }

# WebView JavaScript interfaces
-keepclassmembers class com.hashmeter.ytplayer.webview.bridges.** {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep BuildConfig fields
-keep class com.hashmeter.ytplayer.BuildConfig { *; }

# OkHttp / Gson (if used by dependencies)
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**

# Google Play Core (Flutter deferred components)
-dontwarn com.google.android.play.core.splitcompat.SplitCompatApplication
-dontwarn com.google.android.play.core.splitinstall.**
-dontwarn com.google.android.play.core.tasks.**

# 릴리스 빌드에서 디버그 로그 제거
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
