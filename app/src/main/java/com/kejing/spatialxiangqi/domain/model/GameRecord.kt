package com.kejing.spatialxiangqi.domain.model

import com.kejing.spatialxiangqi.domain.notation.Notation
import com.kejing.spatialxiangqi.domain.notation.NotationStyle
import java.time.LocalDate

/**
 * A sideline. [atPly] is the number of main-line plies played before the variation's first move
 * (0 = an alternative first move); the variation's first move is therefore an alternative to
 * main-line ply `atPly + 1`. Nested sidelines are flattened by repeating the parent variation's
 * moves as a prefix; [GameRecord.toTree] merges shared prefixes back into a tree.
 * [comments] and [nags] are keyed by absolute ply, like the ones on [GameRecord].
 */
data class VariationRecord(
    val atPly: Int,
    val movesIccs: List<String>,
    val comments: Map<Int, String> = emptyMap(),
    val nags: Map<Int, String> = emptyMap(),
)

/**
 * A stored game. Moves are ICCS strings (`h2e2`; `H2-E2` is accepted when reading), [comments]
 * and [nags] are keyed by main-line ply (0 = a comment before the first move).
 */
data class GameRecord(
    val id: String,
    val title: String,
    val red: String,
    val black: String,
    /** ISO date, e.g. `2026-09-22`. */
    val date: String,
    /** `1-0` red wins, `0-1` black wins, `1/2-1/2`, `*` unfinished. */
    val result: String,
    val opening: String = "",
    val startFen: String = Position.initial().toFen(),
    val movesIccs: List<String>,
    val variations: List<VariationRecord> = emptyList(),
    val comments: Map<Int, String> = emptyMap(),
    val nags: Map<Int, String> = emptyMap(),
    val level: Int? = null,
    val playerSide: Side? = null,
) {
    /** Number of main-line plies. */
    val plyCount: Int get() = movesIccs.size

    /** Start position; the initial position when [startFen] is empty or malformed. */
    fun startPosition(): Position = Position.fromFen(startFen) ?: Position.initial()

    companion object {
        const val RESULT_RED = "1-0"
        const val RESULT_BLACK = "0-1"
        const val RESULT_DRAW = "1/2-1/2"
        const val RESULT_UNKNOWN = "*"
    }
}

/** Header-style metadata used when turning a [GameTree] into a [GameRecord]. */
data class GameMeta(
    val title: String = "",
    val red: String = "",
    val black: String = "",
    val date: String = LocalDate.now().toString(),
    val result: String = GameRecord.RESULT_UNKNOWN,
    val opening: String = "",
    val level: Int? = null,
    val playerSide: Side? = null,
)

/**
 * Flattens the tree into a [GameRecord]: main line + every sideline (nested sidelines carry their
 * parent's moves as a prefix, see [VariationRecord]) + comments/nags of the main line.
 */
fun GameTree.toRecord(id: String, meta: GameMeta = GameMeta()): GameRecord {
    val main = mainLine()
    val comments = LinkedHashMap<Int, String>()
    val nags = LinkedHashMap<Int, String>()
    if (root.comment.isNotEmpty()) comments[0] = root.comment
    for (n in main) {
        if (n.comment.isNotEmpty()) comments[n.ply] = n.comment
        if (n.nags.isNotEmpty()) nags[n.ply] = n.nags.joinToString(" ")
    }
    val variations = ArrayList<VariationRecord>()
    fun collect(node: GameTree.Node) {
        // every child after the first starts a sideline
        for (i in 1 until node.children.size) {
            val start = node.children[i]
            val pathNodes = pathTo(node)
            var branchPly = 0
            for (n in pathNodes) { if (n.isOnMainLine) branchPly = n.ply else break }
            val prefix = pathNodes.filter { it.ply > branchPly }
            val line = prefix + start.lineFrom()
            val vComments = LinkedHashMap<Int, String>()
            val vNags = LinkedHashMap<Int, String>()
            for (n in line) {
                if (n.comment.isNotEmpty()) vComments[n.ply] = n.comment
                if (n.nags.isNotEmpty()) vNags[n.ply] = n.nags.joinToString(" ")
            }
            variations += VariationRecord(branchPly, line.map { it.move!!.iccs }, vComments, vNags)
        }
        for (c in node.children) collect(c)
    }
    collect(root)
    return GameRecord(
        id = id,
        title = meta.title,
        red = meta.red,
        black = meta.black,
        date = meta.date,
        result = meta.result,
        opening = meta.opening,
        startFen = root.position.toFen(),
        movesIccs = main.map { it.move!!.iccs },
        variations = variations,
        comments = comments,
        nags = nags,
        level = meta.level,
        playerSide = meta.playerSide,
    )
}

/**
 * Rebuilds the [GameTree] of a record. Lenient: a move that cannot be parsed (any notation is
 * accepted, ICCS expected) ends its line silently; use [toTreeStrict] to get an error instead.
 * The tree's [GameTree.current] is left at the end of the main line.
 */
fun GameRecord.toTree(): GameTree = buildTree(strict = false)

/** Like [toTree] but fails with the offending move ("第 N 手无法解析：...") instead of truncating. */
fun GameRecord.toTreeStrict(): Result<GameTree> = runCatching { buildTree(strict = true) }

private fun GameRecord.buildTree(strict: Boolean): GameTree {
    val start = if (strict) {
        Position.fromFen(startFen) ?: throw IllegalArgumentException("FEN 格式错误")
    } else startPosition()
    val tree = GameTree(start)

    fun replay(moves: List<String>, comments: Map<Int, String>, nags: Map<Int, String>): Boolean {
        for (text in moves) {
            val move = Notation.parse(tree.current.position, text)
            if (move == null) {
                if (strict) throw IllegalArgumentException("第 ${tree.current.ply + 1} 手无法解析：$text")
                return false
            }
            val node = tree.play(move)
            comments[node.ply]?.let { if (node.comment.isEmpty()) node.comment = it }
            nags[node.ply]?.let { s -> node.nags += s.split(' ').filter { it.isNotEmpty() } }
        }
        return true
    }

    comments[0]?.let { tree.root.comment = it }
    replay(movesIccs, comments, nags)
    val main = tree.mainLine()
    for (v in variations) {
        tree.goTo(if (v.atPly == 0) tree.root else main.getOrNull(v.atPly - 1) ?: continue)
        replay(v.movesIccs, v.comments, v.nags)
    }
    tree.toStart()
    tree.toEnd()
    return tree
}

/** The main line as [Move]s (stops at the first unparseable move). */
fun GameRecord.mainLineMoves(): List<Move> = toTree().mainLine().map { it.move!! }

/** Convenience: the position after the main line. */
fun GameRecord.finalPosition(): Position = toTree().mainLine().lastOrNull()?.position ?: startPosition()

/** Formats a stored ICCS move for display in the record's own context, e.g. for a library card. */
fun GameRecord.firstMovesText(count: Int = 4, style: NotationStyle = NotationStyle.CHINESE): String {
    val tree = toTree()
    val nodes = tree.mainLine().take(count)
    return nodes.joinToString(" ") { n -> Notation.format(n.parent!!.position, n.move!!, style) }
}
