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

/** Palette values are internal so unit tests can enforce the S2 contrast contract without rendering. */
internal object MyFinHubPalette {
    val lightBackground = Color(0xFFF6F7FA)
    val lightSurface = Color(0xFFFFFFFF)
    val lightSecondarySurface = Color(0xFFEEF1F5)
    val lightText = Color(0xFF151922)
    val lightSecondaryText = Color(0xFF596273)
    val lightAccent = Color(0xFF3659E3)
    val lightOnAccent = Color(0xFFFFFFFF)
    val lightPositive = Color(0xFF087443)
    val lightNegative = Color(0xFFB42335)
    val lightWarning = Color(0xFF895400)
    val lightControlBorder = Color(0xFF7B8494)
    val lightDivider = Color(0xFFE0E4EB)

    val darkBackground = Color(0xFF101216)
    val darkSurface = Color(0xFF191D24)
    val darkSecondarySurface = Color(0xFF242A34)
    val darkText = Color(0xFFF2F4F8)
    val darkSecondaryText = Color(0xFFB6BECC)
    val darkAccent = Color(0xFFA6B8FF)
    val darkOnAccent = Color(0xFF14245E)
    val darkPositive = Color(0xFF73D6A1)
    val darkNegative = Color(0xFFFFADB8)
    val darkWarning = Color(0xFFF4C56A)
    val darkControlBorder = Color(0xFF8792A5)
    val darkDivider = Color(0xFF343B48)

    // Compatibility aliases used by the retained semantic finance API and existing tests.
    val canvas = lightBackground
    val surface = lightSurface
    val softSurface = lightSecondarySurface
    val ink = lightText
    val mutedInk = lightSecondaryText
    val brandPurple = lightAccent
    val brandPurpleDark = darkAccent
    val brandPurpleContainer = lightSecondarySurface
    val outline = lightControlBorder
    val outlineVariant = lightDivider
    val darkOnBackground = darkText
    val darkOnSurface = darkText
    val darkSurfaceVariant = darkSecondarySurface
    val darkOnSurfaceVariant = darkSecondaryText
    val darkOutline = darkControlBorder
    val darkOutlineVariant = darkDivider

    val lightIncome = lightPositive
    val lightIncomeContainer = lightSecondarySurface
    val lightExpense = lightNegative
    val lightExpenseContainer = lightSecondarySurface
    val lightSavings = lightAccent
    val lightSavingsContainer = lightSecondarySurface
    val lightTransfer = lightSecondaryText
    val lightTransferContainer = lightSecondarySurface
    val lightAttention = lightWarning
    val lightAttentionContainer = lightSecondarySurface
    val lightNeutral = lightSecondaryText
    val lightNeutralContainer = lightSecondarySurface

    val darkIncome = darkPositive
    val darkIncomeContainer = darkSecondarySurface
    val darkExpense = darkNegative
    val darkExpenseContainer = darkSecondarySurface
    val darkSavings = darkAccent
    val darkSavingsContainer = darkSecondarySurface
    val darkTransfer = darkSecondaryText
    val darkTransferContainer = darkSecondarySurface
    val darkAttention = darkWarning
    val darkAttentionContainer = darkSecondarySurface
    val darkNeutral = darkSecondaryText
    val darkNeutralContainer = darkSecondarySurface
}

private val LightColors = lightColorScheme(
    primary = MyFinHubPalette.lightAccent,
    onPrimary = MyFinHubPalette.lightOnAccent,
    primaryContainer = Color(0xFFE8EDFF),
    onPrimaryContainer = Color(0xFF172B75),
    secondary = MyFinHubPalette.lightSecondaryText,
    onSecondary = Color.White,
    secondaryContainer = MyFinHubPalette.lightSecondarySurface,
    onSecondaryContainer = MyFinHubPalette.lightText,
    tertiary = MyFinHubPalette.lightPositive,
    onTertiary = Color.White,
    tertiaryContainer = MyFinHubPalette.lightSecondarySurface,
    onTertiaryContainer = MyFinHubPalette.lightText,
    background = MyFinHubPalette.lightBackground,
    onBackground = MyFinHubPalette.lightText,
    surface = MyFinHubPalette.lightSurface,
    onSurface = MyFinHubPalette.lightText,
    surfaceVariant = MyFinHubPalette.lightSecondarySurface,
    onSurfaceVariant = MyFinHubPalette.lightSecondaryText,
    outline = MyFinHubPalette.lightControlBorder,
    outlineVariant = MyFinHubPalette.lightDivider,
    error = MyFinHubPalette.lightNegative,
    onError = Color.White,
    errorContainer = MyFinHubPalette.lightSecondarySurface,
    onErrorContainer = MyFinHubPalette.lightNegative,
    surfaceDim = MyFinHubPalette.lightSecondarySurface,
    surfaceBright = MyFinHubPalette.lightSurface,
    surfaceContainerLowest = MyFinHubPalette.lightSurface,
    surfaceContainerLow = MyFinHubPalette.lightBackground,
    surfaceContainer = MyFinHubPalette.lightSecondarySurface,
    surfaceContainerHigh = MyFinHubPalette.lightSecondarySurface,
    surfaceContainerHighest = MyFinHubPalette.lightDivider,
    surfaceTint = MyFinHubPalette.lightAccent,
)

private val DarkColors = darkColorScheme(
    primary = MyFinHubPalette.darkAccent,
    onPrimary = MyFinHubPalette.darkOnAccent,
    primaryContainer = Color(0xFF27345F),
    onPrimaryContainer = MyFinHubPalette.darkText,
    secondary = MyFinHubPalette.darkSecondaryText,
    onSecondary = MyFinHubPalette.darkBackground,
    secondaryContainer = MyFinHubPalette.darkSecondarySurface,
    onSecondaryContainer = MyFinHubPalette.darkText,
    tertiary = MyFinHubPalette.darkPositive,
    onTertiary = Color(0xFF0C3A25),
    tertiaryContainer = MyFinHubPalette.darkSecondarySurface,
    onTertiaryContainer = MyFinHubPalette.darkText,
    background = MyFinHubPalette.darkBackground,
    onBackground = MyFinHubPalette.darkText,
    surface = MyFinHubPalette.darkSurface,
    onSurface = MyFinHubPalette.darkText,
    surfaceVariant = MyFinHubPalette.darkSecondarySurface,
    onSurfaceVariant = MyFinHubPalette.darkSecondaryText,
    outline = MyFinHubPalette.darkControlBorder,
    outlineVariant = MyFinHubPalette.darkDivider,
    error = MyFinHubPalette.darkNegative,
    onError = Color(0xFF5E1220),
    errorContainer = MyFinHubPalette.darkSecondarySurface,
    onErrorContainer = MyFinHubPalette.darkNegative,
    surfaceDim = MyFinHubPalette.darkBackground,
    surfaceBright = MyFinHubPalette.darkSurface,
    surfaceContainerLowest = MyFinHubPalette.darkBackground,
    surfaceContainerLow = MyFinHubPalette.darkSurface,
    surfaceContainer = MyFinHubPalette.darkSecondarySurface,
    surfaceContainerHigh = MyFinHubPalette.darkSecondarySurface,
    surfaceContainerHighest = MyFinHubPalette.darkDivider,
    surfaceTint = MyFinHubPalette.darkAccent,
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
    val micro = MyFinHubSpacingSpec.xxs
    val xxs = MyFinHubSpacingSpec.xxs
    val xs = MyFinHubSpacingSpec.xs
    val sm = MyFinHubSpacingSpec.sm
    val md = MyFinHubSpacingSpec.md
    val lg = MyFinHubSpacingSpec.lg
    val xl = MyFinHubSpacingSpec.xl
    val xxl = MyFinHubSpacingSpec.xxl
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
    ),
    headlineMedium = TextStyle(
        fontSize = MyFinHubTypographySpec.headlineMediumSize,
        lineHeight = MyFinHubTypographySpec.headlineMediumLineHeight,
        fontWeight = MyFinHubTypographySpec.headlineMediumWeight,
    ),
    headlineSmall = TextStyle(
        fontSize = MyFinHubTypographySpec.headlineSmallSize,
        lineHeight = MyFinHubTypographySpec.headlineSmallLineHeight,
        fontWeight = MyFinHubTypographySpec.headlineSmallWeight,
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
    labelMedium = TextStyle(
        fontSize = MyFinHubTypographySpec.labelMediumSize,
        lineHeight = MyFinHubTypographySpec.labelMediumLineHeight,
        fontWeight = MyFinHubTypographySpec.labelMediumWeight,
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