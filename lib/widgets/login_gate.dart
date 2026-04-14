import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/constants/app_colors.dart';
import '../providers/webview_provider.dart';

/// 로그인이 필요한 화면에서 사용하는 공통 로그인 유도 위젯
class LoginRequiredView extends ConsumerWidget {
  final IconData icon;
  final String message;

  const LoginRequiredView({
    super.key,
    this.icon = Icons.person_outline,
    this.message = '로그인이 필요합니다',
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 64, color: colorScheme.onSurfaceVariant),
          const SizedBox(height: 16),
          Text(message, style: TextStyle(color: colorScheme.onSurfaceVariant)),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: () => ref.read(loginStateProvider.notifier).signIn(),
            icon: const Icon(Icons.login),
            label: const Text('구글 로그인'),
            style: FilledButton.styleFrom(backgroundColor: AppColors.primary),
          ),
        ],
      ),
    );
  }
}

/// 로그인 필요 다이얼로그를 표시하고, 사용자가 로그인을 선택하면 실행
Future<bool> showLoginRequiredDialog(
  BuildContext context,
  WidgetRef ref, {
  String title = '로그인 필요',
  String content = '이 기능을 사용하려면 로그인이 필요합니다.',
}) async {
  final shouldLogin = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(title),
      content: Text(content),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx, false),
          child: const Text('취소'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(ctx, true),
          child: const Text('로그인'),
        ),
      ],
    ),
  );
  if (shouldLogin == true) {
    ref.read(loginStateProvider.notifier).signIn();
    return true;
  }
  return false;
}
