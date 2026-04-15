import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../providers/webview_provider.dart';

class DashboardProfileCard extends ConsumerWidget {
  final bool isLoggedIn;
  final AuthUser? user;
  final VoidCallback onLogout;

  const DashboardProfileCard({
    super.key,
    required this.isLoggedIn,
    required this.user,
    required this.onLogout,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;

    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      color: colorScheme.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: isLoggedIn
            ? _buildLoggedInView(ref, colorScheme)
            : _buildLoggedOutView(ref, colorScheme),
      ),
    );
  }

  Widget _buildLoggedInView(WidgetRef ref, ColorScheme colorScheme) {
    final photoUrl = user?.photoUrl;
    final displayName = user?.displayName ?? '';
    final email = user?.email ?? '로그인됨';

    return Row(
      children: [
        CircleAvatar(
          radius: 18,
          backgroundColor: AppColors.primary.withValues(alpha: 0.15),
          child: photoUrl == null || photoUrl.isEmpty
              ? const Icon(Icons.person, color: AppColors.primary, size: 22)
              : ClipOval(
                  child: CachedNetworkImage(
                    imageUrl: photoUrl,
                    width: 36,
                    height: 36,
                    fit: BoxFit.cover,
                    placeholder: (_, __) => const Icon(Icons.person,
                        color: AppColors.primary, size: 22),
                    errorWidget: (_, __, ___) => const Icon(Icons.person,
                        color: AppColors.primary, size: 22),
                  ),
                ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                displayName.isNotEmpty ? displayName : email,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: colorScheme.onSurface,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
        TextButton.icon(
          onPressed: onLogout,
          icon: const Icon(Icons.logout, size: 16),
          label: const Text('로그아웃'),
          style: TextButton.styleFrom(
            foregroundColor: colorScheme.onSurfaceVariant,
            textStyle: const TextStyle(fontSize: 13),
          ),
        ),
      ],
    );
  }

  Widget _buildLoggedOutView(WidgetRef ref, ColorScheme colorScheme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Icon(Icons.info_outline, size: 20, color: colorScheme.onSurfaceVariant),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                '로그인하여 리워드를 교환하세요',
                style: TextStyle(
                  fontSize: 13,
                  color: colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        FilledButton.icon(
          onPressed: () => ref.read(loginStateProvider.notifier).signIn(),
          icon: const Icon(Icons.login, size: 18),
          label: const Text('구글 로그인'),
          style: FilledButton.styleFrom(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            textStyle:
                const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
          ),
        ),
      ],
    );
  }
}
