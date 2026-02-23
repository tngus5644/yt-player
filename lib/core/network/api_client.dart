import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../constants/app_constants.dart';

class ApiClient {
  late final Dio _dio;
  final SharedPreferences _prefs;

  ApiClient(this._prefs) {
    _dio = Dio(BaseOptions(
      baseUrl: AppConstants.apiBaseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
    ));
    _dio.interceptors.add(_HeaderInterceptor(_prefs));
  }

  Dio get dio => _dio;

  /// GET /notice
  Future<Response> getNotice() => _dio.get('/notice');

  /// GET /rewards
  Future<Response> getRewards() => _dio.get('/rewards');

  /// GET /version_check
  Future<Response> getVersionCheck(String appVersion) =>
      _dio.get('/version_check', queryParameters: {'appVersion': appVersion});

  /// POST /reward
  Future<Response> postReward(Map<String, dynamic> body) =>
      _dio.post('/reward', data: body);

  /// POST /install_count
  Future<Response> sendInstallCount(String referrer) =>
      _dio.post('/install_count', data: {'referrer': referrer});

  /// POST /live_count
  Future<Response> sendLiveCount(String referrer) =>
      _dio.post('/live_count', data: {'referrer': referrer});
}

class _HeaderInterceptor extends Interceptor {
  final SharedPreferences _prefs;
  AndroidDeviceInfo? _cachedAndroidInfo;
  PackageInfo? _cachedPackageInfo;

  _HeaderInterceptor(this._prefs);

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    final token = _prefs.getString(AppConstants.prefAccessToken);
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }

    _cachedAndroidInfo ??= await DeviceInfoPlugin().androidInfo;
    _cachedPackageInfo ??= await PackageInfo.fromPlatform();

    final androidInfo = _cachedAndroidInfo!;
    final packageInfo = _cachedPackageInfo!;

    options.headers.addAll({
      'X-Package-Name': packageInfo.packageName,
      'X-App-Version': packageInfo.version,
      'X-Device-UUID': _prefs.getString(AppConstants.prefDeviceUuid) ?? '',
      'X-Device-Id': _prefs.getString(AppConstants.prefFirebaseInstallId) ?? '',
      'X-Install-ID': _prefs.getString(AppConstants.prefServerInstallId) ?? '',
      'X-Os': 'Android',
      'X-Os-Version': androidInfo.version.release,
      'X-Language': androidInfo.host,
      'X-AD-ID': _prefs.getString(AppConstants.prefAdId) ?? '',
    });

    handler.next(options);
  }
}
