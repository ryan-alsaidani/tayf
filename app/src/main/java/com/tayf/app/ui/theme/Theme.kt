package com.tayf.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import java.util.Locale

private val TayfColorScheme = darkColorScheme(
    primary = TayfGold,
    onPrimary = TayfBrown,
    primaryContainer = TayfSurface,
    onPrimaryContainer = TayfGoldLight,
    secondary = TayfGoldLight,
    onSecondary = TayfBrown,
    secondaryContainer = TayfSurface,
    onSecondaryContainer = TayfGoldLight,
    background = TayfBrown,
    onBackground = TayfText,
    surface = TayfBrownLight,
    onSurface = TayfText,
    surfaceVariant = TayfSurface,
    onSurfaceVariant = TayfTextDim,
    surfaceContainerLowest = TayfBrown,
    surfaceContainerLow = TayfBrownLight,
    surfaceContainer = TayfBrownLight,
    surfaceContainerHigh = TayfSurface,
    surfaceContainerHighest = TayfSurface,
    outline = TayfTextDim,
    outlineVariant = TayfSurface,
    error = TayfDanger,
    onError = TayfBrown
)

/** Dark-only premium theme; layout direction follows the locale (Arabic = RTL). */
@Composable
fun TayfTheme(content: @Composable () -> Unit) {
    val rtl = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == ViewCompat.LAYOUT_DIRECTION_RTL
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = TayfColorScheme, typography = TayfTypography, content = content)
    }
}
