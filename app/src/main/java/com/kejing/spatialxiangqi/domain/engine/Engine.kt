package com.kejing.spatialxiangqi.domain.engine

import com.kejing.spatialxiangqi.domain.model.Move
import com.kejing.spatialxiangqi.domain.model.Position

/**
 * One principal variation. [scoreCp] and [mateIn] are always from red's perspective: positive means
 * red is better / red mates in [mateIn] moves, negative means black is better / black mates.
 * Mate scores are `±(100000 - ply)` centipawns.
 */
data class EngineLine(val moves: List<Move>, val scoreCp: Int, val mateIn: Int? = null, val depth: Int) {
    val firstMove: Move? get() = moves.firstOrNull()
}

interface Engine {
    /** The engine's move for [level] 1..5, or `null` when the side to move has no legal move. */
    suspend fun bestMove(pos: Position, level: Int): Move?

    /**
     * Up to [multiPv] lines within roughly [timeMs] milliseconds, best line first for the side to
     * move (descending [EngineLine.scoreCp] when red is to move, ascending when black is).
     */
    suspend fun analyse(pos: Position, multiPv: Int = 3, timeMs: Long = 600): List<EngineLine>

    /** Static evaluation in centipawns from red's perspective (material, piece-square tables, mobility). */
    fun evaluate(pos: Position): Int
}
