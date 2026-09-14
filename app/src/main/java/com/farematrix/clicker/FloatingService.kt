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
    private val targetParams = mutableMapOf<String, WindowManager.LayoutParams>()

    /**
     * Stores the screen-space center (x, y) of each target.
     * Read by MainActivity when it needs to fire taps.
     */
    val targetPositions = mutableMapOf<String, Pair<Float, Float>>()

    private val moneyKeys = listOf("50", "20", "10", "5", "1")
    private val allKeys = moneyKeys + "CHECK"

    private var targetsHidden = false

    companion object {
        @Volatile
        var isRunning = false
            private set

        @Volatile
        var instance: FloatingService? = null
            private set
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
            val ch = NotificationChannel(
                channelId,
                "Auto Sukli",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Auto Sukli is active")
            .setContentText("Targets are showing on screen")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun setupOverlays() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = savedX.toInt()
                y = savedY.toInt()
            }

            val view = LayoutInflater.from(this)
                .inflate(R.layout.floating_target, null)

            val label = view.findViewById<TextView>(R.id.targetLabel)
            label.text = if (key == "CHECK") "✓" else key

            val circle = view.findViewById<View>(R.id.circleBody)
            if (key == "CHECK") {
                circle.setBackgroundResource(R.drawable.target_bg_check)
            }

            // Drag handler
            circle.setOnTouchListener(object : View.OnTouchListener {
                private var startX = 0
                private var startY = 0
                private var touchX = 0f
                private var touchY = 0f
                private var dragging = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = params.x
                            startY = params.y
                            touchX = event.rawX
                            touchY = event.rawY
                            dragging = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - touchX
                            val dy = event.rawY - touchY
                            if (!dragging && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) {
                                dragging = true
                            }
                            if (dragging) {
                                params.x = startX + dx.toInt()
                                params.y = startY + dy.toInt()
                                windowManager.updateViewLayout(view, params)
                                savePosition(key, params.x, params.y, view)
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (dragging) {
                                savePosition(key, params.x, params.y, view)
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            // Close button hides that single target
            view.findViewById<View>(R.id.closeBtn).setOnClickListener {
                windowManager.removeView(view)
                targetViews.remove(key)
                targetParams.remove(key)
                targetPositions.remove(key)
                prefs.edit {
                    putFloat("x_$key", -9999f)
                    putFloat("y_$key", -9999f)
                }
            }

            windowManager.addView(view, params)
            targetViews[key] = view
            targetParams[key] = params

            // Once the view is laid out, compute the true center.
            view.post { savePosition(key, params.x, params.y, view) }
        }
    }

    private fun savePosition(key: String, x: Int, y: Int, view: View) {
        prefs.edit {
            putFloat("x_$key", x.toFloat())
            putFloat("y_$key", y.toFloat())
        }
        // view.width/height are the ACTUAL rendered size AFTER layout.
        // Fall back to a sensible default if not yet measured.
        val w = if (view.width > 0) view.width else 88  // 56dp + 16dp*2 padding ≈ 88px on mdpi
        val h = if (view.height > 0) view.height else 88
        targetPositions[key] = Pair(x + w / 2f, y + h / 2f)
    }

    fun getTargetCenter(key: String): Pair<Float, Float>? {
        targetPositions[key]?.let { return it }
        // Fallback: recompute from the current view bounds
        val view = targetViews[key] ?: return null
        val params = targetParams[key] ?: return null
        val w = if (view.width > 0) view.width else 88
        val h = if (view.height > 0) view.height else 88
        val center = Pair(params.x + w / 2f, params.y + h / 2f)
        targetPositions[key] = center
        return center
    }

    /**
     * Hide all target circles before playback so they don't intercept the
     * synthetic taps meant for the underlying app. We use INVISIBLE (not
     * GONE) so the layout and saved coordinates stay intact.
     */
    fun hideAllTargets() {
        if (targetsHidden) return
        targetsHidden = true
        for ((_, v) in targetViews) v.visibility = View.INVISIBLE
    }

    fun showAllTargets() {
        if (!targetsHidden) return
        targetsHidden = false
        for ((_, v) in targetViews) v.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        isRunning = false
        instance = null
        for ((_, v) in targetViews) {
            if (v.isAttachedToWindow) {
                try { windowManager.removeView(v) } catch (_: Exception) {}
            }
        }
        targetViews.clear()
        targetParams.clear()
        super.onDestroy()
    }
}
