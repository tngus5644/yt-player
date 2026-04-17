import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../models/api/response/notice_response.dart';
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
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Text('\u{1F4E2}', style: TextStyle(fontSize: 18)),
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
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12)),
              clipBehavior: Clip.antiAlias,
              color: colorScheme.surfaceContainerHighest,
              child: _NoticeAccordion(notices: notices),
            ),
          ],
        );
      },
      loading: () => const SizedBox.shrink(),
      error: (_, __) => const SizedBox.shrink(),
    );
  }
}

class _NoticeAccordion extends StatefulWidget {
  final List<NoticeItem> notices;
  const _NoticeAccordion({required this.notices});

  @override
  State<_NoticeAccordion> createState() => _NoticeAccordionState();
}

class _NoticeAccordionState extends State<_NoticeAccordion> {
  int? _expandedIndex;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Column(
      children: List.generate(widget.notices.length, (i) {
        final notice = widget.notices[i];
        final isExpanded = _expandedIndex == i;
        final isLast = i == widget.notices.length - 1;

        return Column(
          children: [
            InkWell(
              onTap: () =>
                  setState(() => _expandedIndex = isExpanded ? null : i),
              child: Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            notice.title,
                            style: TextStyle(
                              fontSize: 13,
                              fontWeight: isExpanded
                                  ? FontWeight.w600
                                  : FontWeight.w500,
                              color: colorScheme.onSurface,
                            ),
                            maxLines: isExpanded ? null : 1,
                            overflow: isExpanded
                                ? TextOverflow.visible
                                : TextOverflow.ellipsis,
                          ),
                          if (notice.createdAt != null) ...[
                            const SizedBox(height: 2),
                            Text(
                              notice.createdAt!.length >= 10
                                  ? notice.createdAt!.substring(0, 10)
                                  : notice.createdAt!,
                              style: TextStyle(
                                fontSize: 11,
                                color: colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                    AnimatedRotation(
                      turns: isExpanded ? 0.5 : 0,
                      duration: const Duration(milliseconds: 200),
                      child: Icon(
                        Icons.expand_more,
                        size: 20,
                        color: colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            AnimatedCrossFade(
              firstChild: const SizedBox.shrink(),
              secondChild: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: Text(
                    notice.content,
                    style: TextStyle(
                      fontSize: 13,
                      color: colorScheme.onSurface,
                      height: 1.6,
                    ),
                  ),
                ),
              ),
              crossFadeState: isExpanded
                  ? CrossFadeState.showSecond
                  : CrossFadeState.showFirst,
              duration: const Duration(milliseconds: 200),
            ),
            if (!isLast)
              Divider(
                height: 1,
                thickness: 0.5,
                indent: 16,
                endIndent: 16,
                color: colorScheme.outlineVariant,
              ),
          ],
        );
      }),
    );
  }
}
