package com.coffeeledger.app.sms

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import com.coffeeledger.app.domain.parse.ParseResult
import com.coffeeledger.app.domain.parse.ParsedTransaction
import com.coffeeledger.app.domain.parse.RejectionReason
import com.coffeeledger.app.domain.parse.SmsMessage
import com.coffeeledger.app.domain.parse.SmsTransactionParser

/** What one inbox scan found, shown to the user so the parsing is never a black box. */
data class SmsScanReport(
    val messagesRead: Int,
    val parsed: List<ParsedTransaction>,
    val rejections: Map<RejectionReason, Int>,
) {
    val recognised: Int get() = parsed.size
}

/**
 * Reads the device SMS inbox and parses it in place.
 *
 * The cursor is walked, each message is handed to the local parser, and the message text
 * never leaves this process except to be written into the encrypted database.
 */
class SmsInboxReader(private val context: Context) {

    fun scan(sinceMillis: Long = 0L, limit: Int = MAX_MESSAGES): SmsScanReport {
        val parsed = mutableListOf<ParsedTransaction>()
        val rejections = mutableMapOf<RejectionReason, Int>()
        var read = 0

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val selection = if (sinceMillis > 0L) "${Telephony.Sms.DATE} > ?" else null
        val selectionArgs = if (sinceMillis > 0L) arrayOf(sinceMillis.toString()) else null

        val cursor: Cursor? = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} DESC LIMIT $limit",
        )

        cursor?.use {
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                read++
                val message = SmsMessage(
                    sender = if (addressIndex >= 0) it.getString(addressIndex).orEmpty() else "",
                    body = if (bodyIndex >= 0) it.getString(bodyIndex).orEmpty() else "",
                    receivedAt = if (dateIndex >= 0) it.getLong(dateIndex) else System.currentTimeMillis(),
                )
                when (val result = SmsTransactionParser.parse(message)) {
                    is ParseResult.Success -> parsed.add(result.transaction)
                    is ParseResult.Rejected ->
                        rejections[result.reason] = (rejections[result.reason] ?: 0) + 1
                }
            }
        }

        return SmsScanReport(read, parsed, rejections)
    }

    private companion object {
        /** Enough to cover several years of bank messages without stalling the scan. */
        const val MAX_MESSAGES = 5000
    }
}
