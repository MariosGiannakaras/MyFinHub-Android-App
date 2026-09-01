package app.myfinhub.android.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Curated static icon registry for the native Android product.
 *
 * The vocabulary deliberately uses one rounded 1.8dp stroke language instead of mixing arbitrary
 * filled Material glyphs. Feature screens depend on semantic names here, so the visual vocabulary
 * remains replaceable in one place and Android never needs a desktop-style icon picker.
 */
object MyFinHubIcons {
    val Add by lazy {
        icon("MyFinHubAdd") {
            strokePath {
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }
    }

    val Back by lazy {
        icon("MyFinHubBack", autoMirror = true) {
            strokePath {
                moveTo(19f, 12f); lineTo(5f, 12f)
                moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
            }
        }
    }

    val Search by lazy {
        icon("MyFinHubSearch") {
            strokePath {
                circle(10.5f, 10.5f, 5.5f)
                moveTo(14.5f, 14.5f); lineTo(20f, 20f)
            }
        }
    }

    val Home by lazy {
        icon("MyFinHubHome") {
            strokePath {
                moveTo(3.5f, 10.5f); lineTo(12f, 3.5f); lineTo(20.5f, 10.5f)
                moveTo(5.5f, 9.2f); lineTo(5.5f, 20f); lineTo(18.5f, 20f); lineTo(18.5f, 9.2f)
                moveTo(9.5f, 20f); lineTo(9.5f, 14.5f); lineTo(14.5f, 14.5f); lineTo(14.5f, 20f)
            }
        }
    }

    val Activity by lazy {
        icon("MyFinHubActivity") {
            strokePath {
                moveTo(5f, 4f); lineTo(19f, 4f); lineTo(19f, 20f); lineTo(5f, 20f); close()
                moveTo(8f, 8f); lineTo(9f, 8f)
                moveTo(12f, 8f); lineTo(16f, 8f)
                moveTo(8f, 12f); lineTo(9f, 12f)
                moveTo(12f, 12f); lineTo(16f, 12f)
                moveTo(8f, 16f); lineTo(9f, 16f)
                moveTo(12f, 16f); lineTo(16f, 16f)
            }
        }
    }

    val Money by lazy {
        icon("MyFinHubMoney") {
            strokePath {
                moveTo(4f, 6f); lineTo(17f, 6f); cubicTo(18.7f, 6f, 20f, 7.3f, 20f, 9f)
                lineTo(20f, 18f); lineTo(4f, 18f); cubicTo(2.9f, 18f, 2f, 17.1f, 2f, 16f)
                lineTo(2f, 8f); cubicTo(2f, 6.9f, 2.9f, 6f, 4f, 6f)
                moveTo(15f, 10f); lineTo(21f, 10f); lineTo(21f, 14f); lineTo(15f, 14f)
                cubicTo(13.9f, 14f, 13f, 13.1f, 13f, 12f); cubicTo(13f, 10.9f, 13.9f, 10f, 15f, 10f)
                moveTo(17f, 12f); lineTo(17.1f, 12f)
            }
        }
    }

    val Plan by lazy {
        icon("MyFinHubPlan") {
            strokePath {
                moveTo(5f, 5.5f); lineTo(19f, 5.5f); lineTo(19f, 20f); lineTo(5f, 20f); close()
                moveTo(8f, 3.5f); lineTo(8f, 7.5f)
                moveTo(16f, 3.5f); lineTo(16f, 7.5f)
                moveTo(5f, 9f); lineTo(19f, 9f)
                moveTo(8.5f, 14.5f); lineTo(11f, 17f); lineTo(16f, 12f)
            }
        }
    }

    val Insights by lazy {
        icon("MyFinHubInsights") {
            strokePath {
                moveTo(4f, 20f); lineTo(4f, 14f); lineTo(8f, 14f); lineTo(8f, 20f)
                moveTo(10f, 20f); lineTo(10f, 10f); lineTo(14f, 10f); lineTo(14f, 20f)
                moveTo(16f, 20f); lineTo(16f, 5f); lineTo(20f, 5f); lineTo(20f, 20f)
            }
        }
    }

    val All by lazy {
        icon("MyFinHubAll") {
            strokePath {
                rect(4f, 4f, 6f, 6f)
                rect(14f, 4f, 6f, 6f)
                rect(4f, 14f, 6f, 6f)
                rect(14f, 14f, 6f, 6f)
            }
        }
    }

    val Income by lazy {
        icon("MyFinHubIncome") {
            strokePath {
                moveTo(12f, 4f); lineTo(12f, 17f)
                moveTo(7f, 12f); lineTo(12f, 17f); lineTo(17f, 12f)
                moveTo(5f, 20f); lineTo(19f, 20f)
            }
        }
    }

    val Expense by lazy {
        icon("MyFinHubExpense") {
            strokePath {
                moveTo(12f, 20f); lineTo(12f, 7f)
                moveTo(7f, 12f); lineTo(12f, 7f); lineTo(17f, 12f)
                moveTo(5f, 4f); lineTo(19f, 4f)
            }
        }
    }

    val Savings by lazy {
        icon("MyFinHubSavings") {
            strokePath {
                circle(12f, 12f, 8f)
                moveTo(12f, 8f); lineTo(12f, 16f)
                moveTo(9f, 10f); cubicTo(9f, 8.9f, 10.3f, 8f, 12f, 8f)
                cubicTo(13.7f, 8f, 15f, 8.9f, 15f, 10f)
                cubicTo(15f, 11.2f, 13.8f, 12f, 12f, 12f)
                cubicTo(10.2f, 12f, 9f, 12.8f, 9f, 14f)
                cubicTo(9f, 15.1f, 10.3f, 16f, 12f, 16f)
                cubicTo(13.7f, 16f, 15f, 15.1f, 15f, 14f)
            }
        }
    }

    val Transfer by lazy {
        icon("MyFinHubTransfer") {
            strokePath {
                moveTo(4f, 8f); lineTo(18f, 8f)
                moveTo(14f, 4f); lineTo(18f, 8f); lineTo(14f, 12f)
                moveTo(20f, 16f); lineTo(6f, 16f)
                moveTo(10f, 12f); lineTo(6f, 16f); lineTo(10f, 20f)
            }
        }
    }

    val Attention by lazy {
        icon("MyFinHubAttention") {
            strokePath {
                moveTo(12f, 3f); lineTo(21f, 20f); lineTo(3f, 20f); close()
                moveTo(12f, 9f); lineTo(12f, 14f)
                moveTo(12f, 17f); lineTo(12.1f, 17f)
            }
        }
    }

    val Filter by lazy {
        icon("MyFinHubFilter") {
            strokePath {
                moveTo(4f, 7f); lineTo(20f, 7f)
                circle(9f, 7f, 1.5f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                circle(15f, 12f, 1.5f)
                moveTo(4f, 17f); lineTo(20f, 17f)
                circle(11f, 17f, 1.5f)
            }
        }
    }

    val Account by lazy {
        icon("MyFinHubAccount") {
            strokePath {
                moveTo(3f, 9f); lineTo(12f, 4f); lineTo(21f, 9f); close()
                moveTo(5f, 10.5f); lineTo(19f, 10.5f)
                moveTo(7f, 10.5f); lineTo(7f, 18f)
                moveTo(12f, 10.5f); lineTo(12f, 18f)
                moveTo(17f, 10.5f); lineTo(17f, 18f)
                moveTo(4f, 18f); lineTo(20f, 18f)
                moveTo(3f, 21f); lineTo(21f, 21f)
            }
        }
    }

    val Card by lazy {
        icon("MyFinHubCard") {
            strokePath {
                moveTo(4f, 5f); lineTo(20f, 5f); cubicTo(21.1f, 5f, 22f, 5.9f, 22f, 7f)
                lineTo(22f, 17f); cubicTo(22f, 18.1f, 21.1f, 19f, 20f, 19f)
                lineTo(4f, 19f); cubicTo(2.9f, 19f, 2f, 18.1f, 2f, 17f)
                lineTo(2f, 7f); cubicTo(2f, 5.9f, 2.9f, 5f, 4f, 5f)
                moveTo(2f, 9f); lineTo(22f, 9f)
                moveTo(6f, 15f); lineTo(10f, 15f)
            }
        }
    }

    val Goal by lazy {
        icon("MyFinHubGoal") {
            strokePath {
                circle(12f, 12f, 8f)
                circle(12f, 12f, 4f)
                circle(12f, 12f, 0.8f)
                moveTo(15f, 9f); lineTo(20f, 4f)
                moveTo(17f, 4f); lineTo(20f, 4f); lineTo(20f, 7f)
            }
        }
    }
}

private fun icon(
    name: String,
    autoMirror: Boolean = false,
    content: Builder.() -> Unit,
): ImageVector = Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = autoMirror,
).apply(content).build()

private fun Builder.strokePath(block: PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}

private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
    val c = radius * 0.55228475f
    moveTo(cx + radius, cy)
    cubicTo(cx + radius, cy + c, cx + c, cy + radius, cx, cy + radius)
    cubicTo(cx - c, cy + radius, cx - radius, cy + c, cx - radius, cy)
    cubicTo(cx - radius, cy - c, cx - c, cy - radius, cx, cy - radius)
    cubicTo(cx + c, cy - radius, cx + radius, cy - c, cx + radius, cy)
    close()
}

private fun PathBuilder.rect(x: Float, y: Float, width: Float, height: Float) {
    moveTo(x, y)
    lineTo(x + width, y)
    lineTo(x + width, y + height)
    lineTo(x, y + height)
    close()
}
