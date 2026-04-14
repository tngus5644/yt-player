class NoticeResponse {
  final bool success;
  final String? message;
  final List<NoticeItem>? data;

  const NoticeResponse({
    required this.success,
    this.message,
    this.data,
  });

  factory NoticeResponse.fromJson(Map<String, dynamic> json) {
    return NoticeResponse(
      success: json['success'] as bool? ?? false,
      message: json['message'] as String?,
      data: json['data'] != null
          ? (json['data'] as List)
              .map((e) => NoticeItem.fromJson(Map<String, dynamic>.from(e)))
              .toList()
          : null,
    );
  }
}

class NoticeItem {
  final int id;
  final String title;
  final String content;
  final int? priority;
  final String? createdAt;

  const NoticeItem({
    required this.id,
    required this.title,
    required this.content,
    this.priority,
    this.createdAt,
  });

  factory NoticeItem.fromJson(Map<String, dynamic> json) {
    return NoticeItem(
      id: json['id'] as int? ?? 0,
      title: json['title'] as String? ?? '',
      content: json['content'] as String? ?? '',
      priority: json['priority'] as int?,
      createdAt: json['created_at'] as String?,
    );
  }
}
