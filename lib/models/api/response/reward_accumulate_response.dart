class RewardAccumulateResponse {
  final bool success;
  final String? message;
  final RewardAccumulateData? data;

  const RewardAccumulateResponse({
    required this.success,
    this.message,
    this.data,
  });

  factory RewardAccumulateResponse.fromJson(Map<String, dynamic> json) {
    return RewardAccumulateResponse(
      success: json['success'] as bool? ?? false,
      message: json['message'] as String?,
      data: json['data'] != null
          ? RewardAccumulateData.fromJson(Map<String, dynamic>.from(json['data']))
          : null,
    );
  }
}

class RewardAccumulateData {
  final double? points;
  final double? totalPoints;

  const RewardAccumulateData({
    this.points,
    this.totalPoints,
  });

  factory RewardAccumulateData.fromJson(Map<String, dynamic> json) {
    return RewardAccumulateData(
      points: (json['points'] as num?)?.toDouble(),
      totalPoints: (json['total_points'] as num?)?.toDouble(),
    );
  }
}
