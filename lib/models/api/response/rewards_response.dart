class RewardsResponse {
  final bool success;
  final String? message;
  final List<RewardItem>? data;

  const RewardsResponse({
    required this.success,
    this.message,
    this.data,
  });

  factory RewardsResponse.fromJson(Map<String, dynamic> json) {
    return RewardsResponse(
      success: json['success'] as bool? ?? false,
      message: json['message'] as String?,
      data: (json['data'] as List<dynamic>?)
          ?.map((e) => RewardItem.fromJson(Map<String, dynamic>.from(e)))
          .toList(),
    );
  }
}

class RewardItem {
  final int id;
  final int applicationId;
  final String name;
  final String? description;
  final double pointsRequired;
  final String? duration;
  final String? imageUrl;
  final String? expiresAt;
  final Map<String, dynamic>? goldInfo;
  final Map<String, dynamic>? goldPriceInfo;

  const RewardItem({
    required this.id,
    required this.applicationId,
    required this.name,
    this.description,
    required this.pointsRequired,
    this.duration,
    this.imageUrl,
    this.expiresAt,
    this.goldInfo,
    this.goldPriceInfo,
  });

  factory RewardItem.fromJson(Map<String, dynamic> json) {
    return RewardItem(
      id: json['id'] as int? ?? 0,
      applicationId: json['application_id'] as int? ?? 0,
      name: json['name'] as String? ?? '',
      description: json['description'] as String?,
      pointsRequired: double.tryParse(json['points_required']?.toString() ?? '0') ?? 0.0,
      duration: json['duration'] as String?,
      imageUrl: json['image_url'] as String?,
      expiresAt: json['expires_at'] as String?,
      goldInfo: json['gold_info'] != null
          ? Map<String, dynamic>.from(json['gold_info'])
          : null,
      goldPriceInfo: json['gold_price_info'] != null
          ? Map<String, dynamic>.from(json['gold_price_info'])
          : null,
    );
  }
}
