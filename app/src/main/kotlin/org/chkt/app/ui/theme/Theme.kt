package org.chkt.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Lightmorphic brand colours: yellow is the accent, navy grounds it, and the
// CHKT green stays for the identity marks (icon, tick).
private val BrandYellow = Color(0xFFFBC711)
private val OnYellow = Color(0xFF645007)
private val YellowContainerLight = Color(0xFFFFF8E2)
private val OnYellowContainerLight = Color(0xFF5F4C06)
private val YellowContainerDark = Color(0xFF4B3C05)
private val OnYellowContainerDark = Color(0xFFFDE694)
private val BrandNavy = Color(0xFF111827)
private val ChktGreen = Color(0xFF1B5E4A)

private val LightColors = lightColorScheme(
    primary = BrandYellow,
    onPrimary = OnYellow,
    primaryContainer = YellowContainerLight,
    onPrimaryContainer = OnYellowContainerLight,
    secondary = BrandNavy,
    secondaryContainer = YellowContainerLight,
    onSecondaryContainer = OnYellowContainerLight,
    tertiary = ChktGreen,
)

private val DarkColors = darkColorScheme(
    primary = BrandYellow,
    onPrimary = OnYellow,
    primaryContainer = YellowContainerDark,
    onPrimaryContainer = OnYellowContainerDark,
    secondary = Color(0xFF9CA3AF),
    secondaryContainer = YellowContainerDark,
    onSecondaryContainer = OnYellowContainerDark,
    tertiary = Color(0xFF2E8B6F),
)

@Composable
fun ChktTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
