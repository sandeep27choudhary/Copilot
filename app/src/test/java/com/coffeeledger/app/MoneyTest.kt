package com.coffeeledger.app

import com.coffeeledger.app.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `parses plain and grouped amounts`() {
        assertEquals(120_000_00L, Money.parseAmount("1,20,000"))
        assertEquals(120_000_00L, Money.parseAmount("120000"))
        assertEquals(74_200L, Money.parseAmount("742.00"))
        assertEquals(74_250L, Money.parseAmount("742.5"))
        assertEquals(100L, Money.parseAmount("1"))
    }

    @Test
    fun `rejects text that is not an amount`() {
        assertNull(Money.parseAmount(""))
        assertNull(Money.parseAmount("abc"))
        assertNull(Money.parseAmount("12-34"))
    }

    @Test
    fun `formats with indian digit grouping`() {
        assertEquals("₹61,937", Money.format(61_937_00L))
        assertEquals("₹1,20,000", Money.format(1_20_000_00L))
        assertEquals("₹3,00,000", Money.format(3_00_000_00L))
        assertEquals("₹1,00,00,000", Money.format(1_00_00_000_00L))
        assertEquals("₹742", Money.format(74_200L))
        assertEquals("₹0", Money.format(0L))
    }

    @Test
    fun `keeps paise when they are not zero`() {
        assertEquals("₹742.50", Money.format(74_250L))
        assertEquals("₹742.00", Money.format(74_200L, withDecimals = true))
    }

    @Test
    fun `formats negatives with the sign before the symbol`() {
        assertEquals("-₹61,937", Money.format(-61_937_00L))
    }

    @Test
    fun `compact form uses indian scale words`() {
        assertEquals("₹78.6K", Money.formatCompact(78_632_00L))
        assertEquals("₹1.2L", Money.formatCompact(1_20_000_00L))
        assertEquals("₹2.4Cr", Money.formatCompact(2_40_00_000_00L))
        assertEquals("₹742", Money.formatCompact(74_200L))
    }
}
