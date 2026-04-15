import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/constants/app_colors.dart';
import '../../models/webview/video_item.dart';
import '../../providers/webview_provider.dart';
import '../../widgets/video_card.dart';
import '../../widgets/shorts_card.dart';

class HistoryScreen extends ConsumerStatefulWidget {
  final bool shortsOnly;
  const HistoryScreen({super.key, this.shortsOnly = false});

  @override
  ConsumerState<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends ConsumerState<HistoryScreen> {
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (widget.shortsOnly) {
        ref.read(libraryDataProvider.notifier).load();
      } else {
        ref.read(historyListProvider.notifier).load();
      }
    });
    _scrollController.addListener(_onScroll);
  }

  void _onScroll() {
    if (widget.shortsOnly) return; // 라이브러리 데이터는 추가 페이지 없음
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 300) {
      ref.read(historyListProvider.notifier).loadMore();
    }
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    // shorts-only 모드는 라이브러리(FElibrary) 응답을, 그 외는 FEhistory 응답을 사용
    final historyAsync = widget.shortsOnly
        ? ref.watch(libraryDataProvider).whenData((d) => d.historyVideos)
        : ref.watch(historyListProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.shortsOnly ? 'Shorts 기록' : '기록'),
        actions: [
          IconButton(
            icon: const Icon(Icons.search),
            onPressed: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('준비 중인 기능입니다'),
                  duration: Duration(seconds: 2),
                ),
              );
            },
          ),
          IconButton(
            icon: const Icon(Icons.delete_outline),
            onPressed: () {
              showDialog(
                context: context,
                builder: (context) => AlertDialog(
                  title: const Text('시청 기록 삭제'),
                  content: const Text('모든 시청 기록을 삭제하시겠습니까?'),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text('취소'),
                    ),
                    TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text('삭제',
                          style: TextStyle(color: AppColors.liveBadge)),
                    ),
                  ],
                ),
              );
            },
          ),
        ],
      ),
      body: historyAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.error_outline,
                  size: 48, color: colorScheme.onSurfaceVariant),
              const SizedBox(height: 12),
              Text('시청 기록을 불러올 수 없습니다',
                  style: TextStyle(color: colorScheme.onSurfaceVariant)),
              const SizedBox(height: 12),
              FilledButton(
                onPressed: () => ref.read(historyListProvider.notifier).load(),
                child: const Text('다시 시도'),
              ),
            ],
          ),
        ),
        data: (allVideos) {
          bool isShorts(VideoItem v) {
            if (v.videoType == VideoType.shorts) return true;
            // duration "0:30" / "1:00" 등 60초 이하 = Shorts로 간주
            final m = RegExp(r'^(\d+):(\d{2})$').firstMatch(v.duration);
            if (m != null) {
              final total = int.parse(m.group(1)!) * 60 + int.parse(m.group(2)!);
              return total <= 60;
            }
            return false;
          }

          final videos = widget.shortsOnly
              ? allVideos.where(isShorts).toList()
              : allVideos;
          if (videos.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.history,
                      size: 64, color: colorScheme.onSurfaceVariant),
                  const SizedBox(height: 16),
                  Text('시청 기록이 없습니다',
                      style: TextStyle(color: colorScheme.onSurfaceVariant)),
                ],
              ),
            );
          }

          // Shorts와 일반 영상 분리
          final shorts = videos.where(isShorts).toList();
          final regularVideos = videos.where((v) => !isShorts(v)).toList();

          return RefreshIndicator(
            onRefresh: () => ref.read(historyListProvider.notifier).load(),
            child: CustomScrollView(
              controller: _scrollController,
              slivers: [
                // Shorts 섹션
                if (shorts.isNotEmpty) ...[
                  if (!widget.shortsOnly)
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                        child: Row(
                          children: [
                            Icon(Icons.play_circle_filled,
                                size: 20, color: Colors.red[600]),
                            const SizedBox(width: 8),
                            const Text('Shorts',
                                style: TextStyle(
                                    fontSize: 16,
                                    fontWeight: FontWeight.bold)),
                          ],
                        ),
                      ),
                    ),
                  if (widget.shortsOnly)
                    SliverPadding(
                      padding: const EdgeInsets.all(8),
                      sliver: SliverGrid(
                        gridDelegate:
                            const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 3,
                          childAspectRatio: 9 / 16,
                          mainAxisSpacing: 8,
                          crossAxisSpacing: 8,
                        ),
                        delegate: SliverChildBuilderDelegate(
                          (context, index) {
                            final video = shorts[index];
                            return ShortsCard(
                              video: video,
                              onTap: () => ref
                                  .read(webViewChannelProvider)
                                  .playVideo(video.youtubeUrl),
                            );
                          },
                          childCount: shorts.length,
                        ),
                      ),
                    )
                  else
                    SliverToBoxAdapter(
                      child: SizedBox(
                        height: 220,
                        child: ListView.builder(
                          scrollDirection: Axis.horizontal,
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          itemCount: shorts.length,
                          itemBuilder: (context, index) {
                            final video = shorts[index];
                            return Padding(
                              padding: const EdgeInsets.only(right: 8),
                              child: SizedBox(
                                width: 140,
                                child: ShortsCard(
                                  video: video,
                                  onTap: () {
                                    ref
                                        .read(webViewChannelProvider)
                                        .playVideo(video.youtubeUrl);
                                  },
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                    ),
                ],
                // 일반 영상 리스트
                if (regularVideos.isNotEmpty)
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                      child: Text('오늘',
                          style: TextStyle(
                              fontSize: 14,
                              color: colorScheme.onSurfaceVariant)),
                    ),
                  ),
                SliverList(
                  delegate: SliverChildBuilderDelegate(
                    (context, index) {
                      final video = regularVideos[index];
                      return VideoCard(
                        video: video,
                        onTap: () {
                          ref
                              .read(webViewChannelProvider)
                              .playVideo(video.youtubeUrl);
                        },
                      );
                    },
                    childCount: regularVideos.length,
                  ),
                ),
                // 로딩 인디케이터
                if (!widget.shortsOnly &&
                    ref.read(historyListProvider.notifier).hasMore)
                  const SliverToBoxAdapter(
                    child: Padding(
                      padding: EdgeInsets.all(16),
                      child: Center(child: CircularProgressIndicator()),
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }
}
