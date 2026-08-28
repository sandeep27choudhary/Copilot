package com.coffeeledger.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.domain.advisor.AdvisorAnswer
import com.coffeeledger.app.domain.advisor.AdvisorContext
import com.coffeeledger.app.domain.advisor.LocalAdvisor
import com.coffeeledger.app.ui.LedgerUiState
import com.coffeeledger.app.ui.components.BackRow
import com.coffeeledger.app.ui.components.CoffeeChip
import com.coffeeledger.app.ui.components.PaperCard
import com.coffeeledger.app.ui.components.PaperField
import com.coffeeledger.app.ui.components.ScreenHeader
import com.coffeeledger.app.ui.components.SectionLabel
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/** One question and the answer worked out for it. */
data class AdvisorExchange(val question: String, val answer: AdvisorAnswer)

/**
 * "Ask about your money".
 *
 * The advisor matches a question to an intent, runs a query over the in-memory ledger and
 * assembles the answer from the result. There is no model call and no network call: the
 * banner at the top of the screen says exactly that, and it is true by construction.
 */
@Composable
fun AdvisorScreen(
    state: LedgerUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    var question by remember { mutableStateOf("") }
    val exchanges: SnapshotStateList<AdvisorExchange> = remember { mutableListOf<AdvisorExchange>().toMutableStateList() }
    val listState = rememberLazyListState()

    val context = remember(state.snapshot, state.now) {
        AdvisorContext(
            txns = state.snapshot.transactions,
            trackerProgress = state.trackers,
            now = state.now,
            accounts = state.snapshot.accounts,
        )
    }

    fun ask(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        exchanges.add(AdvisorExchange(trimmed, LocalAdvisor.answer(trimmed, context)))
        question = ""
    }

    LaunchedEffect(exchanges.size) {
        if (exchanges.isNotEmpty()) listState.animateScrollToItem(exchanges.size)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            BackRow(label = "Back", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            ScreenHeader(
                title = "Ask about your money",
                subtitle = "Answered from your transactions, on this device.",
            )
            Spacer(Modifier.height(16.dp))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            if (exchanges.isEmpty()) {
                item {
                    PaperCard {
                        Text(
                            text = "Nothing you type here leaves the phone.",
                            style = CoffeeType.Title,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Questions are matched against your own ledger and answered locally. " +
                                "The answers describe your spending; they are not investment advice.",
                            style = CoffeeType.Body,
                            color = colors.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("Try asking")
                    Spacer(Modifier.height(12.dp))
                }
                items(LocalAdvisor.suggestedQuestions.size) { index ->
                    val suggestion = LocalAdvisor.suggestedQuestions[index]
                    Text(
                        text = suggestion,
                        style = CoffeeType.Body,
                        color = colors.accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ask(suggestion) }
                            .padding(vertical = 10.dp),
                    )
                }
            }

            items(exchanges.size) { index ->
                val exchange = exchanges[index]
                Column(Modifier.padding(bottom = 20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            text = exchange.question,
                            style = CoffeeType.Body,
                            color = colors.textPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.accentWash)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PaperCard {
                        Text(
                            text = exchange.answer.headline,
                            style = CoffeeType.LargeAmount,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(12.dp))
                        exchange.answer.detail.forEach { line ->
                            Text(
                                text = line,
                                style = CoffeeType.Body,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        if (exchange.answer.isPlanning) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.sunken)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "General information, not investment advice",
                                    style = CoffeeType.Caption,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (exchanges.isNotEmpty()) {
            LazyRowSuggestions(onAsk = { ask(it) })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.page)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaperField(
                value = question,
                onValueChange = { question = it },
                placeholder = "Ask about your money",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Ask",
                style = CoffeeType.Label,
                color = if (question.isBlank()) colors.textTertiary else colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .clickable(enabled = question.isNotBlank()) { ask(question) }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
            )
        }
    }
}

/** Follow-up prompts, kept to one scrolling line so the conversation stays the focus. */
@Composable
private fun LazyRowSuggestions(onAsk: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(LocalAdvisor.suggestedQuestions.size) { index ->
            val suggestion = LocalAdvisor.suggestedQuestions[index]
            CoffeeChip(text = suggestion, selected = false, onClick = { onAsk(suggestion) })
        }
    }
}
