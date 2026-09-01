package app.myfinhub.android.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BrandPurple = Color(0xFF6547C7)
private val BrandPurpleDark = Color(0xFFCAB8FF)
private val BrandPurpleContainer = Color(0xFFEDE7FF)
private val Ink = Color(0xFF1C1922)
private val MutedInk = Color(0xFF65606B)
private val Canvas = Color(0xFFFBF9FF)
private val Surface = Color(0xFFFFFBFF)
private val SoftSurface = Color(0xFFF5F1F8)
private val Outline = Color(0xFFD2CAD8)

private val LightColors = lightColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = BrandPurpleContainer,
    onPrimaryContainer = Color(0xFF25105F),
    secondary = Color(0xFF5E5B73),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E2F5),
    onSecondaryContainer = Color(0xFF1B1929),
    tertiary = Color(0xFF44658B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7E7FF),
    onTertiaryContainer = Color(0xFF071D35),
    background = Canvas,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SoftSurface,
    onSurfaceVariant = MutedInk,
    outline = Outline,
    outlineVariant = Color(0xFFE8E1EC),
    error = Color(0xFFBA1A2A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF410008),
)

private val DarkColors = darkColorScheme(
    primary = BrandPurpleDark,
    onPrimary = Color(0xFF34206E),
    primaryContainer = Color(0xFF4C3594),
    onPrimaryContainer = Color(0xFFEAE1FF),
    secondary = Color(0xFFC9C4DC),
    onSecondary = Color(0xFF302E3F),
    secondaryContainer = Color(0xFF474657),
    onSecondaryContainer = Color(0xFFE5E1F4),
    tertiary = Color(0xFFAECBEF),
    onTertiary = Color(0xFF163552),
    tertiaryContainer = Color(0xFF2D4D6B),
    onTertiaryContainer = Color(0xFFD3E6FF),
    background = Color(0xFF111016),
    onBackground = Color(0xFFE8E1EA),
    surface = Color(0xFF151319),
    onSurface = Color(0xFFE8E1EA),
    surfaceVariant = Color(0xFF25222B),
    onSurfaceVariant = Color(0xFFCAC3CE),
    outline = Color(0xFF948E99),
    outlineVariant = Color(0xFF48434D),
    error = Color(0xFFFFB3B5),
    onError = Color(0xFF680014),
    errorContainer = Color(0xFF93001F),
    onErrorContainer = Color(0xFFFFDAD9),
)

@Immutable
data class MyFinHubFinanceColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val savings: Color,
    val savingsContainer: Color,
    val transfer: Color,
    val transferContainer: Color,
    val attention: Color,
    val attentionContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
)

private val LightFinanceColors = MyFinHubFinanceColors(
    income = Color(0xFF0B8754),
    incomeContainer = Color(0xFFE0F6EB),
    expense = Color(0xFFD23E47),
    expenseContainer = Color(0xFFFFE7E8),
    savings = Color(0xFF6C4BC4),
    savingsContainer = Color(0xFFEDE7FF),
    transfer = Color(0xFF2E6BC4),
    transferContainer = Color(0xFFE5EFFF),
    attention = Color(0xFFA96D00),
    attentionContainer = Color(0xFFFFEFC8),
    neutral = Color(0xFF68717D),
    neutralContainer = Color(0xFFF0F2F5),
)

private val DarkFinanceColors = MyFinHubFinanceColors(
    income = Color(0xFF72DBA5),
    incomeContainer = Color(0xFF123B2A),
    expense = Color(0xFFFFB2B7),
    expenseContainer = Color(0xFF532126),
    savings = Color(0xFFD0BCFF),
    savingsContainer = Color(0xFF3D2F65),
    transfer = Color(0xFFA7C8FF),
    transferContainer = Color(0xFF203B64),
    attention = Color(0xFFFFCA6A),
    attentionContainer = Color(0xFF4D3710),
    neutral = Color(0xFFC5CBD3),
    neutralContainer = Color(0xFF30343A),
)

private val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }

object MyFinHubSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object MyFinHubThemeTokens {
    val finance: MyFinHubFinanceColors
        @Composable get() = LocalFinanceColors.current
}

private val MyFinHubShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

private val MyFinHubTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 25.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 21.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 19.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)

@Composable
fun MyFinHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFinanceColors provides if (darkTheme) DarkFinanceColors else LightFinanceColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MyFinHubTypography,
            shapes = MyFinHubShapes,
            content = content,
        )
    }
}
