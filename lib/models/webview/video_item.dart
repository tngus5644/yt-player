class VideoItem {
  final String id;
  final String title;
  final String thumbnail;
  final String channelTitle;
  final String channelThumbnail;
  final String duration;
  final String viewCount;
  final String publishedAt;
  final VideoType videoType;

  const VideoItem({
    required this.id,
    required this.title,
    required this.thumbnail,
    required this.channelTitle,
    this.channelThumbnail = '',
    this.duration = '',
    this.viewCount = '',
    this.publishedAt = '',
    this.videoType = VideoType.video,
  });

  factory VideoItem.fromJson(Map<String, dynamic> json) {
    return VideoItem(
      id: json['id'] as String? ?? '',
      title: json['title'] as String? ?? '',
      thumbnail: json['thumbnail'] as String? ?? '',
      channelTitle: json['channel'] as String? ?? json['channelTitle'] as String? ?? '',
      channelThumbnail: json['channelThumbnail'] as String? ?? '',
      duration: json['duration'] as String? ?? '',
      viewCount: json['views'] as String? ?? json['viewCount'] as String? ?? '',
      publishedAt: json['publishedAt'] as String? ?? '',
      videoType: VideoType.fromString(json['videoType'] as String?),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'thumbnail': thumbnail,
        'channelTitle': channelTitle,
        'channelThumbnail': channelThumbnail,
        'duration': duration,
        'viewCount': viewCount,
        'publishedAt': publishedAt,
        'videoType': videoType.name,
      };

  String get youtubeUrl {
    switch (videoType) {
      case VideoType.shorts:
        return 'https://m.youtube.com/shorts/$id';
      case VideoType.live:
      case VideoType.video:
        return 'https://m.youtube.com/watch?v=$id';
    }
  }
}

enum VideoType {
  video,
  shorts,
  live;

  static VideoType fromString(String? value) {
    switch (value?.toUpperCase()) {
      case 'SHORTS':
      case 'SHORT':
        return VideoType.shorts;
      case 'LIVE':
        return VideoType.live;
      default:
        return VideoType.video;
    }
  }
}
