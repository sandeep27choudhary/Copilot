package com.coffeeledger.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One clean sans-serif at a few deliberate sizes.
 *
 * Amounts use tabular figures so a column of numbers lines up on the decimal, which is the
 * difference between a ledger and a list of strings.
 */
private val Sans = FontFamily.SansSerif

private const val TABULAR = "tnum"

object CoffeeType {

    /** The single hero figure on a screen. */
    val DisplayAmount = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.2).sp,
        fontFeatureSettings = TABULAR,
    )

    /** Secondary figures: tracker targets, summary tiles. */
    val LargeAmount = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp,
        fontFeatureSettings = TABULAR,
    )

    /** Row-level figures in the transaction list. */
    val RowAmount = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = TABULAR,
    )

    val Title = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.1).sp,
    )

    val Body = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )

    val Label = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    val Caption = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )

    /** Small all-caps section markers. Used sparingly. */
    val Overline = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.1.sp,
    )
}

val CoffeeTypography = Typography(
    displayLarge = CoffeeType.DisplayAmount,
    displayMedium = CoffeeType.LargeAmount,
    headlineSmall = CoffeeType.RowAmount,
    titleMedium = CoffeeType.Title,
    bodyMedium = CoffeeType.Body,
    labelLarge = CoffeeType.Label,
    labelMedium = CoffeeType.Caption,
    labelSmall = CoffeeType.Overline,
)
