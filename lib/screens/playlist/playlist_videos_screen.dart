import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/webview_provider.dart';
import '../../widgets/video_card.dart';

class PlaylistVideosScreen extends ConsumerStatefulWidget {
  final String playlistId;
  final String title;

  const PlaylistVideosScreen({
    super.key,
    required this.playlistId,
    required this.title,
  });

  @override
  ConsumerState<PlaylistVideosScreen> createState() =>
      _PlaylistVideosScreenState();
}

class _PlaylistVideosScreenState extends ConsumerState<PlaylistVideosScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(playlistDetailProvider.notifier).load(widget.playlistId);
    });
  }

  @override
  Widget build(BuildContext context) {
    final asyncVideos = ref.watch(playlistDetailProvider);
    final colorScheme = Theme.of(context).colorScheme;

    // 영상 수 받아지면 라이브러리 카드 카운트 보정
    ref.listen(playlistDetailProvider, (prev, next) {
      next.whenData((videos) {
        debugPrint('[PlaylistVideos] update count ${widget.playlistId} → ${videos.length}');
        if (videos.isNotEmpty) {
          ref
              .read(libraryDataProvider.notifier)
              .updatePlaylistVideoCount(widget.playlistId, videos.length);
        }
      });
    });

    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      body: asyncVideos.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Text('영상을 불러올 수 없습니다',
              style: TextStyle(color: colorScheme.onSurfaceVariant)),
        ),
        data: (videos) {
          if (videos.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.video_library_outlined,
                      size: 64, color: colorScheme.onSurfaceVariant),
                  const SizedBox(height: 16),
                  Text('영상이 없습니다',
                      style: TextStyle(color: colorScheme.onSurfaceVariant)),
                ],
              ),
            );
          }
          return ListView.builder(
            padding: const EdgeInsets.symmetric(vertical: 8),
            itemCount: videos.length,
            itemBuilder: (context, i) => VideoCard(video: videos[i]),
          );
        },
      ),
    );
  }
}
