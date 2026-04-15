import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/constants/app_colors.dart';
import '../../../providers/reward_provider.dart';
import '../../../providers/webview_provider.dart';
import 'ytplayer/dashboard_profile_card.dart';
import 'ytplayer/dashboard_notice_section.dart';
import 'ytplayer/dashboard_info_banner.dart';
import 'ytplayer/dashboard_reward_shop_section.dart';
import 'ytplayer/dashboard_usage_history_section.dart';

class YTPlayerTab extends ConsumerStatefulWidget {
  const YTPlayerTab({super.key});

  @override
  ConsumerState<YTPlayerTab> createState() => _YTPlayerTabState();
}

class _YTPlayerTabState extends ConsumerState<YTPlayerTab> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(noticeProvider.notifier).fetch();
      ref.read(rewardListProvider.notifier).fetch();
      ref.read(rewardUsagesProvider.notifier).fetch();
    });
  }

  Future<void> _onRefresh() async {
    await Future.wait([
      ref.read(noticeProvider.notifier).fetch(),
      ref.read(rewardListProvider.notifier).fetch(),
      ref.read(rewardUsagesProvider.notifier).fetch(),
    ]);
  }

  @override
  Widget build(BuildContext context) {
    final isLoggedIn = ref.watch(loginStateProvider);
    final googleUser = ref.watch(googleUserProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'YTPlayer',
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.bold,
            color: AppColors.primary,
          ),
        ),
        actions: [
          IconButton(
            icon: Icon(
              isLoggedIn ? Icons.person : Icons.person_outline,
              color: isLoggedIn ? AppColors.primary : null,
            ),
            onPressed: () {
              if (isLoggedIn) {
                _showLogoutDialog();
              } else {
                ref.read(loginStateProvider.notifier).signIn();
              }
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _onRefresh,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              DashboardProfileCard(
                isLoggedIn: isLoggedIn,
                user: googleUser,
                onLogout: _showLogoutDialog,
              ),
              const SizedBox(height: 16),
              const DashboardNoticeSection(),
              const SizedBox(height: 16),
              const DashboardInfoBanner(),
              const SizedBox(height: 20),
              const DashboardRewardShopSection(),
              const SizedBox(height: 20),
              const DashboardUsageHistorySection(),
              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }

  void _showLogoutDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('로그아웃'),
        content: const Text('로그아웃 하시겠습니까?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('취소'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(ctx);
              ref.read(loginStateProvider.notifier).signOut();
            },
            child: const Text('로그아웃'),
          ),
        ],
      ),
    );
  }
}
