package com.coffeeledger.app

import com.coffeeledger.app.data.sample.SampleData
import com.coffeeledger.app.domain.advisor.AdvisorContext
import com.coffeeledger.app.domain.advisor.AdvisorIntent
import com.coffeeledger.app.domain.advisor.LocalAdvisor
import com.coffeeledger.app.domain.analytics.AnalyticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class LocalAdvisorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val now = System.currentTimeMillis()
    private val txns = SampleData.transactions(now, zone)
    private val context = AdvisorContext(
        txns = txns,
        trackerProgress = AnalyticsEngine.progressOf(SampleData.trackers(), txns, now, zone),
        now = now,
        accounts = SampleData.accounts(),
        zone = zone,
    )

    private fun ask(q: String) = LocalAdvisor.answer(q, context)

    @Test
    fun `answers where the money went`() {
        val answer = ask("Where did I spend the most?")
        assertEquals(AdvisorIntent.TOP_SPEND, answer.intent)
        assertTrue(answer.headline.startsWith("Rent"))
        assertTrue(answer.headline.contains("₹35,000"))
    }

    @Test
    fun `answers a category question with the tracker context`() {
        val answer = ask("How much did I spend on food?")
        assertEquals(AdvisorIntent.CATEGORY_SPEND, answer.intent)
        assertEquals("₹4,180", answer.headline)
        assertTrue(answer.detail.any { it.contains("Food") || it.contains("Zomato") })
    }

    @Test
    fun `answers a groceries question`() {
        val answer = ask("how much did I spend on groceries this month")
        assertEquals("₹14,005", answer.headline)
    }

    @Test
    fun `lists recurring expenses`() {
        val answer = ask("What are my recurring expenses?")
        assertEquals(AdvisorIntent.RECURRING, answer.intent)
        assertTrue(answer.headline.endsWith("a month"))
        assertTrue(answer.detail.first().contains("repeating payments"))
        assertTrue(answer.detail.any { it.contains("NoBroker") || it.contains("Groww") })
    }

    @Test
    fun `names the most used merchants`() {
        val answer = ask("Which merchants do I use most?")
        assertEquals(AdvisorIntent.TOP_MERCHANTS, answer.intent)
        assertTrue(answer.headline.contains("Blinkit") || answer.headline.contains("Zomato"))
    }

    @Test
    fun `compares this month with last month`() {
        val answer = ask("What changed compared with last month?")
        assertEquals(AdvisorIntent.COMPARE, answer.intent)
        assertTrue(answer.detail.first().contains("this month"))
    }

    @Test
    fun `answers an affordability question and marks it as planning`() {
        val answer = ask("Can I afford a ₹60,000 purchase?")
        assertEquals(AdvisorIntent.AFFORD, answer.intent)
        assertTrue(answer.isPlanning)
        assertTrue(answer.headline.contains("₹60,000"))
        assertTrue(answer.detail.any { it.contains("investment advice") })
    }

    @Test
    fun `asks for the amount when an affordability question omits it`() {
        val answer = ask("Can I afford it?")
        assertEquals(AdvisorIntent.AFFORD, answer.intent)
        assertTrue(answer.headline.contains("How much"))
    }

    @Test
    fun `suggests what to reduce`() {
        val answer = ask("What can I reduce next month?")
        assertEquals(AdvisorIntent.REDUCE, answer.intent)
        assertTrue(answer.isPlanning)
    }

    @Test
    fun `estimates how much can be saved`() {
        val answer = ask("How much can I save?")
        assertEquals(AdvisorIntent.SAVE, answer.intent)
        assertTrue(answer.isPlanning)
    }

    @Test
    fun `scopes a question to this week`() {
        val answer = ask("How much did I spend this week?")
        assertEquals(AdvisorIntent.PERIOD_SPEND, answer.intent)
        assertTrue(answer.detail.any { it.contains("this week") } || answer.headline == "Nothing recorded")
    }

    @Test
    fun `reports the balance`() {
        val answer = ask("What is my balance?")
        assertEquals(AdvisorIntent.BALANCE, answer.intent)
        assertTrue(answer.headline.startsWith("₹"))
    }

    @Test
    fun `every suggested question is understood`() {
        LocalAdvisor.suggestedQuestions.forEach { question ->
            val answer = ask(question)
            assertTrue("unhandled: $question", answer.intent != AdvisorIntent.UNKNOWN)
        }
    }

    @Test
    fun `an unrelated question does not invent an answer`() {
        val answer = ask("What is the weather tomorrow?")
        assertEquals(AdvisorIntent.UNKNOWN, answer.intent)
    }

    @Test
    fun `parses shorthand amounts`() {
        assertEquals(60_000_00L, LocalAdvisor.extractAmount("can i afford a ₹60,000 purchase"))
        assertEquals(1_50_000_00L, LocalAdvisor.extractAmount("can i afford 1.5l"))
        assertEquals(45_000_00L, LocalAdvisor.extractAmount("can i afford a 45k laptop"))
    }
}
