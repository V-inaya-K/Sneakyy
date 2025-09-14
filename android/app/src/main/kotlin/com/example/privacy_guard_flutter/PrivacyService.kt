package com.example.privacy_guard_flutter

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import android.hardware.camera2.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class PrivacyService : Service() {

    companion object {
        private const val TAG = "PrivacyService"
        const val CHANNEL_ID = "privacy_guard_channel"
        var isRunning: Boolean = false
    }

    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var cameraId: String? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var detectionScheduled: ScheduledExecutorService? = null
    private var lastProcessTime = 0L
    private var pauseDetection = false
    private var ownCameraOpen = false

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }

    private val availabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(id: String) {
            super.onCameraUnavailable(id)
            // If camera becomes unavailable and we DON'T own it, another app opened camera -> pause detection
            if (!ownCameraOpen) {
                pauseDetection = true
                Log.d(TAG, "Camera unavailable (other app). Pausing detection.")
            }
        }

        override fun onCameraAvailable(id: String) {
            super.onCameraAvailable(id)
            // If camera available again, resume detection
            pauseDetection = false
            Log.d(TAG, "Camera available. Resuming detection.")
        }
    }

    override fun onCreate() {
        super.onCreate()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraManager.registerAvailabilityCallback(availabilityCallback, Handler(Looper.getMainLooper()))
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification("Privacy Guard active"))
        isRunning = true
        openFrontCamera()
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        closeCamera()
        detector.close()
        detectionScheduled?.shutdownNow()
        cameraManager.unregisterAvailabilityCallback(availabilityCallback)
        isRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "Privacy Guard", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Privacy Guard")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun openFrontCamera() {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val lens = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (lens != null && lens == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id
                    break
                }
            }
            cameraId?.let { id ->
                imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2)
                imageReader?.setOnImageAvailableListener({ reader ->
                    // process latest image on separate thread (throttled)
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    handleImage(image)
                    image.close()
                }, Handler(Looper.getMainLooper()))

                cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        cameraDevice = device
                        ownCameraOpen = true
                        startCaptureSession()
                    }

                    override fun onDisconnected(device: CameraDevice) {
                        device.close()
                        cameraDevice = null
                        ownCameraOpen = false
                    }

                    override fun onError(device: CameraDevice, error: Int) {
                        device.close()
                        cameraDevice = null
                        ownCameraOpen = false
                        Log.e(TAG, "Camera error: $error")
                    }
                }, Handler(Looper.getMainLooper()))

                // schedule periodic resume if needed
                detectionScheduled = Executors.newSingleThreadScheduledExecutor()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission missing: $e")
        } catch (e: Exception) {
            Log.e(TAG, "openFrontCamera error: $e")
        }
    }

    private fun startCaptureSession() {
        try {
            cameraDevice?.let { device ->
                val surface = imageReader!!.surface
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder.addTarget(surface)
                device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        this@PrivacyService.session = session
                        val req = builder.build()
                        session.setRepeatingRequest(req, null, Handler(Looper.getMainLooper()))
                        // start throttled detection loop if needed - ML processing occurs from ImageReader callback
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configure failed")
                    }
                }, Handler(Looper.getMainLooper()))
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "startCaptureSession error: $e")
        }
    }

    private fun handleImage(image: Image) {
        // throttle processing to about once per 800ms to save CPU/battery
        val now = System.currentTimeMillis()
        if (now - lastProcessTime < 800) return
        lastProcessTime = now

        if (pauseDetection) return // another app using camera; skip

        // convert to NV21 bytes
        val nv21 = CameraYuvHelper.yuv420ToNv21(image)
        val rotation = 0 // front camera rotation handling can be device specific; using 0 may work for many
        try {
            val inputImage = InputImage.fromByteArray(nv21, image.width, image.height, rotation, InputImage.IMAGE_FORMAT_NV21)
            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    onFacesDetected(faces)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Face detection failed: $e")
                }
        } catch (e: Exception) {
            Log.e(TAG, "handleImage exception: $e")
        }
    }

    private fun onFacesDetected(faces: List<Face>) {
        Log.d(TAG, "Faces detected: ${faces.size}")
        // If more than 1 face -> alert
        if (faces.size > 1) {
            sendPeekNotification(faces.size)
        }
    }

    private fun sendPeekNotification(count: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Privacy Alert")
            .setContentText("Someone is looking — $count faces detected")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(1002, notif)

        // Optionally vibrate
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(300)
        }
    }

    private fun closeCamera() {
        try {
            session?.close()
            session = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            ownCameraOpen = false
        } catch (e: Exception) {
            Log.e(TAG, "closeCamera error: $e")
        }
    }
}
