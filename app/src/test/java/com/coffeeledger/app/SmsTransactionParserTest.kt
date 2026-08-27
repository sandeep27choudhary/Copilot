package com.coffeeledger.app

import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.PaymentMethod
import com.coffeeledger.app.domain.parse.ParseResult
import com.coffeeledger.app.domain.parse.RejectionReason
import com.coffeeledger.app.domain.parse.SmsMessage
import com.coffeeledger.app.domain.parse.SmsTransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTransactionParserTest {

    private val now = 1_756_200_000_000L // 26 Aug 2026, roughly

    private fun parse(body: String, sender: String = "VM-HDFCBK") =
        SmsTransactionParser.parse(SmsMessage(sender, body, now))

    private fun success(body: String, sender: String = "VM-HDFCBK") =
        (parse(body, sender) as? ParseResult.Success)?.transaction
            ?: error("expected a parsed transaction for: $body")

    private fun rejection(body: String, sender: String = "VM-HDFCBK") =
        (parse(body, sender) as? ParseResult.Rejected)?.reason
            ?: error("expected a rejection for: $body")

    // ------------------------------------------------------------ debits

    @Test
    fun `parses a classic upi debit`() {
        val txn = success(
            "Rs.742.00 debited from a/c XX4143 on 26-08-26 to BLINKIT COMMERCE PVT LTD " +
                "UPI Ref 552312345678. Avl Bal Rs.35,120.50 -HDFC Bank",
        )
        assertEquals(74_200L, txn.amountMinor)
        assertEquals(Direction.DEBIT, txn.direction)
        assertEquals("4143", txn.accountTail)
        assertEquals("552312345678", txn.reference)
        assertEquals(PaymentMethod.UPI, txn.paymentMethod)
        assertEquals("HDFC Bank", txn.institution)
        assertEquals(35_120_50L, txn.balanceMinor)
        assertTrue(txn.merchantRaw!!.contains("BLINKIT", ignoreCase = true))
    }

    @Test
    fun `does not mistake the available balance for the amount`() {
        val txn = success("Avl Bal Rs.35,120.50. Rs.742.00 debited from a/c XX4143 to BLINKIT")
        assertEquals(74_200L, txn.amountMinor)
    }

    @Test
    fun `parses a multiline phonepe style debit`() {
        val txn = success(
            "Sent Rs.350.00\nFrom HDFC Bank A/C x4143\nTo ZOMATO\nOn 24/08/26\nRef 123456789012\nUPI",
            sender = "VK-HDFCBK",
        )
        assertEquals(35_000L, txn.amountMinor)
        assertEquals(Direction.DEBIT, txn.direction)
        assertEquals("ZOMATO", txn.merchantRaw)
    }

    @Test
    fun `parses a card spend with an available limit trailer`() {
        val txn = success(
            "INR 3,200.00 spent on ICICI Bank Card XX7788 on 22-Aug-26 at AMAZON. " +
                "Avl Lmt: INR 96,800.00",
            sender = "AD-ICICIT",
        )
        assertEquals(320_000L, txn.amountMinor)
        assertEquals(Direction.DEBIT, txn.direction)
        assertEquals("7788", txn.accountTail)
        assertEquals(PaymentMethod.CARD, txn.paymentMethod)
        assertEquals("AMAZON", txn.merchantRaw)
        assertEquals("ICICI Bank", txn.institution)
    }

    @Test
    fun `parses a vpa counterparty`() {
        val txn = success(
            "Rs 1200.00 debited from your A/c XX1234 on 24-08-2026 to VPA swiggy.food@axisbank " +
                "Ref 456789123456. -Axis Bank",
            sender = "AX-AXISBK",
        )
        assertEquals(120_000L, txn.amountMinor)
        assertTrue(txn.merchantRaw!!.contains("swiggy", ignoreCase = true))
        assertEquals(PaymentMethod.UPI, txn.paymentMethod)
    }

    @Test
    fun `parses an atm withdrawal`() {
        val txn = success(
            "Rs.5000 withdrawn from A/c XXXXXX4143 at ATM on 20-08-2026. Avl Bal Rs.30,000",
        )
        assertEquals(500_000L, txn.amountMinor)
        assertEquals(Direction.DEBIT, txn.direction)
        assertEquals(PaymentMethod.ATM, txn.paymentMethod)
    }

    // ----------------------------------------------------------- credits

    @Test
    fun `parses a salary credit`() {
        val txn = success(
            "INR 1,25,000.00 credited to A/c XXXXXX1234 on 01-Aug-26 by NEFT from " +
                "ACME TECHNOLOGIES PAYROLL. Ref NEFT2608001. -ICICI Bank",
            sender = "AD-ICICIB",
        )
        assertEquals(1_25_00_000L, txn.amountMinor)
        assertEquals(Direction.CREDIT, txn.direction)
        assertEquals("1234", txn.accountTail)
        assertEquals(PaymentMethod.NEFT, txn.paymentMethod)
        assertTrue(txn.merchantRaw!!.contains("ACME", ignoreCase = true))
    }

    @Test
    fun `parses a refund credit`() {
        val txn = success("Rs.1,499.00 credited to your a/c XX4143 on 18-08-26 from AMAZON refund. UPI Ref 887766554433")
        assertEquals(149_900L, txn.amountMinor)
        assertEquals(Direction.CREDIT, txn.direction)
    }

    @Test
    fun `debit wins when a message describes both legs of a transfer`() {
        val txn = success(
            "Rs.45,000.00 debited from a/c XX4143 and credited to a/c XX1234 on 25-08-26. -HDFC Bank",
        )
        assertEquals(Direction.DEBIT, txn.direction)
        assertEquals(45_00_000L, txn.amountMinor)
    }

    // ---------------------------------------------------------- rejects

    @Test
    fun `rejects otp messages`() {
        assertEquals(
            RejectionReason.OTP_OR_VERIFICATION,
            rejection("123456 is your OTP for a transaction of Rs.5000 on HDFC Bank Card. Do not share."),
        )
    }

    @Test
    fun `rejects promotional loan offers`() {
        assertEquals(
            RejectionReason.PROMOTIONAL,
            rejection(
                "Congratulations! You are eligible for a pre-approved credit card with a limit of " +
                    "Rs 5,00,000 on your account. Apply now. T&C apply",
            ),
        )
    }

    @Test
    fun `rejects future mandate notices`() {
        assertEquals(
            RejectionReason.FUTURE_OR_MANDATE,
            rejection("Rs.599.00 will be debited from your a/c XX4143 on 01-09-26 towards NETFLIX autopay."),
        )
    }

    @Test
    fun `rejects failed transactions`() {
        assertEquals(
            RejectionReason.FAILED_TRANSACTION,
            rejection("Your payment of Rs.2,300 to SWIGGY has failed. Amount will be refunded."),
        )
    }

    @Test
    fun `rejects collect requests`() {
        assertEquals(
            RejectionReason.FUTURE_OR_MANDATE,
            rejection("RAHUL has requested Rs.500.00 via UPI. Approve in your app to pay."),
        )
    }

    @Test
    fun `rejects a plain balance enquiry`() {
        val reason = rejection("Your A/c XX4143 balance is Rs.35,120.50 as of 26-08-26. -HDFC Bank")
        assertTrue(reason == RejectionReason.NO_AMOUNT || reason == RejectionReason.NO_DIRECTION)
    }

    @Test
    fun `rejects marketing that never mentions money at all`() {
        assertEquals(
            RejectionReason.NOT_FINANCIAL,
            rejection("Get a personal loan of Rs 5,00,000 at 9% interest. Visit your nearest branch."),
        )
    }

    @Test
    fun `rejects non financial chatter`() {
        assertEquals(RejectionReason.NOT_FINANCIAL, rejection("Your parcel will arrive today between 4 and 6 pm."))
    }

    // ------------------------------------------------------- confidence

    @Test
    fun `a fully identified message scores above the review threshold`() {
        val txn = success(
            "Rs.742.00 debited from a/c XX4143 on 26-08-26 to BLINKIT UPI Ref 552312345678. -HDFC Bank",
        )
        assertTrue(txn.confidence > SmsTransactionParser.REVIEW_THRESHOLD)
    }

    @Test
    fun `a sparse message scores below the review threshold`() {
        val txn = success("Rs.200 debited", sender = "UNKNOWN")
        assertTrue(txn.confidence <= SmsTransactionParser.REVIEW_THRESHOLD)
    }

    @Test
    fun `extracts the date from the body rather than the arrival time`() {
        val txn = success("Rs.742.00 debited from a/c XX4143 on 26-08-2026 11:38 AM to BLINKIT")
        assertNotNull(txn.occurredAt)
        assertTrue(txn.occurredAt != now)
    }
}
