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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private val targetViews = mutableMapOf<String, View>()
    private val targetPositions = mutableMapOf<String, Pair<Float, Float>>()
    private var controllerView: View? = null
    private var sequenceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val moneyKeys = listOf("50", "20", "10", "5", "1")
    private val allKeys = moneyKeys + "CHECK"
    private val defaults = mapOf(
        "50" to (150f to 400f),
        "20" to (280f to 500f),
        "10" to (410f to 400f),
        "5" to (280f to 650f),
        "1" to (410f to 650f),
        "CHECK" to (150f to 800f)
    )

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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(1, buildNotification())
        setupController()
        restoreAllTargets()
    }

    private fun buildNotification(): Notification {
        val channelId = "floating_clicker"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Clicker", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Auto Sukli Controller")
            .setContentText("▶ Play change  + Restore targets")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun setupController() {
        val view = LayoutInflater.from(this).inflate(R.layout.floating_controller, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 120
        }
        view.findViewById<Button>(R.id.controllerPlay).setOnClickListener { playSavedChange() }
        view.findViewById<Button>(R.id.controllerRestore).setOnClickListener { restoreAllTargets() }
        windowManager.addView(view, params)
        controllerView = view
    }

    fun restoreAllTargets() {
        for (key in allKeys) {
            if (!targetViews.containsKey(key)) createTarget(key)
        }
        Toast.makeText(this, "All sukli targets restored", Toast.LENGTH_SHORT).show()
    }

    private fun createTarget(key: String) {
        val defaultPosition = defaults.getValue(key)
        val savedX = prefs.getFloat("x_$key", defaultPosition.first)
        val savedY = prefs.getFloat("y_$key", defaultPosition.second)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX.toInt()
            y = savedY.toInt()
        }

        val view = LayoutInflater.from(this).inflate(R.layout.floating_target, null)
        view.findViewById<TextView>(R.id.targetLabel).text = if (key == "CHECK") "✓" else key
        if (key == "CHECK") view.findViewById<View>(R.id.circleBody)
            .setBackgroundResource(R.drawable.target_bg_check)

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
        }
        windowManager.addView(view, params)
        targetViews[key] = view
        view.post { savePosition(key, params.x, params.y, view) }
    }

    private fun savePosition(key: String, x: Int, y: Int, view: View) {
        prefs.edit {
            putFloat("x_$key", x.toFloat())
            putFloat("y_$key", y.toFloat())
        }
        targetPositions[key] = Pair(x + view.width / 2f, y + view.height / 2f)
    }

    fun getTargetCenter(key: String): Pair<Float, Float>? = targetPositions[key]

    private fun playSavedChange() {
        if (sequenceJob?.isActive == true) {
            Toast.makeText(this, "Ginagawa pa ang sukli", Toast.LENGTH_SHORT).show()
            return
        }
        val saved = getSharedPreferences("fare_session", MODE_PRIVATE)
            .getString("pending_sequence", "") ?: ""
        val sequence = saved.split(",").filter { it.isNotBlank() }
        if (sequence.isEmpty()) {
            Toast.makeText(this, "Pumili muna ng trip at kalkulahin ang sukli", Toast.LENGTH_LONG).show()
            return
        }
        val accessibilityService = AutoClickService.instance
        if (accessibilityService == null) {
            Toast.makeText(this, "Enable Accessibility muna", Toast.LENGTH_LONG).show()
            return
        }
        sequenceJob = scope.launch {
            try {
                for (key in sequence) {
                    val position = getTargetCenter(key)
                    if (position == null) {
                        Toast.makeText(this@FloatingService, "Ibalik muna ang target: $key gamit ang +", Toast.LENGTH_LONG).show()
                        break
                    }
                    accessibilityService.tapAt(position.first, position.second)
                    delay(if (key == "CHECK") 400L else 250L)
                }
            } finally {
                sequenceJob = null
            }
        }
    }

    override fun onDestroy() {
        sequenceJob?.cancel()
        scope.cancel()
        isRunning = false
        instance = null
        controllerView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        for (view in targetViews.values) {
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        controllerView = null
        targetViews.clear()
        targetPositions.clear()
        super.onDestroy()
    }
}
