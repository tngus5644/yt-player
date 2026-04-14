import 'package:flutter/material.dart';
import '../models/api/response/reward_chart_response.dart';

class PointHistoryTable extends StatelessWidget {
  final List<ChartDataPoint> data;

  const PointHistoryTable({super.key, required this.data});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 헤더
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          decoration: BoxDecoration(
            color: colorScheme.surfaceContainerHighest,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(8)),
          ),
          child: Row(
            children: [
              Expanded(
                flex: 3,
                child: Text(
                  '날짜',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
              Expanded(
                flex: 4,
                child: Text(
                  '내용',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
              Expanded(
                flex: 2,
                child: Text(
                  '포인트',
                  textAlign: TextAlign.right,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            ],
          ),
        ),
        const Divider(height: 1),
        // 데이터 행
        if (data.isEmpty)
          Padding(
            padding: const EdgeInsets.all(24),
            child: Center(
              child: Text(
                '적립/사용 내역이 없습니다',
                style: TextStyle(
                  fontSize: 13,
                  color: colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          )
        else
          ...data.map((point) => _buildRow(context, point)),
      ],
    );
  }

  Widget _buildRow(BuildContext context, ChartDataPoint point) {
    final colorScheme = Theme.of(context).colorScheme;
    final earned = point.goldEarned;
    final isPositive = earned >= 0;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(
        border: Border(
          bottom: BorderSide(color: colorScheme.outlineVariant, width: 0.5),
        ),
      ),
      child: Row(
        children: [
          Expanded(
            flex: 3,
            child: Text(
              point.date,
              style: TextStyle(fontSize: 12, color: colorScheme.onSurface),
            ),
          ),
          Expanded(
            flex: 4,
            child: Text(
              isPositive ? '영상 시청 적립' : '포인트 사용',
              style: TextStyle(fontSize: 12, color: colorScheme.onSurface),
            ),
          ),
          Expanded(
            flex: 2,
            child: Text(
              '${isPositive ? '+' : ''}${earned.toStringAsFixed(earned.truncateToDouble() == earned ? 0 : 2)}',
              textAlign: TextAlign.right,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: isPositive ? Colors.green : Colors.red,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
