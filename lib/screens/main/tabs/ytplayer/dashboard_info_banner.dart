import 'package:flutter/material.dart';
import '../../../../core/constants/app_colors.dart';

class DashboardInfoBanner extends StatelessWidget {
  const DashboardInfoBanner({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          const Text('\u{1F4E2}', style: TextStyle(fontSize: 18)),
          const SizedBox(width: 10),
          Expanded(
            child: Text.rich(
              TextSpan(
                children: [
                  const TextSpan(text: 'YT플레이어로 시청하시면 '),
                  TextSpan(
                    text: 'YT포인트',
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      color: AppColors.primary,
                    ),
                  ),
                  const TextSpan(text: '가 쌓입니다.\n영상을 보면서 포인트를 적립하세요!'),
                ],
              ),
              style: TextStyle(
                fontSize: 13,
                color: colorScheme.onSurface,
                height: 1.4,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
