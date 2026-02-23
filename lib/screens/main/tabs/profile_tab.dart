import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/constants/app_colors.dart';
import '../../../providers/webview_provider.dart';

class ProfileTab extends ConsumerWidget {
  const ProfileTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isLoggedIn = ref.watch(loginStateProvider);
    final colorScheme = Theme.of(context).colorScheme;

    if (!isLoggedIn) {
      return Scaffold(
        appBar: AppBar(title: const Text('프로필')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.person_outline,
                  size: 64, color: colorScheme.onSurfaceVariant),
              const SizedBox(height: 16),
              Text('로그인이 필요합니다',
                  style: TextStyle(color: colorScheme.onSurfaceVariant)),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => ref.read(loginStateProvider.notifier).signIn(),
                style: FilledButton.styleFrom(backgroundColor: AppColors.primary),
                child: const Text('YouTube 로그인'),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('프로필'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            onPressed: () => context.push('/settings'),
          ),
        ],
      ),
      body: ListView(
        children: [
          // 프로필 헤더
          Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              children: [
                CircleAvatar(
                  radius: 40,
                  backgroundColor: colorScheme.surfaceContainerHighest,
                  child: Icon(Icons.person,
                      size: 40, color: colorScheme.onSurfaceVariant),
                ),
                const SizedBox(height: 12),
                const Text('사용자',
                    style:
                        TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              ],
            ),
          ),
          const Divider(),
          // 메뉴 항목
          ListTile(
            leading: const Icon(Icons.history, color: AppColors.watchHistory),
            title: const Text('시청 기록'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push('/history'),
          ),
          ListTile(
            leading: const Icon(Icons.download, color: AppColors.videoSearch),
            title: const Text('다운로드'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {},
          ),
          const Divider(),
          ListTile(
            leading: Icon(Icons.logout, color: colorScheme.onSurfaceVariant),
            title: const Text('로그아웃'),
            onTap: () => ref.read(loginStateProvider.notifier).signOut(),
          ),
        ],
      ),
    );
  }
}
