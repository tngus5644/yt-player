import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../../core/constants/app_colors.dart';
import '../../../providers/webview_provider.dart';
import '../../../widgets/async_state_view.dart';
import '../../../widgets/login_gate.dart';
import '../../../models/webview/video_item.dart';

class SubscribeTab extends ConsumerWidget {
  const SubscribeTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isLoggedIn = ref.watch(loginStateProvider);

    if (!isLoggedIn) {
      return Scaffold(
        appBar: AppBar(title: const Text('구독')),
        body: const LoginRequiredView(icon: Icons.subscriptions_outlined),
      );
    }

    return Scaffold(
      appBar: AppBar(title: const Text('구독')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.read(subscriptionListProvider.notifier).load();
          ref.read(subscriptionFeedProvider.notifier).load();
        },
        child: CustomScrollView(
          slivers: [
            // 채널 아바타 가로 스크롤
            SliverToBoxAdapter(
              child: _ChannelAvatarRow(),
            ),
            const SliverToBoxAdapter(child: Divider(height: 1)),
            // 구독 피드 영상 리스트
            _SubscriptionFeedList(),
          ],
        ),
      ),
    );
  }
}

class _ChannelAvatarRow extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final subscriptions = ref.watch(subscriptionListProvider);
    final colorScheme = Theme.of(context).colorScheme;

    return subscriptions.when(
      data: (channels) {
        if (channels.isEmpty) {
          return const SizedBox(height: 8);
        }
        return SizedBox(
          height: 88,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            itemCount: channels.length,
            itemBuilder: (context, index) {
              final channel = channels[index];
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 6),
                child: Column(
                  children: [
                    Stack(
                      children: [
                        CircleAvatar(
                          radius: 24,
                          backgroundColor:
                              colorScheme.surfaceContainerHighest,
                          backgroundImage: channel.thumbnail.isNotEmpty
                              ? CachedNetworkImageProvider(
                                  channel.thumbnail)
                              : null,
                          child: channel.thumbnail.isEmpty
                              ? Text(
                                  channel.title.isNotEmpty
                                      ? channel.title[0]
                                      : '?',
                                  style: TextStyle(
                                      color: colorScheme.onSurfaceVariant),
                                )
                              : null,
                        ),
                        if (channel.isLive)
                          Positioned(
                            bottom: 0,
                            left: 0,
                            right: 0,
                            child: Center(
                              child: Container(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 4, vertical: 1),
                                decoration: BoxDecoration(
                                  color: AppColors.liveBadge,
                                  borderRadius: BorderRadius.circular(2),
                                ),
                                child: const Text('LIVE',
                                    style: TextStyle(
                                        color: Colors.white,
                                        fontSize: 8,
                                        fontWeight: FontWeight.bold)),
                              ),
                            ),
                          ),
                        if (channel.hasNew)
                          Positioned(
                            top: 0,
                            right: 0,
                            child: Container(
                              width: 10,
                              height: 10,
                              decoration: BoxDecoration(
                                color: AppColors.newBadge,
                                shape: BoxShape.circle,
                                border: Border.all(
                                    color: colorScheme.surface, width: 1.5),
                              ),
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    SizedBox(
                      width: 56,
                      child: Text(
                        channel.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 10,
                          color: colorScheme.onSurface,
                        ),
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        );
      },
      loading: () => const SizedBox(
        height: 88,
        child: Center(
            child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2))),
      ),
      error: (_, __) => const SizedBox(height: 8),
    );
  }
}

class _SubscriptionFeedList extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final feedState = ref.watch(subscriptionFeedProvider);

    return feedState.when(
      data: (videos) {
        if (videos.isEmpty) {
          return SliverFillRemaining(
            child: EmptyStateView(
              icon: Icons.subscriptions_outlined,
              message: '구독 피드가 비어있습니다',
              onRetry: () =>
                  ref.read(subscriptionFeedProvider.notifier).load(),
            ),
          );
        }
        return SliverList(
          delegate: SliverChildBuilderDelegate(
            (context, index) {
              final video = videos[index];
              return _SubscriptionVideoCard(video: video);
            },
            childCount: videos.length,
          ),
        );
      },
      loading: () => const SliverFillRemaining(
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (error, _) => SliverFillRemaining(
        child: ErrorStateView(
          error: error,
          onRetry: () =>
              ref.read(subscriptionFeedProvider.notifier).load(),
        ),
      ),
    );
  }
}

class _SubscriptionVideoCard extends ConsumerWidget {
  final VideoItem video;

  const _SubscriptionVideoCard({required this.video});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;

    return InkWell(
      onTap: () {
        ref.read(webViewChannelProvider).playVideo(video.youtubeUrl);
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 썸네일 16:9
            AspectRatio(
              aspectRatio: 16 / 9,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    CachedNetworkImage(
                      imageUrl: video.thumbnail,
                      fit: BoxFit.cover,
                      placeholder: (_, __) => Container(
                        color: colorScheme.surfaceContainerHighest,
                      ),
                      errorWidget: (_, __, ___) => Container(
                        color: colorScheme.surfaceContainerHighest,
                        child: const Icon(Icons.error_outline),
                      ),
                    ),
                    if (video.duration.isNotEmpty)
                      Positioned(
                        right: 6,
                        bottom: 6,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 4, vertical: 2),
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.8),
                            borderRadius: BorderRadius.circular(3),
                          ),
                          child: Text(
                            video.duration,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 11,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 8),
            // 채널 + 제목 + 메타정보
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (video.channelThumbnail.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(right: 10, top: 2),
                    child: CircleAvatar(
                      radius: 16,
                      backgroundImage:
                          CachedNetworkImageProvider(video.channelThumbnail),
                    ),
                  )
                else
                  Padding(
                    padding: const EdgeInsets.only(right: 10, top: 2),
                    child: CircleAvatar(
                      radius: 16,
                      backgroundColor: colorScheme.surfaceContainerHighest,
                      child: Icon(Icons.person,
                          size: 16, color: colorScheme.onSurfaceVariant),
                    ),
                  ),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        video.title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w500,
                          color: colorScheme.onSurface,
                          height: 1.3,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        [
                          video.channelTitle,
                          if (video.viewCount.isNotEmpty) video.viewCount,
                          if (video.publishedAt.isNotEmpty) video.publishedAt,
                        ].join(' • '),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 12,
                          color: colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
