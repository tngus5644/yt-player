import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../providers/reward_provider.dart';
import '../../../../widgets/point_history_table.dart';

class DashboardEarningHistorySection extends ConsumerWidget {
  const DashboardEarningHistorySection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final chartAsync = ref.watch(rewardChartProvider);
    final colorScheme = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(Icons.receipt_long, size: 18, color: colorScheme.onSurface),
            const SizedBox(width: 6),
            Text(
              '포인트 적립 내역',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: colorScheme.onSurface,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        chartAsync.when(
          data: (chartData) => PointHistoryTable(
            data: chartData?.chart.where((p) => p.goldEarned != 0).toList() ?? [],
          ),
          loading: () => const Padding(
            padding: EdgeInsets.symmetric(vertical: 24),
            child: Center(child: CircularProgressIndicator()),
          ),
          error: (_, __) => const PointHistoryTable(data: []),
        ),
      ],
    );
  }
}
