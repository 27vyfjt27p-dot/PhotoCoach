package com.starqcy.photocoach

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val captureCode = 1001
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 64, 32, 24) }
        layout.addView(TextView(this).apply { text = "PhotoCoach V0.1 测试版\n悬浮窗 + 取景帧检测 + 倾斜提醒\n尚无 AI 人物识别"; textSize = 19f })
        layout.addView(Switch(this).apply {
            text = "中文语音提醒（默认关闭）"
            isChecked = prefs.getBoolean("voice", false)
            setOnCheckedChangeListener { _, enabled ->
                prefs.edit().putBoolean("voice", enabled).apply()
                sendBroadcast(Intent(CoachService.ACTION_SETTINGS).setPackage(packageName))
            }
        })
        layout.addView(Button(this).apply { text = "启动悬浮助手"; setOnClickListener { requestStart() } })
        layout.addView(Button(this).apply { text = "停止悬浮助手"; setOnClickListener { stopService(Intent(this@MainActivity, CoachService::class.java)) } })
        setContentView(layout)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
        }
    }
    private fun requestStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许显示在其他应用上层", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureCode)
    }
    @Deprecated("Legacy activity result used for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureCode && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, CoachService::class.java).apply {
                action = CoachService.ACTION_START
                putExtra(CoachService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CoachService.EXTRA_CAPTURE_DATA, data)
            }
            startForegroundService(intent)
        }
    }
}
