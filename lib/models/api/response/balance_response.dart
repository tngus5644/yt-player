class BalanceResponse {
  final bool success;
  final String? message;
  final double? balance;
  final double? totalEarned;
  final double? totalSpent;
  final BalanceData? data;

  const BalanceResponse({
    required this.success,
    this.message,
    this.balance,
    this.totalEarned,
    this.totalSpent,
    this.data,
  });

  factory BalanceResponse.fromJson(Map<String, dynamic> json) {
    return BalanceResponse(
      success: json['success'] as bool? ?? false,
      message: json['message'] as String?,
      balance: (json['balance'] as num?)?.toDouble(),
      totalEarned: (json['total_earned'] as num?)?.toDouble(),
      totalSpent: (json['total_spent'] as num?)?.toDouble(),
      data: json['data'] != null
          ? BalanceData.fromJson(Map<String, dynamic>.from(json['data']))
          : null,
    );
  }
}

class BalanceData {
  final double balance;
  final String? currency;
  final double totalEarned;
  final double totalSpent;

  const BalanceData({
    required this.balance,
    this.currency,
    this.totalEarned = 0.0,
    this.totalSpent = 0.0,
  });

  factory BalanceData.fromJson(Map<String, dynamic> json) {
    return BalanceData(
      balance: (json['balance'] as num?)?.toDouble() ?? 0.0,
      currency: json['currency'] as String?,
      totalEarned: (json['total_earned'] as num?)?.toDouble() ?? 0.0,
      totalSpent: (json['total_spent'] as num?)?.toDouble() ?? 0.0,
    );
  }
}
