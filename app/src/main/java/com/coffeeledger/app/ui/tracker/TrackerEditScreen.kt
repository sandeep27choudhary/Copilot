package com.coffeeledger.app.ui.tracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.coffeeledger.app.domain.model.Tracker
import com.coffeeledger.app.domain.model.TrackerKind
import com.coffeeledger.app.domain.model.TrackerPeriod
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.ui.components.BackRow
import com.coffeeledger.app.ui.components.ChipRow
import com.coffeeledger.app.ui.components.CoffeeButton
import com.coffeeledger.app.ui.components.CoffeeChip
import com.coffeeledger.app.ui.components.PaperField
import com.coffeeledger.app.ui.components.QuietButton
import com.coffeeledger.app.ui.components.ScreenHeader
import com.coffeeledger.app.ui.components.SectionLabel
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/** The ready-made trackers offered when creating a new one. */
data class TrackerPreset(
    val title: String,
    val kind: TrackerKind,
    val period: TrackerPeriod,
    val targetMinor: Long,
    val categoryIds: List<String> = emptyList(),
)

val TRACKER_PRESETS = listOf(
    TrackerPreset("Monthly spending", TrackerKind.SPENDING_LIMIT, TrackerPeriod.MONTHLY, 1_00_000_00L),
    TrackerPreset("Groceries", TrackerKind.SPENDING_LIMIT, TrackerPeriod.MONTHLY, 15_000_00L, listOf(Category.GROCERIES.id)),
    TrackerPreset("Food delivery", TrackerKind.SPENDING_LIMIT, TrackerPeriod.MONTHLY, 6_000_00L, listOf(Category.FOOD.id)),
    TrackerPreset("Shopping", TrackerKind.SPENDING_LIMIT, TrackerPeriod.MONTHLY, 8_000_00L, listOf(Category.SHOPPING.id)),
    TrackerPreset("Travel", TrackerKind.SPENDING_LIMIT, TrackerPeriod.MONTHLY, 10_000_00L, listOf(Category.TRAVEL.id)),
    TrackerPreset("Bills", TrackerKind.SPENDING_LIMIT, TrackerPeriod.MONTHLY, 6_000_00L, listOf(Category.BILLS.id, Category.UTILITIES.id)),
    TrackerPreset("Savings", TrackerKind.SAVINGS_TARGET, TrackerPeriod.MONTHLY, 30_000_00L),
    TrackerPreset("Emergency fund", TrackerKind.GOAL, TrackerPeriod.ALL_TIME, 3_00_000_00L),
    TrackerPreset("Investment contribution", TrackerKind.SPENDING_LIMIT, TrackerPeriod.MONTHLY, 20_000_00L, listOf(Category.INVESTMENT.id)),
)

/**
 * Create or edit a tracker.
 *
 * A tracker is a title, a target and a filter. Keeping it to those three things is what
 * lets one screen cover a monthly cap, a category limit and a long-running goal.
 */
@Composable
fun TrackerEditScreen(
    existing: Tracker?,
    accounts: List<Account>,
    onSave: (Tracker) -> Unit,
    onDelete: ((String) -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: TrackerKind.SPENDING_LIMIT) }
    var period by remember { mutableStateOf(existing?.period ?: TrackerPeriod.MONTHLY) }
    var target by remember { mutableStateOf(existing?.targetMinor?.let { majorText(it) }.orEmpty()) }
    var manual by remember { mutableStateOf(existing?.manualProgressMinor?.takeIf { it > 0 }?.let { majorText(it) }.orEmpty()) }
    var categoryIds by remember { mutableStateOf(existing?.categoryIds?.toSet() ?: emptySet()) }
    var accountIds by remember { mutableStateOf(existing?.accountIds?.toSet() ?: emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val targetMinor = Money.parseAmount(target) ?: 0L
    val canSave = title.isNotBlank() && targetMinor > 0L

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
    ) {
        item {
            BackRow(label = "Tracker", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            ScreenHeader(title = if (existing == null) "New tracker" else "Edit tracker")
            Spacer(Modifier.height(24.dp))
        }

        if (existing == null) {
            item {
                SectionLabel("Start from")
                Spacer(Modifier.height(10.dp))
                ChipRow(
                    items = TRACKER_PRESETS,
                    selected = { it.title == title },
                    label = { it.title },
                    onSelect = { preset ->
                        title = preset.title
                        kind = preset.kind
                        period = preset.period
                        target = majorText(preset.targetMinor)
                        categoryIds = preset.categoryIds.toSet()
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        item {
            SectionLabel("Name")
            Spacer(Modifier.height(10.dp))
            PaperField(
                value = title,
                onValueChange = { title = it },
                placeholder = "Groceries, Emergency fund, …",
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionLabel("Type")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackerKind.entries.forEach { option ->
                    CoffeeChip(
                        text = option.label,
                        selected = kind == option,
                        onClick = {
                            kind = option
                            if (option == TrackerKind.GOAL) period = TrackerPeriod.ALL_TIME
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (kind) {
                    TrackerKind.SPENDING_LIMIT -> "Counts what you spend. Transfers between your own accounts are excluded."
                    TrackerKind.SAVINGS_TARGET -> "Counts money arriving in the accounts you pick."
                    TrackerKind.GOAL -> "A long-running target. Record what you have already put aside below."
                },
                style = CoffeeType.Caption,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionLabel("Resets")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackerPeriod.entries.forEach { option ->
                    CoffeeChip(
                        text = option.label,
                        selected = period == option,
                        onClick = { period = option },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionLabel("Target")
            Spacer(Modifier.height(10.dp))
            PaperField(
                value = target,
                onValueChange = { target = it.filter { char -> char.isDigit() || char == '.' } },
                placeholder = "0",
                keyboardType = KeyboardType.Decimal,
                prefix = "₹",
            )
            if (targetMinor > 0L) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Money.format(targetMinor),
                    style = CoffeeType.Caption,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        if (kind == TrackerKind.SPENDING_LIMIT) {
            item {
                SectionLabel("Categories")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Leave empty to track everything you spend.",
                    style = CoffeeType.Caption,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(10.dp))
                ChipRow(
                    items = Category.debits(),
                    selected = { it.id in categoryIds },
                    label = { it.label },
                    onSelect = { category ->
                        categoryIds = if (category.id in categoryIds) {
                            categoryIds - category.id
                        } else {
                            categoryIds + category.id
                        }
                    },
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        if (kind == TrackerKind.SAVINGS_TARGET && accounts.isNotEmpty()) {
            item {
                SectionLabel("Accounts")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Money arriving in these accounts counts towards the target.",
                    style = CoffeeType.Caption,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(10.dp))
                ChipRow(
                    items = accounts,
                    selected = { it.id in accountIds },
                    label = { it.displayName },
                    onSelect = { account ->
                        accountIds = if (account.id in accountIds) {
                            accountIds - account.id
                        } else {
                            accountIds + account.id
                        }
                    },
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        if (kind == TrackerKind.GOAL) {
            item {
                SectionLabel("Already saved")
                Spacer(Modifier.height(10.dp))
                PaperField(
                    value = manual,
                    onValueChange = { manual = it.filter { char -> char.isDigit() || char == '.' } },
                    placeholder = "0",
                    keyboardType = KeyboardType.Decimal,
                    prefix = "₹",
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        item {
            CoffeeButton(
                text = "Save tracker",
                enabled = canSave,
                onClick = {
                    onSave(
                        Tracker(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            title = title.trim(),
                            kind = kind,
                            period = period,
                            targetMinor = targetMinor,
                            categoryIds = categoryIds.toList(),
                            merchantNames = existing?.merchantNames.orEmpty(),
                            accountIds = accountIds.toList(),
                            manualProgressMinor = Money.parseAmount(manual) ?: 0L,
                            sortOrder = existing?.sortOrder ?: 99,
                            archived = false,
                        ),
                    )
                },
            )
            if (existing != null && onDelete != null) {
                Spacer(Modifier.height(12.dp))
                QuietButton(
                    text = if (confirmDelete) "Tap again to delete" else "Delete tracker",
                    tint = colors.caution,
                    onClick = { if (confirmDelete) onDelete(existing.id) else confirmDelete = true },
                )
            }
        }
    }
}

private fun majorText(minor: Long): String = (minor / 100L).toString()
