package com.farematrix.clicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FareCalculatorTest {
    @Test
    fun calculatesMixedPassengerFareAtMinimumUnits() {
        val fare = calculateFare(fromIdx = 0, toIdx = 3, regular = 1, student = 1, senior = 0)

        assertEquals(4, fare.unitsCount)
        assertEquals(13L, fare.regularFare)
        assertEquals(11L, fare.reducedFare)
        assertEquals(24L, fare.total)
    }

    @Test
    fun addsSurchargeForUnitsBeyondMinimum() {
        val fare = calculateFare(fromIdx = 0, toIdx = 5, regular = 2, student = 1, senior = 2)

        assertEquals(6, fare.unitsCount)
        assertEquals(2, fare.extra)
        assertEquals(17L, fare.regularFare)
        assertEquals(15L, fare.reducedFare)
        assertEquals(79L, fare.total)
    }

    @Test
    fun rejectsNegativePassengerCounts() {
        assertThrows(IllegalArgumentException::class.java) {
            calculateFare(fromIdx = 0, toIdx = 0, regular = 1, student = -1, senior = 0)
        }
    }

    @Test
    fun checkedFareArithmeticRejectsOverflow() {
        assertThrows(ArithmeticException::class.java) {
            calculateFare(fromIdx = 0, toIdx = 0, regular = Long.MAX_VALUE, student = 0, senior = 0)
        }
    }

    @Test
    fun parsesMoneyExactlyToCentavos() {
        assertEquals(1_349L, parsePaymentCents("13.49"))
        assertEquals(1_350L, parsePaymentCents("13.5"))
        assertEquals(5_000L, parsePaymentCents("50"))
        assertEquals("13.49", formatPesoCents(1_349L))
        assertEquals("13", formatPesoCents(1_300L))
    }

    @Test
    fun rejectsBlankNegativeMalformedOrOverPrecisePayments() {
        assertNull(parsePaymentCents(""))
        assertNull(parsePaymentCents("  "))
        assertNull(parsePaymentCents("-1.00"))
        assertNull(parsePaymentCents("13.499"))
        assertNull(parsePaymentCents("NaN"))
        assertNull(parsePaymentCents("1e6"))
        assertNull(parsePaymentCents("999999999999999999999999"))
    }

    @Test
    fun fractionalChangeIsNeverRoundedIntoAWholePesoPlan() {
        val fareCents = 1_300L
        val payments = listOf(1_300L, 1_301L, 1_349L, 1_350L)
        val change = payments.map { it - fareCents }

        assertEquals(emptyList<Int>(), denominationPlan(change[0]))
        assertNull(denominationPlan(change[1]))
        assertNull(denominationPlan(change[2]))
        assertNull(denominationPlan(change[3]))
    }

    @Test
    fun limitsAutomaticTapCount() {
        val maxPlan = denominationPlan(2_500L * 100L)
        assertEquals(50, maxPlan?.size)
        assertNull(denominationPlan(2_501L * 100L))
        assertNull(denominationPlan(-100L))
        assertTrue(denominationPlan(0L).isNullOrEmpty())
    }
}
