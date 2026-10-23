package com.kejing.spatialxiangqi.domain.engine

import com.kejing.spatialxiangqi.domain.rules.Board
import com.kejing.spatialxiangqi.domain.rules.MoveList

/** Material values, piece-square tables and mobility, all in centipawns from red's perspective. */
internal object Evaluation {
    const val MATE = 100_000
    const val MATE_THRESHOLD = MATE - 1000
    const val INF = MATE + 1

    /** Indexed by [Board] type constant. Pawn value is 100 on its own half, 200 once across (see [pawnValue]). */
    val MATERIAL = intArrayOf(10_000, 200, 200, 400, 900, 450, 100)

    private val MOBILITY = intArrayOf(0, 0, 0, 4, 4, 2, 0)

    // Tables are written as seen on a printed board: first row = rank 9 (black's back rank),
    // last row = rank 0 (red's back rank); all from the red piece's point of view.
    private val KING_T = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, -6, -6, -6, 0, 0, 0),
        intArrayOf(0, 0, 0, -2, -2, -2, 0, 0, 0),
        intArrayOf(0, 0, 0, 1, 5, 1, 0, 0, 0),
    )
    private val ADVISOR_T = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 3, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
    )
    private val ELEPHANT_T = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, -1, 0, 0, 0, -1, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(-2, 0, 0, 0, 3, 0, 0, 0, -2),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
    )
    private val HORSE_T = arrayOf(
        intArrayOf(4, 8, 16, 12, 4, 12, 16, 8, 4),
        intArrayOf(4, 10, 28, 16, 8, 16, 28, 10, 4),
        intArrayOf(12, 14, 16, 20, 18, 20, 16, 14, 12),
        intArrayOf(8, 24, 18, 24, 20, 24, 18, 24, 8),
        intArrayOf(6, 16, 14, 18, 16, 18, 14, 16, 6),
        intArrayOf(4, 12, 16, 14, 12, 14, 16, 12, 4),
        intArrayOf(2, 6, 8, 6, 10, 6, 8, 6, 2),
        intArrayOf(4, 2, 8, 8, 4, 8, 8, 2, 4),
        intArrayOf(0, 2, 4, 4, -2, 4, 4, 2, 0),
        intArrayOf(0, -4, 0, 0, 0, 0, 0, -4, 0),
    )
    private val ROOK_T = arrayOf(
        intArrayOf(14, 14, 12, 18, 16, 18, 12, 14, 14),
        intArrayOf(16, 20, 18, 24, 26, 24, 18, 20, 16),
        intArrayOf(12, 12, 12, 18, 18, 18, 12, 12, 12),
        intArrayOf(12, 18, 16, 22, 22, 22, 16, 18, 12),
        intArrayOf(12, 14, 12, 18, 18, 18, 12, 14, 12),
        intArrayOf(12, 16, 14, 20, 20, 20, 14, 16, 12),
        intArrayOf(6, 10, 8, 14, 14, 14, 8, 10, 6),
        intArrayOf(4, 8, 6, 14, 12, 14, 6, 8, 4),
        intArrayOf(8, 4, 8, 16, 8, 16, 8, 4, 8),
        intArrayOf(-2, 10, 6, 14, 12, 14, 6, 10, -2),
    )
    private val CANNON_T = arrayOf(
        intArrayOf(6, 4, 0, -10, -12, -10, 0, 4, 6),
        intArrayOf(2, 2, 0, -4, -14, -4, 0, 2, 2),
        intArrayOf(2, 2, 0, -10, -8, -10, 0, 2, 2),
        intArrayOf(0, 0, -2, 4, 10, 4, -2, 0, 0),
        intArrayOf(0, 0, 0, 2, 8, 2, 0, 0, 0),
        intArrayOf(-2, 0, 4, 2, 6, 2, 4, 0, -2),
        intArrayOf(0, 0, 0, 2, 4, 2, 0, 0, 0),
        intArrayOf(4, 0, 8, 6, 10, 6, 8, 0, 4),
        intArrayOf(0, 2, 4, 6, 6, 6, 4, 2, 0),
        intArrayOf(0, 0, 2, 6, 6, 6, 2, 0, 0),
    )
    private val PAWN_T = arrayOf(
        intArrayOf(0, 3, 6, 9, 12, 9, 6, 3, 0),
        intArrayOf(18, 36, 56, 80, 120, 80, 56, 36, 18),
        intArrayOf(14, 26, 42, 60, 80, 60, 42, 26, 14),
        intArrayOf(10, 20, 30, 34, 40, 34, 30, 20, 10),
        intArrayOf(6, 12, 18, 18, 20, 18, 18, 12, 6),
        intArrayOf(0, 0, -2, 0, 4, 0, -2, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
    )

    /** PST[type][index] for a red piece; black pieces use the vertically mirrored index. */
    private val PST: Array<IntArray> = Array(7) { type ->
        val table = when (type) {
            Board.T_KING -> KING_T
            Board.T_ADVISOR -> ADVISOR_T
            Board.T_ELEPHANT -> ELEPHANT_T
            Board.T_HORSE -> HORSE_T
            Board.T_ROOK -> ROOK_T
            Board.T_CANNON -> CANNON_T
            else -> PAWN_T
        }
        IntArray(90) { idx -> table[9 - Board.rankOf(idx)][Board.fileOf(idx)] }
    }

    fun pieceValue(code: Int, idx: Int): Int {
        val type = Board.typeOf(code)
        if (type == Board.T_PAWN) return pawnValue(Board.sideOf(code), Board.rankOf(idx))
        return MATERIAL[type]
    }

    fun pawnValue(side: Int, rank: Int): Int = if (Board.crossed(side, rank)) 200 else 100

    /** Value of the piece on [idx] including its square bonus (0 for empty). */
    fun placedValue(board: Board, idx: Int): Int {
        val c = board.cells[idx]
        if (c == Board.EMPTY) return 0
        val side = Board.sideOf(c)
        val type = Board.typeOf(c)
        val mirrored = if (side == Board.RED) idx else (9 - Board.rankOf(idx)) * 9 + Board.fileOf(idx)
        return pieceValue(c, idx) + PST[type][mirrored]
    }

    /** Full static evaluation from red's perspective. */
    fun evaluate(board: Board, scratch: MoveList): Int {
        var score = 0
        for (i in 0 until 90) {
            val c = board.cells[i]
            if (c == Board.EMPTY) continue
            val side = Board.sideOf(c)
            val type = Board.typeOf(c)
            var v = placedValue(board, i)
            val w = MOBILITY[type]
            if (w != 0) {
                scratch.clear()
                board.generatePseudoFrom(i, scratch)
                v += w * scratch.size
            }
            score += if (side == Board.RED) v else -v
        }
        return score
    }
}
