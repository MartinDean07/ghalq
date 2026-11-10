package com.martdean.cutoff.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.martdean.cutoff.R

/** Design tokens shared with Martin Dean's other apps (calm, dark-first). */
@Immutable
data class CutoffColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val danger: Color,
    val ok: Color,
    val dark: Boolean,
)

val DarkTokens = CutoffColors(
    bg = Color(0xFF0D0F13), surface = Color(0xFF15181E), surface2 = Color(0xFF1E222A),
    line = Color(0xFF232832), text = Color(0xFFECEEF2), muted = Color(0xFF8B93A1),
    faint = Color(0xFF596170), accent = Color(0xFF8B9BFF), danger = Color(0xFFFF4D5E),
    ok = Color(0xFF4CC38A), dark = true,
)

val LightTokens = CutoffColors(
    bg = Color(0xFFF7F8FA), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEEF0F4),
    line = Color(0xFFE4E7EC), text = Color(0xFF12151A), muted = Color(0xFF5D6574),
    faint = Color(0xFFA0A7B3), accent = Color(0xFF5B6CF0), danger = Color(0xFFE5394B),
    ok = Color(0xFF23A26A), dark = false,
)

val LocalTokens = staticCompositionLocalOf { DarkTokens }

private fun sans(weight: Int) = Font(
    R.font.open_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun arabic(weight: Int) = Font(
    R.font.cairo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val OpenSans = FontFamily(sans(400), sans(500), sans(600), sans(700))
val Cairo = FontFamily(arabic(400), arabic(500), arabic(600), arabic(700))

@Composable
fun CutoffTheme(content: @Composable () -> Unit) {
    val tokens = if (isSystemInDarkTheme()) DarkTokens else LightTokens
    val arabicUi = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "ar"
    val family = if (arabicUi) Cairo else OpenSans
    val base = TextStyle(fontFamily = family, color = tokens.text)
    val typography = Typography(
        headlineMedium = base.copy(fontSize = 26.sp, fontWeight = FontWeight(650), lineHeight = 32.sp),
        titleMedium = base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.copy(fontSize = 16.sp, lineHeight = if (arabicUi) 28.sp else 22.sp),
        bodyMedium = base.copy(fontSize = 14.sp, lineHeight = if (arabicUi) 24.sp else 20.sp),
        labelMedium = base.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
        labelSmall = base.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
    val scheme = if (tokens.dark) {
        darkColorScheme(
            primary = tokens.accent, background = tokens.bg, surface = tokens.surface,
            onBackground = tokens.text, onSurface = tokens.text, outline = tokens.line,
            surfaceVariant = tokens.surface2, onSurfaceVariant = tokens.muted,
        )
    } else {
        lightColorScheme(
            primary = tokens.accent, background = tokens.bg, surface = tokens.surface,
            onBackground = tokens.text, onSurface = tokens.text, outline = tokens.line,
            surfaceVariant = tokens.surface2, onSurfaceVariant = tokens.muted,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
