import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../constants/app_constants.dart';
import '../encryption/hmac_signer.dart';
import '../../models/api/response/balance_response.dart';
import '../../models/api/response/rewards_response.dart';
import '../../models/api/response/reward_accumulate_response.dart';
import '../../models/api/response/reward_chart_response.dart';
import '../../models/api/response/notice_response.dart';
import '../../models/api/response/version_check_response.dart';
import '../../models/api/response/count_response.dart';
import '../../models/api/response/ad_tracking_response.dart';
import '../../models/api/response/use_reward_response.dart';
import '../../models/api/response/reward_usages_response.dart';
import '../../models/api/response/reward_usage_detail_response.dart';
import '../../models/api/response/update_phone_response.dart';
import '../../models/api/response/auth_response.dart';
import '../../models/api/response/symlink_response.dart';

class RewardApiClient {
  late final Dio _dio;
  final SharedPreferences _prefs;

  RewardApiClient(this._prefs) {
    _dio = Dio(BaseOptions(
      baseUrl: AppConstants.rewardApiBaseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
      headers: {
        'Accept': 'application/json',
        'X-Requested-With': 'XMLHttpRequest',
      },
    ));
    _dio.interceptors.add(_AuthInterceptor(_prefs));
  }

  String get _deviceId =>
      _prefs.getString(AppConstants.prefDeviceUuid) ?? '';

  // ─── 기존 엔드포인트 ─────────────────────────────────────

  /// GET /api/ytplayer/balance
  Future<BalanceResponse> getBalance() async {
    final response = await _dio.get(
      '/api/ytplayer/balance',
      queryParameters: {'encrypted': _deviceId},
    );
    return BalanceResponse.fromJson(response.data);
  }

  /// GET /api/ytplayer/rewards
  Future<RewardsResponse> getRewards() async {
    final response = await _dio.get(
      '/api/ytplayer/rewards',
      queryParameters: {'application': 'goldnity'},
    );
    return RewardsResponse.fromJson(response.data);
  }

  /// POST /api/ytplayer/reward
  Future<RewardAccumulateResponse> postReward({
    required String rewardType,
    String application = 'goldnity',
    String? where,
    String? videoUrl,
    int? videoTime,
    String? videoStringtime,
  }) async {
    final body = <String, dynamic>{
      'encrypted': _deviceId,
      'reward_type': rewardType,
      'application': application,
    };
    if (where != null) body['where'] = where;
    if (videoUrl != null) body['video_url'] = videoUrl;
    if (videoTime != null) body['video_time'] = videoTime;
    if (videoStringtime != null) body['video_stringtime'] = videoStringtime;

    final response = await _dio.post(
      '/api/ytplayer/reward',
      data: body,
    );
    return RewardAccumulateResponse.fromJson(response.data);
  }

  /// GET /api/ytplayer/reward_chart
  Future<RewardChartResponse> getRewardChart({int days = 30}) async {
    final response = await _dio.get(
      '/api/ytplayer/reward_chart',
      queryParameters: {
        'encrypted': _deviceId,
        'days': days,
      },
    );
    return RewardChartResponse.fromJson(response.data);
  }

  /// GET /api/live/count — 광고 트래킹 URL 조회
  Future<AdTrackingResponse> getAdTrackingUrls({
    required String adId,
    String? encrypted,
  }) async {
    final params = <String, dynamic>{'ad_id': adId};
    if (encrypted != null) params['encrypted'] = encrypted;

    final response = await _dio.get(
      '/api/live/count',
      queryParameters: params,
    );
    return AdTrackingResponse.fromJson(response.data);
  }

  // ─── 마이그레이션 엔드포인트 (구 ApiClient → RewardApiClient) ───

  /// GET /api/ytplayer/notice
  Future<NoticeResponse> getNotice() async {
    final response = await _dio.get(
      '/api/notices',
      queryParameters: {'application': 'ytplayer'},
    );
    return NoticeResponse.fromJson(response.data);
  }

  /// GET /api/ytplayer/version_check
  Future<VersionCheckResponse> getVersionCheck(String appVersion) async {
    final response = await _dio.get(
      '/api/ytplayer/version_check',
      queryParameters: {'appVersion': appVersion},
    );
    return VersionCheckResponse.fromJson(response.data);
  }

  /// POST /api/ytplayer/install_count
  Future<CountResponse> postInstallCount({String? referrer}) async {
    final body = <String, dynamic>{
      'encrypted': _deviceId,
    };
    if (referrer != null) body['referrer'] = referrer;

    final response = await _dio.post(
      '/api/ytplayer/install_count',
      data: body,
    );
    return CountResponse.fromJson(response.data);
  }

  /// POST /api/ytplayer/live_count
  Future<CountResponse> postLiveCount({
    String? referrer,
    String? sessionId,
  }) async {
    final body = <String, dynamic>{
      'encrypted': _deviceId,
    };
    if (referrer != null) body['referrer'] = referrer;
    if (sessionId != null) body['session_id'] = sessionId;

    final response = await _dio.post(
      '/api/ytplayer/live_count',
      data: body,
    );
    return CountResponse.fromJson(response.data);
  }

  // ─── 신규 엔드포인트 (리워드 사용/교환) ──────────────────

  /// POST /api/ytplayer/use_reward
  Future<UseRewardResponse> postUseReward({required int rewardId}) async {
    final body = {
      'encrypted': _deviceId,
      'reward_id': rewardId,
    };
    final response = await _dio.post(
      '/api/ytplayer/use_reward',
      data: body,
    );
    return UseRewardResponse.fromJson(response.data);
  }

  /// GET /api/ytplayer/reward/usages
  Future<RewardUsagesResponse> getRewardUsages({int limit = 20}) async {
    final response = await _dio.get(
      '/api/ytplayer/reward/usages',
      queryParameters: {
        'encrypted': _deviceId,
        'limit': limit,
      },
    );
    return RewardUsagesResponse.fromJson(response.data);
  }

  /// GET /api/ytplayer/reward/usages/{id} — Bearer auth
  Future<RewardUsageDetailResponse> getRewardUsageDetail(int id) async {
    final response = await _dio.get(
      '/api/ytplayer/reward/usages/$id',
      options: Options(extra: {'authType': 'bearer'}),
    );
    return RewardUsageDetailResponse.fromJson(response.data);
  }

  // ─── 인증 엔드포인트 ────────────────────────────────────

  /// POST /api/auth/google/callback — Google OAuth 로그인
  Future<AuthResponse> googleLogin({
    required String idToken,
    required String userId,
    required String email,
    String? name,
    String? picture,
  }) async {
    final response = await _dio.post('/api/auth/google/callback', data: {
      'id_token': idToken,
      'user_id': userId,
      'email': email,
      'name': name,
      'picture': picture,
    });
    return AuthResponse.fromJson(response.data);
  }

  /// POST /api/auth/logout — 현재 토큰 무효화
  Future<void> authLogout() async {
    await _dio.post(
      '/api/auth/logout',
      options: Options(extra: {'authType': 'bearer'}),
    );
  }

  // ─── 추가 인증 엔드포인트 ──────────────────────────────

  /// POST /api/auth/login — HMAC 인증
  Future<AuthResponse> authLogin() async {
    final response = await _dio.post('/api/auth/login', data: {
      'encrypted': _deviceId,
    });
    return AuthResponse.fromJson(response.data);
  }

  /// GET /api/auth/user — Bearer 인증
  Future<AuthResponse> getAuthUser() async {
    final response = await _dio.get(
      '/api/auth/user',
      options: Options(extra: {'authType': 'bearer'}),
    );
    return AuthResponse.fromJson(response.data);
  }

  /// POST /api/auth/logout-all — Bearer 인증, 모든 세션 무효화
  Future<void> authLogoutAll() async {
    await _dio.post(
      '/api/auth/logout-all',
      options: Options(extra: {'authType': 'bearer'}),
    );
  }

  /// GET /api/symlink — 인증 없음
  Future<SymlinkResponse> getSymlink() async {
    final response = await _dio.get('/api/symlink');
    return SymlinkResponse.fromJson(response.data);
  }

  /// PATCH /api/ytplayer/reward/usages/{id}/phone — Bearer auth
  Future<UpdatePhoneResponse> updateRewardUsagePhone({
    required int id,
    required String phone,
  }) async {
    final response = await _dio.patch(
      '/api/ytplayer/reward/usages/$id/phone',
      data: {'phone': phone},
      options: Options(extra: {'authType': 'bearer'}),
    );
    return UpdatePhoneResponse.fromJson(response.data);
  }
}

class _AuthInterceptor extends Interceptor {
  final SharedPreferences _prefs;

  _AuthInterceptor(this._prefs);

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final authType = options.extra['authType'] as String?;

    if (authType == 'bearer') {
      final token = _prefs.getString(AppConstants.prefAccessToken);
      if (token != null && token.isNotEmpty) {
        options.headers['Authorization'] = 'Bearer $token';
      }
    } else if (options.method == 'POST') {
      final bodyString = options.data != null ? jsonEncode(options.data) : '';
      final signResult = HmacSigner.sign(bodyString);

      options.headers.addAll({
        'Content-Type': 'application/json',
        'X-YTPlayer-Signature': signResult['signature']!,
        'X-YTPlayer-Timestamp': signResult['timestamp']!,
        'X-YTPlayer-Nonce': signResult['nonce']!,
      });
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    final statusCode = err.response?.statusCode;

    switch (statusCode) {
      case 401:
        // Bearer 인증 요청에서만 토큰 제거 (HMAC 요청의 401은 토큰과 무관)
        final authType = err.requestOptions.extra['authType'] as String?;
        if (authType == 'bearer') {
          _prefs.remove(AppConstants.prefAccessToken);
        }
        handler.next(DioException(
          requestOptions: err.requestOptions,
          response: err.response,
          type: err.type,
          message: '인증이 만료되었습니다. 다시 로그인해주세요.',
        ));
        break;
      case 429:
        // Rate limit → 재시도 안내
        handler.next(DioException(
          requestOptions: err.requestOptions,
          response: err.response,
          type: err.type,
          message: '요청이 너무 많습니다. 잠시 후 다시 시도해주세요.',
        ));
        break;
      case 500:
      case 502:
      case 503:
        handler.next(DioException(
          requestOptions: err.requestOptions,
          response: err.response,
          type: err.type,
          message: '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
        ));
        break;
      default:
        if (err.type == DioExceptionType.connectionTimeout ||
            err.type == DioExceptionType.receiveTimeout) {
          handler.next(DioException(
            requestOptions: err.requestOptions,
            response: err.response,
            type: err.type,
            message: '네트워크 연결 시간이 초과되었습니다.',
          ));
        } else {
          handler.next(err);
        }
    }
  }
}
