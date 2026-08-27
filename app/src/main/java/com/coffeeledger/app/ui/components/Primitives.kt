package com.coffeeledger.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/** A calm section marker. Used instead of a heavier card header. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = CoffeeType.Overline,
        color = coffeeColors.textTertiary,
        modifier = modifier,
    )
}

/** The standard paper card: one hairline border, generous padding, no shadow. */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(20.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = coffeeColors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * A horizontal progress meter.
 *
 * Meters are the app's main visual device, so this one stays plain on purpose: a track, a
 * fill, and nothing else. Over-budget fills turn brick red rather than growing past the end.
 */
@Composable
fun MeterBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    height: androidx.compose.ui.unit.Dp = 8.dp,
    animate: Boolean = true,
) {
    val colors = coffeeColors
    val clamped = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = clamped, label = "meter")
    val target = if (animate) animated else clamped
    val fill = color ?: if (fraction > 1f) colors.caution else colors.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(colors.sunken),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(target)
                .clip(RoundedCornerShape(height / 2))
                .background(fill),
        )
    }
}

/**
 * A complete meter row: title, value over target, the bar, and the percentage.
 * This is the shape used on Home, on Tracker and inside insights.
 */
@Composable
fun MeterRow(
    title: String,
    currentMinor: Long,
    targetMinor: Long,
    percent: Int,
    modifier: Modifier = Modifier,
    caption: String? = null,
    over: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = coffeeColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = title,
                style = CoffeeType.Title,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "$percent%",
                style = CoffeeType.Label,
                color = if (over) colors.caution else colors.textSecondary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${Money.format(currentMinor)} / ${Money.format(targetMinor)}",
            style = CoffeeType.Body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        MeterBar(
            fraction = if (targetMinor <= 0L) 0f else currentMinor.toFloat() / targetMinor,
            color = if (over) colors.caution else null,
        )
        if (caption != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = caption, style = CoffeeType.Caption, color = colors.textTertiary)
        }
    }
}

/** A labelled figure. The number leads; the label explains it underneath. */
@Composable
fun AmountTile(
    amountMinor: Long,
    label: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = CoffeeType.LargeAmount,
    color: Color? = null,
    prefix: String = "",
) {
    val colors = coffeeColors
    Column(modifier = modifier) {
        Text(
            text = prefix + Money.format(amountMinor),
            style = style,
            color = color ?: colors.textPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(text = label, style = CoffeeType.Caption, color = colors.textSecondary)
    }
}

/** A hairline rule used between rows instead of another card. */
@Composable
fun HairLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(coffeeColors.border),
    )
}

/** Empty states are text, not illustrations: quiet, specific and actionable. */
@Composable
fun EmptyNote(title: String, body: String, modifier: Modifier = Modifier) {
    val colors = coffeeColors
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 32.dp)) {
        Text(text = title, style = CoffeeType.Title, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(text = body, style = CoffeeType.Body, color = colors.textSecondary)
    }
}
