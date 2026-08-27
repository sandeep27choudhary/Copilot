package com.coffeeledger.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.Txn
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/**
 * One line of the ledger.
 *
 * Merchant leads, the amount is right-aligned and tabular, and the supporting line carries
 * the category, the source app and the account. Credits are prefixed with a plus and take
 * the muted green; debits stay in ink.
 */
@Composable
fun TransactionRow(
    txn: Txn,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
) {
    val colors = coffeeColors
    val credit = txn.direction == Direction.CREDIT
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = txn.merchant,
                    style = CoffeeType.Title,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (txn.isTransfer) {
                    Spacer(Modifier.width(8.dp))
                    Tag(text = "Transfer")
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = supportingLine(txn, showDate),
                style = CoffeeType.Caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = (if (credit) "+" else "") + Money.format(txn.amountMinor),
            style = CoffeeType.RowAmount,
            color = when {
                txn.isTransfer -> colors.textSecondary
                credit -> colors.positive
                else -> colors.textPrimary
            },
        )
    }
}

private fun supportingLine(txn: Txn, showDate: Boolean): String = buildList {
    if (showDate) add(Formats.shortDate(txn.occurredAt))
    add(txn.category.label)
    add(txn.sourceApp)
    txn.accountTail?.let { add("••$it") }
}.joinToString(" · ")

/** A small inline marker. Used for transfers and for entries that need a look. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, caution: Boolean = false) {
    val colors = coffeeColors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (caution) colors.cautionWash else colors.sunken)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = CoffeeType.Caption,
            color = if (caution) colors.caution else colors.textSecondary,
        )
    }
}

/** The sticky-ish day heading in the timeline. */
@Composable
fun DayHeading(text: String, totalMinor: Long?, modifier: Modifier = Modifier) {
    val colors = coffeeColors
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(text)
        if (totalMinor != null && totalMinor != 0L) {
            Text(
                text = Money.format(totalMinor),
                style = CoffeeType.Caption,
                color = colors.textTertiary,
            )
        }
    }
}
