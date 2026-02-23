import 'package:flutter/material.dart';

/// 빈 상태 위젯 (데이터 로드 실패 또는 빈 목록)
class EmptyStateView extends StatelessWidget {
  final IconData icon;
  final String message;
  final VoidCallback? onRetry;

  const EmptyStateView({
    super.key,
    this.icon = Icons.video_library_outlined,
    required this.message,
    this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 48, color: colorScheme.onSurfaceVariant),
          const SizedBox(height: 16),
          Text(message,
              textAlign: TextAlign.center,
              style: TextStyle(color: colorScheme.onSurfaceVariant)),
          if (onRetry != null) ...[
            const SizedBox(height: 16),
            FilledButton(
              onPressed: onRetry,
              child: const Text('다시 시도'),
            ),
          ],
        ],
      ),
    );
  }
}

/// 에러 상태 위젯
class ErrorStateView extends StatelessWidget {
  final Object error;
  final VoidCallback? onRetry;

  const ErrorStateView({
    super.key,
    required this.error,
    this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.error_outline,
              size: 48, color: colorScheme.onSurfaceVariant),
          const SizedBox(height: 16),
          Text('오류가 발생했습니다\n$error',
              textAlign: TextAlign.center,
              style: TextStyle(color: colorScheme.onSurfaceVariant)),
          if (onRetry != null) ...[
            const SizedBox(height: 16),
            FilledButton(
              onPressed: onRetry,
              child: const Text('다시 시도'),
            ),
          ],
        ],
      ),
    );
  }
}
