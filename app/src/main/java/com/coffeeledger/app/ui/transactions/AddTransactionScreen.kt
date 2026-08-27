package com.coffeeledger.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.domain.model.Account
import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.PaymentMethod
import com.coffeeledger.app.domain.model.SourceType
import com.coffeeledger.app.domain.model.Txn
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.domain.normalize.MerchantNormalizer
import com.coffeeledger.app.ui.components.BackRow
import com.coffeeledger.app.ui.components.ChipRow
import com.coffeeledger.app.ui.components.CoffeeButton
import com.coffeeledger.app.ui.components.CoffeeChip
import com.coffeeledger.app.ui.components.PaperField
import com.coffeeledger.app.ui.components.ScreenHeader
import com.coffeeledger.app.ui.components.SectionLabel
import com.coffeeledger.app.ui.components.ToggleRow
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors
import java.util.UUID

/** Manual entry, for cash and for anything the parser could not see. */
@Composable
fun AddTransactionScreen(
    accounts: List<Account>,
    onSave: (Txn) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    var amount by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(Direction.DEBIT) }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.OTHER_EXPENSE) }
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    var method by remember { mutableStateOf(PaymentMethod.UPI) }
    var notes by remember { mutableStateOf("") }
    var isTransfer by remember { mutableStateOf(false) }

    val amountMinor = Money.parseAmount(amount) ?: 0L
    val canSave = amountMinor > 0L && merchant.isNotBlank()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
    ) {
        item {
            BackRow(label = "Transactions", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            ScreenHeader(title = "Add transaction")
            Spacer(Modifier.height(24.dp))
        }

        item {
            SectionLabel("Amount")
            Spacer(Modifier.height(10.dp))
            PaperField(
                value = amount,
                onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
                placeholder = "0",
                keyboardType = KeyboardType.Decimal,
                prefix = "₹",
            )
            if (amountMinor > 0L) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Money.format(amountMinor),
                    style = CoffeeType.Caption,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionLabel("Direction")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoffeeChip(
                    text = "Money out",
                    selected = direction == Direction.DEBIT,
                    onClick = {
                        direction = Direction.DEBIT
                        category = Category.OTHER_EXPENSE
                    },
                )
                CoffeeChip(
                    text = "Money in",
                    selected = direction == Direction.CREDIT,
                    onClick = {
                        direction = Direction.CREDIT
                        category = Category.OTHER_INCOME
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionLabel("Merchant")
            Spacer(Modifier.height(10.dp))
            PaperField(
                value = merchant,
                onValueChange = {
                    merchant = it
                    MerchantNormalizer.suggestedCategory(it)?.let { suggested ->
                        if (direction == Direction.DEBIT) category = suggested
                    }
                },
                placeholder = "Who was paid, or who paid you",
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionLabel("Category")
            Spacer(Modifier.height(10.dp))
            ChipRow(
                items = if (direction == Direction.CREDIT) Category.credits() else Category.debits(),
                selected = { it == category },
                label = { it.label },
                onSelect = { category = it },
            )
            Spacer(Modifier.height(20.dp))
        }

        if (accounts.isNotEmpty()) {
            item {
                SectionLabel("Account")
                Spacer(Modifier.height(10.dp))
                ChipRow(
                    items = accounts,
                    selected = { it.id == accountId },
                    label = { "${it.displayName} ${it.maskedLabel}" },
                    onSelect = { accountId = it.id },
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        item {
            SectionLabel("Payment method")
            Spacer(Modifier.height(10.dp))
            ChipRow(
                items = PaymentMethod.entries.toList(),
                selected = { it == method },
                label = { it.label },
                onSelect = { method = it },
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionLabel("Note")
            Spacer(Modifier.height(10.dp))
            PaperField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = "Optional",
                singleLine = false,
            )
            Spacer(Modifier.height(16.dp))
            ToggleRow(
                title = "This is a transfer",
                description = "Between your own accounts, so it is not spending.",
                checked = isTransfer,
                onCheckedChange = { isTransfer = it },
            )
            Spacer(Modifier.height(24.dp))
        }

        item {
            CoffeeButton(
                text = "Add transaction",
                enabled = canSave,
                onClick = {
                    val account = accounts.firstOrNull { it.id == accountId }
                    onSave(
                        Txn(
                            id = UUID.randomUUID().toString(),
                            occurredAt = System.currentTimeMillis(),
                            amountMinor = amountMinor,
                            direction = direction,
                            merchant = MerchantNormalizer.normalize(merchant),
                            merchantRaw = merchant.trim(),
                            category = if (isTransfer) {
                                if (direction == Direction.CREDIT) Category.TRANSFER_IN else Category.TRANSFER_OUT
                            } else {
                                category
                            },
                            accountId = account?.id,
                            accountTail = account?.tail,
                            sourceType = SourceType.MANUAL,
                            sourceApp = account?.institution ?: "Manual",
                            paymentMethod = method,
                            notes = notes.trim().ifEmpty { null },
                            isTransfer = isTransfer,
                        ),
                    )
                },
            )
        }
    }
}
