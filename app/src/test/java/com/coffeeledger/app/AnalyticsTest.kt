package com.coffeeledger.app

import com.coffeeledger.app.data.sample.SampleData
import com.coffeeledger.app.domain.analytics.AnalyticsEngine
import com.coffeeledger.app.domain.analytics.FinancialHealth
import com.coffeeledger.app.domain.analytics.InsightGenerator
import com.coffeeledger.app.domain.analytics.InsightKind
import com.coffeeledger.app.domain.analytics.TimeRanges
import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.PaymentMethod
import com.coffeeledger.app.domain.model.Txn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class AnalyticsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val now = System.currentTimeMillis()
    private val txns = SampleData.transactions(now, zone)
    private val progress = AnalyticsEngine.progressOf(SampleData.trackers(), txns, now, zone)

    @Test
    fun `balance is opening balances plus every signed entry`() {
        val opening = SampleData.accounts().filter { it.includeInTotals }.sumOf { it.openingBalanceMinor }
        val expected = opening + txns.sumOf { it.signedMinor }
        assertEquals(expected, AnalyticsEngine.totalBalance(txns, opening))
    }

    @Test
    fun `median ignores a single outlier`() {
        assertEquals(300L, AnalyticsEngine.medianOf(listOf(100L, 300L, 500L, 1_000_000L, 200L)))
        assertEquals(0L, AnalyticsEngine.medianOf(emptyList()))
    }

    @Test
    fun `an unusually large transaction is flagged`() {
        val range = TimeRanges.currentMonth(now, zone)
        val outlier = Txn(
            id = "outlier",
            occurredAt = now - 86_400_000L,
            amountMinor = 45_000_00L,
            direction = Direction.DEBIT,
            merchant = "Croma",
            category = Category.SHOPPING,
            paymentMethod = PaymentMethod.CARD,
        )
        val flagged = AnalyticsEngine.unusualTransactions(txns + outlier, range)
        assertTrue(flagged.any { it.txn.id == "outlier" })
    }

    @Test
    fun `insights are ranked and capped`() {
        val insights = InsightGenerator.generate(txns, progress, now, zone, limit = 6)
        assertTrue(insights.size <= 6)
        assertTrue(insights.isNotEmpty())
        assertEquals(insights.sortedByDescending { it.priority }, insights)
        assertTrue(insights.all { it.evidence.isNotBlank() })
    }

    @Test
    fun `the groceries tracker nearing its limit produces an insight`() {
        val insights = InsightGenerator.generate(txns, progress, now, zone, limit = 10)
        assertTrue(
            insights.any {
                it.kind == InsightKind.TRACKER_NEAR_LIMIT && it.title.contains("Groceries")
            },
        )
    }

    @Test
    fun `the health score explains every component it used`() {
        val health = FinancialHealth.evaluate(txns, progress, now, zone)
        assertTrue(health.score in 0..100)
        assertTrue(health.hasData)
        assertEquals(5, health.components.size)
        assertTrue(health.components.all { it.explanation.isNotBlank() })
        assertEquals(100, health.components.sumOf { it.weight })
    }

    @Test
    fun `an empty ledger scores nothing rather than guessing`() {
        val health = FinancialHealth.evaluate(emptyList(), emptyList(), now, zone)
        assertEquals(0, health.score)
        assertEquals("Not enough data", health.label)
    }

    @Test
    fun `a spending tracker ignores transfers`() {
        val monthly = progress.first { it.tracker.id == "trk-monthly" }
        val range = TimeRanges.currentMonth(now, zone)
        assertEquals(AnalyticsEngine.summarize(txns, range).spendMinor, monthly.currentMinor)
    }
}
