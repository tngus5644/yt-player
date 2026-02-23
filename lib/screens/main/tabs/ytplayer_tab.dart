import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/constants/app_colors.dart';
import '../../../core/channel/overlay_channel.dart';

/// 오버레이 서비스 상태 Provider
final overlayRunningProvider = StateProvider<bool>((ref) => false);

class YTPlayerTab extends ConsumerStatefulWidget {
  const YTPlayerTab({super.key});

  @override
  ConsumerState<YTPlayerTab> createState() => _YTPlayerTabState();
}

class _YTPlayerTabState extends ConsumerState<YTPlayerTab> {
  bool _hasPermission = false;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _checkStatus();
  }

  Future<void> _checkStatus() async {
    final permission = await OverlayChannel.hasOverlayPermission();
    final running = await OverlayChannel.isOverlayServiceRunning();
    if (mounted) {
      setState(() => _hasPermission = permission);
      ref.read(overlayRunningProvider.notifier).state = running;
    }
  }

  Future<void> _toggleService() async {
    final isRunning = ref.read(overlayRunningProvider);

    setState(() => _isLoading = true);

    if (isRunning) {
      await OverlayChannel.stopOverlayService();
      ref.read(overlayRunningProvider.notifier).state = false;
    } else {
      if (!_hasPermission) {
        await OverlayChannel.requestOverlayPermission();
        // 권한 요청 후 다시 체크
        await Future.delayed(const Duration(seconds: 1));
        await _checkStatus();
        setState(() => _isLoading = false);
        return;
      }

      final started = await OverlayChannel.startOverlayService();
      ref.read(overlayRunningProvider.notifier).state = started;
    }

    setState(() => _isLoading = false);
  }

  @override
  Widget build(BuildContext context) {
    final isRunning = ref.watch(overlayRunningProvider);
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'YTPlayer',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        centerTitle: true,
      ),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 상태 카드
            Card(
              elevation: 2,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(16),
              ),
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  children: [
                    Icon(
                      isRunning
                          ? Icons.cloud_done_rounded
                          : Icons.cloud_off_rounded,
                      size: 64,
                      color: isRunning
                          ? Colors.green
                          : colorScheme.onSurfaceVariant,
                    ),
                    const SizedBox(height: 16),
                    Text(
                      isRunning ? '백그라운드 서비스 실행 중' : '백그라운드 서비스 중지됨',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w600,
                        color: isRunning
                            ? Colors.green
                            : colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      isRunning
                          ? '오버레이 WebView가 더미 URL을 처리하고 있습니다'
                          : '서비스를 시작하면 더미 URL 테스트가 실행됩니다',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        color: colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),

            // 시작/중지 버튼
            SizedBox(
              height: 52,
              child: FilledButton.icon(
                onPressed: _isLoading ? null : _toggleService,
                icon: _isLoading
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : Icon(isRunning ? Icons.stop : Icons.play_arrow),
                label: Text(
                  _isLoading
                      ? '처리 중...'
                      : isRunning
                          ? '서비스 중지'
                          : '서비스 시작',
                  style: const TextStyle(
                      fontSize: 16, fontWeight: FontWeight.w600),
                ),
                style: FilledButton.styleFrom(
                  backgroundColor:
                      isRunning ? Colors.red.shade600 : AppColors.primary,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 12),

            // 권한 상태
            if (!_hasPermission)
              Card(
                color: Colors.orange.shade50,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    children: [
                      Icon(Icons.warning_amber_rounded,
                          color: Colors.orange.shade700),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          '오버레이 권한이 필요합니다.\n시작 버튼을 눌러 권한을 허용해주세요.',
                          style: TextStyle(
                            fontSize: 13,
                            color: Colors.orange.shade900,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),

            const SizedBox(height: 24),

            // 설명
            const Text(
              '동작 원리',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 12),
            _buildInfoRow(
              context,
              Icons.web,
              'WindowManager 오버레이',
              '보이지 않는 WebView를 오버레이로 생성',
            ),
            _buildInfoRow(
              context,
              Icons.link,
              '리다이렉트 체인 팔로잉',
              '더미 URL의 리다이렉트를 순차적으로 추적',
            ),
            _buildInfoRow(
              context,
              Icons.app_shortcut,
              '커스텀 스킴 감지',
              'testapp://, dummyshop:// 등 커스텀 스킴 감지',
            ),
            _buildInfoRow(
              context,
              Icons.screen_lock_portrait,
              '화면 상태 감지',
              '화면 꺼짐 시 일시 중지, 켜짐 시 재개',
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoRow(
      BuildContext context, IconData icon, String title, String desc) {
    final colorScheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 20, color: AppColors.primary),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                Text(
                  desc,
                  style: TextStyle(
                    fontSize: 12,
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
