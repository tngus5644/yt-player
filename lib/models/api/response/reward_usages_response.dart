class RewardUsagesResponse {
  final bool success;
  final String? message;
  final List<RewardUsageItem>? data;

  const RewardUsagesResponse({
    required this.success,
    this.message,
    this.data,
  });

  factory RewardUsagesResponse.fromJson(Map<String, dynamic> json) {
    return RewardUsagesResponse(
      success: json['success'] as bool? ?? false,
      message: json['message'] as String?,
      data: json['data'] != null
          ? (json['data'] as List)
              .map((e) => RewardUsageItem.fromJson(Map<String, dynamic>.from(e)))
              .toList()
          : null,
    );
  }
}

class RewardUsageItem {
  final int id;
  final String type;
  final int itemId;
  final String itemName;
  final double pointsSpent;
  final String status;
  final String? createdAt;

  const RewardUsageItem({
    required this.id,
    required this.type,
    required this.itemId,
    required this.itemName,
    required this.pointsSpent,
    required this.status,
    this.createdAt,
  });

  factory RewardUsageItem.fromJson(Map<String, dynamic> json) {
    return RewardUsageItem(
      id: json['id'] as int? ?? 0,
      type: json['type'] as String? ?? '',
      itemId: json['item_id'] as int? ?? 0,
      itemName: json['item_name'] as String? ?? '',
      pointsSpent: (json['points_spent'] as num?)?.toDouble() ?? 0.0,
      status: json['status'] as String? ?? '',
      createdAt: json['created_at'] as String?,
    );
  }
}
