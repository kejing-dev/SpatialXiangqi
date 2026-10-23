package com.kejing.spatialxiangqi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kejing.spatialxiangqi.R
import com.pico.spatial.ui.design.ColorScheme
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Typography
import com.pico.spatial.ui.design.systemColorScheme

/**
 * Design tokens (design-doc §3). Semantic roles drive the SpatialUI ColorScheme;
 * brand colors without a PICO role stay named tokens.
 */
object XiangqiColors {
    // ColorScheme roles overridden with exact design values
    val labelPrimaryLight = Color(0xFFF7F1E4) // design-style: fixed-figma-color design-doc §3 tokens；宣纸白
    val error = Color(0xFFD64545) // design-style: fixed-figma-color design-doc §3 tokens
    val alert = Color(0xFFE8A33D) // design-style: fixed-figma-color design-doc §3 tokens
    val passable = Color(0xFF3E9B6A) // design-style: fixed-figma-color design-doc §3 tokens
    val interaction = Color(0xFFE8A33D) // design-style: fixed-figma-color design-doc §3 tokens；琥珀
    // Label roles pinned to ink tones: the Vibrant defaults read grey on light glass in a dim room (device feedback 2026-09-23)
    val labelPrimary = Color(0xFF000000) // design-style: fixed-figma-color design-doc §3 tokens；墨
    val labelSecondary = Color(0xFF161311) // design-style: fixed-figma-color design-doc §3 tokens
    val labelTertiary = Color(0xFF2E2925) // design-style: fixed-figma-color design-doc §3 tokens
    val labelQuaternary = Color(0xFF5A534C) // design-style: fixed-figma-color design-doc §3 tokens

    // Brand / decorative tokens (no ColorScheme role)
    val cinnabar = Color(0xFFB8322A) // design-style: fixed-figma-color design-doc §3 brand tokens
    val ink = Color(0xFF1F1B18) // design-style: fixed-figma-color design-doc §3 tokens
    val paper = Color(0xFFF4ECD8) // design-style: fixed-figma-color design-doc §3 tokens
    val amber = Color(0xFFE8A33D) // design-style: fixed-figma-color design-doc §3 tokens
    val brass = Color(0xFFC9A24A) // design-style: fixed-figma-color design-doc §3 tokens
    val rosewood = Color(0xFF5A2E1E) // design-style: fixed-figma-color design-doc §3 tokens
}

/** Complete 16-role scheme: exact design values for the semantic roles, Vibrant inheritance for the rest. */
@Composable
fun xiangqiColorScheme(): ColorScheme {
    val system = systemColorScheme(LocalContext.current)
    return system.copy(
        fillPrimary = system.fillPrimary,
        fillSecondary = system.fillSecondary,
        fillTertiary = system.fillTertiary,
        fillLight = system.fillLight,
        labelPrimaryLight = XiangqiColors.labelPrimaryLight,
        labelPrimary = XiangqiColors.labelPrimary,
        labelSecondary = XiangqiColors.labelSecondary,
        labelTertiary = XiangqiColors.labelTertiary,
        labelQuaternary = XiangqiColors.labelQuaternary,
        lightenHover = system.lightenHover,
        lightenPressed = system.lightenPressed,
        error = XiangqiColors.error,
        alert = XiangqiColors.alert,
        passable = XiangqiColors.passable,
        interaction = XiangqiColors.interaction,
        dividerLine = system.dividerLine,
    )
}

/** Noto Sans SC (variable) for every type role; piece glyphs use the same family at weight 700. */
val NotoSansFamily = FontFamily(
    Font(R.font.notosanssc, FontWeight.Normal),
    Font(R.font.notosanssc, FontWeight.Medium),
    Font(R.font.notosanssc, FontWeight.SemiBold),
    Font(R.font.notosanssc, FontWeight.Bold),
)

/** Noto Sans at a guaranteed minimum weight: body/label text reads grey at 400 on glass, so nothing goes below Medium. */
private fun TextStyle.noto(minWeight: FontWeight = FontWeight.Medium): TextStyle {
    val w = fontWeight
    return copy(fontFamily = NotoSansFamily, fontWeight = if (w == null || w.weight < minWeight.weight) minWeight else w)
}

@Composable
fun xiangqiTypography(): Typography {
    val t = PicoTheme.typography
    return t.copy(
        bodyLarge = t.bodyLarge.noto(),
        bodyLargeMultiline = t.bodyLargeMultiline.noto(),
        bodyMedium = t.bodyMedium.noto(),
        bodyMediumMultiline = t.bodyMediumMultiline.noto(),
        bodySmall = t.bodySmall.noto(),
        bodyTiny = t.bodyTiny.noto(),
        displayLarge = t.displayLarge.noto(FontWeight.SemiBold),
        displayMedium = t.displayMedium.noto(FontWeight.SemiBold),
        displaySmall = t.displaySmall.noto(FontWeight.SemiBold),
        headlineLarge = t.headlineLarge.noto(FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.noto(FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.noto(FontWeight.SemiBold),
        labelLarge = t.labelLarge.noto(),
        labelMedium = t.labelMedium.noto(),
        labelSmall = t.labelSmall.noto(),
        titleLarge = t.titleLarge.noto(FontWeight.SemiBold),
        titleMedium = t.titleMedium.noto(FontWeight.SemiBold),
        titleSmall = t.titleSmall.noto(FontWeight.SemiBold),
    )
}

/** App theme wrapper: complete custom ColorScheme + Noto Sans typography injected into PicoTheme. */
@Composable
fun XiangqiTheme(content: @Composable () -> Unit) {
    PicoTheme(colorScheme = xiangqiColorScheme(), typography = xiangqiTypography(), content = content)
}
