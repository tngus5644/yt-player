abstract class AppConstants {
  static const String appName = 'YTPlayer';
  static const String packageName = 'com.hashmeter.ytplayer';

  // WebView URLs
  static const String youtubeHomeUrl = 'https://m.youtube.com/';
  static const String youtubeSearchUrl = 'https://m.youtube.com/results';
  static const String youtubeSubscriptionsUrl = 'https://m.youtube.com/feed/subscriptions';
  static const String youtubeLibraryUrl = 'https://m.youtube.com/feed/library?dark_theme=1';
  static const String youtubeShortsBaseUrl = 'https://m.youtube.com/shorts/';
  static const String youtubeWatchBaseUrl = 'https://m.youtube.com/watch?v=';
  static const String youtubeLogoutUrl = 'https://m.youtube.com/logout';
  static const String youPlayerLoginUrl = 'https://youplayer.co.kr/app_login';
  static const String youPlayerDashboardUrl = 'https://youplayer.co.kr/app_dashboard';
  static const String youPlayerShareBaseUrl = 'https://youplayer.co.kr/s/ytb/';
  static const String googleSignInUrl = 'https://accounts.google.com/ServiceLogin';

  // Method Channel
  static const String methodChannelName = 'com.ytplayer/webview';
  static const String eventChannelName = 'com.ytplayer/data';
  static const String playerChannelName = 'com.ytplayer/player';

  // Database
  static const String databaseName = 'ytplayer_database';

  // SharedPreferences Keys
  static const String prefAccessToken = 'access_token';
  static const String prefShouldShowIntro = 'should_show_intro';
  static const String prefFirebaseInstallId = 'firebase_install_id';
  static const String prefReferrerUrl = 'referrer_url';
  static const String prefDeviceUuid = 'device_uuid';
  static const String prefServerInstallId = 'server_install_id';
  static const String prefPointVideoView = 'point_video_view';
  static const String prefMobileNumber = 'mobile_number';
  static const String prefAdId = 'ad_id';

  // Reward
  static const int rewardThreshold = 60;
  static const String rewardApiBaseUrl = 'https://pcaview.com';

  // SharedPreferences Keys - Reward
  static const String prefRewardBalance = 'reward_balance';
}
