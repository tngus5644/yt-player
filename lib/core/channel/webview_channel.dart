import 'dart:async';
import 'dart:convert';
import 'package:flutter/services.dart';
import '../constants/app_constants.dart';

/// 네이티브 WebView와 Flutter 간 통신 채널
class WebViewChannel {
  static const _methodChannel = MethodChannel(AppConstants.methodChannelName);
  static const _eventChannel = EventChannel(AppConstants.eventChannelName);

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
  Future<void> loadHomeFeed() async {
    await _methodChannel.invokeMethod('loadHomeFeed');
  }

  /// 검색 요청
  Future<void> search(String query) async {
    await _methodChannel.invokeMethod('search', {'query': query});
  }

  /// 구독 목록 로드 요청
  Future<void> loadSubscriptions() async {
    await _methodChannel.invokeMethod('loadSubscriptions');
  }

  /// 쇼츠 로드 요청
  Future<void> loadShorts() async {
    await _methodChannel.invokeMethod('loadShorts');
  }

  /// 프로필/라이브러리 로드 요청
  Future<void> loadProfile() async {
    await _methodChannel.invokeMethod('loadProfile');
  }

  /// 유플레이어 대시보드 로드 요청
  Future<void> loadDashboard() async {
    await _methodChannel.invokeMethod('loadDashboard');
  }

  /// 영상 재생 요청 (별도 Activity)
  Future<void> playVideo(String videoUrl) async {
    await _methodChannel.invokeMethod('playVideo', {'url': videoUrl});
  }

  /// 로그인 화면 요청
  Future<Map<String, dynamic>?> startSignIn() async {
    final result = await _methodChannel.invokeMethod('startSignIn');
    if (result != null) {
      return Map<String, dynamic>.from(jsonDecode(result as String));
    }
    return null;
  }

  /// 로그아웃 요청
  Future<void> signOut() async {
    await _methodChannel.invokeMethod('signOut');
  }

  /// 로그인 상태 확인
  Future<bool> isLoggedIn() async {
    final result = await _methodChannel.invokeMethod<bool>('isLoggedIn');
    return result ?? false;
  }

  /// 스크롤 다운 요청 (더보기)
  Future<void> scrollBottom() async {
    await _methodChannel.invokeMethod('scrollBottom');
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
  static const String watchHistory = 'watchHistory';
  static const String playState = 'playState';
  static const String loginState = 'loginState';
  static const String rewardEarned = 'rewardEarned';
  static const String fullscreenChanged = 'fullscreenChanged';
  static const String shortsList = 'shortsList';
  static const String searchResults = 'searchResults';
  static const String error = 'error';
}
