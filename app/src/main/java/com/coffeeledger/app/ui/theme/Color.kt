package com.coffeeledger.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Coffee and paper.
 *
 * The palette is deliberately narrow: two browns for text, one accent, and two muted
 * signal colours. Nothing here is saturated enough to shout, because a number that matters
 * should be the loudest thing on the screen.
 */
object Coffee {
    val Cream = Color(0xFFF6F1E8) // page
    val Card = Color(0xFFFCF8F1) // slightly lighter than the page
    val CardSunken = Color(0xFFF1EADD) // meter tracks, inset rows
    val Border = Color(0xFFE8DFCF) // barely there
    val BorderStrong = Color(0xFFDCD0BB)

    val Espresso = Color(0xFF3A2C21) // primary text and large numbers
    val Cocoa = Color(0xFF6B5847) // headings on cards
    val Taupe = Color(0xFF8B7B6B) // secondary text
    val Ash = Color(0xFFA99B8D) // tertiary text, captions

    val Accent = Color(0xFF7A5A40) // medium coffee brown
    val AccentSoft = Color(0xFFB99C82) // secondary meters
    val AccentWash = Color(0xFFEFE6D9) // selected chips

    val Moss = Color(0xFF4F6B52) // money in
    val Brick = Color(0xFF9A4A38) // over budget, needs attention
    val BrickWash = Color(0xFFF4E7E2)
}

/** The extra roles Material3 has no slot for. */
@Immutable
data class CoffeeColors(
    val page: Color = Coffee.Cream,
    val card: Color = Coffee.Card,
    val sunken: Color = Coffee.CardSunken,
    val border: Color = Coffee.Border,
    val borderStrong: Color = Coffee.BorderStrong,
    val textPrimary: Color = Coffee.Espresso,
    val textHeading: Color = Coffee.Cocoa,
    val textSecondary: Color = Coffee.Taupe,
    val textTertiary: Color = Coffee.Ash,
    val accent: Color = Coffee.Accent,
    val accentSoft: Color = Coffee.AccentSoft,
    val accentWash: Color = Coffee.AccentWash,
    val positive: Color = Coffee.Moss,
    val caution: Color = Coffee.Brick,
    val cautionWash: Color = Coffee.BrickWash,
)

val LocalCoffeeColors = staticCompositionLocalOf { CoffeeColors() }
