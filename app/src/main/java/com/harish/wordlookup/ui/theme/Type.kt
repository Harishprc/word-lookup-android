package com.harish.wordlookup.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.harish.wordlookup.R

/**
 * Round 7: bundled as real `res/font/` files (SIL Open Font License, from
 * Google Fonts' own repo). Chosen as the closest legitimately-bundleable
 * substitute for "Google Sans" - that's Google's proprietary internal
 * typeface, never published to Google Fonts and not licensed for
 * third-party redistribution, so it isn't an option here. Poppins has no
 * Kannada/Devanagari/CJK glyphs, which is why [BodyNative] deliberately
 * stays on the system default below - Android's own per-character font
 * fallback already substitutes a script font for anything Poppins can't
 * render, so native-script text is unaffected by this swap.
 */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
)

/** Sizes/weights/line-heights straight from DESIGN.md's `typography:` tokens. */
val WordLookupTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontFamily = Poppins,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 28.sp * 1.2f,
            letterSpacing = (-0.02).em,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = Poppins,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp * 1.3f,
            letterSpacing = (-0.01).em,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = Poppins,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp * 1.3f,
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = Poppins,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp * 1.3f,
        ),
        bodyLarge = base.bodyLarge.copy(
            fontFamily = Poppins,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 19.sp * 1.7f,
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = Poppins,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 15.sp * 1.55f,
        ),
        bodySmall = base.bodySmall.copy(
            fontFamily = Poppins,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 13.5.sp * 1.5f,
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = Poppins,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 13.sp * 1.4f,
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = Poppins,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 13.sp * 1.4f,
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = Poppins,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 11.sp,
            letterSpacing = 0.07.em,
        ),
    )
}

/**
 * body-native from DESIGN.md - not a default M3 slot, so it's a standalone
 * style. Deliberately NOT on [Poppins] - this renders native-script example
 * sentences (Kannada, etc.), which Poppins has no glyphs for at all.
 */
val BodyNative = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 15.sp * 1.75f)
