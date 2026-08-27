package com.coffeeledger.app.domain.parse

import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.PaymentMethod

/** The raw text the parser was given. Kept as a value type so the parser stays pure. */
data class SmsMessage(
    val sender: String,
    val body: String,
    val receivedAt: Long,
)

/**
 * What the local parser could pull out of one message. This never leaves the device,
 * and neither does the [rawBody] it was derived from.
 */
data class ParsedTransaction(
    val amountMinor: Long,
    val direction: Direction,
    val merchantRaw: String?,
    val accountTail: String?,
    val institution: String?,
    val reference: String?,
    val occurredAt: Long,
    val paymentMethod: PaymentMethod,
    val balanceMinor: Long?,
    val rawBody: String,
    val sender: String,
    /** 0..1. Below [SmsTransactionParser.REVIEW_THRESHOLD] the entry is flagged for review. */
    val confidence: Float,
)

/** Why a message was not turned into a transaction. Surfaced in the SMS scan report. */
enum class RejectionReason {
    NOT_FINANCIAL,
    OTP_OR_VERIFICATION,
    PROMOTIONAL,
    FUTURE_OR_MANDATE,
    FAILED_TRANSACTION,
    NO_AMOUNT,
    NO_DIRECTION,
}

sealed interface ParseResult {
    data class Success(val transaction: ParsedTransaction) : ParseResult
    data class Rejected(val reason: RejectionReason) : ParseResult
}
