package com.farematrix.clicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())

    private val targetViews = mutableMapOf<String, View>()
    private val targetParams = mutableMapOf<String, WindowManager.LayoutParams>()

    /**
     * Stores the screen-space center (x, y) of each target.
     * Read by tapStep() when it needs to fire taps.
     */
    val targetPositions = mutableMapOf<String, Pair<Float, Float>>()

    private val moneyKeys = listOf("50", "20", "10", "5", "1")
    private val allKeys = moneyKeys + "CHECK"

    private var targetsHidden = false
    private var locked = false

    // The floating controller (Play / Stop / Restore / Lock / Hide)
    private var controllerView: View? = null
    private var controllerParams: WindowManager.LayoutParams? = null
    private lateinit var controllerStatus: TextView
    private lateinit var controllerPlayBtn: Button
    private lateinit var controllerLockBtn: Button

    // The auto-sukli sequence most recently saved by MainActivity, e.g. ["20","10","CHECK"]
    private var savedSequence: List<String> = emptyList()
    private var isPlaying = false
    private var stopRequested = false

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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupTargets()
        setupController()
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

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    // ---------------------------------------------------------------
    // Denomination targets
    // ---------------------------------------------------------------

    private fun setupTargets() {
        for (key in allKeys) {
            addOrRecreateTarget(key)
        }
    }

    /** Creates (or re-creates, after Restore) a single denomination target. */
    private fun addOrRecreateTarget(key: String) {
        if (targetViews.containsKey(key)) return

        val defaults = mapOf(
            "50"    to Pair(150f, 400f),
            "20"    to Pair(280f, 500f),
            "10"    to Pair(410f, 400f),
            "5"     to Pair(280f, 650f),
            "1"     to Pair(410f, 650f),
            "CHECK" to Pair(150f, 800f)
        )

        val savedX = prefs.getFloat("x_$key", defaults[key]!!.first)
        val savedY = prefs.getFloat("y_$key", defaults[key]!!.second)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
                if (locked) return true
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

        // Close button hides that single target (disabled while locked)
        view.findViewById<View>(R.id.closeBtn).setOnClickListener {
            if (locked) return@setOnClickListener
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

    private fun savePosition(key: String, x: Int, y: Int, view: View) {
        prefs.edit {
            putFloat("x_$key", x.toFloat())
            putFloat("y_$key", y.toFloat())
        }
        val w = if (view.width > 0) view.width else 88
        val h = if (view.height > 0) view.height else 88
        targetPositions[key] = Pair(x + w / 2f, y + h / 2f)
    }

    fun getTargetCenter(key: String): Pair<Float, Float>? {
        targetPositions[key]?.let { return it }
        val view = targetViews[key] ?: return null
        val params = targetParams[key] ?: return null
        val w = if (view.width > 0) view.width else 88
        val h = if (view.height > 0) view.height else 88
        val center = Pair(params.x + w / 2f, params.y + h / 2f)
        targetPositions[key] = center
        return center
    }

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

    /** Re-creates any denomination target that was closed via its X button. */
    fun restoreAllTargets() {
        for (key in allKeys) {
            addOrRecreateTarget(key)
        }
        Toast.makeText(this, "Naibalik ang mga target", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------
    // Floating controller (Play / Stop / Restore / Lock / Hide)
    // ---------------------------------------------------------------

    private fun setupController() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getFloat("controller_x", 40f).toInt()
            y = prefs.getFloat("controller_y", 200f).toInt()
        }

        val view = LayoutInflater.from(this).inflate(R.layout.floating_controller, null)

        controllerStatus = view.findViewById(R.id.controllerStatus)
        controllerPlayBtn = view.findViewById(R.id.controllerPlay)
        controllerLockBtn = view.findViewById(R.id.controllerLock)
        val stopBtn = view.findViewById<Button>(R.id.controllerStop)
        val restoreBtn = view.findViewById<Button>(R.id.controllerRestore)
        val hideBtn = view.findViewById<Button>(R.id.controllerHide)

        // Drag support using the status label as the handle, so the buttons
        // underneath it stay tappable.
        controllerStatus.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
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
                        prefs.edit {
                            putFloat("controller_x", params.x.toFloat())
                            putFloat("controller_y", params.y.toFloat())
                        }
                        return true
                    }
                }
                return false
            }
        })

        controllerPlayBtn.setOnClickListener { playSequence() }
        stopBtn.setOnClickListener { stopPlayback() }
        restoreBtn.setOnClickListener { restoreAllTargets() }
        controllerLockBtn.setOnClickListener { toggleLock() }
        hideBtn.setOnClickListener { view.visibility = View.GONE }

        windowManager.addView(view, params)
        controllerView = view
        controllerParams = params
    }

    /** Called by MainActivity — bring the controller back after Hide, without restarting the service. */
    fun showController() {
        controllerView?.visibility = View.VISIBLE
    }

    private fun toggleLock() {
        locked = !locked
        controllerLockBtn.text = if (locked) "🔒" else "🔓"
        Toast.makeText(
            this,
            if (locked) "Naka-lock ang mga target" else "Naka-unlock ang mga target",
            Toast.LENGTH_SHORT
        ).show()
    }

    /** Called by MainActivity whenever the trip details change, to keep the Play button up to date. */
    fun updateSavedSequence(sequence: List<String>, statusLabel: String) {
        savedSequence = sequence
        if (!isPlaying) {
            controllerStatus.text = statusLabel
        }
    }

    private fun playSequence() {
        if (isPlaying) {
            Toast.makeText(this, "Tumatakbo na ang sequence", Toast.LENGTH_SHORT).show()
            return
        }
        val svc = AutoClickService.instance
        if (svc == null) {
            Toast.makeText(this, "I-enable muna ang Accessibility Service", Toast.LENGTH_LONG).show()
            return
        }
        if (savedSequence.isEmpty()) {
            Toast.makeText(this, "Wala pang naka-save na sukli sequence", Toast.LENGTH_LONG).show()
            return
        }

        isPlaying = true
        stopRequested = false
        controllerPlayBtn.isEnabled = false
        controllerStatus.text = "▶ Running..."
        hideAllTargets()

        mainHandler.postDelayed({
            tapStep(svc, savedSequence, 0)
        }, 200L)
    }

    private fun stopPlayback() {
        if (!isPlaying) return
        stopRequested = true
    }

    private fun tapStep(svc: AutoClickService, sequence: List<String>, index: Int) {
        if (stopRequested || index >= sequence.size) {
            showAllTargets()
            isPlaying = false
            controllerPlayBtn.isEnabled = true
            controllerStatus.text = if (stopRequested) "⏹ Stopped" else "✅ Done"
            stopRequested = false
            return
        }

        val key = sequence[index]
        val pos = getTargetCenter(key)

        if (pos == null) {
            // Target was closed/missing — skip and continue
            tapStep(svc, sequence, index + 1)
            return
        }

        svc.tapAt(pos.first, pos.second) {
            val nextDelay = if (key == "CHECK") 350L else 180L
            mainHandler.postDelayed({
                tapStep(svc, sequence, index + 1)
            }, nextDelay)
        }
    }

    override fun onDestroy() {
        isRunning = false
        instance = null
        mainHandler.removeCallbacksAndMessages(null)
        for ((_, v) in targetViews) {
            if (v.isAttachedToWindow) {
                try { windowManager.removeView(v) } catch (_: Exception) {}
            }
        }
        controllerView?.let {
            if (it.isAttachedToWindow) {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
        }
        targetViews.clear()
        targetParams.clear()
        super.onDestroy()
    }
}
