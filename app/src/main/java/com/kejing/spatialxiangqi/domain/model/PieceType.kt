package com.kejing.spatialxiangqi.domain.model

/**
 * The seven piece types. [redGlyph]/[blackGlyph] are the traditional characters printed on the
 * pieces, [letter] is the WXF letter and [fenChar] the lowercase FEN letter (Pikafish/UCI
 * convention: `b` = elephant, `n` = horse).
 */
enum class PieceType(val redGlyph: Char, val blackGlyph: Char, val letter: Char, val fenChar: Char) {
    KING('帥', '將', 'K', 'k'),
    ADVISOR('仕', '士', 'A', 'a'),
    ELEPHANT('相', '象', 'E', 'b'),
    HORSE('傌', '馬', 'H', 'n'),
    ROOK('俥', '車', 'R', 'r'),
    CANNON('炮', '砲', 'C', 'c'),
    PAWN('兵', '卒', 'P', 'p');

    fun glyph(side: Side): Char = if (side == Side.RED) redGlyph else blackGlyph

    /** Maximum number of pieces of this type one side may own. */
    val maxCount: Int
        get() = when (this) {
            KING -> 1
            PAWN -> 5
            else -> 2
        }

    companion object {
        /** Resolves a WXF letter (`K A E H R C P`, case-insensitive; also accepts `B` and `N`). */
        fun fromLetter(c: Char): PieceType? = when (c.uppercaseChar()) {
            'K' -> KING
            'A' -> ADVISOR
            'E', 'B' -> ELEPHANT
            'H', 'N' -> HORSE
            'R' -> ROOK
            'C' -> CANNON
            'P' -> PAWN
            else -> null
        }

        /** Resolves a FEN letter (case-insensitive; accepts both `b`/`e` for elephant and `n`/`h` for horse). */
        fun fromFenChar(c: Char): PieceType? = fromLetter(c)

        /** Resolves any Chinese piece character (simplified, traditional, either side). */
        fun fromGlyph(c: Char): PieceType? = when (c) {
            '帥', '帅', '將', '将' -> KING
            '仕', '士' -> ADVISOR
            '相', '象' -> ELEPHANT
            '傌', '馬', '马' -> HORSE
            '俥', '車', '车' -> ROOK
            '炮', '砲' -> CANNON
            '兵', '卒' -> PAWN
            else -> null
        }
    }
}
