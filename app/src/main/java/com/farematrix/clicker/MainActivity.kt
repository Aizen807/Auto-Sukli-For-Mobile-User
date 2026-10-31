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
    private lateinit var routeButtons: List<Button>
    private lateinit var spPickup: Spinner
    private lateinit var spDropoff: Spinner
    private lateinit var tvRegularCount: TextView
    private lateinit var tvStudentCount: TextView
    private lateinit var tvSeniorCount: TextView
    private lateinit var etPayment: EditText
    private lateinit var tvFareResult: TextView
    private lateinit var tvBreakdown: TextView
    private lateinit var tvChangeResult: TextView
    private lateinit var tvAutoSequence: TextView
    private lateinit var btnCalculateAndGive: Button
    private lateinit var btnReset: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private val routeKeys = ROUTES.keys.toList()
    private var selectedRouteIndex = 0
    private var currentBarangays: List<Pair<String, Int>> = emptyList()
    private var suppressRecalc = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        routeButtons = listOf(
            findViewById(R.id.btnRouteBalagtas),
            findViewById(R.id.btnRouteGuiguinto),
            findViewById(R.id.btnRouteMalolos)
        )
        spPickup = findViewById(R.id.spPickup)
        spDropoff = findViewById(R.id.spDropoff)
        tvRegularCount = findViewById(R.id.tvRegularCount)
        tvStudentCount = findViewById(R.id.tvStudentCount)
        tvSeniorCount = findViewById(R.id.tvSeniorCount)
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

        // Restore the controller after it has been hidden without re-showing
        // every denomination target.
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

        setupRouteSelector()
        buildPickupDropoffAdapters()
        setupPassengerSteppers()
        setupRecalcListeners()

        findViewById<Button>(R.id.btnSwap).setOnClickListener {
            val pickup = spPickup.selectedItemPosition
            val dropoff = spDropoff.selectedItemPosition
            spPickup.setSelection(dropoff)
            spDropoff.setSelection(pickup)
        }

        listOf(
            R.id.btnPay20 to "20",
            R.id.btnPay50 to "50",
            R.id.btnPay100 to "100"
        ).forEach { (buttonId, amount) ->
            findViewById<Button>(buttonId).setOnClickListener {
                etPayment.setText(amount)
                etPayment.setSelection(etPayment.text.length)
            }
        }

        btnCalculateAndGive.setOnClickListener { recalcAndSave(showToast = true) }
        btnReset.setOnClickListener { resetTrip() }

        updateRouteButtonStates()
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
        // Immediately send the currently displayed trip to a newly started controller.
        mainHandler.postDelayed({
            updateStatus()
            recalcAndSave(showToast = false)
        }, 500)
    }

    private fun setupRouteSelector() {
        routeButtons.forEachIndexed { index, button ->
            button.setOnClickListener { selectRoute(index) }
        }
    }

    private fun selectRoute(index: Int) {
        if (index !in routeKeys.indices || index == selectedRouteIndex) return
        selectedRouteIndex = index
        updateRouteButtonStates()
        buildPickupDropoffAdapters()
        recalcAndSave(showToast = false)
    }

    private fun updateRouteButtonStates() {
        routeButtons.forEachIndexed { index, button ->
            val selected = index == selectedRouteIndex
            val routeName = ROUTES[routeKeys[index]]!!.label
            button.isSelected = selected
            button.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (selected) R.color.ink else R.color.cream
                )
            )
            button.contentDescription = "$routeName route${if (selected) ", selected" else ""}"
        }
    }

    private fun buildPickupDropoffAdapters() {
        val route = ROUTES[routeKeys[selectedRouteIndex]]!!
        val flat = mutableListOf<Pair<String, Int>>()
        route.units.forEachIndexed { idx, names -> names.forEach { flat.add(it to idx) } }
        currentBarangays = flat

        val names = flat.map { it.first }
        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            names
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return styleSpinnerText(super.getView(position, convertView, parent), dropdown = false)
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return styleSpinnerText(super.getDropDownView(position, convertView, parent), dropdown = true)
            }
        }.also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val itemListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                recalcAndSave(showToast = false)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        suppressRecalc = true
        spPickup.adapter = adapter
        spDropoff.adapter = adapter
        spPickup.onItemSelectedListener = itemListener
        spDropoff.onItemSelectedListener = itemListener
        spPickup.setSelection(0)
        spDropoff.setSelection(names.lastIndex)
        suppressRecalc = false
    }

    private fun styleSpinnerText(view: View, dropdown: Boolean): View {
        val text = view as? TextView ?: return view
        text.setTextColor(ContextCompat.getColor(this, R.color.cream))
        text.textSize = 15f
        text.setPadding(dp(14), dp(if (dropdown) 12 else 10), dp(14), dp(if (dropdown) 12 else 10))
        if (dropdown) text.setBackgroundColor(ContextCompat.getColor(this, R.color.surface))
        return text
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setupPassengerSteppers() {
        bindStepper(R.id.btnRegularMinus, tvRegularCount, R.id.btnRegularPlus, "Regular")
        bindStepper(R.id.btnStudentMinus, tvStudentCount, R.id.btnStudentPlus, "Student")
        bindStepper(R.id.btnSeniorMinus, tvSeniorCount, R.id.btnSeniorPlus, "Senior")
    }

    private fun bindStepper(minusId: Int, count: TextView, plusId: Int, label: String) {
        val minus = findViewById<Button>(minusId)
        val plus = findViewById<Button>(plusId)
        minus.contentDescription = "Remove one $label passenger"
        plus.contentDescription = "Add one $label passenger"
        minus.setOnClickListener { adjustPassengerCount(count, -1L) }
        plus.setOnClickListener { adjustPassengerCount(count, 1L) }
        count.tag = label
        count.contentDescription = "$label passengers: ${count.text}"
    }

    private fun adjustPassengerCount(count: TextView, delta: Long) {
        val current = count.text.toString().toLongOrNull() ?: 0L
        val updated = try {
            if (delta > 0L) Math.addExact(current, delta) else (current + delta).coerceAtLeast(0L)
        } catch (_: ArithmeticException) {
            Toast.makeText(this, "Sobra ang bilang ng pasahero.", Toast.LENGTH_SHORT).show()
            return
        }
        count.text = updated.toString()
        count.contentDescription = "${count.tag} passengers: $updated"
        recalcAndSave(showToast = false)
    }

    private fun setupRecalcListeners() {
        etPayment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                recalcAndSave(showToast = false)
            }
        })
    }

    private fun passengerCount(count: TextView): Long? {
        val value = count.text.toString().trim().toLongOrNull() ?: return null
        return value.takeIf { it >= 0L }
    }

    /** Recalculates the visible fare/change and saves the latest safe tap sequence. */
    private fun recalcAndSave(showToast: Boolean) {
        if (suppressRecalc || currentBarangays.isEmpty()) return

        val fromIdx = currentBarangays.getOrNull(spPickup.selectedItemPosition)?.second ?: return
        val toIdx = currentBarangays.getOrNull(spDropoff.selectedItemPosition)?.second ?: return

        val regular = passengerCount(tvRegularCount)
        val student = passengerCount(tvStudentCount)
        val senior = passengerCount(tvSeniorCount)
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
            tvFareResult.text = "₱0"
            tvBreakdown.text = "Maglagay ng hindi bababa sa 1 pasahero."
            tvChangeResult.text = "₱0"
            tvAutoSequence.text = "—"
            FloatingService.instance?.updateSavedSequence(emptyList(), "Ready")
            return
        }

        val result = try {
            calculateFare(fromIdx, toIdx, regular, student, senior)
        } catch (_: ArithmeticException) {
            showInvalidCalculation("Sobra ang bilang ng pasahero.")
            return
        }
        tvFareResult.text = "₱${result.total}"

        val unitNote = if (result.unitsCount <= MINIMUM_UNITS) {
            "${result.unitsCount} unit(s), within minimum"
        } else {
            "${result.unitsCount} units, +${result.extra} beyond minimum"
        }
        tvBreakdown.text = "Regular ₱${result.regularFare} • Student/Senior ₱${result.reducedFare} • $unitNote"

        val paymentText = etPayment.text.toString().trim()
        if (paymentText.isEmpty()) {
            tvChangeResult.text = "—"
            tvAutoSequence.text = "Ilagay ang binayad para makita ang sukli."
            FloatingService.instance?.updateSavedSequence(emptyList(), "Ilagay ang bayad")
            return
        }

        val paymentCents = parsePaymentCents(paymentText)
        if (paymentCents == null) {
            tvChangeResult.text = "Invalid na halaga"
            tvAutoSequence.text = "—"
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
            tvAutoSequence.text = "—"
            FloatingService.instance?.updateSavedSequence(emptyList(), "Kulang ang bayad")
            if (showToast) Toast.makeText(this, "Kulang ang bayad para sa fare", Toast.LENGTH_SHORT).show()
            return
        }

        val changeCents = paymentCents - totalCents
        tvChangeResult.text = "₱${formatPesoCents(changeCents)}"

        val plan = denominationPlan(changeCents)
        if (plan == null) {
            tvAutoSequence.text = "Centavo o sobrang daming taps. Manu-manong ibigay ang eksaktong sukli."
            FloatingService.instance?.updateSavedSequence(emptyList(), "Manu-manong sukli")
            if (showToast) Toast.makeText(this, "Ibigay nang manu-mano ang eksaktong sukli; hindi ito mare-represent ng kasalukuyang targets.", Toast.LENGTH_LONG).show()
            return
        }

        val sequence = plan.map { it.toString() } + "CHECK"
        tvAutoSequence.text = if (plan.isEmpty()) {
            "Exact payment · ✓ lang"
        } else {
            "${plan.joinToString(" + ")} → ✓"
        }

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
        tvFareResult.text = "—"
        tvBreakdown.text = message
        tvChangeResult.text = "—"
        tvAutoSequence.text = "—"
        FloatingService.instance?.updateSavedSequence(emptyList(), "Invalid na detalye")
    }

    private fun resetTrip() {
        suppressRecalc = true
        tvRegularCount.text = "1"
        tvStudentCount.text = "0"
        tvSeniorCount.text = "0"
        etPayment.setText("")
        spPickup.setSelection(0)
        spDropoff.setSelection(currentBarangays.lastIndex)
        suppressRecalc = false
        tvRegularCount.contentDescription = "Regular passengers: 1"
        tvStudentCount.contentDescription = "Student passengers: 0"
        tvSeniorCount.contentDescription = "Senior passengers: 0"
        recalcAndSave(showToast = false)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
