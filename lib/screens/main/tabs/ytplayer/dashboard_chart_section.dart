import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../providers/reward_provider.dart';
import '../../../../widgets/reward_chart_widget.dart';

class DashboardChartSection extends ConsumerWidget {
  const DashboardChartSection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final chartAsync = ref.watch(rewardChartProvider);
    final colorScheme = Theme.of(context).colorScheme;

    return chartAsync.when(
      data: (chartData) {
        if (chartData == null || chartData.chart.isEmpty) {
          return const SizedBox.shrink();
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '포인트 추이 (30일)',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: colorScheme.onSurface,
              ),
            ),
            const SizedBox(height: 12),
            RewardChartWidget(data: chartData.chart),
            const SizedBox(height: 20),
          ],
        );
      },
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 24),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (_, __) => const SizedBox.shrink(),
    );
  }
}
