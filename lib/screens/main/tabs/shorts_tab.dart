import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../providers/webview_provider.dart';
import '../../../widgets/async_state_view.dart';

class ShortsTab extends ConsumerWidget {
  const ShortsTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final shortsList = ref.watch(shortsVideoListProvider);

    return Scaffold(
      backgroundColor: Colors.black,
      body: shortsList.when(
        data: (videos) {
          if (videos.isEmpty) {
            return EmptyStateView(
              message: '쇼츠를 불러오지 못했습니다',
              onRetry: () =>
                  ref.read(shortsVideoListProvider.notifier).load(),
            );
          }

          return AndroidView(
            viewType: 'shorts-webview',
            creationParams: {
              'url': 'https://m.youtube.com/shorts/${videos.first.id}',
            },
            creationParamsCodec: const StandardMessageCodec(),
          );
        },
        loading: () => const Center(
          child: CircularProgressIndicator(color: Colors.white),
        ),
        error: (error, stack) => ErrorStateView(
          error: error,
          onRetry: () =>
              ref.read(shortsVideoListProvider.notifier).load(),
        ),
      ),
    );
  }
}
