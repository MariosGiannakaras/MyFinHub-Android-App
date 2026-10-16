package app.myfinhub.android.core.network

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Minimal Web /api/history summary, never snapshots or state payloads. In-memory only. */
data class ServerFinanceHistoryPoint(
    val id: String,
    val parentId: String?,
    val label: String,
    val createdAt: String,
    val current: Boolean,
) {
    override fun toString(): String = "ServerFinanceHistoryPoint(<redacted>)"
}

data class ServerFinanceHistorySummary(
    val available: Boolean,
    val generation: String,
    val financeRevision: String,
    val currentPointId: String?,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val undoDepth: Int,
    val redoDepth: Int,
    val points: List<ServerFinanceHistoryPoint>,
) {
    override fun toString(): String = "ServerFinanceHistorySummary(<redacted>)"
}

private val historyNumericId = Regex("^\\d{1,30}$")
private fun JsonObject.historyString(name: String): String? =
    (get(name) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.historyBoolean(name: String): Boolean? =
    (get(name) as? JsonPrimitive)?.booleanOrNull
private fun JsonObject.historyInt(name: String): Int? =
    (get(name) as? JsonPrimitive)?.intOrNull

/**
 * Strict Web-shaped history envelope. Fail closed on malformed payload, retain no raw state.
 * The server is authoritative; never infer that Undo/Redo is safe from these read-only flags.
 */
fun parseServerFinanceHistory(body: String, json: Json = Json): ServerFinanceHistorySummary? =
    runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val available = root.historyBoolean("available") ?: error("Missing available")
        val generation = root.historyString("generation")
            ?.takeIf(historyNumericId::matches) ?: error("Invalid generation")
        val revision = root.historyString("financeRevision")
            ?.takeIf(historyNumericId::matches) ?: error("Invalid revision")
        val current = root.historyString("currentPointId")
            ?.takeIf(historyNumericId::matches)
        if (root["currentPointId"] !is kotlinx.serialization.json.JsonNull &&
            root["currentPointId"] != null && current == null) error("Invalid point id")
        val canUndo = root.historyBoolean("canUndo") ?: error("Missing undo flag")
        val canRedo = root.historyBoolean("canRedo") ?: error("Missing redo flag")
        val undoDepth = root.historyInt("undoDepth")?.takeIf { it in 0..100 }
            ?: error("Invalid undo depth")
        val redoDepth = root.historyInt("redoDepth")?.takeIf { it in 0..100 }
            ?: error("Invalid redo depth")
        val raw = root["points"] as? JsonArray ?: error("Missing points")
        require(raw.size <= 100) { "History beyond bounded display limit" }
        val points = raw.map { entry ->
            val row = entry as? JsonObject ?: error("Invalid history row")
            val id = row.historyString("id")
                ?.takeIf(historyNumericId::matches) ?: error("Invalid history id")
            val parent = row.historyString("parentId")?.takeIf(historyNumericId::matches)
            if (row["parentId"] !is kotlinx.serialization.json.JsonNull &&
                row["parentId"] != null && parent == null) error("Invalid parent id")
            val label = row.historyString("label")
                ?.takeIf { it.isNotBlank() && it.length <= 180 }
                ?: error("Missing history label")
            val created = row.historyString("createdAt")
                ?.takeIf { runCatching { Instant.parse(it) }.isSuccess }
                ?: error("Invalid history date")
            val selected = row.historyBoolean("current") ?: error("Missing current")
            ServerFinanceHistoryPoint(id,parent,label,created,selected)
        }
        ServerFinanceHistorySummary(
            available,generation,revision,current,canUndo,canRedo,undoDepth,redoDepth,points,
        )
    }.getOrNull()
