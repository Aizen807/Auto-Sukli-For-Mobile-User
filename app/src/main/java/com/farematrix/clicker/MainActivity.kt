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

    private lateinit var statusText: TextView
    private lateinit var fareResult: TextView
    private lateinit var breakdownResult: TextView
    private lateinit var changeResult: TextView
    private lateinit var autoSequenceResult: TextView
    private lateinit var routeSpinner: Spinner
    private lateinit var pickupSpinner: Spinner
    private lateinit var dropoffSpinner: Spinner
    private lateinit var regularCountInput: EditText
    private lateinit var studentCountInput: EditText
    private lateinit var seniorCountInput: EditText
    private lateinit var paymentInput: EditText
    private lateinit var calculateButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        fareResult = findViewById(R.id.tvFareResult)
        breakdownResult = findViewById(R.id.tvBreakdown)
        changeResult = findViewById(R.id.tvChangeResult)
        autoSequenceResult = findViewById(R.id.tvAutoSequence)
        routeSpinner = findViewById(R.id.spRoute)
        pickupSpinner = findViewById(R.id.spPickup)
        dropoffSpinner = findViewById(R.id.spDropoff)
        regularCountInput = findViewById(R.id.etRegularCount)
        studentCountInput = findViewById(R.id.etStudentCount)
        seniorCountInput = findViewById(R.id.etSeniorCount)
        paymentInput = findViewById(R.id.etPayment)
        calculateButton = findViewById(R.id.btnCalculateAndGive)

        setupSpinner(routeSpinner, routes.map { it.label })
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
        val regularCount = regularCountInput.text.toString().toIntOrNull() ?: 0
        val studentCount = studentCountInput.text.toString().toIntOrNull() ?: 0
        val seniorCount = seniorCountInput.text.toString().toIntOrNull() ?: 0
        val payment = paymentInput.text.toString().toDoubleOrNull()

        if (pickupIndex < 0 || dropoffIndex < 0) {
            showError("Pumili ng valid na pickup at drop-off.")
            return
        }
        if (pickupIndex == dropoffIndex) {
            showError("Magkaiba dapat ang pickup at drop-off.")
            return
        }
        if (regularCount + studentCount + seniorCount <= 0 ||
            regularCount < 0 || studentCount < 0 || seniorCount < 0) {
            showError("Ilagay ang bilang ng pasahero.")
            return
        }
        if (payment == null || payment < 0) {
            showError("Ilagay ang tamang payment amount.")
            return
        }

        val units = abs(pickupIndex - dropoffIndex) + 1
        val extraUnits = (units - 4).coerceAtLeast(0)
        val regularFare = 13 + extraUnits * 2
        val reducedFare = 11 + extraUnits * 2
        val totalPassengers = regularCount + studentCount + seniorCount
        val totalFare = regularCount * regularFare + (studentCount + seniorCount) * reducedFare
        val change = payment - totalFare

        fareResult.text = "Fare: ₱$totalFare"
        val direction = if (dropoffIndex > pickupIndex) {
            "${route.label.substringBefore(" ↔")} → Bulakan"
        } else {
            "Bulakan → ${route.label.substringBefore(" ↔")}"
        }
        val parts = mutableListOf<String>()
        if (regularCount > 0) parts.add("$regularCount regular × ₱$regularFare")
        if (studentCount > 0) parts.add("$studentCount student × ₱$reducedFare")
        if (seniorCount > 0) parts.add("$seniorCount senior × ₱$reducedFare")
        breakdownResult.text = "$direction\n$units unit(s), $totalPassengers passenger(s)\n${parts.joinToString(" + ")}"

        if (change < 0) {
            changeResult.setTextColor(getColor(android.R.color.holo_red_light))
            changeResult.text = "Kulang: ₱${formatMoney(-change)}"
            autoSequenceResult.text = "Status: Walang auto sukli — kulang ang bayad"
            Toast.makeText(this, "Kulang pa ng ₱${formatMoney(-change)}", Toast.LENGTH_LONG).show()
            return
        }

        changeResult.setTextColor(getColor(android.R.color.holo_green_light))
        changeResult.text = if (change == 0.0) "Sukli: ₱0 (eksakto)" else "Sukli: ₱${formatMoney(change)}"
        if (change == 0.0) {
            autoSequenceResult.text = "Status: Eksakto — walang ita-tap na sukli"
            return
        }
        if (change != change.toInt().toDouble()) {
            autoSequenceResult.text = "Status: Whole-peso targets lamang"
            showError("Ang auto sukli ay para sa whole-peso amounts lamang.")
            return
        }

        val amount = change.toInt()
        val sequence = makeChangeSequence(amount)
        if (sequence.isEmpty()) {
            autoSequenceResult.text = "Status: Hindi mabuo ang sukli"
            showError("Hindi mabuo ang sukli gamit ang ₱50, ₱20, ₱10, ₱5, ₱1 targets.")
            return
        }
        val denominations = sequence.dropLast(1)
        autoSequenceResult.text = "Status: ${denominations.joinToString(" + ")}\n" +
            "Auto-click: highest to lowest, then ✓"

        getSharedPreferences("fare_session", MODE_PRIVATE).edit()
            .putString("pending_sequence", sequence.joinToString(","))
            .putInt("pending_change", amount)
            .apply()
        Toast.makeText(this, "Sukli saved. Pindutin ang blue ▶ Play button.", Toast.LENGTH_LONG).show()
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
        regularCountInput.setText("1")
        studentCountInput.text.clear()
        seniorCountInput.text.clear()
        paymentInput.text.clear()
        fareResult.text = "Fare: ₱0"
        breakdownResult.text = "Pumili ng trip details."
        changeResult.setTextColor(getColor(android.R.color.holo_green_light))
        changeResult.text = "Sukli: ₱0"
        autoSequenceResult.text = "Status: —"
    }

    private class SimpleItemSelectedListener(private val callback: (Int) -> Unit) :
        android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
            callback(position)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }
}
