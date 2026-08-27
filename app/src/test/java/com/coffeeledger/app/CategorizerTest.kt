package com.coffeeledger.app

import com.coffeeledger.app.domain.categorize.CategoryRule
import com.coffeeledger.app.domain.categorize.Categorizer
import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorizerTest {

    @Test
    fun `uses the merchant catalog for known merchants`() {
        assertEquals(
            Category.GROCERIES,
            Categorizer.categorize("BLINKIT COMMERCE PVT LTD", Direction.DEBIT, null).category,
        )
        assertEquals(
            Category.FOOD,
            Categorizer.categorize("ZOMATO LIMITED", Direction.DEBIT, null).category,
        )
    }

    @Test
    fun `falls back to keywords in the message`() {
        val result = Categorizer.categorize(
            merchantRaw = "ACME TECHNOLOGIES",
            direction = Direction.CREDIT,
            rawText = "INR 1,25,000 credited by NEFT SALARY for Aug",
        )
        assertEquals(Category.SALARY, result.category)
    }

    @Test
    fun `a user rule beats the catalog`() {
        val rule = Categorizer.ruleForMerchant("r1", "Blinkit", Category.OTHER_EXPENSE)
        val result = Categorizer.categorize("BLINKIT COMMERCE", Direction.DEBIT, null, listOf(rule))
        assertEquals(Category.OTHER_EXPENSE, result.category)
        assertTrue(result.reason.contains("Your rule"))
    }

    @Test
    fun `a keyword rule the user taught also applies`() {
        val rule = CategoryRule("r2", CategoryRule.MatchType.KEYWORD, "society maint", Category.RENT.id)
        val result = Categorizer.categorize("GREEN ACRES", Direction.DEBIT, "UPI to society maint aug", listOf(rule))
        assertEquals(Category.RENT, result.category)
    }

    @Test
    fun `movements between the user's own accounts are transfers`() {
        val result = Categorizer.categorize(
            merchantRaw = "SELF",
            direction = Direction.DEBIT,
            rawText = "Rs 25,000 transferred to own account XX9021",
            ownAccountTails = setOf("9021"),
            counterpartyTail = "9021",
        )
        assertTrue(result.isTransfer)
        assertEquals(Category.TRANSFER_OUT, result.category)
    }

    @Test
    fun `a transfer is detected even without a known tail`() {
        val result = Categorizer.categorize("SELF TRANSFER", Direction.CREDIT, "self transfer from savings")
        assertTrue(result.isTransfer)
        assertEquals(Category.TRANSFER_IN, result.category)
    }

    @Test
    fun `unknown debits fall back to other expenses and are not transfers`() {
        val result = Categorizer.categorize("SOME SHOP", Direction.DEBIT, "Rs 200 debited")
        assertEquals(Category.OTHER_EXPENSE, result.category)
        assertFalse(result.isTransfer)
    }

    @Test
    fun `credits never pick up a debit category from the catalog`() {
        val result = Categorizer.categorize("ZOMATO LIMITED", Direction.CREDIT, "Rs 300 credited refund")
        assertEquals(Category.REFUND, result.category)
    }
}
