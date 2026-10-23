package com.kejing.spatialxiangqi.domain

import com.kejing.spatialxiangqi.domain.model.Move
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.Square
import com.kejing.spatialxiangqi.domain.notation.Notation
import com.kejing.spatialxiangqi.domain.rules.Rules
import org.junit.Assert.assertNotNull

internal fun fen(s: String): Position = Position.fromFen(s) ?: throw AssertionError("bad FEN: $s")

internal fun sq(iccs: String): Square = Square.fromIccs(iccs) ?: throw AssertionError("bad square: $iccs")

/** Plays a sequence of moves written in any notation and returns the resulting position. */
internal fun play(start: Position, vararg moves: String): Position {
    var pos = start
    for (m in moves) {
        val move = Notation.parse(pos, m)
        assertNotNull("cannot parse $m in ${pos.toFen()}", move)
        pos = Rules.apply(pos, move!!)
    }
    return pos
}

internal fun move(pos: Position, text: String): Move =
    Notation.parse(pos, text) ?: throw AssertionError("cannot parse $text in ${pos.toFen()}")

/**
 * Mate-in-one position used across the suites: red plays b1b9 (R b1-b9). The black pawn on a4 always
 * has a move, so no red move can win by stalemate (困毙) and the checkmate is the only winning move.
 */
internal const val MATE_IN_ONE_FEN = "4k4/R8/9/9/9/p8/9/9/1R7/3K5 w - - 0 1"

/** Black to move has no legal move but is not in check. */
internal const val STALEMATE_FEN = "4k4/3P1P3/9/9/9/9/9/9/9/3K5 b - - 0 1"
