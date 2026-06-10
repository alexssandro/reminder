package com.reminder.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.reminder.R

/** Cascadia Mono everywhere — matches the dev terminal. Variable font; bold is synthesized. */
val CascadiaMono = FontFamily(Font(R.font.cascadia_mono))

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = CascadiaMono),
    displayMedium = base.displayMedium.copy(fontFamily = CascadiaMono),
    displaySmall = base.displaySmall.copy(fontFamily = CascadiaMono),
    headlineLarge = base.headlineLarge.copy(fontFamily = CascadiaMono),
    headlineMedium = base.headlineMedium.copy(fontFamily = CascadiaMono),
    headlineSmall = base.headlineSmall.copy(fontFamily = CascadiaMono),
    titleLarge = base.titleLarge.copy(fontFamily = CascadiaMono),
    titleMedium = base.titleMedium.copy(fontFamily = CascadiaMono),
    titleSmall = base.titleSmall.copy(fontFamily = CascadiaMono),
    bodyLarge = base.bodyLarge.copy(fontFamily = CascadiaMono),
    bodyMedium = base.bodyMedium.copy(fontFamily = CascadiaMono),
    bodySmall = base.bodySmall.copy(fontFamily = CascadiaMono),
    labelLarge = base.labelLarge.copy(fontFamily = CascadiaMono),
    labelMedium = base.labelMedium.copy(fontFamily = CascadiaMono),
    labelSmall = base.labelSmall.copy(fontFamily = CascadiaMono),
)
