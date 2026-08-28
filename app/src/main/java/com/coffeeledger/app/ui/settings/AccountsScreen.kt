package com.coffeeledger.app.ui.settings

import androidx.compose.foundation.clickable
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
import com.coffeeledger.app.domain.model.Account
import com.coffeeledger.app.domain.model.BalanceSource
import com.coffeeledger.app.domain.analytics.AnalyticsEngine
import com.coffeeledger.app.domain.model.Txn
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.ui.components.BackRow
import com.coffeeledger.app.ui.components.EmptyNote
import com.coffeeledger.app.ui.components.HairLine
import com.coffeeledger.app.ui.components.PaperCard
import com.coffeeledger.app.ui.components.ScreenHeader
import com.coffeeledger.app.ui.components.Tag
import com.coffeeledger.app.ui.components.Formats
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/**
 * Every account the ledger knows about, and where each one's balance came from.
 *
 * An account only ever gets here two ways: a bank SMS named a tail the app had not seen
 * before, or the user added one by hand. Nothing here is invented.
 */
@Composable
fun AccountsScreen(
    accounts: List<Account>,
    transactions: List<Txn>,
    onOpenAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
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
                title = "Accounts",
                subtitle = "Balances come from your bank's own messages, or from what you enter here.",
                trailing = {
                    Text(
                        text = "Add",
                        style = CoffeeType.Label,
                        color = colors.accent,
                        modifier = Modifier.clickable(onClick = onAddAccount).padding(6.dp),
                    )
                },
            )
            Spacer(Modifier.height(20.dp))
        }

        if (accounts.isEmpty()) {
            item {
                EmptyNote(
                    title = "No accounts yet",
                    body = "Accounts appear automatically once a bank message is read, or add one yourself — useful for cash.",
                )
            }
            return@LazyColumn
        }

        item {
            PaperCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                accounts.forEachIndexed { index, account ->
                    if (index > 0) HairLine()
                    AccountRow(
                        account = account,
                        derivedBalance = AnalyticsEngine.accountBalance(account, transactions),
                        onClick = { onOpenAccount(account.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(account: Account, derivedBalance: Long, onClick: () -> Unit) {
    val colors = coffeeColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = account.displayName, style = CoffeeType.Title, color = colors.textPrimary)
                if (!account.includeInTotals) {
                    Spacer(Modifier.width(8.dp))
                    Tag(text = "Excluded")
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${account.maskedLabel} · ${balanceCaption(account)}",
                style = CoffeeType.Caption,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = Money.format(account.currentBalanceMinor ?: derivedBalance),
            style = CoffeeType.RowAmount,
            color = colors.textPrimary,
        )
        Spacer(Modifier.width(6.dp))
        Text(text = "›", style = CoffeeType.Title, color = colors.textTertiary)
    }
}

private fun balanceCaption(account: Account): String = when (account.balanceSource) {
    BalanceSource.SMS -> "As reported by ${account.institution}" +
        (account.balanceAsOf?.let { " · ${Formats.shortDate(it)}" } ?: "")
    BalanceSource.MANUAL -> "Set manually" +
        (account.balanceAsOf?.let { " · ${Formats.shortDate(it)}" } ?: "")
    null -> "Not yet confirmed — derived from transactions"
}
