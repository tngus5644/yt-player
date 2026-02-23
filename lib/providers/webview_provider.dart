import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/channel/webview_channel.dart';
import '../models/webview/video_item.dart';
import '../models/webview/channel_item.dart';

/// WebView 채널 싱글톤
final webViewChannelProvider = Provider<WebViewChannel>((ref) {
  final channel = WebViewChannel();
  channel.startListening();
  ref.onDispose(() => channel.dispose());
  return channel;
});

/// 홈 영상 목록
final homeVideoListProvider =
    StateNotifierProvider<VideoListNotifier, AsyncValue<List<VideoItem>>>((ref) {
  return VideoListNotifier(ref.watch(webViewChannelProvider));
});

/// 쇼츠 영상 목록
final shortsVideoListProvider =
    StateNotifierProvider<ShortsListNotifier, AsyncValue<List<VideoItem>>>((ref) {
  return ShortsListNotifier(ref.watch(webViewChannelProvider));
});

/// 검색 결과 목록
final searchVideoListProvider =
    StateNotifierProvider<SearchListNotifier, AsyncValue<List<VideoItem>>>((ref) {
  return SearchListNotifier(ref.watch(webViewChannelProvider));
});

/// 구독 채널 목록
final subscriptionListProvider =
    StateNotifierProvider<SubscriptionListNotifier, AsyncValue<List<ChannelItem>>>((ref) {
  return SubscriptionListNotifier(ref.watch(webViewChannelProvider));
});

/// 로그인 상태
final loginStateProvider = StateNotifierProvider<LoginStateNotifier, bool>((ref) {
  return LoginStateNotifier(ref.watch(webViewChannelProvider));
});

/// 현재 탭 인덱스
final currentTabProvider = StateProvider<int>((ref) => 0);

// --- Notifiers ---

class VideoListNotifier extends StateNotifier<AsyncValue<List<VideoItem>>> {
  final WebViewChannel _channel;
  StreamSubscription? _sub;
  Timer? _timeoutTimer;
  final List<VideoItem> _videos = [];

  VideoListNotifier(this._channel) : super(const AsyncValue.loading()) {
    _sub = _channel.dataStream
        .where((e) =>
            e.type == WebViewEventType.videoList ||
            e.type == WebViewEventType.videoListMore)
        .listen(_onVideoData);
  }

  void _onVideoData(WebViewEvent event) {
    _timeoutTimer?.cancel();

    final list = (event.data?['videoList'] as List<dynamic>?)
            ?.map((e) => VideoItem.fromJson(Map<String, dynamic>.from(e)))
            .toList() ??
        [];

    if (event.type == WebViewEventType.videoList) {
      _videos.clear();
    }
    _videos.addAll(list);
    state = AsyncValue.data(List.unmodifiable(_videos));
  }

  Future<void> loadHomeFeed() async {
    state = const AsyncValue.loading();
    await _channel.loadHomeFeed();

    // 20초 타임아웃 → 데이터 미수신 시 빈 목록으로 전환 (무한 로딩 방지)
    _timeoutTimer?.cancel();
    _timeoutTimer = Timer(const Duration(seconds: 20), () {
      if (state is AsyncLoading) {
        state = const AsyncValue.data([]);
      }
    });
  }

  Future<void> loadMore() async {
    await _channel.scrollBottom();
  }

  @override
  void dispose() {
    _sub?.cancel();
    _timeoutTimer?.cancel();
    super.dispose();
  }
}

class ShortsListNotifier extends StateNotifier<AsyncValue<List<VideoItem>>> {
  final WebViewChannel _channel;
  StreamSubscription? _sub;

  ShortsListNotifier(this._channel) : super(const AsyncValue.loading()) {
    _sub = _channel.dataStream
        .where((e) => e.type == WebViewEventType.shortsList)
        .listen(_onData);
  }

  void _onData(WebViewEvent event) {
    final list = (event.data?['videoList'] as List<dynamic>?)
            ?.map((e) => VideoItem.fromJson(Map<String, dynamic>.from(e)))
            .toList() ??
        [];
    state = AsyncValue.data(list);
  }

  Future<void> load() async {
    state = const AsyncValue.loading();
    await _channel.loadShorts();
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }
}

class SearchListNotifier extends StateNotifier<AsyncValue<List<VideoItem>>> {
  final WebViewChannel _channel;
  StreamSubscription? _sub;

  SearchListNotifier(this._channel) : super(const AsyncValue.data([])) {
    _sub = _channel.dataStream
        .where((e) => e.type == WebViewEventType.searchResults)
        .listen(_onData);
  }

  void _onData(WebViewEvent event) {
    final list = (event.data?['videoList'] as List<dynamic>?)
            ?.map((e) => VideoItem.fromJson(Map<String, dynamic>.from(e)))
            .toList() ??
        [];
    state = AsyncValue.data(list);
  }

  Future<void> search(String query) async {
    if (query.trim().isEmpty) return;
    state = const AsyncValue.loading();
    await _channel.search(query);
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }
}

class SubscriptionListNotifier extends StateNotifier<AsyncValue<List<ChannelItem>>> {
  final WebViewChannel _channel;
  StreamSubscription? _sub;

  SubscriptionListNotifier(this._channel) : super(const AsyncValue.loading()) {
    _sub = _channel.dataStream
        .where((e) => e.type == WebViewEventType.subscriptions)
        .listen(_onData);
  }

  void _onData(WebViewEvent event) {
    final list = (event.data?['channelList'] as List<dynamic>?)
            ?.map((e) => ChannelItem.fromJson(Map<String, dynamic>.from(e)))
            .toList() ??
        [];
    state = AsyncValue.data(list);
  }

  Future<void> load() async {
    state = const AsyncValue.loading();
    await _channel.loadSubscriptions();
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }
}

class LoginStateNotifier extends StateNotifier<bool> {
  final WebViewChannel _channel;
  StreamSubscription? _sub;

  LoginStateNotifier(this._channel) : super(false) {
    _sub = _channel.dataStream
        .where((e) => e.type == WebViewEventType.loginState)
        .listen((e) => state = e.data?['isLogin'] as bool? ?? false);
    _checkLoginState();
  }

  Future<void> _checkLoginState() async {
    state = await _channel.isLoggedIn();
  }

  Future<void> signIn() async {
    await _channel.startSignIn();
    // PlayerActivity에서 Google 로그인 완료 후 쿠키 기반으로 확인
    // 2초 간격으로 최대 60초간 로그인 상태 폴링
    for (int i = 0; i < 30; i++) {
      await Future.delayed(const Duration(seconds: 2));
      final loggedIn = await _channel.isLoggedIn();
      if (loggedIn) {
        state = true;
        return;
      }
    }
  }

  Future<void> signOut() async {
    await _channel.signOut();
    state = false;
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }
}
