class RewardChartResponse {
  final bool success;
  final RewardChartData? data;

  const RewardChartResponse({
    required this.success,
    this.data,
  });

  factory RewardChartResponse.fromJson(Map<String, dynamic> json) {
    return RewardChartResponse(
      success: json['success'] as bool? ?? false,
      data: json['data'] != null
          ? RewardChartData.fromJson(Map<String, dynamic>.from(json['data']))
          : null,
    );
  }
}

class RewardChartData {
  final List<ChartDataPoint> chart;
  final String startDate;
  final String endDate;
  final double currentBalance;
  final double? currentGoldPrice;

  const RewardChartData({
    required this.chart,
    required this.startDate,
    required this.endDate,
    required this.currentBalance,
    this.currentGoldPrice,
  });

  factory RewardChartData.fromJson(Map<String, dynamic> json) {
    return RewardChartData(
      chart: (json['chart'] as List<dynamic>?)
              ?.map((e) => ChartDataPoint.fromJson(Map<String, dynamic>.from(e)))
              .toList() ??
          [],
      startDate: json['start_date'] as String? ?? '',
      endDate: json['end_date'] as String? ?? '',
      currentBalance: (json['current_balance'] as num?)?.toDouble() ?? 0.0,
      currentGoldPrice: (json['current_gold_price'] as num?)?.toDouble(),
    );
  }
}

class ChartDataPoint {
  final String date;
  final double openBalance;
  final double closeBalance;
  final double goldEarned;
  final double goldPrice;
  final double openValue;
  final double closeValue;
  final double goldGrams;

  const ChartDataPoint({
    required this.date,
    required this.openBalance,
    required this.closeBalance,
    required this.goldEarned,
    required this.goldPrice,
    required this.openValue,
    required this.closeValue,
    required this.goldGrams,
  });

  factory ChartDataPoint.fromJson(Map<String, dynamic> json) {
    return ChartDataPoint(
      date: json['date'] as String? ?? '',
      openBalance: (json['open_balance'] as num?)?.toDouble() ?? 0.0,
      closeBalance: (json['close_balance'] as num?)?.toDouble() ?? 0.0,
      goldEarned: (json['gold_earned'] as num?)?.toDouble() ?? 0.0,
      goldPrice: (json['gold_price'] as num?)?.toDouble() ?? 0.0,
      openValue: (json['open_value'] as num?)?.toDouble() ?? 0.0,
      closeValue: (json['close_value'] as num?)?.toDouble() ?? 0.0,
      goldGrams: (json['gold_grams'] as num?)?.toDouble() ?? 0.0,
    );
  }
}
