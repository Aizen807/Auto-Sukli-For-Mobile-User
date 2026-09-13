package com.farematrix.clicker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var sukliInput: EditText
    private lateinit var giveChangeButton: Button
    private var playingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        sukliInput = findViewById(R.id.etSukli)
        giveChangeButton = findViewById(R.id.btnGiveChange)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
            }
        }

        giveChangeButton.setOnClickListener {
            val amount = sukliInput.text.toString().toIntOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Ilagay ang sukli amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            giveChange(amount)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        playingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun updateStatus() {
        val accOk = AutoClickService.instance != null
        val overlayOk = Settings.canDrawOverlays(this)
        statusText.text = "Accessibility: ${if (accOk) "✅ ON" else "❌ OFF"}\n" +
            "Overlay: ${if (overlayOk) "✅ ON" else "❌ OFF"}"
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun giveChange(amount: Int) {
        val accessibilityService = AutoClickService.instance
        val floatingService = FloatingService.instance
        if (accessibilityService == null) {
            Toast.makeText(this, "Enable Accessibility muna", Toast.LENGTH_LONG).show()
            return
        }
        if (floatingService == null) {
            Toast.makeText(this, "I-activate muna ang targets", Toast.LENGTH_LONG).show()
            return
        }
        if (playingJob?.isActive == true) {
            Toast.makeText(this, "Ginagawa pa ang naunang sukli", Toast.LENGTH_SHORT).show()
            return
        }

        val coins = listOf(50, 20, 10, 5, 1)
        val plan = mutableListOf<Int>()
        var remaining = amount
        for (coin in coins) {
            while (remaining >= coin) {
                plan.add(coin)
                remaining -= coin
            }
        }
        if (remaining != 0 || plan.isEmpty()) {
            Toast.makeText(this, "Hindi mabuo ang sukli amount", Toast.LENGTH_LONG).show()
            return
        }

        val sequence = plan.map(Int::toString) + "CHECK"
        Toast.makeText(this, "₱$amount → ${plan.joinToString("+")} → ✓", Toast.LENGTH_LONG).show()
        giveChangeButton.isEnabled = false

        playingJob = scope.launch {
            try {
                for (key in sequence) {
                    val position = floatingService.getTargetCenter(key)
                    if (position == null) {
                        Toast.makeText(this@MainActivity, "Ibalik muna ang target: $key", Toast.LENGTH_LONG).show()
                        break
                    }
                    accessibilityService.tapAt(position.first, position.second)
                    delay(if (key == "CHECK") 400L else 250L)
                }
            } finally {
                giveChangeButton.isEnabled = true
                playingJob = null
            }
        }
    }
}
