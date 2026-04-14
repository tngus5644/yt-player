import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../core/channel/webview_channel.dart';
import '../core/constants/app_constants.dart';
import '../core/network/reward_api_client.dart';
import '../models/api/response/balance_response.dart';
import '../models/api/response/notice_response.dart';
import '../models/api/response/reward_chart_response.dart';
import '../models/api/response/reward_usages_response.dart';
import '../models/api/response/rewards_response.dart';
import '../models/api/response/use_reward_response.dart';
import 'base_fetch_notifier.dart';
import 'webview_provider.dart';

/// SharedPreferences (앱 초기화 시 override)
final sharedPreferencesProvider = Provider<SharedPreferences>((ref) {
  throw UnimplementedError('Must be overridden in ProviderScope');
});

/// Reward API 클라이언트 (싱글톤)
final rewardApiClientProvider = Provider<RewardApiClient>((ref) {
  final prefs = ref.watch(sharedPreferencesProvider);
  return RewardApiClient(prefs);
});

/// 잔액 조회
final balanceProvider =
    StateNotifierProvider<BalanceNotifier, AsyncValue<BalanceData>>((ref) {
  return BalanceNotifier(ref.watch(rewardApiClientProvider));
});

/// 리워드 목록
final rewardListProvider =
    StateNotifierProvider<RewardListNotifier, AsyncValue<List<RewardItem>>>((ref) {
  return RewardListNotifier(ref.watch(rewardApiClientProvider));
});

/// 차트 데이터
final rewardChartProvider =
    StateNotifierProvider<RewardChartNotifier, AsyncValue<RewardChartData?>>((ref) {
  return RewardChartNotifier(ref.watch(rewardApiClientProvider));
});

/// 채굴 상태 관리
final miningProvider =
    StateNotifierProvider<MiningNotifier, MiningState>((ref) {
  return MiningNotifier(
    ref.watch(rewardApiClientProvider),
    ref.watch(webViewChannelProvider),
    ref.watch(balanceProvider.notifier),
  );
});

/// 공지사항
final noticeProvider =
    StateNotifierProvider<NoticeNotifier, AsyncValue<List<NoticeItem>>>((ref) {
  return NoticeNotifier(ref.watch(rewardApiClientProvider));
});

/// 리워드 사용 내역
final rewardUsagesProvider =
    StateNotifierProvider<RewardUsagesNotifier, AsyncValue<List<RewardUsageItem>>>((ref) {
  return RewardUsagesNotifier(ref.watch(rewardApiClientProvider));
});

/// 리워드 교환 액션
final useRewardProvider =
    StateNotifierProvider<UseRewardNotifier, AsyncValue<UseRewardData?>>((ref) {
  return UseRewardNotifier(
    ref.watch(rewardApiClientProvider),
    ref.watch(balanceProvider.notifier),
  );
});

// --- Notifiers ---

class BalanceNotifier extends StateNotifier<AsyncValue<BalanceData>> {
  final RewardApiClient _api;

  BalanceNotifier(this._api)
      : super(const AsyncValue.data(BalanceData(balance: 0.0)));

  Future<void> fetch() async {
    state = const AsyncValue.loading();
    try {
      final response = await _api.getBalance();
      if (response.success) {
        final data = response.data ??
            BalanceData(
              balance: response.balance ?? 0.0,
              totalEarned: response.totalEarned ?? 0.0,
              totalSpent: response.totalSpent ?? 0.0,
            );
        state = AsyncValue.data(data);
      } else {
        state = AsyncValue.error(
          response.message ?? '잔액 조회 실패',
          StackTrace.current,
        );
      }
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  void updateBalance(double balance) {
    final current = state.valueOrNull;
    state = AsyncValue.data(BalanceData(
      balance: balance,
      totalEarned: current?.totalEarned ?? 0.0,
      totalSpent: current?.totalSpent ?? 0.0,
    ));
  }
}

class RewardListNotifier extends BaseFetchNotifier<List<RewardItem>> {
  RewardListNotifier(RewardApiClient api) : super(api, []);

  @override
  String get errorMessage => '리워드 목록 조회 실패';

  @override
  Future<ApiResult<List<RewardItem>>> fetchData() async {
    final response = await api.getRewards();
    return ApiResult(
      success: response.success,
      data: response.data ?? [],
      message: response.message,
    );
  }
}

class RewardChartNotifier extends BaseFetchNotifier<RewardChartData?> {
  RewardChartNotifier(RewardApiClient api) : super(api, null);

  @override
  String get errorMessage => '차트 데이터 조회 실패';

  @override
  Future<ApiResult<RewardChartData?>> fetchData() async {
    final response = await api.getRewardChart(days: _days);
    return ApiResult(
      success: response.success,
      data: response.data,
    );
  }

  int _days = 30;

  @override
  Future<void> fetch({int days = 30}) async {
    _days = days;
    return super.fetch();
  }
}

class NoticeNotifier extends BaseFetchNotifier<List<NoticeItem>> {
  NoticeNotifier(RewardApiClient api) : super(api, []);

  @override
  String get errorMessage => '공지사항 조회 실패';

  @override
  Future<ApiResult<List<NoticeItem>>> fetchData() async {
    final response = await api.getNotice();
    return ApiResult(
      success: response.success,
      data: response.data ?? [],
      message: response.message,
    );
  }
}

class RewardUsagesNotifier extends BaseFetchNotifier<List<RewardUsageItem>> {
  RewardUsagesNotifier(RewardApiClient api) : super(api, []);

  @override
  String get errorMessage => '사용 내역 조회 실패';

  @override
  Future<ApiResult<List<RewardUsageItem>>> fetchData() async {
    final response = await api.getRewardUsages(limit: _limit);
    return ApiResult(
      success: response.success,
      data: response.data ?? [],
      message: response.message,
    );
  }

  int _limit = 20;

  @override
  Future<void> fetch({int limit = 20}) async {
    _limit = limit;
    return super.fetch();
  }
}

// --- Mining ---

enum MiningStatus { idle, mining, submitting }

class MiningState {
  final MiningStatus status;
  final int elapsedSeconds;
  final double? lastEarnedPoints;

  const MiningState({
    this.status = MiningStatus.idle,
    this.elapsedSeconds = 0,
    this.lastEarnedPoints,
  });

  MiningState copyWith({
    MiningStatus? status,
    int? elapsedSeconds,
    double? lastEarnedPoints,
  }) {
    return MiningState(
      status: status ?? this.status,
      elapsedSeconds: elapsedSeconds ?? this.elapsedSeconds,
      lastEarnedPoints: lastEarnedPoints ?? this.lastEarnedPoints,
    );
  }
}

class MiningNotifier extends StateNotifier<MiningState> {
  final RewardApiClient _api;
  final BalanceNotifier _balanceNotifier;
  Timer? _timer;
  StreamSubscription? _playStateSub;
  StreamSubscription? _rewardSub;
  bool _isSubmitting = false;

  MiningNotifier(this._api, WebViewChannel channel, this._balanceNotifier)
      : super(const MiningState()) {
    _playStateSub = channel.dataStream
        .where((e) => e.type == WebViewEventType.playState)
        .listen(_onPlayState);
    _rewardSub = channel.dataStream
        .where((e) => e.type == WebViewEventType.rewardEarned)
        .listen(_onRewardEarned);
  }

  void _onPlayState(WebViewEvent event) {
    final isPlaying = event.data?['isPlaying'] as bool? ?? false;
    if (isPlaying) {
      startMining();
    } else {
      stopMining();
    }
  }

  void _onRewardEarned(WebViewEvent event) {
    _submitReward();
  }

  void startMining() {
    if (state.status == MiningStatus.mining) return;
    state = state.copyWith(status: MiningStatus.mining, elapsedSeconds: 0);
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      final elapsed = state.elapsedSeconds + 1;
      state = state.copyWith(elapsedSeconds: elapsed);
      if (elapsed >= AppConstants.rewardThreshold) {
        _submitReward();
        state = state.copyWith(elapsedSeconds: 0);
      }
    });
  }

  void stopMining() {
    _timer?.cancel();
    state = state.copyWith(status: MiningStatus.idle, elapsedSeconds: 0);
  }

  Future<void> _submitReward() async {
    // 동시 제출 방지
    if (_isSubmitting) return;
    _isSubmitting = true;

    state = state.copyWith(status: MiningStatus.submitting);
    try {
      final response = await _api.postReward(rewardType: 'video_view');
      if (response.success && response.data != null) {
        state = state.copyWith(
          status: MiningStatus.mining,
          lastEarnedPoints: response.data!.points,
        );
        if (response.data!.totalPoints != null) {
          _balanceNotifier.updateBalance(response.data!.totalPoints!);
        }
      } else {
        state = state.copyWith(status: MiningStatus.mining);
      }
    } catch (_) {
      state = state.copyWith(status: MiningStatus.mining);
    } finally {
      _isSubmitting = false;
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _playStateSub?.cancel();
    _rewardSub?.cancel();
    super.dispose();
  }
}

// --- Reward Exchange ---

class UseRewardNotifier extends StateNotifier<AsyncValue<UseRewardData?>> {
  final RewardApiClient _api;
  final BalanceNotifier _balanceNotifier;

  UseRewardNotifier(this._api, this._balanceNotifier)
      : super(const AsyncValue.data(null));

  Future<void> useReward(int rewardId) async {
    state = const AsyncValue.loading();
    try {
      final response = await _api.postUseReward(rewardId: rewardId);
      if (response.success && response.data != null) {
        state = AsyncValue.data(response.data);
        _balanceNotifier.updateBalance(response.data!.balance);
      } else {
        state = AsyncValue.error(
          response.message ?? '리워드 교환 실패',
          StackTrace.current,
        );
      }
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }
}
