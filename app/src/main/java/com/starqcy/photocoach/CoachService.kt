package com.starqcy.photocoach

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.*
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2

class CoachService : Service(), SensorEventListener {
    companion object {
        const val ACTION_START = "com.starqcy.photocoach.START"
        const val ACTION_SETTINGS = "com.starqcy.photocoach.SETTINGS"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_CAPTURE_DATA = "capture_data"
    }
    private lateinit var wm: WindowManager
    private var overlay: TextView? = null
    private var sensorManager: SensorManager? = null
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private var tts: TextToSpeech? = null
    private var voiceReady = false
    private var lastSpeak = 0L
    private var frameCount = 0L
    private var angle = 0.0
    private var stopped = false
    private val handler = Handler(Looper.getMainLooper())
    private val update = object : Runnable {
        override fun run() {
            overlay?.text = "PhotoCoach V0.1\n已接收画面帧：$frameCount\n手机倾斜参考：${"%.0f".format(angle)}°\n${if (abs(angle) > 8) "尝试调整手机水平" else "手机基本水平"}\n注意：此角度不是画面地平线识别"
            if (abs(angle) > 12 && SystemClock.elapsedRealtime() - lastSpeak > 15000 && voiceReady && getSharedPreferences("settings", MODE_PRIVATE).getBoolean("voice", false)) {
                tts?.speak("可以调整一下手机水平", TextToSpeech.QUEUE_FLUSH, null, "tilt")
                lastSpeak = SystemClock.elapsedRealtime()
            }
            handler.postDelayed(this, 1200)
        }
    }
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { handler.post { stopSelf() } }
    }
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { /* voice state read from preferences */ }
    }
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        registerReceiver(settingsReceiver, IntentFilter(ACTION_SETTINGS), RECEIVER_NOT_EXPORTED)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) voiceReady = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE) != TextToSpeech.LANG_MISSING_DATA
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START || projection != null) return START_NOT_STICKY
        createNotification()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        createOverlay()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        handler.post(update)
        val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_CAPTURE_DATA, Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(EXTRA_CAPTURE_DATA)
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED), data)
            projection?.registerCallback(projectionCallback, handler)
            val metrics = resources.displayMetrics
            reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
            reader?.setOnImageAvailableListener({ source ->
                source.acquireLatestImage()?.use { frameCount++ }
            }, handler)
            display = projection?.createVirtualDisplay("PhotoCoachCapture", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, handler)
        } catch (e: Exception) {
            overlay?.text = "屏幕捕获失败：${e.javaClass.simpleName}"
            handler.postDelayed({ stopSelf() }, 4000)
        }
        return START_NOT_STICKY
    }
    private fun createNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("capture", "PhotoCoach 屏幕捕获", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "capture").setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("PhotoCoach 正在运行").setContentText("正在检测取景画面帧数").setContentIntent(pending).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, notification)
    }
    private fun createOverlay() {
        overlay = TextView(this).apply {
            text = "PhotoCoach 启动中…"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xB8000000.toInt())
            setPadding(20, 16, 20, 16)
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 120 }
        wm.addView(overlay, params)
    }
    override fun onSensorChanged(event: SensorEvent) {
        // Rough device roll in portrait only; not scene horizon analysis.
        angle = Math.toDegrees(atan2(event.values[0].toDouble(), -event.values[1].toDouble()))
        if (angle > 90) angle -= 180.0
        if (angle < -90) angle += 180.0
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        handler.removeCallbacks(update)
        sensorManager?.unregisterListener(this)
        display?.release(); display = null
        reader?.close(); reader = null
        val p = projection; projection = null
        p?.unregisterCallback(projectionCallback)
        p?.stop()
        overlay?.let { runCatching { wm.removeView(it) } }; overlay = null
        runCatching { unregisterReceiver(settingsReceiver) }
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }
}
