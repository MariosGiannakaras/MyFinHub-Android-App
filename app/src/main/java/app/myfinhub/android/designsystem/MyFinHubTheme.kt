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
import androidx.compose.ui.unit.dp

/** Palette values are internal so unit tests can enforce contrast contracts without UI rendering. */
internal object MyFinHubPalette {
    val brandPurple = Color(0xFF6547C7)
    val brandPurpleDark = Color(0xFFCAB8FF)
    val brandPurpleContainer = Color(0xFFEDE7FF)

    val ink = Color(0xFF1C1922)
    val mutedInk = Color(0xFF65606B)
    val canvas = Color(0xFFFBF9FF)
    val surface = Color(0xFFFFFBFF)
    val softSurface = Color(0xFFF5F1F8)

    // Essential control boundaries must reach the Android 3:1 graphical contrast threshold.
    val outline = Color(0xFF8A828E)
    val outlineVariant = Color(0xFFE8E1EC)

    // Light semantic accents are chosen to keep normal-sized finance text >= 4.5:1 both on the
    // base surface and on their semantic containers.
    val lightIncome = Color(0xFF087247)
    val lightIncomeContainer = Color(0xFFE0F6EB)
    val lightExpense = Color(0xFFB72F3A)
    val lightExpenseContainer = Color(0xFFFFE7E8)
    val lightSavings = Color(0xFF6C4BC4)
    val lightSavingsContainer = Color(0xFFEDE7FF)
    val lightTransfer = Color(0xFF2E6BC4)
    val lightTransferContainer = Color(0xFFE5EFFF)
    val lightAttention = Color(0xFF8A5900)
    val lightAttentionContainer = Color(0xFFFFEFC8)
    val lightNeutral = Color(0xFF5B626C)
    val lightNeutralContainer = Color(0xFFF0F2F5)

    val darkBackground = Color(0xFF111016)
    val darkOnBackground = Color(0xFFE8E1EA)
    val darkSurface = Color(0xFF151319)
    val darkOnSurface = Color(0xFFE8E1EA)
    val darkSurfaceVariant = Color(0xFF25222B)
    val darkOnSurfaceVariant = Color(0xFFCAC3CE)
    val darkOutline = Color(0xFF948E99)
    val darkOutlineVariant = Color(0xFF48434D)

    val darkIncome = Color(0xFF72DBA5)
    val darkIncomeContainer = Color(0xFF123B2A)
    val darkExpense = Color(0xFFFFB2B7)
    val darkExpenseContainer = Color(0xFF532126)
    val darkSavings = Color(0xFFD0BCFF)
    val darkSavingsContainer = Color(0xFF3D2F65)
    val darkTransfer = Color(0xFFA7C8FF)
    val darkTransferContainer = Color(0xFF203B64)
    val darkAttention = Color(0xFFFFCA6A)
    val darkAttentionContainer = Color(0xFF4D3710)
    val darkNeutral = Color(0xFFC5CBD3)
    val darkNeutralContainer = Color(0xFF30343A)
}

private val LightColors = lightColorScheme(
    primary = MyFinHubPalette.brandPurple,
    onPrimary = Color.White,
    primaryContainer = MyFinHubPalette.brandPurpleContainer,
    onPrimaryContainer = Color(0xFF25105F),
    secondary = Color(0xFF5E5B73),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E2F5),
    onSecondaryContainer = Color(0xFF1B1929),
    tertiary = Color(0xFF44658B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7E7FF),
    onTertiaryContainer = Color(0xFF071D35),
    background = MyFinHubPalette.canvas,
    onBackground = MyFinHubPalette.ink,
    surface = MyFinHubPalette.surface,
    onSurface = MyFinHubPalette.ink,
    surfaceVariant = MyFinHubPalette.softSurface,
    onSurfaceVariant = MyFinHubPalette.mutedInk,
    outline = MyFinHubPalette.outline,
    outlineVariant = MyFinHubPalette.outlineVariant,
    error = Color(0xFFBA1A2A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF410008),
)

private val DarkColors = darkColorScheme(
    primary = MyFinHubPalette.brandPurpleDark,
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
    background = MyFinHubPalette.darkBackground,
    onBackground = MyFinHubPalette.darkOnBackground,
    surface = MyFinHubPalette.darkSurface,
    onSurface = MyFinHubPalette.darkOnSurface,
    surfaceVariant = MyFinHubPalette.darkSurfaceVariant,
    onSurfaceVariant = MyFinHubPalette.darkOnSurfaceVariant,
    outline = MyFinHubPalette.darkOutline,
    outlineVariant = MyFinHubPalette.darkOutlineVariant,
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
    income = MyFinHubPalette.lightIncome,
    incomeContainer = MyFinHubPalette.lightIncomeContainer,
    expense = MyFinHubPalette.lightExpense,
    expenseContainer = MyFinHubPalette.lightExpenseContainer,
    savings = MyFinHubPalette.lightSavings,
    savingsContainer = MyFinHubPalette.lightSavingsContainer,
    transfer = MyFinHubPalette.lightTransfer,
    transferContainer = MyFinHubPalette.lightTransferContainer,
    attention = MyFinHubPalette.lightAttention,
    attentionContainer = MyFinHubPalette.lightAttentionContainer,
    neutral = MyFinHubPalette.lightNeutral,
    neutralContainer = MyFinHubPalette.lightNeutralContainer,
)

private val DarkFinanceColors = MyFinHubFinanceColors(
    income = MyFinHubPalette.darkIncome,
    incomeContainer = MyFinHubPalette.darkIncomeContainer,
    expense = MyFinHubPalette.darkExpense,
    expenseContainer = MyFinHubPalette.darkExpenseContainer,
    savings = MyFinHubPalette.darkSavings,
    savingsContainer = MyFinHubPalette.darkSavingsContainer,
    transfer = MyFinHubPalette.darkTransfer,
    transferContainer = MyFinHubPalette.darkTransferContainer,
    attention = MyFinHubPalette.darkAttention,
    attentionContainer = MyFinHubPalette.darkAttentionContainer,
    neutral = MyFinHubPalette.darkNeutral,
    neutralContainer = MyFinHubPalette.darkNeutralContainer,
)

private val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }

object MyFinHubSpacing {
    val micro = 2.dp
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
    extraSmall = RoundedCornerShape(MyFinHubShapeSpec.extraSmallRadius),
    small = RoundedCornerShape(MyFinHubShapeSpec.smallRadius),
    medium = RoundedCornerShape(MyFinHubShapeSpec.mediumRadius),
    large = RoundedCornerShape(MyFinHubShapeSpec.largeRadius),
    extraLarge = RoundedCornerShape(MyFinHubShapeSpec.extraLargeRadius),
)

private val MyFinHubTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = MyFinHubTypographySpec.headlineLargeSize,
        lineHeight = MyFinHubTypographySpec.headlineLargeLineHeight,
        fontWeight = MyFinHubTypographySpec.headlineLargeWeight,
        letterSpacing = MyFinHubTypographySpec.headlineLargeLetterSpacing,
    ),
    headlineMedium = TextStyle(
        fontSize = MyFinHubTypographySpec.headlineMediumSize,
        lineHeight = MyFinHubTypographySpec.headlineMediumLineHeight,
        fontWeight = MyFinHubTypographySpec.headlineMediumWeight,
        letterSpacing = MyFinHubTypographySpec.headlineMediumLetterSpacing,
    ),
    headlineSmall = TextStyle(
        fontSize = MyFinHubTypographySpec.headlineSmallSize,
        lineHeight = MyFinHubTypographySpec.headlineSmallLineHeight,
        fontWeight = MyFinHubTypographySpec.headlineSmallWeight,
        letterSpacing = MyFinHubTypographySpec.headlineSmallLetterSpacing,
    ),
    titleLarge = TextStyle(
        fontSize = MyFinHubTypographySpec.titleLargeSize,
        lineHeight = MyFinHubTypographySpec.titleLargeLineHeight,
        fontWeight = MyFinHubTypographySpec.titleLargeWeight,
    ),
    titleMedium = TextStyle(
        fontSize = MyFinHubTypographySpec.titleMediumSize,
        lineHeight = MyFinHubTypographySpec.titleMediumLineHeight,
        fontWeight = MyFinHubTypographySpec.titleMediumWeight,
    ),
    bodyLarge = TextStyle(
        fontSize = MyFinHubTypographySpec.bodyLargeSize,
        lineHeight = MyFinHubTypographySpec.bodyLargeLineHeight,
        fontWeight = MyFinHubTypographySpec.bodyLargeWeight,
    ),
    bodyMedium = TextStyle(
        fontSize = MyFinHubTypographySpec.bodyMediumSize,
        lineHeight = MyFinHubTypographySpec.bodyMediumLineHeight,
        fontWeight = MyFinHubTypographySpec.bodyMediumWeight,
    ),
    labelLarge = TextStyle(
        fontSize = MyFinHubTypographySpec.labelLargeSize,
        lineHeight = MyFinHubTypographySpec.labelLargeLineHeight,
        fontWeight = MyFinHubTypographySpec.labelLargeWeight,
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
