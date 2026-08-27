package com.coffeeledger.app.ui.tracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.domain.analytics.TimeRanges
import com.coffeeledger.app.domain.model.TrackerKind
import com.coffeeledger.app.domain.model.TrackerProgress
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.ui.LedgerUiState
import com.coffeeledger.app.ui.components.EmptyNote
import com.coffeeledger.app.ui.components.MeterRow
import com.coffeeledger.app.ui.components.PaperCard
import com.coffeeledger.app.ui.components.ScreenHeader
import com.coffeeledger.app.ui.components.SectionLabel
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/**
 * The tracker board.
 *
 * Spending limits and savings goals are the same primitive shown in two groups, because
 * "how much is left" and "how far have I got" are the two questions a budget answers.
 */
@Composable
fun TrackerScreen(
    state: LedgerUiState,
    onOpenTracker: (String) -> Unit,
    onNewTracker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    val limits = state.trackers.filter { it.tracker.kind == TrackerKind.SPENDING_LIMIT }
    val goals = state.trackers.filter { it.tracker.kind != TrackerKind.SPENDING_LIMIT }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
    ) {
        item {
            ScreenHeader(
                title = "Tracker",
                subtitle = TimeRanges.monthLabel(TimeRanges.yearMonthOf(state.now)),
                trailing = {
                    Text(
                        text = "New",
                        style = CoffeeType.Label,
                        color = colors.accent,
                        modifier = Modifier.clickable(onClick = onNewTracker).padding(6.dp),
                    )
                },
            )
            Spacer(Modifier.height(20.dp))
        }

        if (state.trackers.isEmpty()) {
            item {
                EmptyNote(
                    title = "No trackers yet",
                    body = "Create a monthly limit, a category cap, or a savings goal. Progress is worked out from your transactions.",
                )
            }
        }

        if (limits.isNotEmpty()) {
            item {
                SectionLabel("Spending limits")
                Spacer(Modifier.height(12.dp))
            }
            items(limits.size, key = { limits[it].tracker.id }) { index ->
                TrackerCard(limits[index], state.now, onOpenTracker)
                Spacer(Modifier.height(12.dp))
            }
        }

        if (goals.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                SectionLabel("Savings and goals")
                Spacer(Modifier.height(12.dp))
            }
            items(goals.size, key = { goals[it].tracker.id }) { index ->
                TrackerCard(goals[index], state.now, onOpenTracker)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun TrackerCard(
    progress: TrackerProgress,
    now: Long,
    onOpen: (String) -> Unit,
) {
    PaperCard(onClick = { onOpen(progress.tracker.id) }) {
        MeterRow(
            title = progress.tracker.title,
            currentMinor = progress.currentMinor,
            targetMinor = progress.targetMinor,
            percent = progress.percent,
            over = progress.isOver,
            caption = caption(progress, now),
        )
    }
}

/** The single sentence under a meter that says what to do about it. */
private fun caption(progress: TrackerProgress, now: Long): String {
    val tracker = progress.tracker
    return when {
        tracker.kind == TrackerKind.SPENDING_LIMIT && progress.isOver ->
            "Over by ${Money.format(progress.overBy)}."
        tracker.kind == TrackerKind.SPENDING_LIMIT -> {
            val remaining = Money.format(progress.remainingMinor)
            val daysLeft = daysLeftInMonth(now)
            if (daysLeft > 0) "$remaining left, $daysLeft days to go." else "$remaining left."
        }
        progress.fraction >= 1f -> "Target reached."
        else -> "${Money.format(progress.remainingMinor)} to go."
    }
}

private fun daysLeftInMonth(now: Long): Int {
    val date = TimeRanges.dateOf(now)
    return date.lengthOfMonth() - date.dayOfMonth
}
