class RewardUsageDetailResponse {
  final bool success;
  final String? message;
  final RewardUsageDetailData? data;

  const RewardUsageDetailResponse({
    required this.success,
    this.message,
    this.data,
  });

  factory RewardUsageDetailResponse.fromJson(Map<String, dynamic> json) {
    return RewardUsageDetailResponse(
      success: json['success'] as bool? ?? false,
      message: json['message'] as String?,
      data: json['data'] != null
          ? RewardUsageDetailData.fromJson(Map<String, dynamic>.from(json['data']))
          : null,
    );
  }
}

class RewardUsageDetailData {
  final int id;
  final int userId;
  final String? phone;
  final String type;
  final int itemId;
  final String itemName;
  final String? imageUrl;
  final double? goldGrams;
  final String? category;
  final double pointsSpent;
  final String status;
  final String? createdAt;
  final String? updatedAt;

  const RewardUsageDetailData({
    required this.id,
    required this.userId,
    this.phone,
    required this.type,
    required this.itemId,
    required this.itemName,
    this.imageUrl,
    this.goldGrams,
    this.category,
    required this.pointsSpent,
    required this.status,
    this.createdAt,
    this.updatedAt,
  });

  factory RewardUsageDetailData.fromJson(Map<String, dynamic> json) {
    return RewardUsageDetailData(
      id: json['id'] as int? ?? 0,
      userId: json['user_id'] as int? ?? 0,
      phone: json['phone'] as String?,
      type: json['type'] as String? ?? '',
      itemId: json['item_id'] as int? ?? 0,
      itemName: json['item_name'] as String? ?? '',
      imageUrl: json['image_url'] as String?,
      goldGrams: (json['gold_grams'] as num?)?.toDouble(),
      category: json['category'] as String?,
      pointsSpent: (json['points_spent'] as num?)?.toDouble() ?? 0.0,
      status: json['status'] as String? ?? '',
      createdAt: json['created_at'] as String?,
      updatedAt: json['updated_at'] as String?,
    );
  }
}
