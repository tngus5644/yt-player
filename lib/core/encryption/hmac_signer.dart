import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';
import 'package:pointycastle/digests/sha256.dart';
import 'package:pointycastle/macs/hmac.dart';
import 'package:pointycastle/pointycastle.dart';

class HmacSigner {
  static String? _secret;

  /// 앱 초기화 시 호출하여 비밀키 설정
  static void initialize(String secret) {
    _secret = secret;
  }

  static Map<String, String> sign(String body) {
    final secret = _secret;
    if (secret == null || secret.isEmpty) {
      throw StateError('HmacSigner가 초기화되지 않았습니다. initialize()를 먼저 호출하세요.');
    }

    final timestamp = (DateTime.now().millisecondsSinceEpoch ~/ 1000).toString();
    final nonce = _generateNonce();
    final payload = timestamp + nonce + body;

    final hmac = HMac(SHA256Digest(), 64)
      ..init(KeyParameter(utf8.encode(secret)));
    final payloadBytes = utf8.encode(payload);
    final result = hmac.process(Uint8List.fromList(payloadBytes));

    final signature = result.map((b) => b.toRadixString(16).padLeft(2, '0')).join();

    return {
      'signature': signature,
      'timestamp': timestamp,
      'nonce': nonce,
    };
  }

  static String _generateNonce() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    return bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  }
}
