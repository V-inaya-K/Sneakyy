package com.example.privacy_guard_flutter

import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "privacy_guard_channel"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MethodChannel(flutterEngine?.dartExecutor?.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startService" -> {
                    val intent = Intent(this, PrivacyService::class.java)
                    startForegroundService(intent)
                    result.success(null)
                }
                "stopService" -> {
                    val intent = Intent(this, PrivacyService::class.java)
                    stopService(intent)
                    result.success(null)
                }
                "isRunning" -> {
                    result.success(PrivacyService.isRunning)
                }
                else -> result.notImplemented()
            }
        }
    }
}
