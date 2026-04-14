import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'app/router.dart';
import 'app/theme.dart';
import 'core/encryption/hmac_signer.dart';
import 'providers/reward_provider.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
  ]);

  // 비밀키는 dart-define으로 빌드 시 주입
  // flutter run --dart-define=HMAC_SECRET=your_secret_here
  const hmacSecret = String.fromEnvironment('HMAC_SECRET');
  assert(hmacSecret.isNotEmpty, 'HMAC_SECRET must be provided via --dart-define');
  HmacSigner.initialize(hmacSecret);

  final prefs = await SharedPreferences.getInstance();

  runApp(ProviderScope(
    overrides: [
      sharedPreferencesProvider.overrideWithValue(prefs),
    ],
    child: const YTPlayerApp(),
  ));
}

class YTPlayerApp extends StatelessWidget {
  const YTPlayerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'YTPlayer',
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: ThemeMode.system,
      routerConfig: router,
      debugShowCheckedModeBanner: false,
      builder: (context, child) {
        final brightness = Theme.of(context).brightness;
        SystemChrome.setSystemUIOverlayStyle(SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness:
              brightness == Brightness.dark ? Brightness.light : Brightness.dark,
          systemNavigationBarColor: Theme.of(context)
              .bottomNavigationBarTheme
              .backgroundColor,
          systemNavigationBarIconBrightness:
              brightness == Brightness.dark ? Brightness.light : Brightness.dark,
        ));
        return child!;
      },
    );
  }
}
