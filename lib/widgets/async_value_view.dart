import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// AsyncValue의 loading/error/data 패턴을 공통화하는 위젯
class AsyncValueView<T> extends StatelessWidget {
  final AsyncValue<T> value;
  final Widget Function(T data) builder;
  final Widget? loading;
  final Widget Function(Object error, StackTrace? stackTrace)? errorBuilder;
  final String? emptyMessage;

  const AsyncValueView({
    super.key,
    required this.value,
    required this.builder,
    this.loading,
    this.errorBuilder,
    this.emptyMessage,
  });

  @override
  Widget build(BuildContext context) {
    return value.when(
      data: (data) {
        if (emptyMessage != null && _isEmpty(data)) {
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text(
                emptyMessage!,
                style: TextStyle(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          );
        }
        return builder(data);
      },
      loading: () =>
          loading ??
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 24),
            child: Center(child: CircularProgressIndicator()),
          ),
      error: (e, st) =>
          errorBuilder?.call(e, st) ??
          Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text(
                '오류: $e',
                style: TextStyle(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          ),
    );
  }

  bool _isEmpty(T data) {
    if (data == null) return true;
    if (data is List) return data.isEmpty;
    if (data is Map) return data.isEmpty;
    return false;
  }
}
