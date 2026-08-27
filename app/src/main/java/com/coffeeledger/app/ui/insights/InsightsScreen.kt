package com.coffeeledger.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.domain.analytics.HealthScore
import com.coffeeledger.app.domain.analytics.Insight
import com.coffeeledger.app.domain.analytics.InsightTone
import com.coffeeledger.app.domain.analytics.MonthPoint
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.ui.LedgerUiState
import com.coffeeledger.app.ui.components.EmptyNote
import com.coffeeledger.app.ui.components.HairLine
import com.coffeeledger.app.ui.components.MeterBar
import com.coffeeledger.app.ui.components.PaperCard
import com.coffeeledger.app.ui.components.ScreenHeader
import com.coffeeledger.app.ui.components.SectionLabel
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/**
 * What the numbers add up to: a trend, where the money goes, who it goes to, and a health
 * score that always shows its working.
 */
@Composable
fun InsightsScreen(
    state: LedgerUiState,
    onOpenAdvisor: () -> Unit,
    onDismissInsight: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    val topCategories = state.topCategories.take(6)
    val topMerchants = state.topMerchants.sortedByDescending { it.count }.take(6)
    val largestCategory = topCategories.maxOfOrNull { it.amountMinor } ?: 1L

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
    ) {
        item {
            ScreenHeader(title = "Insights", subtitle = "Worked out on this device")
            Spacer(Modifier.height(24.dp))
        }

        if (!state.hasData) {
            item {
                EmptyNote(
                    title = "Nothing to analyse yet",
                    body = "Once there are transactions, trends, categories and a health score appear here.",
                )
            }
            return@LazyColumn
        }

        if (state.insights.isNotEmpty()) {
            item {
                SectionLabel("Noticed")
                Spacer(Modifier.height(12.dp))
            }
            items(state.insights.size, key = { state.insights[it].id }) { index ->
                InsightCard(state.insights[index], onDismissInsight)
                Spacer(Modifier.height(10.dp))
            }
            item { Spacer(Modifier.height(14.dp)) }
        }

        item {
            PaperCard(onClick = onOpenAdvisor) {
                Text("Ask about your money", style = CoffeeType.Title, color = colors.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Put a question to your own ledger. Nothing is sent anywhere.",
                    style = CoffeeType.Body,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            SectionLabel("Spending trend")
            Spacer(Modifier.height(12.dp))
            PaperCard {
                SpendTrendChart(state.trend)
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            SectionLabel("Top categories")
            Spacer(Modifier.height(12.dp))
            PaperCard {
                topCategories.forEachIndexed { index, total ->
                    if (index > 0) Spacer(Modifier.height(16.dp))
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = total.category.label,
                                style = CoffeeType.Body,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = Money.format(total.amountMinor),
                                style = CoffeeType.RowAmount,
                                color = colors.textPrimary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        MeterBar(
                            fraction = total.amountMinor.toFloat() / largestCategory,
                            color = if (index == 0) colors.accent else colors.accentSoft,
                            height = 6.dp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            SectionLabel("Top merchants")
            Spacer(Modifier.height(12.dp))
            PaperCard {
                topMerchants.forEachIndexed { index, merchant ->
                    if (index > 0) HairLine()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = merchant.merchant,
                                style = CoffeeType.Body,
                                color = colors.textPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${merchant.count} payments",
                                style = CoffeeType.Caption,
                                color = colors.textTertiary,
                            )
                        }
                        Text(
                            text = Money.format(merchant.amountMinor),
                            style = CoffeeType.RowAmount,
                            color = colors.textPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (state.recurring.isNotEmpty()) {
            item {
                SectionLabel("Recurring")
                Spacer(Modifier.height(12.dp))
                PaperCard {
                    Text(
                        text = "${Money.format(state.recurring.sumOf { it.monthlyEquivalentMinor })} a month",
                        style = CoffeeType.LargeAmount,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${state.recurring.size} payments that repeat on a regular cadence.",
                        style = CoffeeType.Caption,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    state.recurring.take(5).forEachIndexed { index, item ->
                        if (index > 0) HairLine()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.merchant, style = CoffeeType.Body, color = colors.textPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "about every ${item.averageIntervalDays} days",
                                    style = CoffeeType.Caption,
                                    color = colors.textTertiary,
                                )
                            }
                            Text(
                                text = Money.format(item.typicalAmountMinor),
                                style = CoffeeType.RowAmount,
                                color = colors.textPrimary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        item {
            SectionLabel("Financial health")
            Spacer(Modifier.height(12.dp))
            HealthCard(state.health)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InsightCard(insight: Insight, onDismiss: (String) -> Unit) {
    val colors = coffeeColors
    val accent = when (insight.tone) {
        InsightTone.CAUTION -> colors.caution
        InsightTone.POSITIVE -> colors.positive
        InsightTone.NEUTRAL -> colors.accent
    }
    PaperCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(insight.title, style = CoffeeType.Title, color = colors.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(insight.evidence, style = CoffeeType.Caption, color = colors.textSecondary)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "×",
                style = CoffeeType.Title,
                color = colors.textTertiary,
                modifier = Modifier.clickable { onDismiss(insight.id) }.padding(4.dp),
            )
        }
    }
}

/**
 * The health score with every component that produced it.
 *
 * The breakdown is expanded by default the first time it is looked at: a score without its
 * working is just a number to argue with.
 */
@Composable
private fun HealthCard(health: HealthScore) {
    val colors = coffeeColors
    var expanded by remember { mutableStateOf(true) }
    PaperCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = health.score.toString(),
                style = CoffeeType.DisplayAmount,
                color = colors.textPrimary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.padding(bottom = 6.dp)) {
                Text(text = "/ 100", style = CoffeeType.Caption, color = colors.textTertiary)
                Text(text = health.label, style = CoffeeType.Label, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(14.dp))
        MeterBar(health.score / 100f, color = colors.accent)
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (expanded) "Hide how this is worked out" else "How is this worked out?",
            style = CoffeeType.Label,
            color = colors.accent,
            modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
        )
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            health.components.forEach { component ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = component.name,
                            style = CoffeeType.Body,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = if (component.weight == 0) "not counted" else "${component.score} · ${component.weight}%",
                            style = CoffeeType.Caption,
                            color = colors.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = component.explanation,
                        style = CoffeeType.Caption,
                        color = colors.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Weights are fixed and shown above. Components with no data are left out and the rest are re-weighted.",
                style = CoffeeType.Caption,
                color = colors.textTertiary,
            )
        }
    }
}

/**
 * A plain column chart. Bars, a baseline and month initials — no axes, no gridlines, no
 * tooltips, because the shape is the message and the exact figures live elsewhere.
 */
@Composable
private fun SpendTrendChart(points: List<MonthPoint>, modifier: Modifier = Modifier) {
    val colors = coffeeColors
    if (points.isEmpty()) return
    val max = points.maxOf { it.spendMinor }.coerceAtLeast(1L)

    Column(modifier.fillMaxWidth()) {
        Text(
            text = Money.format(points.last().spendMinor),
            style = CoffeeType.LargeAmount,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "spent in ${points.last().label}",
            style = CoffeeType.Caption,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEachIndexed { index, point ->
                val fraction = (point.spendMinor.toFloat() / max).coerceIn(0.02f, 1f)
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                if (index == points.lastIndex) colors.accent else colors.accentSoft,
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            points.forEach { point ->
                Text(
                    text = point.label.take(3),
                    style = CoffeeType.Caption,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
