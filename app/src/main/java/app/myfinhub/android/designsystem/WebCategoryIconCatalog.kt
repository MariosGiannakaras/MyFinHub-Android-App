package app.myfinhub.android.designsystem

import android.content.res.AssetManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The pinned Web SVG symbols are normalized into local path data at authoring time.
 * Only closed pack names and catalog identifiers can select an asset; no SVG/HTML, URI or network
 * is evaluated on-device. Loading happens off the UI thread, cached by pack and icon. */
internal object WebCategoryIconCatalog {
    private val names = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    private val packs = mutableMapOf<String, Map<String, String>>()
    private val vectors = object : LinkedHashMap<String, ImageVector>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageVector>?) =
            size > 256
    }

    @Synchronized
    fun get(assets: AssetManager, reference: String): ImageVector? {
        val pack = reference.substringBefore(':')
        val name = reference.substringAfter(':', "")
        if (pack !in WebCategoryIconPolicy.packs || !names.matches(name)) return null
        vectors[reference]?.let { return it }
        val rows = packs[pack] ?: runCatching {
            val directory = "category-icons/$pack"
            val shards = assets.list(directory).orEmpty().filter { it.matches(Regex("[0-9]+\\.tsv")) }
            require(shards.isNotEmpty())
            buildMap {
                for (shard in shards) assets.open("$directory/$shard").bufferedReader().useLines { lines ->
                    lines.forEach { line -> put(line.substringBefore('\t'), line) }
                }
            }
        }.getOrNull()?.also { packs[pack] = it } ?: return null
        val row = rows[name] ?: return null
        return runCatching { decode(reference, row) }.getOrNull()?.also { vectors[reference] = it }
    }

    private fun decode(reference: String, row: String): ImageVector {
        val fields = row.split('\t')
        val viewport = fields[1].toFloat()
        require(viewport in listOf(16f, 24f, 256f) && fields.size >= 3)
        val ink = SolidColor(Color.Black)
        return ImageVector.Builder(reference, 24.dp, 24.dp, viewport, viewport).apply {
            for (field in fields.drop(2)) {
                val parts = field.split('|', limit = 8)
                require(parts.size == 8)
                val transform = parts[6] == "1"
                if (transform) addGroup(scaleY = -1f, translationX = 6f, translationY = 15f)
                addPath(
                    pathData = PathParser().parsePathString(parts[7]).toNodes(),
                    pathFillType = if (parts[5] == "1") PathFillType.EvenOdd else PathFillType.NonZero,
                    fill = if (parts[0] == "1") ink else null,
                    fillAlpha = parts[3].toFloat(),
                    stroke = if (parts[1] == "1") ink else null,
                    strokeAlpha = parts[4].toFloat(),
                    strokeLineWidth = parts[2].toFloat(),
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                if (transform) clearGroup()
            }
        }.build()
    }
}

@Composable
internal fun rememberWebCategoryIcon(candidates: List<String>, fallback: ImageVector): ImageVector {
    val assets = LocalContext.current.assets
    val resolved by produceState<Pair<List<String>, ImageVector?>?>(null, assets, candidates) {
        val vector = if (candidates.isEmpty()) null else withContext(Dispatchers.IO) {
            candidates.firstNotNullOfOrNull { WebCategoryIconCatalog.get(assets, it) }
        }
        value = candidates to vector
    }
    return resolved?.takeIf { it.first == candidates }?.second ?: fallback
}
