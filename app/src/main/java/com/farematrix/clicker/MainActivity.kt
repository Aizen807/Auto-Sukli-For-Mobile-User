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
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var sukliInput: EditText
    private var playing = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        sukliInput = findViewById(R.id.etSukli)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            if (Settings.canDrawOverlays(this)) startFloatingService()
            else startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }

        findViewById<Button>(R.id.btnGiveChange).setOnClickListener {
            val amount = sukliInput.text.toString().toIntOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Ilagay ang sukli amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            giveChange(amount)
        }
    }

    override fun onResume() { super.onResume(); updateStatus() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun updateStatus() {
        val accOk = AutoClickService.instance != null
        val overlayOk = Settings.canDrawOverlays(this)
        statusText.text = "Accessibility: ${if (accOk) "✅ ON" else "❌ OFF"}\n" +
                          "Overlay: ${if (overlayOk) "✅ ON" else "❌ OFF"}"
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun giveChange(amount: Int) {
        val svc = AutoClickService.instance
        val float = FloatingService.instance
        if (svc == null) { Toast.makeText(this, "Enable Accessibility muna", Toast.LENGTH_LONG).show(); return }
        if (float == null) { Toast.makeText(this, "I-activate muna ang targets", Toast.LENGTH_LONG).show(); return }

        val coins = listOf(50, 20, 10, 5, 1)
        val plan = mutableListOf<Int>()
        var remaining = amount
        for (c in coins) while (remaining >= c) { plan.add(c); remaining -= c }

        val sequence = plan.map { it.toString() } + "CHECK"
        Toast.makeText(this, "₱$amount → ${plan.joinToString("+")} → ✓", Toast.LENGTH_LONG).show()

        scope.launch {
            for (key in sequence) {
                val pos = float.getTargetCenter(key)
                if (pos != null) {
                    svc.tapAt(pos.first, pos.second)
                    delay(if (key == "CHECK") 400L else 250L)
                }
            }
        }
    }
}
