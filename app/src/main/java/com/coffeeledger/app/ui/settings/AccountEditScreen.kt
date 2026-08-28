package com.coffeeledger.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import com.coffeeledger.app.domain.model.AccountType
import com.coffeeledger.app.domain.model.BalanceSource
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.ui.components.BackRow
import com.coffeeledger.app.ui.components.CoffeeButton
import com.coffeeledger.app.ui.components.CoffeeChip
import com.coffeeledger.app.ui.components.Formats
import com.coffeeledger.app.ui.components.PaperField
import com.coffeeledger.app.ui.components.QuietButton
import com.coffeeledger.app.ui.components.ScreenHeader
import com.coffeeledger.app.ui.components.SectionLabel
import com.coffeeledger.app.ui.components.ToggleRow
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors
import java.util.UUID

/**
 * Create an account by hand, or correct one's balance.
 *
 * Entering a balance here is always a manual assertion — "as of right now, this is what my
 * account holds" — and it stands until a newer figure supersedes it: either the user edits
 * it again, or a bank SMS with a later timestamp reports a balance of its own.
 */
@Composable
fun AccountEditScreen(
    existing: Account?,
    onSave: (Account) -> Unit,
    onDelete: ((String) -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    var name by remember { mutableStateOf(existing?.displayName.orEmpty()) }
    var institution by remember { mutableStateOf(existing?.institution.orEmpty()) }
    var tail by remember { mutableStateOf(existing?.tail.orEmpty()) }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.BANK) }
    // Left blank unless a balance has actually been confirmed: an auto-created account's
    // openingBalanceMinor defaults to 0, and pre-filling that would show a fake "₹0" as if
    // someone had entered it.
    var balance by remember {
        mutableStateOf(existing?.currentBalanceMinor?.let { majorText(it) }.orEmpty())
    }
    var includeInTotals by remember { mutableStateOf(existing?.includeInTotals ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }

    val balanceMinor = Money.parseAmount(balance)
    val canSave = name.isNotBlank()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
    ) {
        item {
            BackRow(label = "Accounts", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            ScreenHeader(title = if (existing == null) "New account" else "Edit account")
            Spacer(Modifier.height(24.dp))
        }

        item {
            SectionLabel("Name")
            Spacer(Modifier.height(10.dp))
            PaperField(value = name, onValueChange = { name = it }, placeholder = "Everyday account, Cash, …")
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionLabel("Bank or provider")
            Spacer(Modifier.height(10.dp))
            PaperField(value = institution, onValueChange = { institution = it }, placeholder = "HDFC Bank")
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionLabel("Type")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountType.entries.forEach { option ->
                    CoffeeChip(
                        text = option.label,
                        selected = type == option,
                        onClick = { type = option },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionLabel("Last 4 digits")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Lets bank messages match to this account automatically. Leave blank for cash.",
                style = CoffeeType.Caption,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(10.dp))
            PaperField(
                value = tail,
                onValueChange = { tail = it.filter { char -> char.isDigit() }.take(6) },
                placeholder = "4143",
                keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionLabel("Current balance")
            Spacer(Modifier.height(10.dp))
            PaperField(
                value = balance,
                onValueChange = { balance = it.filter { char -> char.isDigit() || char == '.' } },
                placeholder = "0",
                keyboardType = KeyboardType.Decimal,
                prefix = "₹",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = balanceHelperText(existing, balanceMinor),
                style = CoffeeType.Caption,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            ToggleRow(
                title = "Count towards total balance",
                description = "Turn off for a card or account you don't want included in the Home total.",
                checked = includeInTotals,
                onCheckedChange = { includeInTotals = it },
            )
            Spacer(Modifier.height(24.dp))
        }

        item {
            CoffeeButton(
                text = "Save account",
                enabled = canSave,
                onClick = {
                    // Only a value the user actually changed counts as a fresh manual
                    // assertion — resaving an untouched figure must not steal credit from
                    // whatever really set it (a bank SMS, most often) or bump its timestamp.
                    val enteredBalance = balanceMinor
                    val balanceChanged = enteredBalance != existing?.currentBalanceMinor
                    val now = System.currentTimeMillis()
                    val newBalance: Long?
                    val newAsOf: Long?
                    val newSource: BalanceSource?
                    when {
                        !balanceChanged -> {
                            newBalance = existing?.currentBalanceMinor
                            newAsOf = existing?.balanceAsOf
                            newSource = existing?.balanceSource
                        }
                        enteredBalance != null -> {
                            newBalance = enteredBalance
                            newAsOf = now
                            newSource = BalanceSource.MANUAL
                        }
                        else -> {
                            // The field was cleared: fall back to deriving the balance again.
                            newBalance = null
                            newAsOf = null
                            newSource = null
                        }
                    }
                    onSave(
                        Account(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            displayName = name.trim(),
                            institution = institution.trim().ifEmpty { name.trim() },
                            tail = tail.trim().ifEmpty { null },
                            type = type,
                            openingBalanceMinor = existing?.openingBalanceMinor ?: (enteredBalance ?: 0L),
                            includeInTotals = includeInTotals,
                            currentBalanceMinor = newBalance,
                            balanceAsOf = newAsOf,
                            balanceSource = newSource,
                        ),
                    )
                },
            )
            if (existing != null && onDelete != null) {
                Spacer(Modifier.height(12.dp))
                QuietButton(
                    text = if (confirmDelete) "Tap again to delete" else "Delete account",
                    tint = colors.caution,
                    onClick = { if (confirmDelete) onDelete(existing.id) else confirmDelete = true },
                )
            }
        }
    }
}

private fun balanceHelperText(existing: Account?, enteredMinor: Long?): String = when {
    enteredMinor == existing?.currentBalanceMinor && enteredMinor != null ->
        "Unchanged — still counted as of ${existing?.balanceAsOf?.let { Formats.date(it) } ?: "before"}."
    enteredMinor != null -> "Will be saved as what you entered, as of right now."
    existing != null -> "Cleared: the balance will be derived from this account's own transactions instead."
    else -> "Leave blank to derive the balance from this account's own transactions instead."
}

private fun majorText(minor: Long): String = (minor / 100L).toString()
