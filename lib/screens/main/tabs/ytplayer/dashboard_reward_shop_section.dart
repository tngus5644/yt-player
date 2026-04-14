import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../models/api/response/rewards_response.dart';
import '../../../../providers/reward_provider.dart';
import '../../../../providers/webview_provider.dart';
import '../../../../widgets/login_gate.dart';

class DashboardRewardShopSection extends ConsumerWidget {
  const DashboardRewardShopSection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;
    final rewardsAsync = ref.watch(rewardListProvider);
    final balanceAsync = ref.watch(balanceProvider);
    final isLoggedIn = ref.watch(loginStateProvider);

    return rewardsAsync.when(
      data: (rewards) {
        if (rewards.isEmpty) return const SizedBox.shrink();
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.card_giftcard, size: 18, color: colorScheme.onSurface),
                const SizedBox(width: 6),
                Text(
                  '리워드 교환',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: colorScheme.onSurface,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            SizedBox(
              height: 180,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: rewards.length,
                separatorBuilder: (_, __) => const SizedBox(width: 12),
                itemBuilder: (context, index) => _RewardCard(
                  reward: rewards[index],
                  currentBalance: balanceAsync.valueOrNull?.balance ?? 0,
                  isLoggedIn: isLoggedIn,
                ),
              ),
            ),
          ],
        );
      },
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 16),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (_, __) => const SizedBox.shrink(),
    );
  }
}

class _RewardCard extends ConsumerWidget {
  final RewardItem reward;
  final double currentBalance;
  final bool isLoggedIn;

  const _RewardCard({
    required this.reward,
    required this.currentBalance,
    required this.isLoggedIn,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;
    final canAfford = currentBalance >= reward.pointsRequired;

    return SizedBox(
      width: 150,
      child: Card(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: reward.imageUrl != null
                  ? Image.network(
                      reward.imageUrl!,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => Container(
                        color: colorScheme.surfaceContainerHighest,
                        child: Icon(Icons.card_giftcard, size: 40, color: colorScheme.onSurfaceVariant),
                      ),
                    )
                  : Container(
                      color: colorScheme.surfaceContainerHighest,
                      child: Icon(Icons.card_giftcard, size: 40, color: colorScheme.onSurfaceVariant),
                    ),
            ),
            Padding(
              padding: const EdgeInsets.all(8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    reward.name,
                    style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '${reward.pointsRequired.toStringAsFixed(0)} P',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: canAfford ? AppColors.pointGreen : colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 6),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton(
                      onPressed: () => _onExchange(context, ref),
                      style: FilledButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 6),
                        textStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                        minimumSize: Size.zero,
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                      child: const Text('교환'),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _onExchange(BuildContext context, WidgetRef ref) async {
    if (!isLoggedIn) {
      await showLoginRequiredDialog(
        context,
        ref,
        content: '리워드를 교환하려면 로그인이 필요합니다.',
      );
      return;
    }

    if (currentBalance < reward.pointsRequired) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '포인트가 부족합니다. (필요: ${reward.pointsRequired.toStringAsFixed(0)} P / 보유: ${currentBalance.toStringAsFixed(0)} P)',
          ),
        ),
      );
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('리워드 교환'),
        content: Text(
          '${reward.name}을(를) ${reward.pointsRequired.toStringAsFixed(0)} P로 교환하시겠습니까?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('취소'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('교환'),
          ),
        ],
      ),
    );

    if (confirmed != true || !context.mounted) return;

    await ref.read(useRewardProvider.notifier).useReward(reward.id);

    if (!context.mounted) return;
    final result = ref.read(useRewardProvider);
    result.when(
      data: (data) {
        if (data != null) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('${reward.name} 교환이 완료되었습니다!')),
          );
          ref.read(balanceProvider.notifier).fetch();
          ref.read(rewardUsagesProvider.notifier).fetch();
        }
      },
      loading: () {},
      error: (e, _) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('교환 실패: $e')),
        );
      },
    );
  }
}
