package com.kejing.spatialxiangqi.domain.model

data class Piece(val side: Side, val type: PieceType) {
    /** The character printed on the piece (帥 for the red king, 將 for the black king, ...). */
    val glyph: Char
        get() = type.glyph(side)

    /** FEN letter: uppercase for red, lowercase for black. */
    val fenChar: Char
        get() = if (side == Side.RED) type.fenChar.uppercaseChar() else type.fenChar

    override fun toString(): String = "$side ${type.name} ($glyph)"

    companion object {
        private val cache: Map<Pair<Side, PieceType>, Piece> =
            Side.entries.flatMap { s -> PieceType.entries.map { t -> (s to t) to Piece(s, t) } }.toMap()

        /** Shared instance for the given side and type (avoids allocating 14 identical values). */
        fun of(side: Side, type: PieceType): Piece = cache.getValue(side to type)

        /** Parses a FEN letter, `null` for anything that is not a piece. */
        fun fromFenChar(c: Char): Piece? {
            val type = PieceType.fromFenChar(c) ?: return null
            return of(if (c.isUpperCase()) Side.RED else Side.BLACK, type)
        }
    }
}
