package com.coffeeledger.app

import com.coffeeledger.app.data.sample.SampleData
import com.coffeeledger.app.domain.analytics.AnalyticsEngine
import com.coffeeledger.app.domain.analytics.TimeRanges
import com.coffeeledger.app.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * The sample ledger is what a new install shows, so its headline figures are pinned to the
 * numbers in the product brief. If a sample entry changes, this test says so.
 */
class SampleLedgerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val now = System.currentTimeMillis()
    private val txns = SampleData.transactions(now, zone)
    private val thisMonth = TimeRanges.currentMonth(now, zone)

    @Test
    fun `current month matches the dashboard figures in the brief`() {
        val summary = AnalyticsEngine.summarize(txns, thisMonth)
        assertEquals(78_632_00L, summary.spendMinor)
        assertEquals(16_695_00L, summary.incomeMinor)
        assertEquals(61_937_00L, -summary.netMinor)
        assertEquals(45_000_00L, summary.transferOutMinor)
    }

    @Test
    fun `tracker categories match the meters in the brief`() {
        val byCategory = AnalyticsEngine.categoryTotals(txns, thisMonth).associateBy { it.category }
        assertEquals(14_005_00L, byCategory[Category.GROCERIES]?.amountMinor)
        assertEquals(4_180_00L, byCategory[Category.FOOD]?.amountMinor)
        assertEquals(3_200_00L, byCategory[Category.SHOPPING]?.amountMinor)
        assertEquals(1_800_00L, byCategory[Category.TRANSPORT]?.amountMinor)
    }

    @Test
    fun `trackers report the percentages in the brief`() {
        val progress = AnalyticsEngine.progressOf(SampleData.trackers(), txns, now, zone)
            .associateBy { it.tracker.id }

        assertEquals(79, progress.getValue("trk-monthly").percent) // 78,632 of 1,00,000
        assertEquals(70, progress.getValue("trk-food").percent)
        assertEquals(93, progress.getValue("trk-groceries").percent)
        assertEquals(67, progress.getValue("trk-savings").percent)
        assertEquals(40, progress.getValue("trk-emergency").percent)
    }

    @Test
    fun `transfers are excluded from spending`() {
        val summary = AnalyticsEngine.summarize(txns, thisMonth)
        val allDebits = AnalyticsEngine.inRange(txns, thisMonth)
            .filter { it.direction == com.coffeeledger.app.domain.model.Direction.DEBIT }
            .sumOf { it.amountMinor }
        assertEquals(summary.spendMinor + summary.transferOutMinor, allDebits)
    }

    @Test
    fun `merchants arrive normalized`() {
        val merchants = txns.map { it.merchant }.toSet()
        assertTrue("Blinkit" in merchants)
        assertTrue("Zomato" in merchants)
        assertTrue("Swiggy" in merchants)
        assertTrue("Amazon" in merchants)
        assertTrue(merchants.none { it.contains("PVT", ignoreCase = true) })
    }

    @Test
    fun `three months of history are present for trends`() {
        val trend = AnalyticsEngine.monthlyTrend(txns, 3, now, zone)
        assertEquals(3, trend.size)
        assertTrue(trend.all { it.spendMinor > 0L })
    }

    @Test
    fun `recurring subscriptions are detected`() {
        val recurring = AnalyticsEngine.recurringPayments(txns)
        val names = recurring.map { it.merchant }
        assertTrue("Netflix" in names)
        assertTrue("Spotify" in names)
        assertTrue(recurring.first { it.merchant == "Netflix" }.isSubscriptionLike)
    }
}
