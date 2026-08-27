package com.coffeeledger.app

import com.coffeeledger.app.data.sample.SampleData
import com.coffeeledger.app.domain.analytics.TimeRanges
import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.query.TransactionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class TransactionFilterTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val now = System.currentTimeMillis()
    private val txns = SampleData.transactions(now, zone)

    @Test
    fun `no filter returns everything`() {
        assertEquals(txns.size, TransactionFilter().apply(txns).size)
        assertFalse(TransactionFilter().isActive)
    }

    @Test
    fun `spent excludes credits and transfers`() {
        val spent = TransactionFilter(flow = TransactionFilter.Flow.SPENT).apply(txns)
        assertTrue(spent.all { it.direction == Direction.DEBIT && !it.isTransfer })
        assertTrue(spent.isNotEmpty())
    }

    @Test
    fun `transfers are their own view`() {
        val transfers = TransactionFilter(flow = TransactionFilter.Flow.TRANSFERS).apply(txns)
        assertTrue(transfers.isNotEmpty())
        assertTrue(transfers.all { it.isTransfer })
    }

    @Test
    fun `category and source combine with and`() {
        val filter = TransactionFilter(
            categoryIds = setOf(Category.GROCERIES.id),
            sourceApps = setOf("PhonePe"),
        )
        val result = filter.apply(txns)
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.category == Category.GROCERIES && it.sourceApp == "PhonePe" })
        assertEquals(2, filter.activeCount)
    }

    @Test
    fun `free text searches merchant and category`() {
        assertTrue(TransactionFilter(query = "blinkit").apply(txns).isNotEmpty())
        assertTrue(TransactionFilter(query = "GROCER").apply(txns).isNotEmpty())
        assertTrue(TransactionFilter(query = "no such merchant").apply(txns).isEmpty())
    }

    @Test
    fun `the review filter narrows to flagged entries`() {
        val flagged = txns.first().copy(id = "flagged", needsReview = true)
        val filter = TransactionFilter(onlyNeedsReview = true)
        val result = filter.apply(txns + flagged)
        assertEquals(listOf("flagged"), result.map { it.id })
        assertEquals(1, filter.activeCount)
    }

    @Test
    fun `a date range narrows to that window`() {
        val lastMonth = TimeRanges.previousMonth(now, zone)
        val result = TransactionFilter(range = lastMonth).apply(txns)
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.occurredAt in lastMonth })
    }
}
