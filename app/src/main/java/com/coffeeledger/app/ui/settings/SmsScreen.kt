package com.coffeeledger.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.domain.parse.RejectionReason
import com.coffeeledger.app.sms.SmsScanReport
import com.coffeeledger.app.ui.components.BackRow
import com.coffeeledger.app.ui.components.CoffeeButton
import com.coffeeledger.app.ui.components.Formats
import com.coffeeledger.app.ui.components.HairLine
import com.coffeeledger.app.ui.components.PaperCard
import com.coffeeledger.app.ui.components.QuietButton
import com.coffeeledger.app.ui.components.ScreenHeader
import com.coffeeledger.app.ui.components.SectionLabel
import com.coffeeledger.app.ui.components.ToggleRow
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/**
 * The SMS permission screen.
 *
 * The permission is asked for once, in context, with the reason stated in plain words and
 * the limits stated just as plainly. Everything stays off until the user turns it on.
 */
@Composable
fun SmsScreen(
    enabled: Boolean,
    permissionGranted: Boolean,
    busy: Boolean,
    lastScanAt: Long,
    lastScan: SmsScanReport?,
    onRequestPermission: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onScan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
    ) {
        item {
            BackRow(label = "Settings", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            ScreenHeader(
                title = "SMS transactions",
                subtitle = "Read bank messages on this device to fill the ledger automatically.",
            )
            Spacer(Modifier.height(24.dp))
        }

        item {
            PaperCard {
                Text(text = "Why this needs SMS access", style = CoffeeType.Title, color = colors.textPrimary)
                Spacer(Modifier.height(10.dp))
                Reason("Indian banks and UPI apps confirm every payment by SMS. Reading those messages is the only way to build a ledger without connecting to your bank.")
                Reason("Messages are parsed here, on the device. Nothing is uploaded — the app declares no internet permission at all.")
                Reason("Only messages that look like a transaction are stored. OTPs, promotions, mandate notices and failed payments are discarded.")
                Reason("You can turn this off at any time, and deleting the app removes everything it read.")
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            if (!permissionGranted) {
                CoffeeButton(text = "Allow SMS access", onClick = onRequestPermission)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Android will ask you to confirm. Nothing is read before you do.",
                    style = CoffeeType.Caption,
                    color = colors.textTertiary,
                )
            } else {
                PaperCard {
                    ToggleRow(
                        title = "Read bank messages",
                        description = "New messages are parsed as they arrive.",
                        checked = enabled,
                        onCheckedChange = onToggle,
                    )
                }
                Spacer(Modifier.height(12.dp))
                QuietButton(
                    text = if (busy) "Scanning…" else "Scan inbox now",
                    onClick = { if (!busy) onScan() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (lastScanAt > 0L) {
                        "Last scanned ${Formats.dateTime(lastScanAt)}. A scan re-reads your existing messages and adds anything not already in the ledger."
                    } else {
                        "A scan reads your existing messages once and adds anything not already in the ledger."
                    },
                    style = CoffeeType.Caption,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        if (lastScan != null) {
            item {
                SectionLabel("Last scan")
                Spacer(Modifier.height(10.dp))
                PaperCard {
                    ScanRow("Messages read", lastScan.messagesRead.toString())
                    HairLine()
                    ScanRow("Recognised as transactions", lastScan.recognised.toString())
                    lastScan.rejections.entries.sortedByDescending { it.value }.forEach { (reason, count) ->
                        HairLine()
                        ScanRow(reason.explanation(), count.toString())
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Anything the parser was unsure about is added but flagged, so you can correct it rather than lose it.",
                    style = CoffeeType.Caption,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun Reason(text: String) {
    val colors = coffeeColors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(text = "·", style = CoffeeType.Body, color = colors.textTertiary)
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = CoffeeType.Body, color = colors.textSecondary)
    }
}

@Composable
private fun ScanRow(label: String, value: String) {
    val colors = coffeeColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = CoffeeType.Body, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(text = value, style = CoffeeType.RowAmount, color = colors.textPrimary)
    }
}

private fun RejectionReason.explanation(): String = when (this) {
    RejectionReason.NOT_FINANCIAL -> "Not about money"
    RejectionReason.OTP_OR_VERIFICATION -> "OTP or verification code"
    RejectionReason.PROMOTIONAL -> "Marketing"
    RejectionReason.FUTURE_OR_MANDATE -> "Upcoming payment or request"
    RejectionReason.FAILED_TRANSACTION -> "Failed or declined payment"
    RejectionReason.NO_AMOUNT -> "No amount found"
    RejectionReason.NO_DIRECTION -> "Could not tell credit from debit"
}
