import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

class CoupangAdSection extends StatefulWidget {
  const CoupangAdSection({super.key});

  @override
  State<CoupangAdSection> createState() => _CoupangAdSectionState();
}

class _CoupangAdSectionState extends State<CoupangAdSection> {
  late final WebViewController _controller;

  static const _html = '''
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    body { margin: 0; padding: 0; display: flex; justify-content: center; align-items: center; }
  </style>
</head>
<body>
  <iframe src="https://ads-partners.coupang.com/widgets.html?id=981030&template=carousel&trackingCode=AF7584878&subId=ytmultiple&width=320&height=320&tsource=" width="320" height="320" frameborder="0" scrolling="no" referrerpolicy="unsafe-url"></iframe>
</body>
</html>
''';

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..loadHtmlString(_html);
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 340,
      child: WebViewWidget(controller: _controller),
    );
  }
}
