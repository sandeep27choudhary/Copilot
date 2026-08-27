package com.coffeeledger.app.ui.transactions

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.Txn
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.ui.components.BackRow
import com.coffeeledger.app.ui.components.ChipRow
import com.coffeeledger.app.ui.components.CoffeeButton
import com.coffeeledger.app.ui.components.Formats
import com.coffeeledger.app.ui.components.HairLine
import com.coffeeledger.app.ui.components.PaperCard
import com.coffeeledger.app.ui.components.PaperField
import com.coffeeledger.app.ui.components.QuietButton
import com.coffeeledger.app.ui.components.SectionLabel
import com.coffeeledger.app.ui.components.Tag
import com.coffeeledger.app.ui.components.ToggleRow
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/**
 * One transaction, in full, and editable in place.
 *
 * Editing the category also offers to teach the rule, which is how the categoriser gets
 * better without ever asking a server what a merchant is.
 */
@Composable
fun TransactionDetailScreen(
    txn: Txn,
    accountLabel: String?,
    rawMessage: String?,
    categories: List<Category>,
    onSave: (Txn, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    var merchant by remember(txn.id) { mutableStateOf(txn.merchant) }
    var category by remember(txn.id) { mutableStateOf(txn.category) }
    var notes by remember(txn.id) { mutableStateOf(txn.notes.orEmpty()) }
    var isTransfer by remember(txn.id) { mutableStateOf(txn.isTransfer) }
    var learnRule by remember(txn.id) { mutableStateOf(true) }
    var showRaw by remember(txn.id) { mutableStateOf(false) }
    var confirmDelete by remember(txn.id) { mutableStateOf(false) }

    LaunchedEffect(txn.id) { confirmDelete = false }

    val credit = txn.direction == Direction.CREDIT
    val edited = merchant != txn.merchant || category != txn.category ||
        notes != txn.notes.orEmpty() || isTransfer != txn.isTransfer

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
    ) {
        item {
            BackRow(label = "Transactions", onBack = onBack)
            Spacer(Modifier.height(20.dp))
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = merchant,
                    style = CoffeeType.Title,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = (if (credit) "+" else "") + Money.format(txn.amountMinor, withDecimals = true),
                    style = CoffeeType.DisplayAmount,
                    color = if (credit) colors.positive else colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (credit) "Credit" else "Debit",
                        style = CoffeeType.Label,
                        color = colors.textSecondary,
                    )
                    if (isTransfer) {
                        Spacer(Modifier.width(8.dp))
                        Tag(text = "Transfer")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = Formats.dateTime(txn.occurredAt),
                    style = CoffeeType.Caption,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        item {
            PaperCard {
                DetailRow("Category", category.label)
                HairLine()
                DetailRow("Source", txn.sourceApp)
                HairLine()
                DetailRow("Account", accountLabel ?: txn.accountTail?.let { "•••• $it" } ?: "Not linked")
                HairLine()
                DetailRow("Payment method", txn.paymentMethod.label)
                HairLine()
                DetailRow("Captured from", txn.sourceType.label)
                if (!txn.reference.isNullOrBlank()) {
                    HairLine()
                    DetailRow("Reference", txn.reference)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            SectionLabel("Change category")
            Spacer(Modifier.height(10.dp))
            ChipRow(
                items = categories,
                selected = { it == category },
                label = { it.label },
                onSelect = { category = it },
            )
            Spacer(Modifier.height(12.dp))
            ToggleRow(
                title = "Remember this",
                description = "Put future $merchant transactions in ${category.label} automatically.",
                checked = learnRule,
                onCheckedChange = { learnRule = it },
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionLabel("Merchant")
            Spacer(Modifier.height(10.dp))
            PaperField(
                value = merchant,
                onValueChange = { merchant = it },
                placeholder = "Merchant name",
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionLabel("Note")
            Spacer(Modifier.height(10.dp))
            PaperField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = "Add a note for yourself",
                singleLine = false,
            )
            Spacer(Modifier.height(16.dp))
        }

        item {
            ToggleRow(
                title = "This is a transfer",
                description = "Transfers between your own accounts are never counted as spending.",
                checked = isTransfer,
                onCheckedChange = { isTransfer = it },
            )
            Spacer(Modifier.height(24.dp))
        }

        if (!rawMessage.isNullOrBlank()) {
            item {
                Text(
                    text = if (showRaw) "Hide original message" else "Show original message",
                    style = CoffeeType.Label,
                    color = colors.accent,
                    modifier = Modifier.clickable { showRaw = !showRaw }.padding(vertical = 6.dp),
                )
                if (showRaw) {
                    Spacer(Modifier.height(10.dp))
                    PaperCard {
                        Text(
                            text = rawMessage,
                            style = CoffeeType.Caption,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Stored in the encrypted database on this device only.",
                            style = CoffeeType.Caption,
                            color = colors.textTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        item {
            CoffeeButton(
                text = if (edited) "Save changes" else "Saved",
                enabled = edited,
                onClick = {
                    val categoryChanged = category != txn.category
                    onSave(
                        txn.copy(
                            merchant = merchant.trim().ifEmpty { txn.merchant },
                            category = category,
                            notes = notes.trim().ifEmpty { null },
                            isTransfer = isTransfer,
                        ),
                        learnRule && categoryChanged,
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            QuietButton(
                text = if (confirmDelete) "Tap again to delete" else "Delete transaction",
                tint = colors.caution,
                onClick = {
                    if (confirmDelete) onDelete(txn.id) else confirmDelete = true
                },
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = coffeeColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = CoffeeType.Body, color = colors.textSecondary)
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = CoffeeType.Body,
            color = colors.textPrimary,
            textAlign = TextAlign.End,
        )
    }
}
