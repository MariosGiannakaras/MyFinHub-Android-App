package app.myfinhub.android.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Curated static icon registry for the native Android product.
 *
 * Feature screens should depend on this registry instead of choosing arbitrary Material icons.
 * This keeps the visual language replaceable in one place and avoids a desktop-style icon picker.
 */
object MyFinHubIcons {
    val Add: ImageVector = Icons.Default.Add
    val Back: ImageVector = Icons.Default.ArrowBack
    val Search: ImageVector = Icons.Default.Search

    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubHome",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 10.6f)
                lineTo(12f, 3f)
                lineTo(21f, 10.6f)
                lineTo(19.45f, 12.35f)
                lineTo(18f, 11.15f)
                verticalLineTo(21f)
                horizontalLineTo(14.2f)
                verticalLineTo(15.4f)
                horizontalLineTo(9.8f)
                verticalLineTo(21f)
                horizontalLineTo(6f)
                verticalLineTo(11.15f)
                lineTo(4.55f, 12.35f)
                close()
            }
        }.build()
    }

    val Activity: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubActivity",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 5f)
                horizontalLineTo(7f)
                verticalLineTo(8f)
                horizontalLineTo(4f)
                close()
                moveTo(9f, 5.4f)
                horizontalLineTo(21f)
                verticalLineTo(7.6f)
                horizontalLineTo(9f)
                close()
                moveTo(4f, 10.5f)
                horizontalLineTo(7f)
                verticalLineTo(13.5f)
                horizontalLineTo(4f)
                close()
                moveTo(9f, 10.9f)
                horizontalLineTo(21f)
                verticalLineTo(13.1f)
                horizontalLineTo(9f)
                close()
                moveTo(4f, 16f)
                horizontalLineTo(7f)
                verticalLineTo(19f)
                horizontalLineTo(4f)
                close()
                moveTo(9f, 16.4f)
                horizontalLineTo(21f)
                verticalLineTo(18.6f)
                horizontalLineTo(9f)
                close()
            }
        }.build()
    }

    val Money: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubMoney",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 6f)
                horizontalLineTo(19f)
                verticalLineTo(8f)
                horizontalLineTo(21f)
                verticalLineTo(19f)
                horizontalLineTo(3f)
                close()
                moveTo(5.4f, 8.4f)
                verticalLineTo(16.6f)
                horizontalLineTo(18.6f)
                verticalLineTo(13.8f)
                horizontalLineTo(14.5f)
                verticalLineTo(11f)
                horizontalLineTo(18.6f)
                verticalLineTo(8.4f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(15.7f, 11.7f)
                horizontalLineTo(20.2f)
                verticalLineTo(13.1f)
                horizontalLineTo(15.7f)
                close()
            }
        }.build()
    }

    val Plan: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubPlan",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(4f, 5f)
                horizontalLineTo(7f)
                verticalLineTo(3f)
                horizontalLineTo(9f)
                verticalLineTo(5f)
                horizontalLineTo(15f)
                verticalLineTo(3f)
                horizontalLineTo(17f)
                verticalLineTo(5f)
                horizontalLineTo(20f)
                verticalLineTo(21f)
                horizontalLineTo(4f)
                close()
                moveTo(6.3f, 9f)
                verticalLineTo(18.7f)
                horizontalLineTo(17.7f)
                verticalLineTo(9f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 11f)
                horizontalLineTo(11f)
                verticalLineTo(14f)
                horizontalLineTo(8f)
                close()
                moveTo(13f, 11f)
                horizontalLineTo(16f)
                verticalLineTo(14f)
                horizontalLineTo(13f)
                close()
            }
        }.build()
    }

    val Insights: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubInsights",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 14f)
                horizontalLineTo(7.2f)
                verticalLineTo(21f)
                horizontalLineTo(4f)
                close()
                moveTo(10.4f, 9f)
                horizontalLineTo(13.6f)
                verticalLineTo(21f)
                horizontalLineTo(10.4f)
                close()
                moveTo(16.8f, 4f)
                horizontalLineTo(20f)
                verticalLineTo(21f)
                horizontalLineTo(16.8f)
                close()
            }
        }.build()
    }

    val All: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubAll",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 4f)
                horizontalLineTo(10f)
                verticalLineTo(10f)
                horizontalLineTo(4f)
                close()
                moveTo(14f, 4f)
                horizontalLineTo(20f)
                verticalLineTo(10f)
                horizontalLineTo(14f)
                close()
                moveTo(4f, 14f)
                horizontalLineTo(10f)
                verticalLineTo(20f)
                horizontalLineTo(4f)
                close()
                moveTo(14f, 14f)
                horizontalLineTo(20f)
                verticalLineTo(20f)
                horizontalLineTo(14f)
                close()
            }
        }.build()
    }

    val Income: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubIncome",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.6f, 3f)
                horizontalLineTo(13.4f)
                verticalLineTo(13.1f)
                lineTo(16.9f, 9.7f)
                lineTo(18.8f, 11.6f)
                lineTo(12f, 18.4f)
                lineTo(5.2f, 11.6f)
                lineTo(7.1f, 9.7f)
                lineTo(10.6f, 13.1f)
                close()
                moveTo(4f, 19f)
                horizontalLineTo(20f)
                verticalLineTo(21f)
                horizontalLineTo(4f)
                close()
            }
        }.build()
    }

    val Expense: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubExpense",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                lineTo(18.8f, 9.8f)
                lineTo(16.9f, 11.7f)
                lineTo(13.4f, 8.3f)
                verticalLineTo(18.4f)
                horizontalLineTo(10.6f)
                verticalLineTo(8.3f)
                lineTo(7.1f, 11.7f)
                lineTo(5.2f, 9.8f)
                close()
                moveTo(4f, 19f)
                horizontalLineTo(20f)
                verticalLineTo(21f)
                horizontalLineTo(4f)
                close()
            }
        }.build()
    }

    val Savings: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubSavings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(4f, 6f)
                horizontalLineTo(20f)
                verticalLineTo(21f)
                horizontalLineTo(4f)
                close()
                moveTo(6.4f, 8.4f)
                verticalLineTo(18.6f)
                horizontalLineTo(17.6f)
                verticalLineTo(8.4f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 3f)
                horizontalLineTo(16f)
                verticalLineTo(5f)
                horizontalLineTo(8f)
                close()
                moveTo(12f, 10f)
                lineTo(13.1f, 12.2f)
                lineTo(15.5f, 12.6f)
                lineTo(13.8f, 14.3f)
                lineTo(14.2f, 16.7f)
                lineTo(12f, 15.6f)
                lineTo(9.8f, 16.7f)
                lineTo(10.2f, 14.3f)
                lineTo(8.5f, 12.6f)
                lineTo(10.9f, 12.2f)
                close()
            }
        }.build()
    }

    val Transfer: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubTransfer",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 6f)
                horizontalLineTo(16f)
                lineTo(13.5f, 3.5f)
                lineTo(15.4f, 1.7f)
                lineTo(21f, 7.3f)
                lineTo(15.4f, 12.9f)
                lineTo(13.5f, 11.1f)
                lineTo(16f, 8.6f)
                horizontalLineTo(5f)
                close()
                moveTo(19f, 15.4f)
                horizontalLineTo(8f)
                lineTo(10.5f, 12.9f)
                lineTo(8.6f, 11.1f)
                lineTo(3f, 16.7f)
                lineTo(8.6f, 22.3f)
                lineTo(10.5f, 20.5f)
                lineTo(8f, 18f)
                horizontalLineTo(19f)
                close()
            }
        }.build()
    }

    val Attention: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubAttention",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12f, 2f)
                lineTo(22f, 12f)
                lineTo(12f, 22f)
                lineTo(2f, 12f)
                close()
                moveTo(10.6f, 7f)
                horizontalLineTo(13.4f)
                verticalLineTo(13.4f)
                horizontalLineTo(10.6f)
                close()
                moveTo(10.6f, 16f)
                horizontalLineTo(13.4f)
                verticalLineTo(18.8f)
                horizontalLineTo(10.6f)
                close()
            }
        }.build()
    }

    val Filter: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubFilter",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 5f)
                horizontalLineTo(21f)
                lineTo(14f, 13f)
                verticalLineTo(19f)
                lineTo(10f, 21f)
                verticalLineTo(13f)
                close()
            }
        }.build()
    }

    val Account: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubAccount",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                lineTo(21f, 8f)
                horizontalLineTo(3f)
                close()
                moveTo(5f, 10f)
                horizontalLineTo(8f)
                verticalLineTo(18f)
                horizontalLineTo(5f)
                close()
                moveTo(10.5f, 10f)
                horizontalLineTo(13.5f)
                verticalLineTo(18f)
                horizontalLineTo(10.5f)
                close()
                moveTo(16f, 10f)
                horizontalLineTo(19f)
                verticalLineTo(18f)
                horizontalLineTo(16f)
                close()
                moveTo(3f, 20f)
                horizontalLineTo(21f)
                verticalLineTo(22f)
                horizontalLineTo(3f)
                close()
            }
        }.build()
    }

    val Card: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubCard",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 5f)
                horizontalLineTo(21f)
                verticalLineTo(19f)
                horizontalLineTo(3f)
                close()
                moveTo(5.4f, 9.5f)
                verticalLineTo(16.6f)
                horizontalLineTo(18.6f)
                verticalLineTo(9.5f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 7f)
                horizontalLineTo(21f)
                verticalLineTo(10f)
                horizontalLineTo(3f)
                close()
            }
        }.build()
    }

    val Goal: ImageVector by lazy {
        ImageVector.Builder(
            name = "MyFinHubGoal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12f, 2f)
                lineTo(22f, 12f)
                lineTo(12f, 22f)
                lineTo(2f, 12f)
                close()
                moveTo(12f, 6f)
                lineTo(18f, 12f)
                lineTo(12f, 18f)
                lineTo(6f, 12f)
                close()
                moveTo(12f, 9f)
                lineTo(15f, 12f)
                lineTo(12f, 15f)
                lineTo(9f, 12f)
                close()
            }
        }.build()
    }
}
