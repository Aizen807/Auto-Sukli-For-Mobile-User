package com.farematrix.clicker

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

private data class RouteDef(val label: String, val units: List<List<String>>)

private val ROUTES = linkedMapOf(
    "balagtas" to RouteDef(
        "Balagtas", listOf(
            listOf("Bagumbayan", "San Jose"),
            listOf("Matungao"),
            listOf("Panginay Guiguinto"),
            listOf("Panginay Balagtas"),
            listOf("Wawa")
        )
    ),
    "guiguinto" to RouteDef(
        "Guiguinto", listOf(
            listOf("Bagumbayan", "San Jose"),
            listOf("Matungao"),
            listOf("Tuktukan")
        )
    ),
    "malolos" to RouteDef(
        "Malolos", listOf(
            listOf("Bagumbayan", "San Jose"),
            listOf("Maysantol"),
            listOf("San Nicolas"),
            listOf("Pitpitan"),
            listOf("Mambog"),
            listOf("Matimbo"),
            listOf("Panasahan"),
            listOf("Bagna"),
            listOf("Atlag"),
            listOf("San Juan", "Sto. Rosario")
        )
    )
)

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var spRoute: Spinner
    private lateinit var spPickup: Spinner
    private lateinit var spDropoff: Spinner
    private lateinit var etRegularCount: EditText
    private lateinit var etStudentCount: EditText
    private lateinit var etSeniorCount: EditText
    private lateinit var etPayment: EditText
    private lateinit var tvFareResult: TextView
    private lateinit var tvBreakdown: TextView
    private lateinit var tvChangeResult: TextView
    private lateinit var tvAutoSequence: TextView
    private lateinit var btnCalculateAndGive: Button
    private lateinit var btnReset: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private val routeKeys = ROUTES.keys.toList()
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startFloatingServiceNow()
            } else {
                Toast.makeText(
                    this,
                    "I-enable ang notifications para makita at mapatigil ang overlay service.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    // Flat (barangay name, unit index) list for the currently selected route
    private var currentBarangays: List<Pair<String, Int>> = emptyList()
    private var suppressRecalc = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        spRoute = findViewById(R.id.spRoute)
        spPickup = findViewById(R.id.spPickup)
        spDropoff = findViewById(R.id.spDropoff)
        etRegularCount = findViewById(R.id.etRegularCount)
        etStudentCount = findViewById(R.id.etStudentCount)
        etSeniorCount = findViewById(R.id.etSeniorCount)
        etPayment = findViewById(R.id.etPayment)
        tvFareResult = findViewById(R.id.tvFareResult)
        tvBreakdown = findViewById(R.id.tvBreakdown)
        tvChangeResult = findViewById(R.id.tvChangeResult)
        tvAutoSequence = findViewById(R.id.tvAutoSequence)
        btnCalculateAndGive = findViewById(R.id.btnCalculateAndGive)
        btnReset = findViewById(R.id.btnReset)

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

        // "3. Show Floating Controller" — brings the controller back after
        // it was hidden with its own Hide button, without restarting the
        // whole overlay service (which would also re-show every target).
        findViewById<Button>(R.id.btnController).setOnClickListener {
            val float = FloatingService.instance
            if (float != null) {
                float.showController()
            } else if (Settings.canDrawOverlays(this)) {
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

        setupRouteSpinner()
        setupRecalcListeners()

        btnCalculateAndGive.setOnClickListener { recalcAndSave(showToast = true) }
        btnReset.setOnClickListener { resetTrip() }

        // Reflect the default trip details (1 regular passenger, default route)
        // immediately instead of leaving the placeholder XML text on screen.
        recalcAndSave(showToast = false)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startFloatingServiceNow()
    }

    private fun startFloatingServiceNow() {
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // Push whatever trip details are already on screen to the new
        // controller right away — the person shouldn't have to touch a
        // field again just because the overlay only now started existing.
        mainHandler.postDelayed({
            updateStatus()
            recalcAndSave(showToast = false)
        }, 500)
    }

    // -----------------------------------------------------------------
    // Trip details UI
    // -----------------------------------------------------------------

    private fun setupRouteSpinner() {
        val labels = routeKeys.map { ROUTES[it]!!.label }
        spRoute.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spRoute.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                buildPickupDropoffAdapters()
                recalcAndSave(showToast = false)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        buildPickupDropoffAdapters()
    }

    private fun buildPickupDropoffAdapters() {
        val route = ROUTES[routeKeys[spRoute.selectedItemPosition]]!!
        val flat = mutableListOf<Pair<String, Int>>()
        route.units.forEachIndexed { idx, names -> names.forEach { flat.add(it to idx) } }
        currentBarangays = flat

        val names = flat.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        suppressRecalc = true
        spPickup.adapter = adapter
        spDropoff.adapter = adapter
        spPickup.setSelection(0)
        spDropoff.setSelection(names.size - 1)
        suppressRecalc = false

        val itemListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                recalcAndSave(showToast = false)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spPickup.onItemSelectedListener = itemListener
        spDropoff.onItemSelectedListener = itemListener
    }

    private fun setupRecalcListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                recalcAndSave(showToast = false)
            }
        }
        etRegularCount.addTextChangedListener(watcher)
        etStudentCount.addTextChangedListener(watcher)
        etSeniorCount.addTextChangedListener(watcher)
        etPayment.addTextChangedListener(watcher)
    }

    private fun longFromPassengerField(field: EditText): Long? {
        val text = field.text.toString().trim()
        if (text.isEmpty()) return 0L
        val count = text.toLongOrNull() ?: return null
        return count.takeIf { it >= 0L }
    }

    /**
     * Recomputes fare + change from the current trip details, updates the
     * on-screen result, and pushes the resulting auto-click sequence to the
     * floating controller so its ▶ Play button always reflects the latest
     * trip — even after this screen is closed.
     */
    private fun recalcAndSave(showToast: Boolean) {
        if (suppressRecalc || currentBarangays.isEmpty()) return

        val fromIdx = currentBarangays.getOrNull(spPickup.selectedItemPosition)?.second ?: return
        val toIdx = currentBarangays.getOrNull(spDropoff.selectedItemPosition)?.second ?: return

        val regular = longFromPassengerField(etRegularCount)
        val student = longFromPassengerField(etStudentCount)
        val senior = longFromPassengerField(etSeniorCount)
        if (regular == null || student == null || senior == null) {
            showInvalidCalculation("Gumamit ng non-negative whole number para sa bilang ng pasahero.")
            return
        }

        val totalPax = try {
            Math.addExact(Math.addExact(regular, student), senior)
        } catch (_: ArithmeticException) {
            showInvalidCalculation("Sobra ang bilang ng pasahero.")
            return
        }
        if (totalPax <= 0L) {
            tvFareResult.text = "Fare: ₱0"
            tvBreakdown.text = "Maglagay ng hindi bababa sa 1 pasahero."
            tvChangeResult.text = "Sukli: ₱0"
            tvAutoSequence.text = "Auto-click order: —"
            FloatingService.instance?.updateSavedSequence(emptyList(), "Ready")
            return
        }

        val result = try {
            calculateFare(fromIdx, toIdx, regular, student, senior)
        } catch (_: ArithmeticException) {
            showInvalidCalculation("Sobra ang bilang ng pasahero.")
            return
        }
        tvFareResult.text = "Fare: ₱${result.total}"

        val unitNote = if (result.unitsCount <= MINIMUM_UNITS)
            "${result.unitsCount} unit(s), within minimum"
        else
            "${result.unitsCount} units, +${result.extra} beyond minimum"
        tvBreakdown.text = "Regular ₱${result.regularFare} • Student/Senior ₱${result.reducedFare} • $unitNote"

        val paymentText = etPayment.text.toString().trim()
        if (paymentText.isEmpty()) {
            tvChangeResult.text = "Ilagay ang bayad"
            tvAutoSequence.text = "Auto-click order: —"
            FloatingService.instance?.updateSavedSequence(emptyList(), "Ilagay ang bayad")
            return
        }

        val paymentCents = parsePaymentCents(paymentText)
        if (paymentCents == null) {
            tvChangeResult.text = "Invalid na halaga ng bayad"
            tvAutoSequence.text = "Auto-click order: —"
            FloatingService.instance?.updateSavedSequence(emptyList(), "Invalid na bayad")
            if (showToast) Toast.makeText(this, "Hanggang dalawang decimal place lang ang bayad.", Toast.LENGTH_SHORT).show()
            return
        }

        val totalCents = try {
            Math.multiplyExact(result.total, 100L)
        } catch (_: ArithmeticException) {
            showInvalidCalculation("Sobra ang halaga ng pamasahe.")
            return
        }
        if (paymentCents < totalCents) {
            tvChangeResult.text = "Kulang ang bayad"
            tvAutoSequence.text = "Auto-click order: —"
            FloatingService.instance?.updateSavedSequence(emptyList(), "Kulang ang bayad")
            if (showToast) Toast.makeText(this, "Kulang ang bayad para sa fare", Toast.LENGTH_SHORT).show()
            return
        }

        val changeCents = paymentCents - totalCents
        tvChangeResult.text = "Sukli: ₱${formatPesoCents(changeCents)}"

        val plan = denominationPlan(changeCents)
        if (plan == null) {
            tvAutoSequence.text = "Auto-click unavailable: centavo o sobrang daming taps. Manu-manong ibigay ang eksaktong sukli."
            FloatingService.instance?.updateSavedSequence(emptyList(), "Manu-manong sukli")
            if (showToast) Toast.makeText(this, "Ibigay nang manu-mano ang eksaktong sukli; hindi ito mare-represent ng kasalukuyang targets.", Toast.LENGTH_LONG).show()
            return
        }

        val sequence = plan.map { it.toString() } + "CHECK"
        tvAutoSequence.text = if (plan.isEmpty())
            "Auto-click order: ✓ lang (exact payment)"
        else
            "Auto-click order: ${plan.joinToString(" + ")} → ✓"

        val statusLabel = "₱${formatPesoCents(changeCents)} → ${plan.joinToString(" + ").ifEmpty { "0" }} → ✓"
        FloatingService.instance?.updateSavedSequence(sequence, statusLabel)

        if (showToast) {
            if (FloatingService.instance == null) {
                Toast.makeText(this, "I-tap ang 'Show / Arrange Targets' para gumana ang Play button", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Na-save ang sukli sequence", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showInvalidCalculation(message: String) {
        tvFareResult.text = "Fare: —"
        tvBreakdown.text = message
        tvChangeResult.text = "Sukli: —"
        tvAutoSequence.text = "Auto-click order: —"
        FloatingService.instance?.updateSavedSequence(emptyList(), "Invalid na detalye")
    }

    private fun resetTrip() {
        suppressRecalc = true
        etRegularCount.setText("1")
        etStudentCount.setText("")
        etSeniorCount.setText("")
        etPayment.setText("")
        spPickup.setSelection(0)
        spDropoff.setSelection(currentBarangays.size - 1)
        suppressRecalc = false
        recalcAndSave(showToast = false)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
