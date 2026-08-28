package com.coffeeledger.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.coffeeledger.app.domain.analytics.TimeRanges
import com.coffeeledger.app.domain.model.TrackerKind
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.ui.LedgerUiState
import com.coffeeledger.app.ui.components.AmountTile
import com.coffeeledger.app.ui.components.EmptyNote
import com.coffeeledger.app.ui.components.HairLine
import com.coffeeledger.app.ui.components.MeterBar
import com.coffeeledger.app.ui.components.MeterRow
import com.coffeeledger.app.ui.components.PaperCard
import com.coffeeledger.app.ui.components.ScreenHeader
import com.coffeeledger.app.ui.components.SectionLabel
import com.coffeeledger.app.ui.components.TransactionRow
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/**
 * The dashboard: balance, the month in three figures, the spending meter, savings
 * progress, and the last few entries. Everything above the fold answers "where do I stand".
 */
@Composable
fun HomeScreen(
    state: LedgerUiState,
    onOpenTransaction: (String) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onOpenAdvisor: () -> Unit,
    onOpenTracker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    val month = state.thisMonth
    val recent = state.snapshot.transactions.take(5)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
    ) {
        item {
            ScreenHeader(
                title = "Home",
                subtitle = TimeRanges.monthLabel(TimeRanges.yearMonthOf(state.now)),
            )
            Spacer(Modifier.height(24.dp))
        }

        item {
            Column {
                SectionLabel("Total balance")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Money.format(state.snapshot.totalBalanceMinor),
                    style = CoffeeType.DisplayAmount,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = balanceCaption(state),
                    style = CoffeeType.Caption,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        if (month != null) {
            item {
                PaperCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        AmountTile(
                            amountMinor = month.spendMinor,
                            label = "Spent this month",
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        AmountTile(
                            amountMinor = month.incomeMinor,
                            label = "Received",
                            modifier = Modifier.weight(1f),
                            color = colors.positive,
                            prefix = "+",
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    HairLine()
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        AmountTile(
                            amountMinor = kotlin.math.abs(month.netMinor),
                            label = if (month.netMinor < 0L) "Net outflow" else "Net inflow",
                            modifier = Modifier.weight(1f),
                            color = if (month.netMinor < 0L) colors.textPrimary else colors.positive,
                        )
                        if (month.transferOutMinor > 0L) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Money.format(month.transferOutMinor),
                                    style = CoffeeType.RowAmount,
                                    color = colors.textSecondary,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Transfers",
                                    style = CoffeeType.Caption,
                                    color = colors.textTertiary,
                                )
                            }
                        }
                    }
                    if (month.transferOutMinor > 0L) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Transfers between your own accounts are not counted as spending.",
                            style = CoffeeType.Caption,
                            color = colors.textTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        state.monthlyTracker?.let { monthly ->
            item {
                PaperCard(onClick = onOpenTracker) {
                    MeterRow(
                        title = "Spending",
                        currentMinor = monthly.currentMinor,
                        targetMinor = monthly.targetMinor,
                        percent = monthly.percent,
                        over = monthly.isOver,
                        caption = pacingCaption(state, monthly.fraction),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        val savings = state.trackers.filter { it.tracker.kind != TrackerKind.SPENDING_LIMIT }
        if (savings.isNotEmpty()) {
            item {
                PaperCard(onClick = onOpenTracker) {
                    SectionLabel("Savings progress")
                    Spacer(Modifier.height(16.dp))
                    savings.take(2).forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(20.dp))
                        Column {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = item.tracker.title,
                                    style = CoffeeType.Body,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "${Money.format(item.currentMinor)} / ${Money.format(item.targetMinor)}",
                                    style = CoffeeType.Caption,
                                    color = colors.textSecondary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            MeterBar(item.fraction, color = colors.accentSoft)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        item {
            PaperCard(onClick = onOpenAdvisor) {
                Text(
                    text = "Ask about your money",
                    style = CoffeeType.Title,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Where did I spend the most? What can I reduce? Answered on this device, from your own transactions.",
                    style = CoffeeType.Body,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Recent")
                Text(
                    text = "See all",
                    style = CoffeeType.Label,
                    color = colors.accent,
                    modifier = Modifier
                        .clickable(onClick = onSeeAllTransactions)
                        .padding(4.dp),
                )
            }
        }

        if (recent.isEmpty()) {
            item {
                EmptyNote(
                    title = "No transactions yet",
                    body = "Turn on SMS reading in Settings, import a statement, or add an entry by hand.",
                )
            }
        } else {
            items(recent.size) { index ->
                val txn = recent[index]
                if (index > 0) HairLine()
                TransactionRow(
                    txn = txn,
                    onClick = { onOpenTransaction(txn.id) },
                    showDate = true,
                )
            }
        }
    }
}

/**
 * Says how many accounts the total covers, and — when at least one is still a derived
 * guess rather than a bank-confirmed figure — says so, instead of presenting a number
 * that could be wrong as if it were certain.
 */
private fun balanceCaption(state: LedgerUiState): String {
    val included = state.snapshot.accounts.count { it.includeInTotals }
    val accountsLabel = "Across $included account${if (included == 1) "" else "s"} on this device"
    return if (state.snapshot.balanceIsConfirmed || included == 0) {
        accountsLabel
    } else {
        "$accountsLabel · not yet confirmed by your bank"
    }
}

/** Says whether spending is ahead of the calendar, which is the only useful framing. */
private fun pacingCaption(state: LedgerUiState, used: Float): String {
    val elapsed = TimeRanges.monthElapsedFraction(state.now)
    val usedPercent = (used * 100).toInt()
    val elapsedPercent = (elapsed * 100).toInt()
    return when {
        used > elapsed + 0.1f -> "$usedPercent% of budget used, $elapsedPercent% of the month gone."
        used < elapsed - 0.1f -> "Ahead of pace: $usedPercent% used with $elapsedPercent% of the month gone."
        else -> "On pace: $usedPercent% used with $elapsedPercent% of the month gone."
    }
}
