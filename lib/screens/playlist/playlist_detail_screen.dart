import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../models/webview/playlist_item.dart';
import '../../providers/webview_provider.dart';

class PlaylistDetailScreen extends ConsumerWidget {
  const PlaylistDetailScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final libraryAsync = ref.watch(libraryDataProvider);
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('재생목록'),
      ),
      body: libraryAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.error_outline,
                  size: 48, color: colorScheme.onSurfaceVariant),
              const SizedBox(height: 12),
              Text('데이터를 불러올 수 없습니다',
                  style: TextStyle(color: colorScheme.onSurfaceVariant)),
            ],
          ),
        ),
        data: (data) {
          final playlists = data.playlists;
          if (playlists.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.playlist_play,
                      size: 64, color: colorScheme.onSurfaceVariant),
                  const SizedBox(height: 16),
                  Text('재생목록이 없습니다',
                      style: TextStyle(color: colorScheme.onSurfaceVariant)),
                ],
              ),
            );
          }

          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 정렬 드롭다운
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  decoration: BoxDecoration(
                    color: colorScheme.surfaceContainerHighest,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text('최신순',
                          style: TextStyle(
                              fontSize: 13, color: colorScheme.onSurface)),
                      const SizedBox(width: 4),
                      Icon(Icons.arrow_drop_down,
                          size: 18, color: colorScheme.onSurface),
                    ],
                  ),
                ),
              ),
              // 재생목록 리스트
              Expanded(
                child: ListView.builder(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  itemCount: playlists.length,
                  itemBuilder: (context, index) =>
                      _PlaylistListTile(playlist: playlists[index]),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _PlaylistListTile extends StatelessWidget {
  final PlaylistItem playlist;

  const _PlaylistListTile({required this.playlist});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return InkWell(
      onTap: () {
        // TODO: 재생목록 동영상 목록 화면으로 이동
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 썸네일 + 동영상 수 오버레이
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: SizedBox(
              width: 160,
              height: 90,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  if (playlist.thumbnail.isNotEmpty)
                    CachedNetworkImage(
                      imageUrl: playlist.thumbnail,
                      fit: BoxFit.cover,
                      placeholder: (_, __) =>
                          Container(color: Colors.grey[800]),
                      errorWidget: (_, __, ___) => Container(
                          color: Colors.grey[800],
                          child: const Icon(Icons.playlist_play,
                              color: Colors.grey)),
                    )
                  else
                    Container(
                        color: Colors.grey[800],
                        child: const Icon(Icons.playlist_play,
                            color: Colors.grey, size: 40)),
                  Positioned(
                    left: 0,
                    right: 0,
                    bottom: 0,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 3),
                      color: Colors.black54,
                      child: Row(
                        children: [
                          const Icon(Icons.playlist_play,
                              size: 14, color: Colors.white),
                          const SizedBox(width: 4),
                          Text('동영상 ${playlist.videoCount}개',
                              style: const TextStyle(
                                  fontSize: 11, color: Colors.white)),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(width: 12),
          // 정보
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 2),
                Text(playlist.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 14)),
                const SizedBox(height: 4),
                Text(
                  '${playlist.visibility == 'public' ? '공개' : '비공개'} \u00B7 재생목록',
                  style: TextStyle(
                      fontSize: 12, color: colorScheme.onSurfaceVariant),
                ),
                if (playlist.updatedAt.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Text(playlist.updatedAt,
                        style: TextStyle(
                            fontSize: 12,
                            color: colorScheme.onSurfaceVariant)),
                  ),
              ],
            ),
          ),
          // 더보기 메뉴
          IconButton(
            icon: const Icon(Icons.more_vert, size: 20),
            onPressed: () {},
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(),
          ),
        ],
      ),
      ),
    );
  }
}
