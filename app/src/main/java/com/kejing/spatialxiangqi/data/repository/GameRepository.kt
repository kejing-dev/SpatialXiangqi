package com.kejing.spatialxiangqi.data.repository

import android.content.Context
import com.kejing.spatialxiangqi.domain.model.GameRecord
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.VariationRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local game library (棋谱库). Records are stored as JSON files in the app's private storage. */
interface GameRepository {
    val games: StateFlow<List<GameRecord>>
    suspend fun save(record: GameRecord)
    suspend fun delete(id: String)
    suspend fun get(id: String): GameRecord?
}

/** In-memory implementation for tests and previews. */
class FakeGameRepository(initial: List<GameRecord> = emptyList()) : GameRepository {
    private val state = MutableStateFlow(initial.sortedByDescending { it.date })
    override val games: StateFlow<List<GameRecord>> = state
    override suspend fun save(record: GameRecord) {
        state.value = (state.value.filterNot { it.id == record.id } + record).sortedByDescending { it.date }
    }
    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
    override suspend fun get(id: String): GameRecord? = state.value.firstOrNull { it.id == id }
}

/** File-backed implementation: one JSON file per game under files/games/. */
class FileGameRepository(context: Context) : GameRepository {
    private val dir = File(context.applicationContext.filesDir, "games").apply { mkdirs() }
    private val state = MutableStateFlow(loadAll())
    override val games: StateFlow<List<GameRecord>> = state

    override suspend fun save(record: GameRecord) {
        File(dir, "${record.id}.json").writeText(GameJson.encode(record).toString())
        state.value = (state.value.filterNot { it.id == record.id } + record).sortedByDescending { it.date }
    }

    override suspend fun delete(id: String) {
        File(dir, "$id.json").delete()
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun get(id: String): GameRecord? = state.value.firstOrNull { it.id == id }

    private fun loadAll(): List<GameRecord> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { f -> runCatching { GameJson.decode(JSONObject(f.readText())) }.getOrNull() }
            .sortedByDescending { it.date }
}

/** JSON codec for [GameRecord] (org.json, no extra dependency). */
object GameJson {
    fun encode(r: GameRecord): JSONObject = JSONObject().apply {
        put("id", r.id); put("title", r.title); put("red", r.red); put("black", r.black)
        put("date", r.date); put("result", r.result); put("opening", r.opening); put("startFen", r.startFen)
        put("moves", JSONArray(r.movesIccs))
        put("variations", JSONArray(r.variations.map { v -> JSONObject().put("atPly", v.atPly).put("moves", JSONArray(v.movesIccs)) }))
        put("comments", JSONObject(r.comments.mapKeys { it.key.toString() }))
        put("nags", JSONObject(r.nags.mapKeys { it.key.toString() }))
        r.level?.let { put("level", it) }
        r.playerSide?.let { put("playerSide", it.name) }
    }

    fun decode(o: JSONObject): GameRecord {
        fun strList(a: JSONArray?): List<String> = a?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList()
        fun intMap(obj: JSONObject?): Map<Int, String> = obj?.let { m -> m.keys().asSequence().associate { it.toInt() to m.getString(it) } } ?: emptyMap()
        val vars = o.optJSONArray("variations")?.let { arr ->
            List(arr.length()) { i -> val v = arr.getJSONObject(i); VariationRecord(v.getInt("atPly"), strList(v.optJSONArray("moves"))) }
        } ?: emptyList()
        return GameRecord(
            id = o.getString("id"),
            title = o.optString("title"),
            red = o.optString("red"),
            black = o.optString("black"),
            date = o.optString("date"),
            result = o.optString("result", "*"),
            opening = o.optString("opening"),
            startFen = o.optString("startFen"),
            movesIccs = strList(o.optJSONArray("moves")),
            variations = vars,
            comments = intMap(o.optJSONObject("comments")),
            nags = intMap(o.optJSONObject("nags")),
            level = if (o.has("level")) o.getInt("level") else null,
            playerSide = o.optString("playerSide").takeIf { it.isNotEmpty() }?.let { Side.valueOf(it) },
        )
    }
}
