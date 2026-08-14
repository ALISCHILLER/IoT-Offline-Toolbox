package com.msa.iotofflinetoolbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MsaGreen = Color(0xFF006C55)
private val MsaGreenDark = Color(0xFF004D3D)
private val MsaMint = Color(0xFF9AF2D4)
private val MsaBlue = Color(0xFF35618D)
private val MsaAmber = Color(0xFF8A5200)

private val LightColors = lightColorScheme(
    primary = MsaGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF2D5),
    onPrimaryContainer = Color(0xFF002019),
    inversePrimary = MsaMint,
    secondary = MsaBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E4FF),
    onSecondaryContainer = Color(0xFF001C38),
    tertiary = MsaAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDCE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F2),
    surfaceContainer = Color(0xFFEBEFED),
    surfaceContainerHigh = Color(0xFFE5E9E7),
    surfaceContainerHighest = Color(0xFFDDE2DF),
    outline = Color(0xFF6F7974),
    outlineVariant = Color(0xFFBFC9C3),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = MsaMint,
    onPrimary = Color(0xFF00382C),
    primaryContainer = MsaGreenDark,
    onPrimaryContainer = Color(0xFFB8F7E0),
    inversePrimary = MsaGreen,
    secondary = Color(0xFFA6C8F2),
    onSecondary = Color(0xFF063258),
    secondaryContainer = Color(0xFF244A70),
    onSecondaryContainer = Color(0xFFD4E4FF),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF673C00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C3),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    outline = Color(0xFF89938E),
    outlineVariant = Color(0xFF3F4945),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)

private val ToolboxTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.4f).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 50.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

private val ToolboxShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Immutable
data class ToolboxUiTokens(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

val LocalToolboxUiTokens = staticCompositionLocalOf {
    ToolboxUiTokens(
        success = Color(0xFF006D3B),
        onSuccess = Color.White,
        successContainer = Color(0xFF9BF6B9),
        onSuccessContainer = Color(0xFF00210D),
        warning = Color(0xFF805600),
        onWarning = Color.White,
        warningContainer = Color(0xFFFFDEA5),
        onWarningContainer = Color(0xFF281900),
        info = Color(0xFF265D8F),
        onInfo = Color.White,
        infoContainer = Color(0xFFD0E4FF),
        onInfoContainer = Color(0xFF001D35),
    )
}

val toolboxUiTokens: ToolboxUiTokens
    @Composable get() = LocalToolboxUiTokens.current

@Composable
fun ToolboxTheme(darkMode: Boolean, content: @Composable () -> Unit) {
    val tokens = if (darkMode) {
        ToolboxUiTokens(
            success = Color(0xFF7ADB96),
            onSuccess = Color(0xFF00391B),
            successContainer = Color(0xFF005229),
            onSuccessContainer = Color(0xFF96F8B1),
            warning = Color(0xFFFFBA42),
            onWarning = Color(0xFF442B00),
            warningContainer = Color(0xFF614000),
            onWarningContainer = Color(0xFFFFDEA5),
            info = Color(0xFFA2C9F5),
            onInfo = Color(0xFF003258),
            infoContainer = Color(0xFF164A73),
            onInfoContainer = Color(0xFFD0E4FF),
        )
    } else {
        LocalToolboxUiTokens.current
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalToolboxUiTokens provides tokens) {
        MaterialTheme(
            colorScheme = if (darkMode) DarkColors else LightColors,
            typography = ToolboxTypography,
            shapes = ToolboxShapes,
            content = content,
        )
    }
}
