package com.kejing.spatialxiangqi.domain

import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.rules.Board
import com.kejing.spatialxiangqi.domain.rules.MoveList
import com.kejing.spatialxiangqi.domain.rules.Rules
import org.junit.Assert.assertEquals
import org.junit.Test

/** Node counts from the initial position, as published for xiangqi perft (44, 1920, 79666, ...). */
class PerftTest {

    private fun perft(board: Board, depth: Int): Long {
        if (depth == 0) return 1
        val moves = MoveList()
        board.generateLegal(board.sideToMove, moves)
        if (depth == 1) return moves.size.toLong()
        var nodes = 0L
        for (i in 0 until moves.size) {
            val cap = board.make(moves[i])
            nodes += perft(board, depth - 1)
            board.unmake(moves[i], cap)
        }
        return nodes
    }

    @Test
    fun initialPositionPerft() {
        val board = Board.from(Position.initial())
        assertEquals(44L, perft(board, 1))
        assertEquals(1920L, perft(board, 2))
        assertEquals(79666L, perft(board, 3))
        assertEquals(Position.INITIAL_FEN, board.toPosition().toFen()) // make/unmake left the board intact
    }

    @Test
    fun publicRulesAgreeWithBoardAtDepthTwo() {
        var total = 0
        val start = Position.initial()
        for (m in Rules.allLegalMoves(start)) total += Rules.allLegalMoves(Rules.apply(start, m)).size
        assertEquals(1920, total)
    }
}
