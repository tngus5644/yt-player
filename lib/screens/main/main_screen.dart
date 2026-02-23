import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/webview_provider.dart';
import 'tabs/home_tab.dart';
import 'tabs/shorts_tab.dart';
import 'tabs/ytplayer_tab.dart';
import 'tabs/subscribe_tab.dart';
import 'tabs/profile_tab.dart';

class MainScreen extends ConsumerStatefulWidget {
  const MainScreen({super.key});

  @override
  ConsumerState<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends ConsumerState<MainScreen> {
  final _tabs = const [
    HomeTab(),
    ShortsTab(),
    YTPlayerTab(),
    SubscribeTab(),
    ProfileTab(),
  ];

  @override
  void initState() {
    super.initState();
    // 앱 시작 시 홈 피드 로드
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(homeVideoListProvider.notifier).loadHomeFeed();
    });
  }

  void _onTabChanged(int index) {
    final prevIndex = ref.read(currentTabProvider);
    ref.read(currentTabProvider.notifier).state = index;

    // 탭 전환 시 해당 탭의 데이터 로드 (WebView는 하나이므로 탭별 로드 관리)
    if (index != prevIndex) {
      switch (index) {
        case 0: // 홈
          final videos = ref.read(homeVideoListProvider);
          if (videos is AsyncLoading || (videos.valueOrNull?.isEmpty ?? true)) {
            ref.read(homeVideoListProvider.notifier).loadHomeFeed();
          }
          break;
        case 1: // 쇼츠
          final shorts = ref.read(shortsVideoListProvider);
          if (shorts is AsyncLoading || (shorts.valueOrNull?.isEmpty ?? true)) {
            ref.read(shortsVideoListProvider.notifier).load();
          }
          break;
        case 3: // 구독
          final isLoggedIn = ref.read(loginStateProvider);
          if (isLoggedIn) {
            ref.read(subscriptionListProvider.notifier).load();
          }
          break;
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final currentTab = ref.watch(currentTabProvider);

    return Scaffold(
      body: IndexedStack(
        index: currentTab,
        children: _tabs,
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: currentTab,
        onTap: (index) {
          if (index == 1) {
            // 쇼츠 탭 → YouTube 쇼츠 바로 재생
            ref
                .read(webViewChannelProvider)
                .playVideo('https://m.youtube.com/shorts');
            return;
          }
          // 구독/프로필 탭 → 미로그인이면 자동 로그인
          if ((index == 3 || index == 4) && !ref.read(loginStateProvider)) {
            ref.read(loginStateProvider.notifier).signIn();
            return;
          }
          _onTabChanged(index);
        },
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.home_outlined),
            activeIcon: Icon(Icons.home),
            label: '홈',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.short_text),
            activeIcon: Icon(Icons.short_text),
            label: '쇼츠',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.play_circle_outline),
            activeIcon: Icon(Icons.play_circle_filled),
            label: 'YTPlayer',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.subscriptions_outlined),
            activeIcon: Icon(Icons.subscriptions),
            label: '구독',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.person_outline),
            activeIcon: Icon(Icons.person),
            label: '프로필',
          ),
        ],
      ),
    );
  }
}
