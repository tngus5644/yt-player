import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/app_constants.dart';

class IntroScreen extends StatefulWidget {
  const IntroScreen({super.key});

  @override
  State<IntroScreen> createState() => _IntroScreenState();
}

class _IntroScreenState extends State<IntroScreen> {
  static const _pageCount = 4;
  static const _permissionPageIndex = 3;

  final _controller = PageController();
  int _currentPage = 0;
  bool _permissionsGranted = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _complete() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(AppConstants.prefShouldShowIntro, false);
    if (mounted) context.go('/main');
  }

  bool get _canProceed {
    if (_currentPage == _permissionPageIndex) return _permissionsGranted;
    return true;
  }

  String get _buttonLabel {
    if (_currentPage == _permissionPageIndex) return '시작하기';
    return '다음';
  }

  VoidCallback? get _buttonAction {
    if (_currentPage == _permissionPageIndex) {
      return _permissionsGranted ? _complete : null;
    }
    return () => _controller.nextPage(
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeInOut,
        );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: PageView(
                controller: _controller,
                onPageChanged: (i) => setState(() => _currentPage = i),
                children: [
                  const _IntroPage(
                    icon: Icons.play_circle_outline,
                    title: '유튜브를 즐기세요',
                    description: '좋아하는 영상을 자유롭게 시청하세요',
                  ),
                  const _IntroPage(
                    icon: Icons.monetization_on_outlined,
                    title: '포인트를 쌓으세요',
                    description: '영상을 보면 자동으로 포인트가 적립됩니다',
                  ),
                  const _IntroPage(
                    icon: Icons.card_giftcard,
                    title: '보상을 받으세요',
                    description: '쌓은 포인트로 다양한 혜택을 누리세요',
                  ),
                  _PermissionPage(
                    onGrantStatusChanged: (granted) {
                      if (_permissionsGranted != granted) {
                        setState(() => _permissionsGranted = granted);
                      }
                    },
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(
                  _pageCount,
                  (i) => Container(
                    width: i == _currentPage ? 24 : 8,
                    height: 8,
                    margin: const EdgeInsets.symmetric(horizontal: 4),
                    decoration: BoxDecoration(
                      color: i == _currentPage
                          ? AppColors.primary
                          : Theme.of(context).dividerColor,
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 0, 24, 32),
              child: SizedBox(
                width: double.infinity,
                height: 52,
                child: FilledButton(
                  onPressed: _canProceed ? _buttonAction : null,
                  style: FilledButton.styleFrom(
                    backgroundColor: AppColors.primary,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  child: Text(
                    _buttonLabel,
                    style: const TextStyle(
                        fontSize: 16, fontWeight: FontWeight.w600),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _IntroPage extends StatelessWidget {
  final IconData icon;
  final String title;
  final String description;

  const _IntroPage({
    required this.icon,
    required this.title,
    required this.description,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Padding(
      padding: const EdgeInsets.all(40),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 120, color: AppColors.primary),
          const SizedBox(height: 40),
          Text(
            title,
            style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 16),
          Text(
            description,
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 16, color: colorScheme.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}

class _PermissionItem {
  final Permission permission;
  final IconData icon;
  final String title;
  final String description;

  const _PermissionItem({
    required this.permission,
    required this.icon,
    required this.title,
    required this.description,
  });
}

class _PermissionPage extends StatefulWidget {
  final ValueChanged<bool> onGrantStatusChanged;

  const _PermissionPage({required this.onGrantStatusChanged});

  @override
  State<_PermissionPage> createState() => _PermissionPageState();
}

class _PermissionPageState extends State<_PermissionPage>
    with WidgetsBindingObserver {
  static const _items = <_PermissionItem>[
    _PermissionItem(
      permission: Permission.systemAlertWindow,
      icon: Icons.picture_in_picture_alt_outlined,
      title: '다른 앱 위에 표시',
      description: 'PIP 모드(화면 속 화면)로 다른 앱을 쓰면서 영상을 계속 보기 위해 필요합니다',
    ),
    _PermissionItem(
      permission: Permission.ignoreBatteryOptimizations,
      icon: Icons.battery_charging_full_outlined,
      title: '배터리 최적화 제외',
      description: '화면을 꺼도 백그라운드 재생이 끊기지 않도록 합니다',
    ),
  ];

  final Map<Permission, bool> _granted = {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshAll();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshAll();
    }
  }

  Future<void> _refreshAll() async {
    for (final item in _items) {
      _granted[item.permission] = await item.permission.isGranted;
    }
    if (!mounted) return;
    setState(() {});
    widget.onGrantStatusChanged(_granted.values.every((v) => v));
  }

  Future<void> _request(_PermissionItem item) async {
    final status = await item.permission.request();
    if (!mounted) return;
    setState(() {
      _granted[item.permission] = status.isGranted;
    });
    widget.onGrantStatusChanged(_granted.values.every((v) => v == true));

    if (status.isPermanentlyDenied) {
      await openAppSettings();
    }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 40, 24, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text(
            '권한 허용',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 12),
          Text(
            '앱이 정상 동작하려면 아래 권한이 모두 필요합니다.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 14, color: colorScheme.onSurfaceVariant),
          ),
          const SizedBox(height: 24),
          Expanded(
            child: ListView.separated(
              itemCount: _items.length,
              separatorBuilder: (_, __) => const SizedBox(height: 12),
              itemBuilder: (context, index) {
                final item = _items[index];
                final granted = _granted[item.permission] ?? false;
                return _PermissionTile(
                  item: item,
                  granted: granted,
                  onTap: granted ? null : () => _request(item),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _PermissionTile extends StatelessWidget {
  final _PermissionItem item;
  final bool granted;
  final VoidCallback? onTap;

  const _PermissionTile({
    required this.item,
    required this.granted,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Material(
      color: colorScheme.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Icon(item.icon, size: 28, color: AppColors.primary),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item.title,
                      style: const TextStyle(
                          fontSize: 16, fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      item.description,
                      style: TextStyle(
                        fontSize: 13,
                        color: colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              if (granted)
                const Icon(Icons.check_circle, color: Colors.green, size: 28)
              else
                Icon(
                  Icons.chevron_right,
                  color: colorScheme.onSurfaceVariant,
                  size: 28,
                ),
            ],
          ),
        ),
      ),
    );
  }
}
