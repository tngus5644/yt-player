class UseRewardResponse {
  final bool success;
  final String? message;
  final UseRewardData? data;

  const UseRewardResponse({
    required this.success,
    this.message,
    this.data,
  });

  factory UseRewardResponse.fromJson(Map<String, dynamic> json) {
    return UseRewardResponse(
      success: json['success'] as bool? ?? false,
      message: json['message'] as String?,
      data: json['data'] != null
          ? UseRewardData.fromJson(Map<String, dynamic>.from(json['data']))
          : null,
    );
  }
}

class UseRewardData {
  final int id;
  final int rewardId;
  final String rewardName;
  final double pointsSpent;
  final String status;
  final double balance;
  final double totalSpent;
  final String? createdAt;

  const UseRewardData({
    required this.id,
    required this.rewardId,
    required this.rewardName,
    required this.pointsSpent,
    required this.status,
    required this.balance,
    required this.totalSpent,
    this.createdAt,
  });

  factory UseRewardData.fromJson(Map<String, dynamic> json) {
    return UseRewardData(
      id: json['id'] as int? ?? 0,
      rewardId: json['reward_id'] as int? ?? 0,
      rewardName: json['reward_name'] as String? ?? '',
      pointsSpent: (json['points_spent'] as num?)?.toDouble() ?? 0.0,
      status: json['status'] as String? ?? '',
      balance: (json['balance'] as num?)?.toDouble() ?? 0.0,
      totalSpent: (json['total_spent'] as num?)?.toDouble() ?? 0.0,
      createdAt: json['created_at'] as String?,
    );
  }
}
