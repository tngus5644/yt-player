import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../providers/reward_provider.dart';

class DashboardNoticeSection extends ConsumerWidget {
  const DashboardNoticeSection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final noticeAsync = ref.watch(noticeProvider);
    final colorScheme = Theme.of(context).colorScheme;

    return noticeAsync.when(
      data: (notices) {
        if (notices.isEmpty) return const SizedBox.shrink();
        final displayNotices = notices.take(3).toList();
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.campaign_outlined, size: 18, color: colorScheme.onSurface),
                const SizedBox(width: 6),
                Text(
                  '공지사항',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: colorScheme.onSurface,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Card(
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              clipBehavior: Clip.antiAlias,
              child: Column(
                children: displayNotices.map((notice) {
                  return ExpansionTile(
                    tilePadding: const EdgeInsets.symmetric(horizontal: 16),
                    title: Text(
                      notice.title,
                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    subtitle: notice.createdAt != null
                        ? Text(
                            notice.createdAt!.substring(0, 10),
                            style: TextStyle(fontSize: 11, color: colorScheme.onSurfaceVariant),
                          )
                        : null,
                    children: [
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                        child: Align(
                          alignment: Alignment.centerLeft,
                          child: Text(
                            notice.content,
                            style: TextStyle(fontSize: 13, color: colorScheme.onSurface, height: 1.5),
                          ),
                        ),
                      ),
                    ],
                  );
                }).toList(),
              ),
            ),
          ],
        );
      },
      loading: () => const SizedBox.shrink(),
      error: (_, __) => const SizedBox.shrink(),
    );
  }
}
