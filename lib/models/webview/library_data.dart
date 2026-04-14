import 'video_item.dart';
import 'playlist_item.dart';

class LibraryData {
  final List<VideoItem> historyVideos;
  final List<PlaylistItem> playlists;

  const LibraryData({
    this.historyVideos = const [],
    this.playlists = const [],
  });

  factory LibraryData.fromJson(Map<String, dynamic> json) {
    return LibraryData(
      historyVideos: (json['historyVideos'] as List<dynamic>?)
              ?.map((e) => VideoItem.fromJson(Map<String, dynamic>.from(e)))
              .toList() ??
          [],
      playlists: (json['playlists'] as List<dynamic>?)
              ?.map((e) => PlaylistItem.fromJson(Map<String, dynamic>.from(e)))
              .toList() ??
          [],
    );
  }
}
