package com.farematrix.clicker

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max

internal const val MINIMUM_UNITS = 4
private const val BASE_REGULAR_FARE = 14L
private const val BASE_REDUCED_FARE = 12L
private const val EXTRA_FARE_PER_UNIT = 2L
private const val MAX_AUTOMATIC_TAPS = 50L

internal data class FareResult(
    val unitsCount: Int,
    val extra: Int,
    val regularFare: Long,
    val reducedFare: Long,
    val total: Long
)

/** Calculates fares in whole pesos while using Long arithmetic for passenger totals. */
internal fun calculateFare(
    fromIdx: Int,
    toIdx: Int,
    regular: Long,
    student: Long,
    senior: Long
): FareResult {
    require(regular >= 0L && student >= 0L && senior >= 0L) {
        "Passenger counts must be non-negative"
    }

    val unitsCount = abs(fromIdx - toIdx) + 1
    val extra = max(0, unitsCount - MINIMUM_UNITS)
    val regularFare = BASE_REGULAR_FARE + extra.toLong() * EXTRA_FARE_PER_UNIT
    val reducedFare = BASE_REDUCED_FARE + extra.toLong() * EXTRA_FARE_PER_UNIT
    val reducedPassengers = Math.addExact(student, senior)
    val regularTotal = Math.multiplyExact(regular, regularFare)
    val reducedTotal = Math.multiplyExact(reducedPassengers, reducedFare)
    val total = Math.addExact(regularTotal, reducedTotal)

    return FareResult(unitsCount, extra, regularFare, reducedFare, total)
}

/** Parses a non-negative amount with at most two decimal places to integer cents. */
internal fun parsePaymentCents(raw: String): Long? {
    val value = raw.trim()
    if (value.isEmpty() || !value.matches(Regex("(?:\\d+(?:\\.\\d{0,2})?|\\.\\d{1,2})"))) {
        return null
    }

    return try {
        BigDecimal(value)
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
    } catch (_: NumberFormatException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}

/** Formats cents without unnecessary trailing zeroes (e.g. 1300 -> "13", 1349 -> "13.49"). */
internal fun formatPesoCents(cents: Long): String {
    require(cents >= 0L) { "Amount must be non-negative" }
    val pesos = cents / 100L
    val centavos = cents % 100L
    return if (centavos == 0L) pesos.toString() else "$pesos.${centavos.toString().padStart(2, '0')}"
}

/**
 * Returns the whole-peso target sequence, or null if centavos are present or
 * the sequence would require too many individual taps. Never rounds change.
 */
internal fun denominationPlan(changeCents: Long): List<Int>? {
    if (changeCents < 0L || changeCents % 100L != 0L) return null

    var remaining = changeCents / 100L
    var tapCount = 0L
    val plan = mutableListOf<Int>()
    for (denomination in intArrayOf(50, 20, 10, 5, 1)) {
        val count = remaining / denomination
        if (tapCount + count > MAX_AUTOMATIC_TAPS) return null
        repeat(count.toInt()) { plan += denomination }
        tapCount += count
        remaining %= denomination
    }
    return if (remaining == 0L) plan else null
}
/** Formats cents as pesos, omitting decimals for whole-peso amounts (e.g. 1300 -> "13", 1349 -> "13.49"). */
