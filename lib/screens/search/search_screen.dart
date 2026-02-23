import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/webview_provider.dart';
import '../../widgets/video_card.dart';
import '../../widgets/async_state_view.dart';

class SearchScreen extends ConsumerStatefulWidget {
  const SearchScreen({super.key});

  @override
  ConsumerState<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends ConsumerState<SearchScreen> {
  final _controller = TextEditingController();
  final _focusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _focusNode.requestFocus();
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _performSearch(String query) {
    if (query.trim().isEmpty) return;
    _focusNode.unfocus();
    ref.read(searchVideoListProvider.notifier).search(query);
  }

  @override
  Widget build(BuildContext context) {
    final searchResults = ref.watch(searchVideoListProvider);
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: TextField(
          controller: _controller,
          focusNode: _focusNode,
          decoration: InputDecoration(
            hintText: '검색',
            border: InputBorder.none,
            hintStyle: TextStyle(color: colorScheme.onSurfaceVariant),
          ),
          style: TextStyle(color: colorScheme.onSurface),
          textInputAction: TextInputAction.search,
          onSubmitted: _performSearch,
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.search),
            onPressed: () => _performSearch(_controller.text),
          ),
          IconButton(
            icon: const Icon(Icons.clear),
            onPressed: () {
              _controller.clear();
              _focusNode.requestFocus();
            },
          ),
        ],
      ),
      body: searchResults.when(
        data: (videos) {
          if (videos.isEmpty) {
            return Center(
              child: Text(
                '검색어를 입력하세요',
                style: TextStyle(color: colorScheme.onSurfaceVariant),
              ),
            );
          }
          return ListView.builder(
            itemCount: videos.length,
            itemBuilder: (context, index) {
              return VideoCard(
                video: videos[index],
                onTap: () {
                  ref
                      .read(webViewChannelProvider)
                      .playVideo(videos[index].youtubeUrl);
                },
              );
            },
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => ErrorStateView(
          error: error,
          onRetry: () => _performSearch(_controller.text),
        ),
      ),
    );
  }
}
