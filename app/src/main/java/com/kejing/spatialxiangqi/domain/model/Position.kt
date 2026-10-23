package com.kejing.spatialxiangqi.domain.model

/**
 * An immutable board position: piece placement, side to move and the two FEN clocks.
 *
 * FEN follows the Pikafish/UCI convention: ranks from 9 (top, black) down to 0 separated by `/`,
 * `rnbakabnr` letters (lowercase black, uppercase red, `b` = elephant, `n` = horse), then the side
 * to move (`w` = red, `b` = black; `r` is accepted for red), two placeholder fields and the clocks.
 */
data class Position(
    val pieces: Map<Square, Piece>,
    val sideToMove: Side,
    val halfmoveClock: Int = 0,
    val fullmoveNumber: Int = 1,
) {
    fun pieceAt(sq: Square): Piece? = pieces[sq]

    fun pieceAt(file: Int, rank: Int): Piece? = pieces[Square(file, rank)]

    fun kingSquare(side: Side): Square? =
        pieces.entries.firstOrNull { it.value.side == side && it.value.type == PieceType.KING }?.key

    /** Full FEN string, e.g. the initial position is [INITIAL_FEN]. */
    fun toFen(): String = "${placementFen()} ${if (sideToMove == Side.RED) "w" else "b"} - - $halfmoveClock $fullmoveNumber"

    /** Piece placement field only (the first FEN field). */
    fun placementFen(): String {
        val sb = StringBuilder()
        for (rank in 9 downTo 0) {
            var empty = 0
            for (file in 0..8) {
                val p = pieces[Square(file, rank)]
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(p.fenChar)
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank > 0) sb.append('/')
        }
        return sb.toString()
    }

    /** Placement + side to move, ignoring the clocks: the key used for repetition detection. */
    val repetitionKey: String
        get() = placementFen() + (if (sideToMove == Side.RED) " w" else " b")

    /** True when both positions have the same pieces and the same side to move (clocks ignored). */
    fun samePlacement(other: Position): Boolean = sideToMove == other.sideToMove && pieces == other.pieces

    fun count(side: Side, type: PieceType): Int = pieces.values.count { it.side == side && it.type == type }

    override fun toString(): String = toFen()

    companion object {
        const val INITIAL_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"

        private val initialPosition: Position by lazy { requireNotNull(fromFen(INITIAL_FEN)) }

        fun initial(): Position = initialPosition

        /**
         * Parses a FEN string. Only the placement field is mandatory; the side defaults to red and the
         * clocks to `0 1`. Returns `null` for anything that does not describe a 9x10 board.
         */
        fun fromFen(fen: String): Position? {
            val parts = fen.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            val rows = parts[0].split('/')
            if (rows.size != 10) return null
            val map = HashMap<Square, Piece>(32)
            for ((i, row) in rows.withIndex()) {
                val rank = 9 - i
                var file = 0
                for (ch in row) {
                    if (ch in '1'..'9') {
                        file += ch - '0'
                        continue
                    }
                    val piece = Piece.fromFenChar(ch) ?: return null
                    if (file > 8) return null
                    map[Square(file, rank)] = piece
                    file++
                }
                if (file != 9) return null
            }
            val side = when (parts.getOrNull(1)?.lowercase()) {
                null, "w", "r", "red" -> Side.RED
                "b", "black" -> Side.BLACK
                else -> return null
            }
            val halfmove = parts.getOrNull(4)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val fullmove = parts.getOrNull(5)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            return Position(map, side, halfmove, fullmove)
        }

        /** Loose check used by importers: does this line look like a FEN placement field? */
        fun looksLikeFen(text: String): Boolean =
            Regex("^[kabenhrcpKABENHRCP1-9]+(/[kabenhrcpKABENHRCP1-9]+){9}(\\s+[wbrWBR].*)?$").matches(text.trim())
    }
}
