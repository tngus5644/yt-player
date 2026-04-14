class PlaylistItem {
  final String playlistId;
  final String title;
  final String thumbnail;
  final int videoCount;
  final String visibility;
  final String updatedAt;
  final String channelTitle;

  const PlaylistItem({
    required this.playlistId,
    required this.title,
    this.thumbnail = '',
    this.videoCount = 0,
    this.visibility = 'private',
    this.updatedAt = '',
    this.channelTitle = '',
  });

  factory PlaylistItem.fromJson(Map<String, dynamic> json) {
    return PlaylistItem(
      playlistId: json['playlistId'] as String? ?? '',
      title: json['title'] as String? ?? '',
      thumbnail: json['thumbnail'] as String? ?? '',
      videoCount: json['videoCount'] as int? ?? 0,
      visibility: json['visibility'] as String? ?? 'private',
      updatedAt: json['updatedAt'] as String? ?? '',
      channelTitle: json['channelTitle'] as String? ?? '',
    );
  }
}
