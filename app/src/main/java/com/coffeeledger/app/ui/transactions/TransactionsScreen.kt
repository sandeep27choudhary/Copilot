package com.coffeeledger.app.ui.transactions

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.domain.analytics.TimeRanges
import com.coffeeledger.app.domain.model.Txn
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.domain.query.TransactionFilter
import com.coffeeledger.app.ui.LedgerUiState
import com.coffeeledger.app.ui.components.ChipRow
import com.coffeeledger.app.ui.components.CoffeeChip
import com.coffeeledger.app.ui.components.DayHeading
import com.coffeeledger.app.ui.components.EmptyNote
import com.coffeeledger.app.ui.components.Formats
import com.coffeeledger.app.ui.components.HairLine
import com.coffeeledger.app.ui.components.PaperField
import com.coffeeledger.app.ui.components.ScreenHeader
import com.coffeeledger.app.ui.components.SectionLabel
import com.coffeeledger.app.ui.components.TransactionRow
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/**
 * One timeline for every bank and every app, grouped by day.
 *
 * Filters are chips rather than a modal: the current view should always be readable at a
 * glance, and a filter you cannot see is a filter you forget you set.
 */
@Composable
fun TransactionsScreen(
    state: LedgerUiState,
    onOpenTransaction: (String) -> Unit,
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    var filter by remember { mutableStateOf(TransactionFilter()) }
    var showFilters by remember { mutableStateOf(false) }

    val all = state.snapshot.transactions
    val visible = remember(all, filter) { filter.apply(all) }
    val grouped = remember(visible) { visible.groupBy { Formats.dayKey(it.occurredAt) } }
    val periods = remember(state.now) {
        listOf(
            "This month" to TimeRanges.currentMonth(state.now),
            "Last month" to TimeRanges.previousMonth(state.now),
            "Last 90 days" to TimeRanges.lastDays(state.now, 90),
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
    ) {
        item {
            ScreenHeader(
                title = "Transactions",
                subtitle = summaryLine(visible),
                trailing = {
                    Text(
                        text = "Add",
                        style = CoffeeType.Label,
                        color = colors.accent,
                        modifier = Modifier.clickable(onClick = onAddTransaction).padding(6.dp),
                    )
                },
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            PaperField(
                value = filter.query,
                onValueChange = { filter = filter.copy(query = it) },
                placeholder = "Search merchant, category or reference",
            )
            Spacer(Modifier.height(14.dp))
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransactionFilter.Flow.entries.forEach { flow ->
                    CoffeeChip(
                        text = flow.label,
                        selected = filter.flow == flow,
                        onClick = { filter = filter.copy(flow = flow) },
                    )
                }
                if (state.needsReviewCount > 0) {
                    CoffeeChip(
                        text = "Check ${state.needsReviewCount}",
                        selected = filter.onlyNeedsReview,
                        onClick = { filter = filter.copy(onlyNeedsReview = !filter.onlyNeedsReview) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (showFilters) "Fewer filters" else "More filters",
                    style = CoffeeType.Label,
                    color = colors.accent,
                    modifier = Modifier
                        .clickable { showFilters = !showFilters }
                        .padding(vertical = 6.dp),
                )
                if (filter.isActive) {
                    Text(
                        text = "Clear",
                        style = CoffeeType.Label,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .clickable { filter = TransactionFilter() }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }

        item {
            AnimatedVisibility(visible = showFilters) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    SectionLabel("Category")
                    Spacer(Modifier.height(8.dp))
                    ChipRow(
                        items = TransactionFilter.categoriesPresent(all),
                        selected = { it.id in filter.categoryIds },
                        label = { it.label },
                        onSelect = { category ->
                            filter = filter.copy(categoryIds = filter.categoryIds.toggle(category.id))
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Bank or app")
                    Spacer(Modifier.height(8.dp))
                    ChipRow(
                        items = TransactionFilter.sourcesPresent(all),
                        selected = { it in filter.sourceApps },
                        label = { it },
                        onSelect = { source ->
                            filter = filter.copy(sourceApps = filter.sourceApps.toggle(source))
                        },
                    )
                    if (state.snapshot.accounts.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        SectionLabel("Account")
                        Spacer(Modifier.height(8.dp))
                        ChipRow(
                            items = state.snapshot.accounts,
                            selected = { it.id in filter.accountIds },
                            label = { "${it.displayName} ${it.maskedLabel}" },
                            onSelect = { account ->
                                filter = filter.copy(accountIds = filter.accountIds.toggle(account.id))
                            },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Period")
                    Spacer(Modifier.height(8.dp))
                    ChipRow(
                        items = periods,
                        selected = { filter.range?.label == it.second.label },
                        label = { it.first },
                        onSelect = { entry ->
                            filter = filter.copy(
                                range = if (filter.range?.label == entry.second.label) null else entry.second,
                            )
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyNote(
                    title = if (all.isEmpty()) "Nothing recorded yet" else "No matches",
                    body = if (all.isEmpty()) {
                        "Turn on SMS reading, import a statement, or add an entry by hand."
                    } else {
                        "No transactions match these filters."
                    },
                )
            }
        }

        grouped.forEach { (day, dayTxns) ->
            item(key = "head-$day") {
                DayHeading(
                    text = Formats.dayHeading(dayTxns.first().occurredAt, state.now),
                    totalMinor = dayTxns.filter { it.countsAsSpending }.sumOf { it.amountMinor }
                        .takeIf { it > 0L },
                )
            }
            transactionItems(dayTxns, onOpenTransaction)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.transactionItems(
    transactions: List<Txn>,
    onOpen: (String) -> Unit,
) {
    items(transactions.size, key = { transactions[it].id }) { index ->
        val txn = transactions[index]
        Column {
            if (index > 0) HairLine()
            TransactionRow(txn = txn, onClick = { onOpen(txn.id) })
        }
    }
}

private fun summaryLine(transactions: List<Txn>): String {
    if (transactions.isEmpty()) return "Nothing to show"
    val spent = transactions.filter { it.countsAsSpending }.sumOf { it.amountMinor }
    val received = transactions.filter { it.countsAsIncome }.sumOf { it.amountMinor }
    return "${transactions.size} entries · ${Money.format(spent)} out · ${Money.format(received)} in"
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (contains(value)) this - value else this + value
