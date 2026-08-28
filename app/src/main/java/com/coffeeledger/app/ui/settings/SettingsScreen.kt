package com.coffeeledger.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.ui.LedgerUiState
import com.coffeeledger.app.ui.components.HairLine
import com.coffeeledger.app.ui.components.NavigationRow
import com.coffeeledger.app.ui.components.PaperCard
import com.coffeeledger.app.ui.components.ScreenHeader
import com.coffeeledger.app.ui.components.SectionLabel
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/**
 * Settings is mostly a set of doors: data in, data out, and the privacy controls.
 * Anything destructive lives behind its own screen with its own confirmation.
 */
@Composable
fun SettingsScreen(
    state: LedgerUiState,
    onOpenPrivacy: () -> Unit,
    onOpenSms: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenAccounts: () -> Unit,
    onExportBackup: () -> Unit,
    onExportCsv: () -> Unit,
    onRestoreBackup: () -> Unit,
    onRemoveSampleData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
    ) {
        item {
            ScreenHeader(title = "Settings")
            Spacer(Modifier.height(24.dp))
        }

        item {
            PaperCard {
                Text(
                    text = "Your financial data stays on your device.",
                    style = CoffeeType.Title,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Transactions, categories, budgets, insights and the advisor all run locally, in an encrypted database.",
                    style = CoffeeType.Body,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                NavigationRow(title = "Privacy", description = "What is stored, and where", onClick = onOpenPrivacy)
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            SectionLabel("Accounts")
            Spacer(Modifier.height(8.dp))
            PaperCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                NavigationRow(
                    title = "Your accounts",
                    description = "${state.snapshot.accounts.size} accounts · balance ${Money.format(state.snapshot.totalBalanceMinor)}",
                    onClick = onOpenAccounts,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            SectionLabel("Sources")
            Spacer(Modifier.height(8.dp))
            PaperCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                NavigationRow(
                    title = "SMS transactions",
                    description = if (state.settings.smsIngestionEnabled) "On" else "Off",
                    onClick = onOpenSms,
                    trailingText = if (state.settings.smsIngestionEnabled) "On" else "Off",
                )
                HairLine()
                NavigationRow(
                    title = "Import a statement",
                    description = "CSV or a text-based PDF",
                    onClick = onOpenImport,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            SectionLabel("Your data")
            Spacer(Modifier.height(8.dp))
            PaperCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                NavigationRow(
                    title = "Export backup",
                    description = "A JSON file you choose the location for",
                    onClick = onExportBackup,
                )
                HairLine()
                NavigationRow(
                    title = "Export transactions as CSV",
                    description = "For a spreadsheet",
                    onClick = onExportCsv,
                )
                HairLine()
                NavigationRow(
                    title = "Restore from backup",
                    description = "Merges into what is already here",
                    onClick = onRestoreBackup,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        if (state.settings.sampleDataPresent) {
            item {
                SectionLabel("Sample data")
                Spacer(Modifier.height(8.dp))
                PaperCard {
                    Text(
                        text = "This ledger starts with a sample month so every screen has something to show.",
                        style = CoffeeType.Body,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    NavigationRow(
                        title = "Remove sample transactions",
                        description = "Keeps anything you added yourself",
                        onClick = onRemoveSampleData,
                        tint = colors.caution,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        item {
            SectionLabel("This ledger")
            Spacer(Modifier.height(8.dp))
            PaperCard {
                Column {
                    Text(
                        text = "${state.snapshot.transactions.size} transactions",
                        style = CoffeeType.Body,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${state.snapshot.accounts.size} accounts · ${state.snapshot.trackers.size} trackers · ${state.snapshot.rules.size} learned rules",
                        style = CoffeeType.Caption,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Balance ${Money.format(state.snapshot.totalBalanceMinor)}",
                        style = CoffeeType.Caption,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}
