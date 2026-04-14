import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/channel/webview_channel.dart';
import '../core/constants/app_constants.dart';
import '../models/webview/video_item.dart';
import '../models/webview/channel_item.dart';
import '../models/webview/library_data.dart';
import 'reward_provider.dart';

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

/// 보관함 데이터 (기록 프리뷰 + 재생목록)
final libraryDataProvider =
    StateNotifierProvider<LibraryDataNotifier, AsyncValue<LibraryData>>((ref) {
  return LibraryDataNotifier(ref.watch(webViewChannelProvider));
});

/// 시청 기록 목록 (상세)
final historyListProvider =
    StateNotifierProvider<HistoryListNotifier, AsyncValue<List<VideoItem>>>((ref) {
  return HistoryListNotifier(ref.watch(webViewChannelProvider));
});

/// 재생목록 상세 (동영상 목록)
final playlistDetailProvider =
    StateNotifierProvider<PlaylistDetailNotifier, AsyncValue<List<VideoItem>>>((ref) {
  return PlaylistDetailNotifier(ref.watch(webViewChannelProvider));
});

/// 구독 피드 영상 목록
final subscriptionFeedProvider =
    StateNotifierProvider<SubscriptionFeedNotifier, AsyncValue<List<VideoItem>>>((ref) {
  return SubscriptionFeedNotifier(ref.watch(webViewChannelProvider));
});

/// Google 사용자 정보
class AuthUser {
  final String displayName;
  final String email;
  final String? photoUrl;

  const AuthUser({
    required this.displayName,
    required this.email,
    this.photoUrl,
  });
}

/// Google 사용자 정보 Provider
final googleUserProvider = StateProvider<AuthUser?>((ref) => null);

/// 로그인 상태 (WebView 기반)
final loginStateProvider = StateNotifierProvider<LoginStateNotifier, bool>((ref) {
  return LoginStateNotifier(
    ref.watch(webViewChannelProvider),
    ref,
  );
});

/// 현재 탭 인덱스
final currentTabProvider = StateProvider<int>((ref) => 0);

// --- Video List State ---

class VideoListState {
  final List<VideoItem> videos;
  final bool hasMore;
  final bool isLoadingMore;

  const VideoListState({
    this.videos = const [],
    this.hasMore = true,
    this.isLoadingMore = false,
  });

  VideoListState copyWith({
    List<VideoItem>? videos,
    bool? hasMore,
    bool? isLoadingMore,
  }) {
    return VideoListState(
      videos: videos ?? this.videos,
      hasMore: hasMore ?? this.hasMore,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
    );
  }
}

// --- Notifiers ---

class VideoListNotifier extends StateNotifier<AsyncValue<List<VideoItem>>> {
  final WebViewChannel _channel;
  StreamSubscription? _sub;
  Timer? _timeoutTimer;
  Timer? _loadMoreTimer;
  Completer<void>? _refreshCompleter;

  VideoListState _listState = const VideoListState();

  bool get hasMore => _listState.hasMore;

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
      _listState = VideoListState(videos: list, hasMore: true);
    } else if (event.type == WebViewEventType.videoListMore) {
      _loadMoreTimer?.cancel();
      _listState = _listState.copyWith(
        videos: [..._listState.videos, ...list],
        hasMore: list.isNotEmpty,
        isLoadingMore: false,
      );
    }

    state = AsyncValue.data(List.unmodifiable(_listState.videos));

    _refreshCompleter?.complete();
    _refreshCompleter = null;
  }

  Future<void> loadHomeFeed({bool isRefresh = false}) async {
    _listState = const VideoListState();
    _loadMoreTimer?.cancel();

    _refreshCompleter?.complete();
    final completer = Completer<void>();
    _refreshCompleter = completer;

    if (!isRefresh) {
      state = const AsyncValue.loading();
    }

    await _channel.loadHomeFeed(isRefresh: isRefresh);

    _timeoutTimer?.cancel();
    _timeoutTimer = Timer(const Duration(seconds: 20), () {
      if (!completer.isCompleted) {
        if (state is AsyncLoading) {
          state = const AsyncValue.data([]);
        }
        completer.complete();
      }
    });

    return completer.future;
  }

  Future<void> loadMore() async {
    if (_listState.isLoadingMore || !_listState.hasMore) return;
    _listState = _listState.copyWith(isLoadingMore: true);
    await _channel.loadMoreHomeFeed();

    _loadMoreTimer?.cancel();
    _loadMoreTimer = Timer(const Duration(seconds: 10), () {
      if (_listState.isLoadingMore) {
        _listState = _listState.copyWith(isLoadingMore: false, hasMore: false);
        state = AsyncValue.data(List.unmodifiable(_listState.videos));
      }
    });
  }

  @override
  void dispose() {
    _sub?.cancel();
    _timeoutTimer?.cancel();
    _loadMoreTimer?.cancel();
    super.dispose();
  }
}

class ShortsListNotifier extends StateNotifier<AsyncValue<List<VideoItem>>> {
  final WebViewChannel _channel;
  StreamSubscription? _sub;
  Completer<void>? _refreshCompleter;
  Timer? _timeoutTimer;

  ShortsListNotifier(this._channel) : super(const AsyncValue.loading()) {
    _sub = _channel.dataStream
        .where((e) => e.type == WebViewEventType.shortsList)
        .listen(_onData);
  }

  void _onData(WebViewEvent event) {
    _timeoutTimer?.cancel();

    final list = (event.data?['videoList'] as List<dynamic>?)
            ?.map((e) => VideoItem.fromJson(Map<String, dynamic>.from(e)))
            .toList() ??
        [];
    state = AsyncValue.data(list);

    _refreshCompleter?.complete();
    _refreshCompleter = null;
  }

  Future<void> load({bool isRefresh = false}) async {
    _refreshCompleter?.complete();
    final completer = Completer<void>();
    _refreshCompleter = completer;

    if (!isRefresh) {
      state = const AsyncValue.loading();
    }

    await _channel.loadShorts();

    _timeoutTimer?.cancel();
    _timeoutTimer = Timer(const Duration(seconds: 20), () {
      if (!completer.isCompleted) {
        if (state is AsyncLoading) {
          state = const AsyncValue.data([]);
        }
        completer.complete();
      }
    });

    return completer.future;
  }

  @override
  void dispose() {
    _sub?.cancel();
    _timeoutTimer?.cancel();
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
  Completer<void>? _loadCompleter;
  Timer? _timeoutTimer;

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
    _timeoutTimer?.cancel();
    _loadCompleter?.complete();
    _loadCompleter = null;
  }

  Future<void> load() async {
    _loadCompleter?.complete();
    final completer = Completer<void>();
    _loadCompleter = completer;

    state = const AsyncValue.loading();
    await _channel.loadSubscriptions();

    _timeoutTimer?.cancel();
    _timeoutTimer = Timer(const Duration(seconds: 15), () {
      if (!completer.isCompleted) {
        if (state is AsyncLoading) {
          state = const AsyncValue.data([]);
        }
        completer.complete();
      }
    });

    return completer.future;
  }

  @override
  void dispose() {
    _sub?.cancel();
    _timeoutTimer?.cancel();
    super.dispose();
  }
}

class SubscriptionFeedNotifier extends StateNotifier<AsyncValue<List<VideoItem>>> {
  final WebViewChannel _channel;
  StreamSubscription? _sub;
  Completer<void>? _loadCompleter;
  Timer? _timeoutTimer;

  SubscriptionFeedNotifier(this._channel) : super(const AsyncValue.loading()) {
    _sub = _channel.dataStream
        .where((e) => e.type == WebViewEventType.subscriptionFeed)
        .listen(_onData);
  }

  void _onData(WebViewEvent event) {
    final list = (event.data?['videoList'] as List<dynamic>?)
            ?.map((e) => VideoItem.fromJson(Map<String, dynamic>.from(e)))
            .toList() ??
        [];
    state = AsyncValue.data(list);
    _timeoutTimer?.cancel();
    _loadCompleter?.complete();
    _loadCompleter = null;
  }

  Future<void> load() async {
    _loadCompleter?.complete();
    final completer = Completer<void>();
    _loadCompleter = completer;

    state = const AsyncValue.loading();
    await _channel.loadSubscriptions();

    _timeoutTimer?.cancel();
    _timeoutTimer = Timer(const Duration(seconds: 20), () {
      if (!completer.isCompleted) {
        if (state is AsyncLoading) {
          state = const AsyncValue.data([]);
        }
        completer.complete();
      }
    });

    return completer.future;
  }

  @override
  void dispose() {
    _sub?.cancel();
    _timeoutTimer?.cancel();
    super.dispose();
  }
}

class LoginStateNotifier extends StateNotifier<bool> {
  final WebViewChannel _channel;
  final Ref _ref;
  StreamSubscription? _loginSub;

  LoginStateNotifier(this._channel, this._ref) : super(false) {
    // loginState 이벤트 리스닝 (WebViewSignInActivity 결과)
    _loginSub = _channel.dataStream
        .where((e) => e.type == WebViewEventType.loginState)
        .listen(_onLoginStateEvent);
    _restoreSession();
  }

  /// WebView 로그인 시작 (WebViewSignInActivity 실행)
  Future<void> signIn() async {
    await _channel.startSignIn();
  }

  /// WebView 로그인 성공 이벤트 수신
  void _onLoginStateEvent(WebViewEvent event) {
    final isLogin = event.data?['isLogin'] as bool? ?? false;
    if (isLogin) {
      _onWebViewLoginSuccess();
    } else {
      state = false;
    }
  }

  /// WebView 로그인 성공 후 백엔드 인증
  Future<void> _onWebViewLoginSuccess() async {
    bool tokenSaved = false;
    try {
      final api = _ref.read(rewardApiClientProvider);
      final response = await api.authLogin();
      if (response.success && response.token != null) {
        final prefs = _ref.read(sharedPreferencesProvider);
        prefs.setString(AppConstants.prefAccessToken, response.token!);
        tokenSaved = true;
        if (response.user != null) {
          _ref.read(googleUserProvider.notifier).state = AuthUser(
            displayName: response.user!.name,
            email: response.user!.email,
            photoUrl: response.user!.profilePhotoUrl,
          );
        }
      }
    } catch (_) {
      // authLogin 실패 시 무시
    }
    if (!tokenSaved) {
      // 토큰 미저장 시 다음 복원에서 네이티브 쿠키로 재시도
    }
    // WebViewSignInActivity에서 Google 로그인 완료 직후이므로 state = true
    state = true;
  }

  /// 앱 시작 시 세션 복원 (2단계)
  /// Phase 1: 저장된 JWT 토큰으로 검증
  /// Phase 2: 토큰 없거나 만료 시 네이티브 WebView 쿠키로 복원
  Future<void> _restoreSession() async {
    final prefs = _ref.read(sharedPreferencesProvider);
    final token = prefs.getString(AppConstants.prefAccessToken);

    // Phase 1: 기존 토큰으로 시도
    if (token != null && token.isNotEmpty) {
      try {
        final api = _ref.read(rewardApiClientProvider);
        final response = await api.getAuthUser();
        if (response.success && response.user != null) {
          _ref.read(googleUserProvider.notifier).state = AuthUser(
            displayName: response.user!.name,
            email: response.user!.email,
            photoUrl: response.user!.profilePhotoUrl,
          );
          state = true;
          return;
        }
      } on DioException catch (e) {
        if (e.response?.statusCode != 401) {
          // 네트워크 에러 (토큰 만료가 아님) → 낙관적으로 로그인 유지
          state = true;
          return;
        }
        // 401 → 토큰 만료, Phase 2로 이동
        // 401 토큰 만료 → Phase 2로 이동
      } catch (_) {
        // 알 수 없는 에러 → 낙관적으로 로그인 유지
        state = true;
        return;
      }
    }

    // Phase 2: 네이티브 WebView 쿠키로 복원
    await _tryRestoreFromNativeCookie();
  }

  /// 네이티브 CookieManager의 Google 세션 쿠키로 로그인 복원
  Future<void> _tryRestoreFromNativeCookie() async {
    try {
      final nativeLoggedIn = await _channel.isLoggedIn();
      if (!nativeLoggedIn) {
        state = false;
        return;
      }

      // 네이티브 쿠키 유효 → 새 백엔드 토큰 발급 시도
      try {
        final api = _ref.read(rewardApiClientProvider);
        final response = await api.authLogin();
        if (response.success && response.token != null) {
          final prefs = _ref.read(sharedPreferencesProvider);
          prefs.setString(AppConstants.prefAccessToken, response.token!);
          if (response.user != null) {
            _ref.read(googleUserProvider.notifier).state = AuthUser(
              displayName: response.user!.name,
              email: response.user!.email,
              photoUrl: response.user!.profilePhotoUrl,
            );
          }
        }
      } catch (_) {
        // 쿠키 기반 토큰 재발급 실패
      }
      // Google 세션 유효 → 백엔드 토큰 유무와 관계없이 로그인 상태
      state = true;
    } catch (_) {
      state = false;
    }
  }

  /// 로그아웃
  Future<void> signOut() async {
    try {
      final api = _ref.read(rewardApiClientProvider);
      await api.authLogout();
    } catch (_) {}
    _ref.read(googleUserProvider.notifier).state = null;
    await _channel.signOut();
    final prefs = _ref.read(sharedPreferencesProvider);
    prefs.remove(AppConstants.prefAccessToken);
    state = false;
  }

  @override
  void dispose() {
    _loginSub?.cancel();
    super.dispose();
  }
}

// --- Library ---

class LibraryDataNotifier extends StateNotifier<AsyncValue<LibraryData>> {
  final WebViewChannel _channel;
  StreamSubscription? _sub;
  Completer<void>? _loadCompleter;
  Timer? _timeoutTimer;

  LibraryDataNotifier(this._channel) : super(const AsyncValue.loading()) {
    _sub = _channel.dataStream
        .where((e) => e.type == WebViewEventType.libraryData)
        .listen(_onData);
  }

  void _onData(WebViewEvent event) {
    _timeoutTimer?.cancel();
    final data = LibraryData.fromJson(event.data ?? {});
    state = AsyncValue.data(data);
    _loadCompleter?.complete();
    _loadCompleter = null;
  }

  Future<void> load() async {
    _loadCompleter?.complete();
    final completer = Completer<void>();
    _loadCompleter = completer;

    state = const AsyncValue.loading();
    await _channel.loadLibrary();

    _timeoutTimer?.cancel();
    _timeoutTimer = Timer(const Duration(seconds: 20), () {
      if (!completer.isCompleted) {
        if (state is AsyncLoading) {
          state = AsyncValue.data(const LibraryData());
        }
        completer.complete();
      }
    });

    return completer.future;
  }

  @override
  void dispose() {
    _sub?.cancel();
    _timeoutTimer?.cancel();
    super.dispose();
  }
}

// --- History ---

class HistoryListNotifier extends StateNotifier<AsyncValue<List<VideoItem>>> {
  final WebViewChannel _channel;
  StreamSubscription? _sub;
  Timer? _timeoutTimer;
  Timer? _loadMoreTimer;
  Completer<void>? _refreshCompleter;

  VideoListState _listState = const VideoListState();
  bool get hasMore => _listState.hasMore;

  HistoryListNotifier(this._channel) : super(const AsyncValue.loading()) {
    _sub = _channel.dataStream
        .where((e) =>
            e.type == WebViewEventType.historyList ||
            e.type == WebViewEventType.historyListMore)
        .listen(_onData);
  }

  void _onData(WebViewEvent event) {
    _timeoutTimer?.cancel();

    final list = (event.data?['videoList'] as List<dynamic>?)
            ?.map((e) => VideoItem.fromJson(Map<String, dynamic>.from(e)))
            .toList() ??
        [];

    if (event.type == WebViewEventType.historyList) {
      _listState = VideoListState(videos: list, hasMore: true);
    } else if (event.type == WebViewEventType.historyListMore) {
      _loadMoreTimer?.cancel();
      _listState = _listState.copyWith(
        videos: [..._listState.videos, ...list],
        hasMore: list.isNotEmpty,
        isLoadingMore: false,
      );
    }

    state = AsyncValue.data(List.unmodifiable(_listState.videos));
    _refreshCompleter?.complete();
    _refreshCompleter = null;
  }

  Future<void> load() async {
    _listState = const VideoListState();
    _loadMoreTimer?.cancel();

    _refreshCompleter?.complete();
    final completer = Completer<void>();
    _refreshCompleter = completer;

    state = const AsyncValue.loading();
    await _channel.loadHistory();

    _timeoutTimer?.cancel();
    _timeoutTimer = Timer(const Duration(seconds: 20), () {
      if (!completer.isCompleted) {
        if (state is AsyncLoading) {
          state = const AsyncValue.data([]);
        }
        completer.complete();
      }
    });

    return completer.future;
  }

  Future<void> loadMore() async {
    if (_listState.isLoadingMore || !_listState.hasMore) return;
    _listState = _listState.copyWith(isLoadingMore: true);
    await _channel.loadHistoryContinuation();

    _loadMoreTimer?.cancel();
    _loadMoreTimer = Timer(const Duration(seconds: 10), () {
      if (_listState.isLoadingMore) {
        _listState = _listState.copyWith(isLoadingMore: false, hasMore: false);
        state = AsyncValue.data(List.unmodifiable(_listState.videos));
      }
    });
  }

  @override
  void dispose() {
    _sub?.cancel();
    _timeoutTimer?.cancel();
    _loadMoreTimer?.cancel();
    super.dispose();
  }
}

// --- Playlist Detail ---

class PlaylistDetailNotifier extends StateNotifier<AsyncValue<List<VideoItem>>> {
  final WebViewChannel _channel;
  StreamSubscription? _sub;
  Timer? _timeoutTimer;

  PlaylistDetailNotifier(this._channel) : super(const AsyncValue.data([])) {
    _sub = _channel.dataStream
        .where((e) => e.type == WebViewEventType.playlistDetail)
        .listen(_onData);
  }

  void _onData(WebViewEvent event) {
    _timeoutTimer?.cancel();
    final list = (event.data?['videoList'] as List<dynamic>?)
            ?.map((e) => VideoItem.fromJson(Map<String, dynamic>.from(e)))
            .toList() ??
        [];
    state = AsyncValue.data(list);
  }

  Future<void> load(String playlistId) async {
    state = const AsyncValue.loading();
    await _channel.loadPlaylistDetail(playlistId);

    _timeoutTimer?.cancel();
    _timeoutTimer = Timer(const Duration(seconds: 15), () {
      if (state is AsyncLoading) {
        state = const AsyncValue.data([]);
      }
    });
  }

  @override
  void dispose() {
    _sub?.cancel();
    _timeoutTimer?.cancel();
    super.dispose();
  }
}
