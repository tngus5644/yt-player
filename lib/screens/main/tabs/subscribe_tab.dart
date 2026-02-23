import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../../core/constants/app_colors.dart';
import '../../../providers/webview_provider.dart';

class SubscribeTab extends ConsumerStatefulWidget {
  const SubscribeTab({super.key});

  @override
  ConsumerState<SubscribeTab> createState() => _SubscribeTabState();
}

class _SubscribeTabState extends ConsumerState<SubscribeTab> {
  @override
  Widget build(BuildContext context) {
    final isLoggedIn = ref.watch(loginStateProvider);
    final subscriptions = ref.watch(subscriptionListProvider);
    final colorScheme = Theme.of(context).colorScheme;

    if (!isLoggedIn) {
      return Scaffold(
        appBar: AppBar(title: const Text('구독')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.subscriptions_outlined,
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
      appBar: AppBar(title: const Text('구독')),
      body: subscriptions.when(
        data: (channels) => ListView.builder(
          itemCount: channels.length,
          itemBuilder: (context, index) {
            final channel = channels[index];
            return ListTile(
              leading: CircleAvatar(
                backgroundImage: channel.thumbnail.isNotEmpty
                    ? CachedNetworkImageProvider(channel.thumbnail)
                    : null,
                child: channel.thumbnail.isEmpty
                    ? Text(channel.title.isNotEmpty ? channel.title[0] : '?')
                    : null,
              ),
              title: Text(channel.title),
              subtitle: channel.handle.isNotEmpty ? Text(channel.handle) : null,
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (channel.isLive)
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: AppColors.liveBadge,
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: const Text('LIVE',
                          style: TextStyle(color: Colors.white, fontSize: 10)),
                    ),
                  if (channel.hasNew) ...[
                    const SizedBox(width: 4),
                    Container(
                      width: 8,
                      height: 8,
                      decoration: const BoxDecoration(
                        color: AppColors.newBadge,
                        shape: BoxShape.circle,
                      ),
                    ),
                  ],
                ],
              ),
            );
          },
        ),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, s) => Center(
          child: Text('오류: $e',
              style: TextStyle(color: colorScheme.onSurfaceVariant)),
        ),
      ),
    );
  }
}
