package com.farematrix.clicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.edit

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private val targetViews = mutableMapOf<String, View>()
    private val targetPositions = mutableMapOf<String, Pair<Float, Float>>()

    private val moneyKeys = listOf("50", "20", "10", "5", "1")
    private val allKeys = moneyKeys + "CHECK"

    companion object {
        @Volatile var isRunning = false
        @Volatile var instance: FloatingService? = null
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
            val channel = NotificationChannel(channelId, "Clicker", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Auto Sukli Clicker")
            .setContentText("Targets active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun setupOverlays() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val defaults = mapOf(
            "50" to (150f to 400f),
            "20" to (280f to 500f),
            "10" to (410f to 400f),
            "5" to (280f to 650f),
            "1" to (410f to 650f),
            "CHECK" to (150f to 800f)
        )

        for (key in allKeys) {
            val defaultPosition = defaults.getValue(key)
            val savedX = prefs.getFloat("x_$key", defaultPosition.first)
            val savedY = prefs.getFloat("y_$key", defaultPosition.second)
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
            view.findViewById<TextView>(R.id.targetLabel).text = if (key == "CHECK") "✓" else key
            if (key == "CHECK") {
                view.findViewById<View>(R.id.circleBody).setBackgroundResource(R.drawable.target_bg_check)
            }

            view.findViewById<View>(R.id.circleBody).setOnTouchListener(object : View.OnTouchListener {
                private var startX = 0
                private var startY = 0
                private var touchX = 0f
                private var touchY = 0f

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = params.x
                            startY = params.y
                            touchX = event.rawX
                            touchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = startX + (event.rawX - touchX).toInt()
                            params.y = startY + (event.rawY - touchY).toInt()
                            windowManager.updateViewLayout(view, params)
                            savePosition(key, params.x, params.y, view)
                            return true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
                    }
                    return false
                }
            })

            view.findViewById<View>(R.id.closeBtn).setOnClickListener {
                if (view.isAttachedToWindow) windowManager.removeView(view)
                targetViews.remove(key)
                targetPositions.remove(key)
                // Clearing the saved position makes this target return to its default on restart.
                prefs.edit { remove("x_$key"); remove("y_$key") }
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
        for (view in targetViews.values) {
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        targetViews.clear()
        targetPositions.clear()
        super.onDestroy()
    }
}
