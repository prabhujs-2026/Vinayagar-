package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class VinayagarExtendedColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val whatsapp: Color,
    val whatsappContainer: Color,
    val goldAccent: Color,
    val goldContainer: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    VinayagarExtendedColors(
        success = TulsiEmerald,
        successContainer = TulsiEmeraldContainer,
        onSuccessContainer = OnTulsiEmeraldContainer,
        warning = TurmericAmber,
        warningContainer = TurmericAmberContainer,
        onWarningContainer = OnTurmericAmberContainer,
        whatsapp = WhatsAppGreen,
        whatsappContainer = WhatsAppGreenContainer,
        goldAccent = LuminousGold,
        goldContainer = GoldContainer
    )
}

private val LightColorScheme = lightColorScheme(
    primary = TempleCrimson,
    onPrimary = Color.White,
    primaryContainer = TempleCrimsonContainer,
    onPrimaryContainer = OnTempleCrimsonContainer,
    secondary = SacredSaffron,
    onSecondary = Color.White,
    secondaryContainer = SacredSaffronContainer,
    onSecondaryContainer = OnSacredSaffronContainer,
    tertiary = AntiqueGold,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = OnGoldContainer,
    background = IvoryBackground,
    onBackground = DeepEspressoInk,
    surface = WarmWhiteCard,
    onSurface = DeepEspressoInk,
    surfaceVariant = SandalwoodSurface,
    onSurfaceVariant = MutedBronzeInk,
    surfaceContainerLowest = WarmWhiteCard,
    surfaceContainerLow = IvoryBackground,
    surfaceContainer = SandalwoodSurface,
    surfaceContainerHigh = SandalwoodSurfaceHigh,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonErrorContainer,
    onErrorContainer = OnTempleCrimsonContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkCrimsonPrimary,
    onPrimary = DarkOnCrimsonPrimary,
    primaryContainer = DarkCrimsonContainer,
    onPrimaryContainer = TempleCrimsonContainer,
    secondary = DarkSaffronSecondary,
    onSecondary = DarkOnSaffronSecondary,
    secondaryContainer = DarkSaffronContainer,
    onSecondaryContainer = SacredSaffronContainer,
    tertiary = LuminousGold,
    onTertiary = OnGoldContainer,
    tertiaryContainer = Color(0xFF4E3600),
    onTertiaryContainer = GoldContainer,
    background = DarkCharcoalBg,
    onBackground = DarkIvoryInk,
    surface = DarkSandalwoodSurface,
    onSurface = DarkIvoryInk,
    surfaceVariant = DarkSandalwoodHigh,
    onSurfaceVariant = DarkMutedInk,
    surfaceContainerLowest = DarkCharcoalBg,
    surfaceContainerLow = DarkSandalwoodSurface,
    surfaceContainer = DarkSandalwoodHigh,
    surfaceContainerHigh = Color(0xFF3A2D28),
    outline = DarkWarmOutline,
    outlineVariant = Color(0xFF3E312C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val VinayagarShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VinayagarFundTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        VinayagarExtendedColors(
            success = DarkEmerald,
            successContainer = DarkEmeraldContainer,
            onSuccessContainer = Color(0xFFD6F9E6),
            warning = DarkAmber,
            warningContainer = DarkAmberContainer,
            onWarningContainer = Color(0xFFFFEBC2),
            whatsapp = Color(0xFF4CD6B8),
            whatsappContainer = Color(0xFF0D3A33),
            goldAccent = LuminousGold,
            goldContainer = Color(0xFF4A3500)
        )
    } else {
        VinayagarExtendedColors(
            success = TulsiEmerald,
            successContainer = TulsiEmeraldContainer,
            onSuccessContainer = OnTulsiEmeraldContainer,
            warning = TurmericAmber,
            warningContainer = TurmericAmberContainer,
            onWarningContainer = OnTurmericAmberContainer,
            whatsapp = WhatsAppGreen,
            whatsappContainer = WhatsAppGreenContainer,
            goldAccent = AntiqueGold,
            goldContainer = GoldContainer
        )
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VinayagarTypography,
            shapes = VinayagarShapes,
            content = content
        )
    }
}
