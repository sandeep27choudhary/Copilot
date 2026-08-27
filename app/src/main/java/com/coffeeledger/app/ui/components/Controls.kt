package com.coffeeledger.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/** The screen title block. No app bar chrome, no elevation, just a heading on paper. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = coffeeColors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = CoffeeType.LargeAmount, color = colors.textPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(text = subtitle, style = CoffeeType.Body, color = colors.textSecondary)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A back affordance that reads as text rather than as a floating icon. */
@Composable
fun BackRow(label: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = coffeeColors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onBack)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "‹", style = CoffeeType.Title, color = colors.accent)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = CoffeeType.Label, color = colors.accent)
    }
}

/** Primary action. Solid coffee brown, generous height, no gradient. */
@Composable
fun CoffeeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = coffeeColors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) colors.accent else colors.sunken)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = CoffeeType.Label,
            color = if (enabled) colors.card else colors.textTertiary,
        )
    }
}

/** Secondary action. Outlined, same height, quieter. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val colors = coffeeColors
    val accent = tint ?: colors.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.borderStrong, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = CoffeeType.Label, color = accent)
    }
}

/** A selectable pill. Selection is a wash of accent, never a saturated block. */
@Composable
fun CoffeeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) colors.accentWash else colors.card)
            .border(
                1.dp,
                if (selected) colors.accentSoft else colors.border,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = CoffeeType.Label,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun <T> ChipRow(
    items: List<T>,
    selected: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
    ) {
        items(items) { item ->
            CoffeeChip(text = label(item), selected = selected(item), onClick = { onSelect(item) })
        }
    }
}

/** A bordered text field that matches the paper look; no Material filled background. */
@Composable
fun PaperField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    prefix: String? = null,
) {
    val colors = coffeeColors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix != null) {
            Text(text = prefix, style = CoffeeType.Title, color = colors.textSecondary)
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(text = placeholder, style = CoffeeType.Body, color = colors.textTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = CoffeeType.Body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A settings row: title, supporting line, and a switch. */
@Composable
fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = CoffeeType.Title, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(text = description, style = CoffeeType.Caption, color = colors.textSecondary)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.card,
                checkedTrackColor = colors.accent,
                checkedBorderColor = colors.accent,
                uncheckedThumbColor = colors.card,
                uncheckedTrackColor = colors.sunken,
                uncheckedBorderColor = colors.borderStrong,
            ),
        )
    }
}

/** A tappable settings row with a chevron. */
@Composable
fun NavigationRow(
    title: String,
    description: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    tint: Color? = null,
) {
    val colors = coffeeColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = CoffeeType.Title, color = tint ?: colors.textPrimary)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = description, style = CoffeeType.Caption, color = colors.textSecondary)
            }
        }
        if (trailingText != null) {
            Text(text = trailingText, style = CoffeeType.Label, color = colors.textSecondary)
            Spacer(Modifier.width(8.dp))
        }
        Text(text = "›", style = CoffeeType.Title, color = colors.textTertiary)
    }
}
