package dev.shadowcrawler.flashcards.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Slate400,
    secondary = Blue600,

    background = Navy900,
    onBackground = Gray100,

    surface = Navy800,
    onSurface = Gray100,

    surfaceVariant = Blue600,
    onSurfaceVariant = Gray100,

    onPrimary = Navy900,
    onSecondary = Gray100
)

@Composable
fun FlashcardsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
