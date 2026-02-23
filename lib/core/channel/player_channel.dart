import 'package:flutter/services.dart';
import '../constants/app_constants.dart';

/// 플레이어 Activity 통신 채널
class PlayerChannel {
  static const _channel = MethodChannel(AppConstants.playerChannelName);

  /// PiP 모드 시작
  Future<bool> startPip() async {
    final result = await _channel.invokeMethod<bool>('startPip');
    return result ?? false;
  }

  /// PiP 지원 여부 확인
  Future<bool> isPipSupported() async {
    final result = await _channel.invokeMethod<bool>('isPipSupported');
    return result ?? false;
  }

  /// 영상 공유
  Future<void> shareVideo(String videoId, String title) async {
    await _channel.invokeMethod('shareVideo', {
      'videoId': videoId,
      'title': title,
    });
  }

  /// 플레이어 닫기
  Future<void> closePlayer() async {
    await _channel.invokeMethod('closePlayer');
  }
}
