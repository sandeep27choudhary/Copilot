package com.coffeeledger.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CoffeeScheme = lightColorScheme(
    primary = Coffee.Accent,
    onPrimary = Coffee.Card,
    primaryContainer = Coffee.AccentWash,
    onPrimaryContainer = Coffee.Espresso,
    secondary = Coffee.Cocoa,
    onSecondary = Coffee.Card,
    background = Coffee.Cream,
    onBackground = Coffee.Espresso,
    surface = Coffee.Card,
    onSurface = Coffee.Espresso,
    surfaceVariant = Coffee.CardSunken,
    onSurfaceVariant = Coffee.Taupe,
    outline = Coffee.Border,
    outlineVariant = Coffee.Border,
    error = Coffee.Brick,
    onError = Coffee.Card,
    errorContainer = Coffee.BrickWash,
    onErrorContainer = Coffee.Brick,
)

/**
 * The app keeps its paper look in both system themes.
 *
 * A dark variant of this palette would be a different product: the whole design leans on
 * warm paper and ink, and inverting it makes every number read as a warning light.
 */
@Composable
fun CoffeeLedgerTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            // Paper background means dark status bar icons, whatever the system theme is.
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }

    CompositionLocalProvider(LocalCoffeeColors provides CoffeeColors()) {
        MaterialTheme(
            colorScheme = CoffeeScheme,
            typography = CoffeeTypography,
            content = content,
        )
    }
}

/** Shorthand for the extended palette. */
val coffeeColors: CoffeeColors
    @Composable get() = LocalCoffeeColors.current
