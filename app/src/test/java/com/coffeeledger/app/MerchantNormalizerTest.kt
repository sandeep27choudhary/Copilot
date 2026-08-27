package com.coffeeledger.app

import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.normalize.MerchantNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantNormalizerTest {

    @Test
    fun `collapses every blinkit spelling to one name`() {
        listOf("BLINKIT", "Blinkit Commerce Pvt Ltd", "blinkit grocery", "BLINKIT*ORDER").forEach {
            assertEquals("Blinkit", MerchantNormalizer.normalize(it))
        }
    }

    @Test
    fun `collapses common food and shopping merchants`() {
        assertEquals("Zomato", MerchantNormalizer.normalize("ZOMATO LIMITED"))
        assertEquals("Swiggy", MerchantNormalizer.normalize("Bundl Technologies Pvt Ltd"))
        assertEquals("Amazon", MerchantNormalizer.normalize("AMAZON SELLER SERVICES"))
        assertEquals("Flipkart", MerchantNormalizer.normalize("FLIPKART INTERNET PVT LTD"))
        assertEquals("Uber", MerchantNormalizer.normalize("UBER INDIA SYSTEMS"))
    }

    @Test
    fun `does not match a token inside a longer word`() {
        assertEquals("Amazonite Jewellers", MerchantNormalizer.normalize("AMAZONITE JEWELLERS"))
        assertEquals("Solaris Power", MerchantNormalizer.normalize("SOLARIS POWER"))
    }

    @Test
    fun `suggests the catalog category`() {
        assertEquals(Category.GROCERIES, MerchantNormalizer.suggestedCategory("BLINKIT COMMERCE"))
        assertEquals(Category.FOOD, MerchantNormalizer.suggestedCategory("zomato"))
        assertEquals(Category.SUBSCRIPTIONS, MerchantNormalizer.suggestedCategory("NETFLIX.COM"))
        assertEquals(null, MerchantNormalizer.suggestedCategory("SOME LOCAL SHOP"))
    }

    @Test
    fun `strips legal noise from unknown merchants`() {
        assertEquals("Sharma Kirana", MerchantNormalizer.normalize("SHARMA KIRANA PVT LTD INDIA"))
        assertEquals("Greenleaf Cafe", MerchantNormalizer.normalize("greenleaf cafe 883920114"))
    }

    @Test
    fun `identifies payment rails separately from merchants`() {
        assertTrue(MerchantNormalizer.isPaymentApp("PHONEPE PRIVATE LIMITED"))
        assertTrue(MerchantNormalizer.isPaymentApp("google pay"))
        assertFalse(MerchantNormalizer.isPaymentApp("ZOMATO"))
    }

    @Test
    fun `never returns an empty name`() {
        assertEquals("Unknown", MerchantNormalizer.normalize(null))
        assertEquals("Unknown", MerchantNormalizer.normalize("   "))
        assertEquals("Unknown", MerchantNormalizer.normalize("!!!"))
    }
}
