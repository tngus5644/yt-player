import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/reward_provider.dart';
import '../../providers/webview_provider.dart';
import 'tabs/home_tab.dart';
import 'tabs/shorts_tab.dart';
import 'tabs/ytplayer_tab.dart';
import 'tabs/subscribe_tab.dart';
import 'tabs/library_tab.dart';

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
    LibraryTab(),
  ];

  StreamSubscription? _navSub;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      // 앱 시작 시 홈 피드 로드
      ref.read(homeVideoListProvider.notifier).loadHomeFeed();
      // 로그인 상태 변화 감지
      ref.listenManual(loginStateProvider, (prev, next) {
        if (next) _onLoggedIn();
      });
      // 네이티브(PlayerActivity 등)에서 보낸 탭 이동 요청 수신
      _navSub = ref.read(webViewChannelProvider).dataStream
          .where((e) => e.type == 'navigateTab')
          .listen((e) {
        final target = e.data?['tab'] as String?;
        final index = switch (target) {
          'home' => 0,
          'shorts' => 1,
          'ytplayer' => 2,
          'subscribe' => 3,
          'library' => 4,
          _ => null,
        };
        if (index != null) {
          ref.read(currentTabProvider.notifier).state = index;
        }
      });
    });
  }

  @override
  void dispose() {
    _navSub?.cancel();
    super.dispose();
  }

  void _onLoggedIn() {
    // YTPlayerTab 데이터 로드
    ref.read(balanceProvider.notifier).fetch();
    ref.read(rewardChartProvider.notifier).fetch();
    ref.read(noticeProvider.notifier).fetch();
    ref.read(rewardListProvider.notifier).fetch();
    ref.read(rewardUsagesProvider.notifier).fetch();
    // 구독 탭 데이터 로드
    ref.read(subscriptionListProvider.notifier).load();
    ref.read(subscriptionFeedProvider.notifier).load();
    // 보관함 데이터 로드
    ref.read(libraryDataProvider.notifier).load();
    // 로그인 후 개인화된 피드로 리로드
    ref.read(homeVideoListProvider.notifier).loadHomeFeed(isRefresh: true);
    ref.read(shortsVideoListProvider.notifier).load(isRefresh: true);
  }

  static const _shortsChannel = MethodChannel('com.ytplayer/shorts');

  void _onTabChanged(int index) {
    final prevIndex = ref.read(currentTabProvider);
    ref.read(currentTabProvider.notifier).state = index;

    // 쇼츠 탭 오디오 관리
    if (prevIndex == 1 && index != 1) {
      _shortsChannel.invokeMethod('pauseShorts');
    }
    if (index == 1 && prevIndex != 1) {
      _shortsChannel.invokeMethod('resumeShorts');
    }

    // 탭 전환 시 해당 탭의 데이터 로드 (WebView는 하나이므로 탭별 로드 관리)
    // YTPlayerTab(case 2)은 자체 initState에서 데이터를 로드
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
            final feed = ref.read(subscriptionFeedProvider);
            if (feed is AsyncLoading || (feed.valueOrNull?.isEmpty ?? true)) {
              ref.read(subscriptionFeedProvider.notifier).load();
            }
          }
          break;
        case 4: // 보관함
          final isLoggedIn = ref.read(loginStateProvider);
          if (isLoggedIn) {
            final library = ref.read(libraryDataProvider);
            if (library is AsyncLoading || library.valueOrNull == null) {
              ref.read(libraryDataProvider.notifier).load();
            }
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
        onTap: _onTabChanged,
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
            icon: Icon(Icons.video_library_outlined),
            activeIcon: Icon(Icons.video_library),
            label: '보관함',
          ),
        ],
      ),
    );
  }
}
