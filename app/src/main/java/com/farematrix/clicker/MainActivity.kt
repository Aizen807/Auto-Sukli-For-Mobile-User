package com.farematrix.clicker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private data class Route(val label: String, val units: List<List<String>>)

    private val routes = listOf(
        Route("Balagtas ↔ Bulakan", listOf(
            listOf("Bagumbayan", "San Jose"), listOf("Matungao"),
            listOf("Panginay Guiguinto"), listOf("Panginay Balagtas"), listOf("Wawa")
        )),
        Route("Guiguinto ↔ Bulakan", listOf(
            listOf("Bagumbayan", "San Jose"), listOf("Matungao"), listOf("Tuktukan")
        )),
        Route("Malolos ↔ Bulakan", listOf(
            listOf("Bagumbayan", "San Jose"), listOf("Maysantol"), listOf("San Nicolas"),
            listOf("Pitpitan"), listOf("Mambog"), listOf("Matimbo"), listOf("Panasahan"),
            listOf("Bagna"), listOf("Atlag"), listOf("San Juan", "Sto. Rosario")
        ))
    )

    private val passengerTypes = listOf("Regular", "Student", "Senior")
    private lateinit var statusText: TextView
    private lateinit var fareResult: TextView
    private lateinit var breakdownResult: TextView
    private lateinit var changeResult: TextView
    private lateinit var routeSpinner: Spinner
    private lateinit var pickupSpinner: Spinner
    private lateinit var dropoffSpinner: Spinner
    private lateinit var passengerTypeSpinner: Spinner
    private lateinit var passengerCountInput: EditText
    private lateinit var paymentInput: EditText
    private lateinit var calculateButton: Button
    private var playingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        fareResult = findViewById(R.id.tvFareResult)
        breakdownResult = findViewById(R.id.tvBreakdown)
        changeResult = findViewById(R.id.tvChangeResult)
        routeSpinner = findViewById(R.id.spRoute)
        pickupSpinner = findViewById(R.id.spPickup)
        dropoffSpinner = findViewById(R.id.spDropoff)
        passengerTypeSpinner = findViewById(R.id.spPassengerType)
        passengerCountInput = findViewById(R.id.etPassengerCount)
        paymentInput = findViewById(R.id.etPayment)
        calculateButton = findViewById(R.id.btnCalculateAndGive)

        setupSpinner(routeSpinner, routes.map { it.label })
        setupSpinner(passengerTypeSpinner, passengerTypes)
        updateStops(0)

        routeSpinner.setOnItemSelectedListener(SimpleItemSelectedListener { updateStops(it) })
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
        calculateButton.setOnClickListener { calculateAndGiveChange() }
        findViewById<Button>(R.id.btnReset).setOnClickListener { resetTrip() }
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

    private fun setupSpinner(spinner: Spinner, values: List<String>) {
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)
    }

    private fun updateStops(routeIndex: Int) {
        if (routeIndex !in routes.indices) return
        val stops = routes[routeIndex].units.flatMap { it }
        setupSpinner(pickupSpinner, stops)
        setupSpinner(dropoffSpinner, stops)
        if (stops.size > 1) dropoffSpinner.setSelection(stops.lastIndex)
    }

    private fun updateStatus() {
        val accessibilityOn = AutoClickService.instance != null
        val overlayOn = Settings.canDrawOverlays(this)
        statusText.text = "Accessibility: ${if (accessibilityOn) "✅ ON" else "❌ OFF"}\n" +
            "Overlay: ${if (overlayOn) "✅ ON" else "❌ OFF"}"
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun calculateAndGiveChange() {
        val route = routes.getOrNull(routeSpinner.selectedItemPosition) ?: return
        val pickupIndex = stopIndex(route, pickupSpinner.selectedItem?.toString())
        val dropoffIndex = stopIndex(route, dropoffSpinner.selectedItem?.toString())
        val passengerCount = passengerCountInput.text.toString().toIntOrNull()
        val payment = paymentInput.text.toString().toDoubleOrNull()

        if (pickupIndex < 0 || dropoffIndex < 0) {
            showError("Pumili ng valid na pickup at drop-off.")
            return
        }
        if (pickupIndex == dropoffIndex) {
            showError("Magkaiba dapat ang pickup at drop-off.")
            return
        }
        if (passengerCount == null || passengerCount <= 0) {
            showError("Ilagay ang bilang ng pasahero.")
            return
        }
        if (payment == null || payment < 0) {
            showError("Ilagay ang tamang payment amount.")
            return
        }

        val units = abs(pickupIndex - dropoffIndex) + 1
        val extraUnits = (units - 4).coerceAtLeast(0)
        val isReduced = passengerTypeSpinner.selectedItemPosition != 0
        val fareEach = (if (isReduced) 11 else 13) + extraUnits * 2
        val totalFare = fareEach * passengerCount
        val change = payment - totalFare

        fareResult.text = "Fare: ₱$totalFare"
        val direction = if (dropoffIndex > pickupIndex) {
            "${route.label.substringBefore(" ↔")} → Bulakan"
        } else {
            "Bulakan → ${route.label.substringBefore(" ↔")}"
        }
        breakdownResult.text = "$direction\n$units unit(s), ₱$fareEach x $passengerCount " +
            "${passengerTypeSpinner.selectedItem} passenger(s)"

        if (change < 0) {
            changeResult.setTextColor(getColor(android.R.color.holo_red_light))
            changeResult.text = "Kulang: ₱${formatMoney(-change)}"
            Toast.makeText(this, "Kulang pa ng ₱${formatMoney(-change)}", Toast.LENGTH_LONG).show()
            return
        }

        changeResult.setTextColor(getColor(android.R.color.holo_green_light))
        changeResult.text = if (change == 0.0) "Sukli: ₱0 (eksakto)" else "Sukli: ₱${formatMoney(change)}"
        if (change == 0.0) return
        if (change != change.toInt().toDouble()) {
            showError("Ang auto sukli ay para sa whole-peso amounts lamang.")
            return
        }

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

        val amount = change.toInt()
        val sequence = makeChangeSequence(amount)
        if (sequence.isEmpty()) {
            showError("Hindi mabuo ang sukli gamit ang ₱50, ₱20, ₱10, ₱5, ₱1 targets.")
            return
        }
        calculateButton.isEnabled = false
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
                calculateButton.isEnabled = true
                playingJob = null
            }
        }
    }

    private fun stopIndex(route: Route, name: String?): Int =
        route.units.indexOfFirst { name != null && name in it }

    private fun makeChangeSequence(amount: Int): List<String> {
        var remaining = amount
        val sequence = mutableListOf<String>()
        for (coin in listOf(50, 20, 10, 5, 1)) {
            while (remaining >= coin) {
                sequence.add(coin.toString())
                remaining -= coin
            }
        }
        return if (remaining == 0) sequence + "CHECK" else emptyList()
    }

    private fun formatMoney(value: Double): String =
        if (value == value.toInt().toDouble()) value.toInt().toString() else "%.2f".format(value)

    private fun showError(message: String) {
        changeResult.setTextColor(getColor(android.R.color.holo_red_light))
        changeResult.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun resetTrip() {
        routeSpinner.setSelection(0)
        updateStops(0)
        passengerTypeSpinner.setSelection(0)
        passengerCountInput.setText("1")
        paymentInput.text.clear()
        fareResult.text = "Fare: ₱0"
        breakdownResult.text = "Pumili ng trip details."
        changeResult.setTextColor(getColor(android.R.color.holo_green_light))
        changeResult.text = "Sukli: ₱0"
    }

    private class SimpleItemSelectedListener(private val callback: (Int) -> Unit) :
        android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
            callback(position)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }
}
