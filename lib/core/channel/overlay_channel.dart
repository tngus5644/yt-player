import 'package:flutter/services.dart';

/// 오버레이 WebView 서비스 제어 채널
/// Native의 OverlayWebViewService를 Flutter에서 제어
class OverlayChannel {
  static const _channel = MethodChannel('com.ytplayer/overlay');

  /// 오버레이 서비스 시작
  /// 오버레이 권한이 없으면 false 반환
  static Future<bool> startOverlayService() async {
    try {
      final result = await _channel.invokeMethod<bool>('startOverlayService');
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// 오버레이 서비스 중지
  static Future<bool> stopOverlayService() async {
    try {
      final result = await _channel.invokeMethod<bool>('stopOverlayService');
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// 오버레이 서비스 실행 중인지 확인
  static Future<bool> isOverlayServiceRunning() async {
    try {
      final result =
          await _channel.invokeMethod<bool>('isOverlayServiceRunning');
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// 오버레이 권한 보유 여부 확인
  static Future<bool> hasOverlayPermission() async {
    try {
      final result = await _channel.invokeMethod<bool>('hasOverlayPermission');
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// 오버레이 권한 요청
  static Future<void> requestOverlayPermission() async {
    try {
      await _channel.invokeMethod('requestOverlayPermission');
    } on PlatformException {
      // 권한 요청 실패 시 무시
    }
  }
}
