import 'package:flutter/services.dart';

class PrivacyService {
  static const MethodChannel _channel = MethodChannel('privacy_guard_channel');

  static Future<void> startService() async {
    return _channel.invokeMethod('startService');
  }

  static Future<void> stopService() async {
    return _channel.invokeMethod('stopService');
  }

  static Future<bool> isRunning() async {
    final bool? running = await _channel.invokeMethod('isRunning');
    return running ?? false;
  }
}
