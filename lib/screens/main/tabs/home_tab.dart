import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/constants/app_colors.dart';
import '../../../providers/webview_provider.dart';
import '../../../widgets/video_card.dart';
import '../../../widgets/async_state_view.dart';

class HomeTab extends ConsumerWidget {
  const HomeTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final videoList = ref.watch(homeVideoListProvider);
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'YTPlayer',
          style: TextStyle(
            fontWeight: FontWeight.bold,
            color: AppColors.primary,
            fontSize: 22,
          ),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.search),
            onPressed: () => context.push('/search'),
          ),
          IconButton(
            icon: const Icon(Icons.history),
            onPressed: () => context.push('/history'),
          ),
        ],
      ),
      body: videoList.when(
        data: (videos) {
          if (videos.isEmpty) {
            return EmptyStateView(
              message: '영상을 불러오지 못했습니다',
              onRetry: () =>
                  ref.read(homeVideoListProvider.notifier).loadHomeFeed(),
            );
          }
          return RefreshIndicator(
            onRefresh: () =>
                ref.read(homeVideoListProvider.notifier).loadHomeFeed(),
            child: ListView.builder(
              itemCount: videos.length + 1,
              itemBuilder: (context, index) {
                if (index == videos.length) {
                  // 마지막 아이템 → 더 로드
                  WidgetsBinding.instance.addPostFrameCallback((_) {
                    ref.read(homeVideoListProvider.notifier).loadMore();
                  });
                  return const Padding(
                    padding: EdgeInsets.all(16),
                    child: Center(child: CircularProgressIndicator()),
                  );
                }
                return VideoCard(
                  video: videos[index],
                  onTap: () {
                    ref
                        .read(webViewChannelProvider)
                        .playVideo(videos[index].youtubeUrl);
                  },
                );
              },
            ),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => ErrorStateView(
          error: error,
          onRetry: () =>
              ref.read(homeVideoListProvider.notifier).loadHomeFeed(),
        ),
      ),
    );
  }
}
