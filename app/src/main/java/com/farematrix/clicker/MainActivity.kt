package com.farematrix.clicker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var sukliInput: EditText
    private lateinit var resultText: TextView
    private lateinit var giveBtn: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private var playing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        sukliInput = findViewById(R.id.etSukli)
        resultText = findViewById(R.id.resultText)
        giveBtn = findViewById(R.id.btnGiveChange)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        giveBtn.setOnClickListener {
            if (playing) {
                Toast.makeText(this, "Tumatakbo pa ang sequence...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val amount = sukliInput.text.toString().trim().toIntOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Ilagay ang sukli amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (amount > 500) {
                Toast.makeText(this, "Max ₱500 lang", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            giveChange(amount)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val accEnabled = AutoClickService.isEnabled(this)
        val accAlive = AutoClickService.instance != null
        val overlayOk = Settings.canDrawOverlays(this)
        val floatAlive = FloatingService.instance != null

        val accLabel = when {
            accAlive -> "✅ ON (ready)"
            accEnabled -> "⚠️ Enabled, restart app"
            else -> "❌ OFF"
        }
        val floatLabel = when {
            floatAlive -> "✅ ON"
            overlayOk -> "⚠️ Permission OK, tap to start"
            else -> "❌ OFF"
        }

        statusText.text = "Accessibility: $accLabel\nOverlay Service: $floatLabel"
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        mainHandler.postDelayed({ updateStatus() }, 500)
    }

    /**
     * Compute the greedy coin combination, then fire each tap SEQUENTIALLY,
     * waiting for the system to confirm each one finished before starting
     * the next. This is what fixes the "only CHECK registers" bug.
     */
    private fun giveChange(amount: Int) {
        val svc = AutoClickService.instance
        val float = FloatingService.instance

        if (svc == null) {
            Toast.makeText(this, "Enable Accessibility Service muna (Settings → Accessibility)", Toast.LENGTH_LONG).show()
            return
        }
        if (float == null) {
            Toast.makeText(this, "I-tap muna ang 'Show Targets' para lumabas ang circles", Toast.LENGTH_LONG).show()
            return
        }

        // Greedy coin change — highest to lowest
        val coins = listOf(50, 20, 10, 5, 1)
        val plan = mutableListOf<Int>()
        var remaining = amount
        for (c in coins) {
            while (remaining >= c) {
                plan.add(c)
                remaining -= c
            }
        }

        val sequence = plan.map { it.toString() } + "CHECK"

        resultText.text = "₱$amount → ${plan.joinToString(" + ")} → ✓"
        playing = true
        giveBtn.isEnabled = false
        giveBtn.text = "⏳ Tumatakbo..."

        // Hide overlays so synthetic taps go THROUGH to the game underneath
        float.hideAllTargets()

        // Small delay to let the INVISIBLE transition settle before the first tap
        mainHandler.postDelayed({
            tapStep(svc, float, sequence, 0)
        }, 200L)
    }

    /**
     * Fires sequence[index], then — only after the gesture completes —
     * schedules sequence[index+1]. Recursion is safe here because the
     * callback is guaranteed to fire exactly once.
     */
    private fun tapStep(
        svc: AutoClickService,
        float: FloatingService,
        sequence: List<String>,
        index: Int
    ) {
        if (index >= sequence.size) {
            // All taps done — restore UI
            float.showAllTargets()
            playing = false
            giveBtn.isEnabled = true
            giveBtn.text = "💵 Give Change → Check"
            Toast.makeText(this, "✅ Tapos na", Toast.LENGTH_SHORT).show()
            return
        }

        val key = sequence[index]
        val pos = float.getTargetCenter(key)

        if (pos == null) {
            // Target was closed/missing — skip and continue
            tapStep(svc, float, sequence, index + 1)
            return
        }

        svc.tapAt(pos.first, pos.second) {
            // This runs on the main thread AFTER the tap is confirmed done.
            // Add a small settle delay so the game has time to process it.
            val nextDelay = if (key == "CHECK") 350L else 180L
            mainHandler.postDelayed({
                tapStep(svc, float, sequence, index + 1)
            }, nextDelay)
        }
    }

    override fun onDestroy() {
        // Don't kill the floating service here — user might want it running.
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
