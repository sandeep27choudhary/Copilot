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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.domain.importer.ImportReport
import com.coffeeledger.app.domain.model.Account
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.ui.components.BackRow
import com.coffeeledger.app.ui.components.ChipRow
import com.coffeeledger.app.ui.components.CoffeeButton
import com.coffeeledger.app.ui.components.Formats
import com.coffeeledger.app.ui.components.HairLine
import com.coffeeledger.app.ui.components.PaperCard
import com.coffeeledger.app.ui.components.QuietButton
import com.coffeeledger.app.ui.components.ScreenHeader
import com.coffeeledger.app.ui.components.SectionLabel
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/**
 * Statement import.
 *
 * CSV is exact; PDF is best-effort, and the screen says so rather than presenting a guess
 * as a fact. Imported rows land in the ledger with their source recorded.
 */
@Composable
fun ImportScreen(
    accounts: List<Account>,
    busy: Boolean,
    report: ImportReport?,
    onPickCsv: (String?) -> Unit,
    onPickPdf: (String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
    ) {
        item {
            BackRow(label = "Settings", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            ScreenHeader(
                title = "Import a statement",
                subtitle = "The file is read on this device and never uploaded.",
            )
            Spacer(Modifier.height(24.dp))
        }

        if (accounts.isNotEmpty()) {
            item {
                SectionLabel("Add to account")
                Spacer(Modifier.height(10.dp))
                ChipRow(
                    items = accounts,
                    selected = { it.id == accountId },
                    label = { "${it.displayName} ${it.maskedLabel}" },
                    onSelect = { accountId = it.id },
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        item {
            CoffeeButton(text = if (busy) "Reading…" else "Choose a CSV file", onClick = { if (!busy) onPickCsv(accountId) })
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Works with the CSV export from most Indian banks. Separate debit and credit columns, or a single amount column with a Dr/Cr marker, are both understood.",
                style = CoffeeType.Caption,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(20.dp))
            QuietButton(text = "Choose a PDF statement", onClick = { if (!busy) onPickPdf(accountId) })
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Text-based PDFs only. Scanned statements contain no text to read, and password-protected files must be unlocked first. Check imported rows before relying on them.",
                style = CoffeeType.Caption,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(28.dp))
        }

        if (report != null) {
            item {
                SectionLabel("Last import")
                Spacer(Modifier.height(10.dp))
                PaperCard {
                    Text(
                        text = "${report.importedCount} rows read",
                        style = CoffeeType.LargeAmount,
                        color = colors.textPrimary,
                    )
                    if (report.notes.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        report.notes.forEach { note ->
                            Text(
                                text = note,
                                style = CoffeeType.Caption,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                    if (report.rows.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        report.rows.take(6).forEachIndexed { index, row ->
                            if (index > 0) HairLine()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = row.description.take(48),
                                        style = CoffeeType.Body,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = Formats.date(row.occurredAt),
                                        style = CoffeeType.Caption,
                                        color = colors.textTertiary,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = Money.format(row.amountMinor),
                                    style = CoffeeType.RowAmount,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
