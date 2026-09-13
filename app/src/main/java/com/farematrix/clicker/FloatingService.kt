package com.farematrix.clicker

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.edit

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private val targetViews = mutableMapOf<String, View>()
    val targetPositions = mutableMapOf<String, Pair<Float, Float>>()

    val moneyKeys = listOf("50", "20", "10", "5", "1")
    val allKeys = moneyKeys + "CHECK"

    companion object {
        var isRunning = false
        var instance: FloatingService? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this
        prefs = getSharedPreferences("target_positions", MODE_PRIVATE)
        startForeground(1, buildNotification())
        setupOverlays()
    }

    private fun buildNotification(): Notification {
        val channelId = "floating_clicker"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Clicker", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Auto Sukli Clicker")
            .setContentText("Targets active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    private fun setupOverlays() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val defaults = mapOf(
            "50"    to Pair(150f, 400f),
            "20"    to Pair(280f, 500f),
            "10"    to Pair(410f, 400f),
            "5"     to Pair(280f, 650f),
            "1"     to Pair(410f, 650f),
            "CHECK" to Pair(150f, 800f)
        )

        for (key in allKeys) {
            val savedX = prefs.getFloat("x_$key", defaults[key]!!.first)
            val savedY = prefs.getFloat("y_$key", defaults[key]!!.second)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = savedX.toInt()
                y = savedY.toInt()
            }

            val view = LayoutInflater.from(this).inflate(R.layout.floating_target, null)
            val label = view.findViewById<TextView>(R.id.targetLabel)
            label.text = if (key == "CHECK") "✓" else key

            val circle = view.findViewById<View>(R.id.circleBody)
            if (key == "CHECK") circle.setBackgroundResource(R.drawable.target_bg_check)

            circle.setOnTouchListener(object : View.OnTouchListener {
                private var startX = 0
                private var startY = 0
                private var touchX = 0f
                private var touchY = 0f

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = params.x; startY = params.y
                            touchX = event.rawX; touchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = startX + (event.rawX - touchX).toInt()
                            params.y = startY + (event.rawY - touchY).toInt()
                            windowManager.updateViewLayout(view, params)
                            savePosition(key, params.x, params.y, view)
                            return true
                        }
                    }
                    return false
                }
            })

            view.findViewById<View>(R.id.closeBtn).setOnClickListener {
                windowManager.removeView(view)
                targetViews.remove(key)
                targetPositions.remove(key)
                prefs.edit { putFloat("x_$key", -9999f); putFloat("y_$key", -9999f) }
            }

            windowManager.addView(view, params)
            targetViews[key] = view
            view.post { savePosition(key, params.x, params.y, view) }
        }
    }

    private fun savePosition(key: String, x: Int, y: Int, view: View) {
        prefs.edit {
            putFloat("x_$key", x.toFloat())
            putFloat("y_$key", y.toFloat())
        }
        targetPositions[key] = Pair(x + view.width / 2f, y + view.height / 2f)
    }

    fun getTargetCenter(key: String): Pair<Float, Float>? = targetPositions[key]

    override fun onDestroy() {
        isRunning = false
        instance = null
        for ((_, view) in targetViews) {
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        targetViews.clear()
        super.onDestroy()
    }
}
