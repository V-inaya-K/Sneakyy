import 'package:flutter/material.dart';
import 'package:sneaky/pages/privacy.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});
  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  bool _running = false;

  @override
  void initState() {
    super.initState();
    PrivacyService.isRunning().then((r) {
      setState(() => _running = r);
    });
  }

  Future<void> _ensurePermissions() async {
    final statuses = await [
      Permission.camera,
      Permission.notification,
    ].request();
    if (statuses[Permission.camera] != PermissionStatus.granted) {
      throw Exception('Camera permission required');
    }
  }

  Future<void> _start() async {
    try {
      await _ensurePermissions();
      await PrivacyService.startService();
      setState(() => _running = true);
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Permission error: $e')),
      );
    }
  }

  Future<void> _stop() async {
    await PrivacyService.stopService();
    setState(() => _running = false);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Privacy Guard',
      home: Scaffold(
        appBar: AppBar(title: const Text('Privacy Guard')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                _running ? 'Protection: ENABLED' : 'Protection: DISABLED',
                style: const TextStyle(fontSize: 20),
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _running ? _stop : _start,
                child: Text(_running ? 'Stop Protection' : 'Start Protection'),
              ),
              const SizedBox(height: 16),
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 24.0),
                child: Text(
                  'Note: App runs a foreground service with camera access. '
                      'Detection is paused automatically when other apps open the camera (so taking a photo won’t trigger alerts).',
                  textAlign: TextAlign.center,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
