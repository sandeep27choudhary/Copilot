package com.coffeeledger.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.coffeeledger.app.CoffeeLedgerApp
import com.coffeeledger.app.domain.parse.ParseResult
import com.coffeeledger.app.domain.parse.SmsMessage
import com.coffeeledger.app.domain.parse.SmsTransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Picks up bank messages as they arrive so the ledger stays current without a scan.
 *
 * The receiver only ever writes to the local database. It does no network work of any kind,
 * and it does nothing at all while SMS ingestion is switched off in Settings.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val container = (context.applicationContext as? CoffeeLedgerApp)?.container ?: return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Multipart messages arrive as several parts of one logical message.
        val grouped = messages.groupBy { it.originatingAddress.orEmpty() }
            .map { (sender, parts) ->
                SmsMessage(
                    sender = sender,
                    body = parts.joinToString("") { it.messageBody.orEmpty() },
                    receivedAt = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis(),
                )
            }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!container.settingsRepository.isSmsIngestionEnabled()) return@launch
                val parsed = grouped.mapNotNull { message ->
                    (SmsTransactionParser.parse(message) as? ParseResult.Success)?.transaction
                }
                if (parsed.isNotEmpty()) {
                    container.ledgerRepository.ingestParsed(parsed)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
