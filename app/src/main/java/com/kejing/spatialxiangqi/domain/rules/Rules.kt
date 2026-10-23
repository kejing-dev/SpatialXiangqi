package com.kejing.spatialxiangqi.domain.rules

import com.kejing.spatialxiangqi.domain.model.IllegalReason
import com.kejing.spatialxiangqi.domain.model.Move
import com.kejing.spatialxiangqi.domain.model.Piece
import com.kejing.spatialxiangqi.domain.model.PieceType
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.PositionStatus
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.Square
import kotlin.math.abs

/** The rules of xiangqi expressed on immutable [Position]s. All functions are pure. */
object Rules {

    /**
     * Legal moves of the piece on [from]. Empty when the square is empty or when the piece does not
     * belong to the side to move (pass `ignoreTurn = true` to list the moves of either side, e.g. in
     * the position editor).
     */
    fun legalMovesFrom(pos: Position, from: Square, ignoreTurn: Boolean = false): List<Move> {
        if (!from.isValid()) return emptyList()
        val piece = pos.pieceAt(from) ?: return emptyList()
        if (!ignoreTurn && piece.side != pos.sideToMove) return emptyList()
        val board = Board.from(pos)
        val out = MoveList()
        board.generateLegalFrom(from.index, out)
        return List(out.size) { board.toMove(out[it]) }
    }

    /** All legal moves for the side to move. */
    fun allLegalMoves(pos: Position): List<Move> {
        val board = Board.from(pos)
        val out = MoveList()
        board.generateLegal(pos.sideToMove.ordinal, out)
        return List(out.size) { board.toMove(out[it]) }
    }

    fun isLegal(pos: Position, from: Square, to: Square): Boolean = illegalReason(pos, from, to) == null

    /** The [Move] for `from -> to` if it is legal in [pos], else `null`. */
    fun moveOrNull(pos: Position, from: Square, to: Square): Move? =
        legalMovesFrom(pos, from).firstOrNull { it.to == to }

    /** Explains why `from -> to` is not allowed; `null` when the move is legal. */
    fun illegalReason(pos: Position, from: Square, to: Square): IllegalReason? {
        if (!from.isValid() || !to.isValid()) return IllegalReason.INVALID_MOVE_PATTERN
        val piece = pos.pieceAt(from) ?: return IllegalReason.NO_PIECE
        if (piece.side != pos.sideToMove) return IllegalReason.NOT_YOUR_TURN
        if (from == to) return IllegalReason.INVALID_MOVE_PATTERN
        val target = pos.pieceAt(to)
        if (target != null && target.side == piece.side) return IllegalReason.OWN_PIECE_ON_TARGET
        val board = Board.from(pos)
        val legal = MoveList()
        board.generateLegalFrom(from.index, legal)
        val packed = Board.pack(from.index, to.index)
        if (legal.contains(packed)) return null

        val side = piece.side
        val sideIdx = side.ordinal
        val df = to.file - from.file
        val dr = to.rank - from.rank
        val adf = abs(df)
        val adr = abs(dr)
        when (piece.type) {
            PieceType.KING -> {
                if (target != null && target.type == PieceType.KING && df == 0) {
                    return if (board.between(from.index, to.index) > 0) IllegalReason.PATH_BLOCKED
                    else IllegalReason.LEAVES_KING_IN_CHECK
                }
                if (adf + adr != 1) return IllegalReason.INVALID_MOVE_PATTERN
                if (!to.inPalace(side)) return IllegalReason.MUST_STAY_IN_PALACE
            }
            PieceType.ADVISOR -> {
                if (adf != 1 || adr != 1) return IllegalReason.INVALID_MOVE_PATTERN
                if (!to.inPalace(side)) return IllegalReason.MUST_STAY_IN_PALACE
            }
            PieceType.ELEPHANT -> {
                if (adf != 2 || adr != 2) return IllegalReason.INVALID_MOVE_PATTERN
                if (!to.onOwnHalf(side)) return IllegalReason.ELEPHANT_CANNOT_CROSS_RIVER
                if (pos.pieceAt(from.file + df / 2, from.rank + dr / 2) != null) return IllegalReason.BLOCKED_ELEPHANT_EYE
            }
            PieceType.HORSE -> {
                if (!((adf == 1 && adr == 2) || (adf == 2 && adr == 1))) return IllegalReason.INVALID_MOVE_PATTERN
                val legFile = if (adf == 2) from.file + df / 2 else from.file
                val legRank = if (adr == 2) from.rank + dr / 2 else from.rank
                if (pos.pieceAt(legFile, legRank) != null) return IllegalReason.HOBBLED_HORSE
            }
            PieceType.ROOK -> {
                if (df != 0 && dr != 0) return IllegalReason.INVALID_MOVE_PATTERN
                if (board.between(from.index, to.index) > 0) return IllegalReason.PATH_BLOCKED
            }
            PieceType.CANNON -> {
                if (df != 0 && dr != 0) return IllegalReason.INVALID_MOVE_PATTERN
                val n = board.between(from.index, to.index)
                if (if (target != null) n != 1 else n != 0) return IllegalReason.CANNON_NEEDS_ONE_SCREEN
            }
            PieceType.PAWN -> {
                if (adf + adr != 1) return IllegalReason.INVALID_MOVE_PATTERN
                if (dr == -side.forward) return IllegalReason.PAWN_CANNOT_RETREAT
                if (adf == 1 && from.onOwnHalf(side)) return IllegalReason.PAWN_SIDEWAYS_BEFORE_RIVER
            }
        }
        // The pattern is fine, so the move must expose the own king.
        board.make(packed)
        val facing = board.kingsFacing()
        val attacked = board.kingSq[sideIdx] >= 0 && board.isAttacked(board.kingSq[sideIdx], 1 - sideIdx)
        return if (facing && !attacked) IllegalReason.KINGS_FACING else IllegalReason.LEAVES_KING_IN_CHECK
    }

    /**
     * Plays [move] without checking legality: moves the piece, flips the side to move, resets the
     * halfmove clock on captures and pawn moves and bumps the fullmove number after black's move.
     */
    fun apply(pos: Position, move: Move): Position {
        val piece = pos.pieceAt(move.from) ?: move.piece
        val captured = pos.pieceAt(move.to)
        val map = HashMap(pos.pieces)
        map.remove(move.from)
        map[move.to] = piece
        val resetClock = captured != null || piece.type == PieceType.PAWN
        return Position(
            pieces = map,
            sideToMove = pos.sideToMove.opponent,
            halfmoveClock = if (resetClock) 0 else pos.halfmoveClock + 1,
            fullmoveNumber = if (pos.sideToMove == Side.BLACK) pos.fullmoveNumber + 1 else pos.fullmoveNumber,
        )
    }

    /** True when [side]'s king is attacked or the kings face each other on an open file. */
    fun isInCheck(pos: Position, side: Side): Boolean = Board.from(pos).inCheck(side.ordinal)

    /** True when the two kings stand on one file with nothing between them. */
    fun kingsFacing(pos: Position): Boolean = Board.from(pos).kingsFacing()

    /** Status of the side to move. Remember that a stalemated side loses in xiangqi. */
    fun status(pos: Position): PositionStatus {
        val board = Board.from(pos)
        val side = pos.sideToMove.ordinal
        val check = board.inCheck(side)
        val hasMove = board.hasLegalMove(side)
        return when {
            hasMove && check -> PositionStatus.CHECK
            hasMove -> PositionStatus.ONGOING
            check -> PositionStatus.CHECKMATE
            else -> PositionStatus.STALEMATE
        }
    }

    /**
     * True when [current] (placement + side to move, clocks ignored) has occurred at least [times]
     * times counting [history] and [current] itself. [history] may or may not already end with
     * [current]; both conventions give the same answer.
     */
    fun isRepetition(history: List<Position>, current: Position, times: Int = 3): Boolean {
        var count = history.count { it.samePlacement(current) }
        if (history.lastOrNull()?.samePlacement(current) != true) count++
        return count >= times
    }

    /** Human-readable problems with a hand-made setup; empty when the position can be played. */
    fun validateSetup(pos: Position): List<String> = validateSetupIssues(pos).map { it.message }

    /** Structured variant of [validateSetup]. */
    fun validateSetupIssues(pos: Position): List<SetupIssue> {
        val issues = ArrayList<SetupIssue>()
        for (side in Side.entries) {
            val name = side.chineseName
            for (type in PieceType.entries) {
                val n = pos.count(side, type)
                if (n > type.maxCount) {
                    issues += SetupIssue(SetupIssue.Kind.TOO_MANY, "$name${type.glyph(side)}的数量超过 ${type.maxCount}")
                }
            }
            if (pos.count(side, PieceType.KING) == 0) {
                issues += SetupIssue(SetupIssue.Kind.MISSING_KING, "缺少$name${PieceType.KING.glyph(side)}")
            }
        }
        for ((sq, piece) in pos.pieces.entries.sortedBy { it.key.index }) {
            if (!sq.isValid()) {
                issues += SetupIssue(SetupIssue.Kind.OFF_BOARD, "棋子超出棋盘：${sq.file},${sq.rank}")
                continue
            }
            val side = piece.side
            val glyph = piece.glyph
            val where = "（${sq.iccs}）"
            when (piece.type) {
                PieceType.KING, PieceType.ADVISOR -> if (!sq.inPalace(side) || (piece.type == PieceType.ADVISOR && !isAdvisorSquare(side, sq))) {
                    issues += SetupIssue(SetupIssue.Kind.BAD_SQUARE, "${side.chineseName}$glyph${where}不在合法位置")
                }
                PieceType.ELEPHANT -> if (!isElephantSquare(side, sq)) {
                    issues += SetupIssue(SetupIssue.Kind.BAD_SQUARE, "${side.chineseName}$glyph${where}不在合法位置")
                }
                PieceType.PAWN -> {
                    val behindStart = if (side == Side.RED) sq.rank < 3 else sq.rank > 6
                    val oddFileOnOwnHalf = sq.onOwnHalf(side) && sq.file % 2 == 1
                    if (behindStart || oddFileOnOwnHalf) {
                        issues += SetupIssue(SetupIssue.Kind.BAD_SQUARE, "${side.chineseName}$glyph${where}不在合法位置")
                    }
                }
                else -> Unit
            }
        }
        val board = Board.from(pos)
        if (board.kingsFacing()) issues += SetupIssue(SetupIssue.Kind.KINGS_FACING, "将帅照面")
        val opponent = pos.sideToMove.opponent
        if (board.kingSq[opponent.ordinal] >= 0 && !board.kingsFacing() &&
            board.isAttacked(board.kingSq[opponent.ordinal], pos.sideToMove.ordinal)
        ) {
            issues += SetupIssue(SetupIssue.Kind.OPPONENT_IN_CHECK, "轮到${pos.sideToMove.chineseName}走棋，但${opponent.chineseName}已处于被将军状态")
        }
        return issues
    }

    private fun isAdvisorSquare(side: Side, sq: Square): Boolean {
        val r0 = if (side == Side.RED) 0 else 9
        val dir = side.forward
        return (sq.rank == r0 && (sq.file == 3 || sq.file == 5)) ||
            (sq.rank == r0 + dir && sq.file == 4) ||
            (sq.rank == r0 + 2 * dir && (sq.file == 3 || sq.file == 5))
    }

    private fun isElephantSquare(side: Side, sq: Square): Boolean {
        val r0 = if (side == Side.RED) 0 else 9
        val dir = side.forward
        return (sq.rank == r0 && (sq.file == 2 || sq.file == 6)) ||
            (sq.rank == r0 + 2 * dir && (sq.file == 0 || sq.file == 4 || sq.file == 8)) ||
            (sq.rank == r0 + 4 * dir && (sq.file == 2 || sq.file == 6))
    }

    /** Material value in centipawns used by the engine and shown by the UI tide bar. */
    fun materialValue(piece: Piece, square: Square): Int = when (piece.type) {
        PieceType.KING -> 10000
        PieceType.ROOK -> 900
        PieceType.CANNON -> 450
        PieceType.HORSE -> 400
        PieceType.ELEPHANT -> 200
        PieceType.ADVISOR -> 200
        PieceType.PAWN -> if (square.onOwnHalf(piece.side)) 100 else 200
    }
}

/** One problem found by [Rules.validateSetupIssues]. */
data class SetupIssue(val kind: Kind, val message: String) {
    enum class Kind { MISSING_KING, TOO_MANY, BAD_SQUARE, OFF_BOARD, KINGS_FACING, OPPONENT_IN_CHECK }
}
