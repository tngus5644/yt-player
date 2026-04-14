import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../models/webview/playlist_item.dart';
import '../../../models/webview/video_item.dart';
import '../../../providers/webview_provider.dart';
import '../../../widgets/login_gate.dart';

class LibraryTab extends ConsumerStatefulWidget {
  const LibraryTab({super.key});

  @override
  ConsumerState<LibraryTab> createState() => _LibraryTabState();
}

class _LibraryTabState extends ConsumerState<LibraryTab> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final isLoggedIn = ref.read(loginStateProvider);
      if (isLoggedIn) {
        final library = ref.read(libraryDataProvider);
        if (library is AsyncLoading || library.valueOrNull == null) {
          ref.read(libraryDataProvider.notifier).load();
        }
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final isLoggedIn = ref.watch(loginStateProvider);
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('보관함'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            onPressed: () => context.push('/settings'),
          ),
        ],
      ),
      body: isLoggedIn
          ? _buildLoggedInContent(context, colorScheme)
          : const LoginRequiredView(
              icon: Icons.video_library_outlined,
              message: '로그인하여 보관함을 확인하세요',
            ),
    );
  }

  Widget _buildLoggedInContent(BuildContext context, ColorScheme colorScheme) {
    final libraryAsync = ref.watch(libraryDataProvider);

    return libraryAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.error_outline, size: 48, color: colorScheme.onSurfaceVariant),
            const SizedBox(height: 12),
            Text('데이터를 불러올 수 없습니다',
                style: TextStyle(color: colorScheme.onSurfaceVariant)),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: () => ref.read(libraryDataProvider.notifier).load(),
              child: const Text('다시 시도'),
            ),
          ],
        ),
      ),
      data: (data) => RefreshIndicator(
        onRefresh: () => ref.read(libraryDataProvider.notifier).load(),
        child: ListView(
          children: [
            _buildProfileHeader(context, colorScheme),
            if (data.historyVideos.isNotEmpty) ...[
              _SectionHeader(
                title: '기록',
                onViewAll: () => context.push('/history'),
              ),
              _HistoryRow(videos: data.historyVideos),
            ],
            if (data.playlists.isNotEmpty) ...[
              _SectionHeader(
                title: '재생목록',
                onViewAll: () => context.push('/playlists'),
              ),
              _PlaylistRow(playlists: data.playlists),
            ],
            if (data.historyVideos.isEmpty && data.playlists.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 60),
                child: Center(
                  child: Column(
                    children: [
                      Icon(Icons.video_library_outlined,
                          size: 48, color: colorScheme.onSurfaceVariant),
                      const SizedBox(height: 12),
                      Text('보관함이 비어있습니다',
                          style: TextStyle(color: colorScheme.onSurfaceVariant)),
                    ],
                  ),
                ),
              ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  Widget _buildProfileHeader(BuildContext context, ColorScheme colorScheme) {
    final googleUser = ref.watch(googleUserProvider);
    final name = googleUser?.displayName.isNotEmpty == true
        ? googleUser!.displayName
        : '사용자';
    final email = googleUser?.email ?? '';

    // 이니셜 추출
    final initials = name.isNotEmpty ? name.characters.first : '?';

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
      child: Row(
        children: [
          CircleAvatar(
            radius: 36,
            backgroundColor: Colors.purple,
            backgroundImage: googleUser?.photoUrl != null
                ? NetworkImage(googleUser!.photoUrl!)
                : null,
            child: googleUser?.photoUrl == null
                ? Text(initials,
                    style: const TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.bold,
                        color: Colors.white))
                : null,
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(name,
                    style: const TextStyle(
                        fontSize: 20, fontWeight: FontWeight.bold)),
                if (email.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Text(email,
                        style: TextStyle(
                            fontSize: 13,
                            color: colorScheme.onSurfaceVariant)),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  final VoidCallback onViewAll;

  const _SectionHeader({required this.title, required this.onViewAll});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 20, 8, 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(title,
              style:
                  const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          TextButton(
            onPressed: onViewAll,
            style: TextButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
                side: BorderSide(
                    color: Theme.of(context).colorScheme.outlineVariant),
              ),
            ),
            child: Text('모두 보기',
                style: TextStyle(
                    fontSize: 13,
                    color: Theme.of(context).colorScheme.onSurface)),
          ),
        ],
      ),
    );
  }
}

class _HistoryRow extends ConsumerWidget {
  final List<VideoItem> videos;

  const _HistoryRow({required this.videos});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // YouTube 스타일: 모든 항목을 동일한 크기(160x90 썸네일)로 통일
    const double cardWidth = 160;
    const double thumbHeight = 90; // 16:9 비율 기준

    return SizedBox(
      height: thumbHeight + 56, // 썸네일 + 제목/채널 텍스트 영역
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        itemCount: videos.length,
        itemBuilder: (context, index) {
          final video = videos[index];
          final isShorts = video.videoType == VideoType.shorts;

          return GestureDetector(
            onTap: () {
              ref.read(webViewChannelProvider).playVideo(video.youtubeUrl);
            },
            child: Container(
              width: cardWidth,
              margin: const EdgeInsets.only(right: 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // 썸네일 — 모든 영상 동일 크기
                  ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: SizedBox(
                      width: cardWidth,
                      height: thumbHeight,
                      child: Stack(
                        fit: StackFit.expand,
                        children: [
                          CachedNetworkImage(
                            imageUrl: video.thumbnail,
                            fit: BoxFit.cover,
                            placeholder: (_, __) =>
                                Container(color: Colors.grey[800]),
                            errorWidget: (_, __, ___) => Container(
                                color: Colors.grey[800],
                                child: const Icon(Icons.image_not_supported,
                                    color: Colors.grey)),
                          ),
                          if (isShorts)
                            Positioned(
                              left: 6,
                              bottom: 6,
                              child: Container(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: Colors.red,
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                child: const Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(Icons.local_fire_department,
                                        size: 12, color: Colors.white),
                                    SizedBox(width: 2),
                                    Text('SHORTS',
                                        style: TextStyle(
                                            fontSize: 10,
                                            fontWeight: FontWeight.bold,
                                            color: Colors.white)),
                                  ],
                                ),
                              ),
                            ),
                          if (!isShorts && video.duration.isNotEmpty)
                            Positioned(
                              right: 4,
                              bottom: 4,
                              child: Container(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 4, vertical: 1),
                                decoration: BoxDecoration(
                                  color: Colors.black87,
                                  borderRadius: BorderRadius.circular(3),
                                ),
                                child: Text(video.duration,
                                    style: const TextStyle(
                                        fontSize: 11, color: Colors.white)),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 6),
                  // 제목
                  Text(video.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 12, height: 1.2)),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _PlaylistRow extends StatelessWidget {
  final List<PlaylistItem> playlists;

  const _PlaylistRow({required this.playlists});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 180,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        itemCount: playlists.length,
        itemBuilder: (context, index) {
          final playlist = playlists[index];
          return GestureDetector(
            onTap: () {
              context.push('/playlists');
            },
            child: Container(
              width: 160,
              margin: const EdgeInsets.only(right: 10),
              child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 썸네일 + 동영상 수 오버레이
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: AspectRatio(
                    aspectRatio: 16 / 9,
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
                        // 동영상 수 오버레이
                        Positioned(
                          left: 0,
                          right: 0,
                          bottom: 0,
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 8, vertical: 4),
                            decoration: const BoxDecoration(
                              gradient: LinearGradient(
                                begin: Alignment.bottomCenter,
                                end: Alignment.topCenter,
                                colors: [
                                  Colors.black87,
                                  Colors.transparent,
                                ],
                              ),
                            ),
                            child: Row(
                              children: [
                                const Icon(Icons.playlist_play,
                                    size: 16, color: Colors.white),
                                const SizedBox(width: 4),
                                Text('동영상 ${playlist.videoCount}개',
                                    style: const TextStyle(
                                        fontSize: 11,
                                        color: Colors.white,
                                        fontWeight: FontWeight.w500)),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 6),
                Text(playlist.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 12)),
                Text(
                  '${playlist.visibility == 'public' ? '공개' : '비공개'} \u00B7 재생목록',
                  maxLines: 1,
                  style: TextStyle(
                      fontSize: 11,
                      color: Theme.of(context).colorScheme.onSurfaceVariant),
                ),
              ],
            ),
            ),
          );
        },
      ),
    );
  }
}
