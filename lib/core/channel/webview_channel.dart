import 'dart:async';
import 'dart:convert';
import 'package:flutter/services.dart';
import '../constants/app_constants.dart';

/// MethodChannel 메서드 이름 상수 (오타 방지)
abstract class ChannelMethods {
  static const String loadHomeFeed = 'loadHomeFeed';
  static const String search = 'search';
  static const String loadSubscriptions = 'loadSubscriptions';
  static const String loadShorts = 'loadShorts';
  static const String playVideo = 'playVideo';
  static const String startSignIn = 'startSignIn';
  static const String isLoggedIn = 'isLoggedIn';
  static const String signOut = 'signOut';
  static const String scrollBottom = 'scrollBottom';
  static const String loadMoreHomeFeed = 'loadMoreHomeFeed';
  static const String getVideoDetail = 'getVideoDetail';
  static const String loadLibrary = 'loadLibrary';
  static const String loadHistory = 'loadHistory';
  static const String loadHistoryContinuation = 'loadHistoryContinuation';
  static const String loadPlaylistDetail = 'loadPlaylistDetail';
}

/// 네이티브 WebView와 Flutter 간 통신 채널
class WebViewChannel {
  static const _methodChannel = MethodChannel(AppConstants.methodChannelName);
  static const _eventChannel = EventChannel(AppConstants.eventChannelName);
  static const _shortsChannel = MethodChannel('com.ytplayer/shorts');

  StreamSubscription? _eventSubscription;
  final _dataController = StreamController<WebViewEvent>.broadcast();

  Stream<WebViewEvent> get dataStream => _dataController.stream;

  /// 이벤트 채널 리스닝 시작
  void startListening() {
    _eventSubscription = _eventChannel
        .receiveBroadcastStream()
        .map((data) => WebViewEvent.fromJson(jsonDecode(data as String)))
        .listen(
          _dataController.add,
          onError: (error) => _dataController.addError(error),
        );
  }

  /// 홈 피드 로드 요청
  Future<void> loadHomeFeed({bool isRefresh = false}) async {
    await _methodChannel.invokeMethod(ChannelMethods.loadHomeFeed, {'isRefresh': isRefresh});
  }

  /// 검색 요청
  Future<void> search(String query) async {
    await _methodChannel.invokeMethod(ChannelMethods.search, {'query': query});
  }

  /// 구독 목록 로드 요청
  Future<void> loadSubscriptions() async {
    await _methodChannel.invokeMethod(ChannelMethods.loadSubscriptions);
  }

  /// 쇼츠 로드 요청
  Future<void> loadShorts() async {
    await _methodChannel.invokeMethod(ChannelMethods.loadShorts);
  }

  /// WebView 로그인 시작 (WebViewSignInActivity 실행)
  Future<void> startSignIn() async {
    await _methodChannel.invokeMethod(ChannelMethods.startSignIn);
  }

  /// WebView 로그인 상태 확인
  Future<bool> isLoggedIn() async {
    return await _methodChannel.invokeMethod<bool>(ChannelMethods.isLoggedIn) ?? false;
  }

  /// 영상 재생 요청 (별도 Activity)
  Future<void> playVideo(String videoUrl) async {
    await _methodChannel.invokeMethod(ChannelMethods.playVideo, {'url': videoUrl});
  }

  /// 로그아웃 요청
  Future<void> signOut() async {
    await _methodChannel.invokeMethod(ChannelMethods.signOut);
  }

  /// 스크롤 다운 요청 (더보기)
  Future<void> scrollBottom() async {
    await _methodChannel.invokeMethod(ChannelMethods.scrollBottom);
  }

  /// 홈 피드 다음 페이지 로드 (InnerTube continuation 기반)
  Future<void> loadMoreHomeFeed() async {
    await _methodChannel.invokeMethod(ChannelMethods.loadMoreHomeFeed);
  }

  /// 쇼츠 WebView 일시정지
  Future<void> pauseShorts() async {
    await _shortsChannel.invokeMethod('pauseShorts');
  }

  /// 쇼츠 WebView 재개
  Future<void> resumeShorts() async {
    await _shortsChannel.invokeMethod('resumeShorts');
  }

  /// 보관함 데이터 로드 요청
  Future<void> loadLibrary() async {
    await _methodChannel.invokeMethod(ChannelMethods.loadLibrary);
  }

  /// 시청 기록 로드 요청
  Future<void> loadHistory() async {
    await _methodChannel.invokeMethod(ChannelMethods.loadHistory);
  }

  /// 시청 기록 다음 페이지 로드
  Future<void> loadHistoryContinuation() async {
    await _methodChannel.invokeMethod(ChannelMethods.loadHistoryContinuation);
  }

  /// 재생목록 상세 로드 요청
  Future<void> loadPlaylistDetail(String playlistId) async {
    await _methodChannel.invokeMethod(ChannelMethods.loadPlaylistDetail, {'playlistId': playlistId});
  }

  void dispose() {
    _eventSubscription?.cancel();
    _dataController.close();
  }
}

/// 네이티브에서 전달되는 이벤트
class WebViewEvent {
  final String type;
  final Map<String, dynamic>? data;

  const WebViewEvent({required this.type, this.data});

  factory WebViewEvent.fromJson(Map<String, dynamic> json) {
    return WebViewEvent(
      type: json['type'] as String,
      data: json['data'] as Map<String, dynamic>?,
    );
  }
}

/// 이벤트 타입 상수
abstract class WebViewEventType {
  static const String videoList = 'videoList';
  static const String videoListMore = 'videoListMore';
  static const String subscriptions = 'subscriptions';
  static const String playState = 'playState';
  static const String rewardEarned = 'rewardEarned';
  static const String shortsList = 'shortsList';
  static const String searchResults = 'searchResults';
  static const String loginState = 'loginState';
  static const String subscriptionFeed = 'subscriptionFeed';
  static const String libraryData = 'libraryData';
  static const String historyList = 'historyList';
  static const String historyListMore = 'historyListMore';
  static const String playlistDetail = 'playlistDetail';
}
