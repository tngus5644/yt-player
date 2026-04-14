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
    final googleUser = ref.watch(googleUserProvider);
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('보관함'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            onPressed: () => context.push('/settings'),
          ),
        ],
      ),
      body: ListView(
        children: [
          // 프로필 헤더 (로그인 시만 표시)
          if (isLoggedIn) ...[
            Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                children: [
                  CircleAvatar(
                    radius: 40,
                    backgroundColor: colorScheme.surfaceContainerHighest,
                    backgroundImage: googleUser?.photoUrl != null
                        ? NetworkImage(googleUser!.photoUrl!)
                        : null,
                    child: googleUser?.photoUrl == null
                        ? Icon(Icons.person,
                            size: 40, color: colorScheme.onSurfaceVariant)
                        : null,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    googleUser?.displayName.isNotEmpty == true
                        ? googleUser!.displayName
                        : '사용자',
                    style: const TextStyle(
                        fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                  if (googleUser != null) ...[
                    const SizedBox(height: 4),
                    Text(
                      googleUser.email,
                      style: TextStyle(
                        fontSize: 13,
                        color: colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const Divider(),
          ],
          // 메뉴 항목 (로그인 여부에 관계없이 표시)
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
            onTap: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('준비 중인 기능입니다'),
                  duration: Duration(seconds: 2),
                ),
              );
            },
          ),
          // 로그아웃 (로그인 시만 표시)
          if (isLoggedIn) ...[
            const Divider(),
            ListTile(
              leading: Icon(Icons.logout, color: colorScheme.onSurfaceVariant),
              title: const Text('로그아웃'),
              onTap: () => ref.read(loginStateProvider.notifier).signOut(),
            ),
          ],
        ],
      ),
    );
  }
}
