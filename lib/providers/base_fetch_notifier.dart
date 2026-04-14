import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/network/reward_api_client.dart';

/// API fetch 패턴을 공통화하는 추상 클래스
/// RewardListNotifier, RewardChartNotifier, NoticeNotifier, RewardUsagesNotifier에서 사용
abstract class BaseFetchNotifier<T> extends StateNotifier<AsyncValue<T>> {
  final RewardApiClient api;

  BaseFetchNotifier(this.api, T defaultValue)
      : super(AsyncValue.data(defaultValue));

  /// 서브클래스에서 구현: 실제 API 호출
  Future<ApiResult<T>> fetchData();

  /// 에러 메시지 기본값
  String get errorMessage;

  Future<void> fetch() async {
    state = const AsyncValue.loading();
    try {
      final result = await fetchData();
      if (result.success) {
        state = AsyncValue.data(result.data);
      } else {
        state = AsyncValue.error(
          result.message ?? errorMessage,
          StackTrace.current,
        );
      }
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }
}

/// API 응답을 추상화하는 결과 클래스
class ApiResult<T> {
  final bool success;
  final T data;
  final String? message;

  const ApiResult({
    required this.success,
    required this.data,
    this.message,
  });
}
