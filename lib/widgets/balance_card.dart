import 'package:flutter/material.dart';
import '../core/constants/app_colors.dart';

class BalanceCard extends StatelessWidget {
  final String title;
  final double points;
  final IconData icon;

  const BalanceCard({
    super.key,
    required this.title,
    required this.points,
    this.icon = Icons.monetization_on_outlined,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
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
                Icon(icon, size: 18, color: colorScheme.onSurfaceVariant),
                const SizedBox(width: 6),
                Text(
                  title,
                  style: TextStyle(
                    fontSize: 13,
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text(
                  points.toStringAsFixed(points.truncateToDouble() == points ? 0 : 2),
                  style: const TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.bold,
                    color: AppColors.pointGreen,
                  ),
                ),
                const SizedBox(width: 4),
                const Text(
                  'P',
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: AppColors.pointGreen,
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
