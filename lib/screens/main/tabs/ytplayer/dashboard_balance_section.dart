import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../models/api/response/balance_response.dart';
import '../../../../models/api/response/reward_chart_response.dart';
import '../../../../providers/reward_provider.dart';
import '../../../../widgets/balance_card.dart';

class DashboardBalanceSection extends ConsumerWidget {
  const DashboardBalanceSection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;
    final balanceAsync = ref.watch(balanceProvider);
    final chartAsync = ref.watch(rewardChartProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 대시보드 헤더
        Row(
          children: [
            Icon(Icons.bar_chart_rounded, size: 20, color: colorScheme.onSurface),
            const SizedBox(width: 8),
            Text(
              '대시보드',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: colorScheme.onSurface,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),

        // Row: 현재 적립 포인트 | 사용한 포인트
        _buildBalanceRow(balanceAsync),
        const SizedBox(height: 12),

        // Row: 총 적립 포인트 | 현재 금시세(g)
        Row(
          children: [
            Expanded(
              child: BalanceCard(
                title: '총 적립 포인트',
                points: balanceAsync.valueOrNull?.totalEarned ?? 0,
                icon: Icons.trending_up,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _buildGoldPriceCard(chartAsync, colorScheme),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildBalanceRow(AsyncValue<BalanceData> balanceAsync) {
    return balanceAsync.when(
      data: (data) => Row(
        children: [
          Expanded(
            child: BalanceCard(
              title: '현재 적립 포인트',
              points: data.balance,
              icon: Icons.savings_outlined,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: BalanceCard(
              title: '사용한 포인트',
              points: data.totalSpent,
              icon: Icons.shopping_cart_outlined,
            ),
          ),
        ],
      ),
      loading: () => const Row(
        children: [
          Expanded(child: BalanceCard(title: '현재 적립 포인트', points: 0, icon: Icons.savings_outlined)),
          SizedBox(width: 12),
          Expanded(child: BalanceCard(title: '사용한 포인트', points: 0, icon: Icons.shopping_cart_outlined)),
        ],
      ),
      error: (_, __) => const Row(
        children: [
          Expanded(child: BalanceCard(title: '현재 적립 포인트', points: 0, icon: Icons.savings_outlined)),
          SizedBox(width: 12),
          Expanded(child: BalanceCard(title: '사용한 포인트', points: 0, icon: Icons.shopping_cart_outlined)),
        ],
      ),
    );
  }

  Widget _buildGoldPriceCard(
    AsyncValue<RewardChartData?> chartAsync,
    ColorScheme colorScheme,
  ) {
    final goldPrice = chartAsync.whenOrNull(data: (data) => data?.currentGoldPrice);
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      color: colorScheme.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.diamond_outlined, size: 18, color: colorScheme.onSurfaceVariant),
                const SizedBox(width: 6),
                Text(
                  '현재 금시세 (g)',
                  style: TextStyle(fontSize: 13, color: colorScheme.onSurfaceVariant),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text(
                  goldPrice != null ? goldPrice.toStringAsFixed(0) : '-',
                  style: const TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.bold,
                    color: AppColors.gold,
                  ),
                ),
                const SizedBox(width: 4),
                Text(
                  goldPrice != null ? '원' : '',
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: AppColors.gold,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
