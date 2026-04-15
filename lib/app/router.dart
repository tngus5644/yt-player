import 'package:go_router/go_router.dart';
import '../screens/splash/splash_screen.dart';
import '../screens/intro/intro_screen.dart';
import '../screens/main/main_screen.dart';
import '../screens/search/search_screen.dart';
import '../screens/history/history_screen.dart';
import '../screens/settings/settings_screen.dart';
import '../screens/playlist/playlist_detail_screen.dart';
import '../screens/playlist/playlist_videos_screen.dart';

final router = GoRouter(
  initialLocation: '/',
  routes: [
    GoRoute(
      path: '/',
      builder: (context, state) => const SplashScreen(),
    ),
    GoRoute(
      path: '/intro',
      builder: (context, state) => const IntroScreen(),
    ),
    GoRoute(
      path: '/main',
      builder: (context, state) => const MainScreen(),
    ),
    GoRoute(
      path: '/search',
      builder: (context, state) => const SearchScreen(),
    ),
    GoRoute(
      path: '/history',
      builder: (context, state) => const HistoryScreen(),
    ),
    GoRoute(
      path: '/settings',
      builder: (context, state) => const SettingsScreen(),
    ),
    GoRoute(
      path: '/playlists',
      builder: (context, state) => const PlaylistDetailScreen(),
    ),
    GoRoute(
      path: '/playlist/:id',
      builder: (context, state) => PlaylistVideosScreen(
        playlistId: state.pathParameters['id'] ?? '',
        title: (state.extra as Map<String, dynamic>?)?['title'] as String? ?? '재생목록',
      ),
    ),
  ],
);
