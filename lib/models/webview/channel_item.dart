class ChannelItem {
  final String id;
  final String title;
  final String thumbnail;
  final String handle;
  final bool isLive;
  final bool hasNew;

  const ChannelItem({
    required this.id,
    required this.title,
    this.thumbnail = '',
    this.handle = '',
    this.isLive = false,
    this.hasNew = false,
  });

  factory ChannelItem.fromJson(Map<String, dynamic> json) {
    return ChannelItem(
      id: json['id'] as String? ?? '',
      title: json['title'] as String? ?? '',
      thumbnail: json['thumbnail'] as String? ?? '',
      handle: json['handle'] as String? ?? '',
      isLive: json['isLive'] as bool? ?? false,
      hasNew: json['hasNew'] as bool? ?? false,
    );
  }
}
