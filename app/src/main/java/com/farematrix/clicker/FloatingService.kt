package com.farematrix.clicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Point
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
    private var savedStatusLabel = "Ready"
    private var isPlaying = false
    private var stopRequested = false
    private var stopServiceAfterPlayback = false
    private var countdownRunnable: Runnable? = null

    companion object {
        const val ACTION_STOP_SERVICE = "com.farematrix.clicker.action.STOP_FLOATING_SERVICE"
        private const val CLICK_THROUGH_SETTLE_MS = 250L

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
        savedSequence = prefs.getString("saved_sequence", "").orEmpty()
            .split(',').filter { it in allKeys }
        savedStatusLabel = prefs.getString("saved_status", "Ready") ?: "Ready"
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
        val stopIntent = Intent(this, FloatingService::class.java).setAction(ACTION_STOP_SERVICE)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val stopPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, 1001, stopIntent, pendingIntentFlags)
        } else {
            PendingIntent.getService(this, 1001, stopIntent, pendingIntentFlags)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Auto Sukli is active")
            .setContentText("Targets are showing on screen")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop overlays", stopPendingIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            requestServiceStop()
            return START_NOT_STICKY
        }
        return START_STICKY
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
        // No longer closed once it's (re)created -- Restore always clears this.
        prefs.edit { putBoolean("closed_$key", false) }

        val defaults = mapOf(
            "50"    to Pair(150f, 400f),
            "20"    to Pair(280f, 500f),
            "10"    to Pair(410f, 400f),
            "5"     to Pair(280f, 650f),
            "1"     to Pair(410f, 650f),
            "CHECK" to Pair(150f, 800f)
        )

        // Self-heals any position saved as -9999 by the old (buggy) close handler,
        // from before this fix -- falls back to the default spot instead of
        // resurrecting an off-screen coordinate.
        val rawX = prefs.getFloat("x_$key", defaults[key]!!.first)
        val rawY = prefs.getFloat("y_$key", defaults[key]!!.second)
        val fallbackSize = (88f * resources.displayMetrics.density).toInt()
        val displaySize = displaySizePx()
        val maxX = (displaySize.x - fallbackSize).coerceAtLeast(0)
        val maxY = (displaySize.y - fallbackSize).coerceAtLeast(0)
        val savedX = (if (rawX < 0f) defaults[key]!!.first else rawX).coerceIn(0f, maxX.toFloat())
        val savedY = (if (rawY < 0f) defaults[key]!!.second else rawY).coerceIn(0f, maxY.toFloat())

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
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
        circle.contentDescription = if (key == "CHECK") "Check target" else "₱$key target"
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
                if (locked || isPlaying) return true
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
                            val safe = clampTargetPosition(startX + dx.toInt(), startY + dy.toInt(), view)
                            params.x = safe.first
                            params.y = safe.second
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

        // Close button hides that single target (disabled while locked).
        // IMPORTANT: this must NOT touch x_$key/y_$key -- doing so would overwrite
        // the user's carefully-dragged position. It only marks the target as
        // closed; restoreAllTargets() / addOrRecreateTarget() clears that flag
        // and reuses the real saved position, so Restore brings it back exactly
        // where it was left, not off-screen.
        view.findViewById<View>(R.id.closeBtn).apply {
            contentDescription = if (key == "CHECK") "Close check target" else "Close ₱$key target"
            setOnClickListener {
            if (locked || isPlaying) return@setOnClickListener
            windowManager.removeView(view)
            targetViews.remove(key)
            targetParams.remove(key)
            targetPositions.remove(key)
            prefs.edit { putBoolean("closed_$key", true) }
            }
        }

        windowManager.addView(view, params)
        targetViews[key] = view
        targetParams[key] = params

        // Once the view is laid out, compute the true center.
        view.post {
            val safe = clampTargetPosition(params.x, params.y, view)
            if (safe.first != params.x || safe.second != params.y) {
                params.x = safe.first
                params.y = safe.second
                windowManager.updateViewLayout(view, params)
            }
            savePosition(key, params.x, params.y, view)
        }
    }

    private fun savePosition(key: String, x: Int, y: Int, view: View) {
        val safe = clampTargetPosition(x, y, view)
        prefs.edit {
            putFloat("x_$key", safe.first.toFloat())
            putFloat("y_$key", safe.second.toFloat())
        }
        val fallbackSize = (88f * resources.displayMetrics.density).toInt()
        val w = view.width.takeIf { it > 0 } ?: fallbackSize
        val h = view.height.takeIf { it > 0 } ?: fallbackSize
        targetPositions[key] = Pair(safe.first + w / 2f, safe.second + h / 2f)
    }

    /**
     * Overlay LayoutParams use display coordinates. Resource metrics can describe
     * only this app's split-screen pane, which incorrectly traps overlays at its edge.
     */
    @Suppress("DEPRECATION")
    private fun displaySizePx(): Point {
        val size = Point()
        windowManager.defaultDisplay.getRealSize(size)
        return size
    }

    private fun clampTargetPosition(x: Int, y: Int, view: View): Pair<Int, Int> {
        val fallbackSize = (88f * resources.displayMetrics.density).toInt()
        val width = view.width.takeIf { it > 0 } ?: fallbackSize
        val height = view.height.takeIf { it > 0 } ?: fallbackSize
        val displaySize = displaySizePx()
        return OverlayGeometry.clampPosition(x, y, width, height, displaySize.x, displaySize.y)
    }

    fun getTargetCenter(key: String): Pair<Float, Float>? {
        targetPositions[key]?.let { return it }
        val view = targetViews[key] ?: return null
        val params = targetParams[key] ?: return null
        val fallbackSize = (88f * resources.displayMetrics.density).toInt()
        val w = view.width.takeIf { it > 0 } ?: fallbackSize
        val h = view.height.takeIf { it > 0 } ?: fallbackSize
        val center = Pair(params.x + w / 2f, params.y + h / 2f)
        targetPositions[key] = center
        return center
    }

    /**
     * Marking a window's *view* INVISIBLE does NOT make the window stop
     * intercepting touches — the window itself is a separate surface from
     * the app underneath, and Android routes touches (including our own
     * synthetic ones) to whatever window occupies that screen region,
     * regardless of the content view's visibility. Without this flag, a
     * synthetic tap aimed at the game's ₱5 button actually lands on our own
     * invisible circle instead and gets silently swallowed there — the
     * gesture still "completes" from Android's point of view, which is why
     * the status looked successful even though nothing happened in-game.
     * FLAG_NOT_TOUCHABLE is what actually makes a window click-through.
     */
    private fun setTargetsClickThrough(enable: Boolean): Boolean {
        var allUpdated = true
        for ((key, params) in targetParams) {
            val view = targetViews[key] ?: continue
            params.flags = if (enable) {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) {
                allUpdated = false
            }
        }
        return allUpdated
    }

    fun hideAllTargets(): Boolean {
        if (targetsHidden) return true
        for ((_, v) in targetViews) v.visibility = View.INVISIBLE
        if (!setTargetsClickThrough(true)) {
            // Don't continue with a partially hidden set of windows: a target
            // whose touch flag did not update could swallow its own synthetic tap.
            for ((_, v) in targetViews) v.visibility = View.VISIBLE
            setTargetsClickThrough(false)
            return false
        }
        targetsHidden = true
        return true
    }

    fun showAllTargets() {
        if (!targetsHidden) return
        targetsHidden = false
        setTargetsClickThrough(false)
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
        controllerStatus.text = savedStatusLabel
        controllerPlayBtn = view.findViewById(R.id.controllerPlay)
        controllerLockBtn = view.findViewById(R.id.controllerLock)
        val stopBtn = view.findViewById<Button>(R.id.controllerStop)
        val restoreBtn = view.findViewById<Button>(R.id.controllerRestore)
        val hideBtn = view.findViewById<Button>(R.id.controllerHide)
        val exitBtn = view.findViewById<Button>(R.id.controllerExit)

        val displaySize = displaySizePx()
        val screenWidth = displaySize.x
        val screenHeight = displaySize.y
        view.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(screenHeight, View.MeasureSpec.AT_MOST)
        )
        params.x = params.x.coerceIn(0, (screenWidth - view.measuredWidth).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenHeight - view.measuredHeight).coerceAtLeast(0))

        // Drag support using the status label as the handle, so the buttons
        // underneath it stay tappable.
        controllerStatus.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (isPlaying) return true
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val currentDisplaySize = displaySizePx()
                        val safe = OverlayGeometry.clampPosition(
                            startX + (event.rawX - touchX).toInt(),
                            startY + (event.rawY - touchY).toInt(),
                            view.width,
                            view.height,
                            currentDisplaySize.x,
                            currentDisplaySize.y
                        )
                        params.x = safe.first
                        params.y = safe.second
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
        exitBtn.setOnClickListener { requestServiceStop() }

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
        savedStatusLabel = statusLabel
        prefs.edit {
            putString("saved_sequence", sequence.joinToString(","))
            putString("saved_status", statusLabel)
        }
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

        val sequence = savedSequence.toList()
        val missingTarget = sequence.firstOrNull { it !in targetViews || getTargetCenter(it) == null }
        if (missingTarget != null) {
            controllerStatus.text = "Ibalik ang target: $missingTarget"
            Toast.makeText(this, "Ibalik muna ang nawawalang target na $missingTarget.", Toast.LENGTH_LONG).show()
            return
        }
        if (sequence.any { targetOverlapsController(it) }) {
            controllerStatus.text = "Ilayo ang controller sa mga target"
            Toast.makeText(this, "Ilayo muna ang controller sa denomination targets para hindi nito maagaw ang tap.", Toast.LENGTH_LONG).show()
            return
        }

        isPlaying = true
        stopRequested = false
        stopServiceAfterPlayback = false
        controllerPlayBtn.isEnabled = false
        var seconds = 3
        val countdown = object : Runnable {
            override fun run() {
                if (stopRequested) {
                    countdownRunnable = null
                    finishPlayback("⏹ Stopped")
                    return
                }
                if (seconds > 0) {
                    controllerStatus.text = "Suriin ${seconds}s"
                    seconds -= 1
                    mainHandler.postDelayed(this, 1_000L)
                    return
                }
                countdownRunnable = null
                controllerStatus.text = "▶ Running..."
                if (!hideAllTargets()) {
                    failPlayback("Hindi maitago nang ligtas ang targets; walang na-click.")
                    return
                }
                // WindowManager applies overlay input-region changes on a
                // later frame. Let the click-through flags settle before
                // sending the first accessibility gesture.
                mainHandler.postDelayed({
                    tapStep(svc, sequence, 0)
                }, CLICK_THROUGH_SETTLE_MS)
            }
        }
        countdownRunnable = countdown
        mainHandler.post(countdown)
    }

    private fun targetOverlapsController(key: String): Boolean {
        val center = getTargetCenter(key) ?: return false
        val controller = controllerView ?: return false
        val params = controllerParams ?: return false
        if (controller.visibility != View.VISIBLE || controller.width <= 0 || controller.height <= 0) return false
        return center.first >= params.x && center.first <= params.x + controller.width &&
                center.second >= params.y && center.second <= params.y + controller.height
    }

    private fun stopPlayback() {
        if (!isPlaying) return
        stopRequested = true
        countdownRunnable?.let {
            mainHandler.removeCallbacks(it)
            countdownRunnable = null
            finishPlayback("⏹ Stopped")
        }
    }

    private fun requestServiceStop() {
        if (isPlaying) {
            stopServiceAfterPlayback = true
            stopPlayback()
        } else {
            stopSelf()
        }
    }

    private fun finishPlayback(status: String) {
        if (targetsHidden) showAllTargets()
        isPlaying = false
        controllerPlayBtn.isEnabled = true
        controllerStatus.text = status
        stopRequested = false
        val shouldStopService = stopServiceAfterPlayback
        stopServiceAfterPlayback = false
        if (shouldStopService) stopSelf()
    }

    private fun failPlayback(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finishPlayback("⚠ Tap failed — stopped")
    }

    private fun tapStep(svc: AutoClickService, sequence: List<String>, index: Int) {
        if (stopRequested) {
            finishPlayback("⏹ Stopped")
            return
        }
        if (index >= sequence.size) {
            finishPlayback("✅ Done")
            return
        }

        val key = sequence[index]
        val pos = getTargetCenter(key)

        if (pos == null) {
            failPlayback("Nawawala ang target na $key; itinigil ang sequence.")
            return
        }

        svc.tapAt(pos.first, pos.second) { succeeded ->
            if (!succeeded) {
                failPlayback("Hindi nakumpleto ang tap sa $key; itinigil ang sequence.")
                return@tapAt
            }
            val nextDelay = if (key == "CHECK") 350L else 180L
            mainHandler.postDelayed({
                tapStep(svc, sequence, index + 1)
            }, nextDelay)
        }
    }

    override fun onDestroy() {
        isRunning = false
        instance = null
        isPlaying = false
        countdownRunnable = null
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
        targetPositions.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
