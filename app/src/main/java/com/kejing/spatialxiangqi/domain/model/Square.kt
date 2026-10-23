package com.kejing.spatialxiangqi.domain.model

/**
 * A board square. [file] 0..8 runs left to right from Red's viewpoint, [rank] 0..9 runs from
 * Red's back rank (0) to Black's back rank (9). The river lies between ranks 4 and 5.
 */
data class Square(val file: Int, val rank: Int) {

    /** ICCS coordinate, lowercase, e.g. `h2`. */
    val iccs: String
        get() = "${'a' + file}$rank"

    /** Linear index `rank * 9 + file` (0..89). */
    val index: Int
        get() = rank * 9 + file

    fun isValid(): Boolean = file in 0..8 && rank in 0..9

    /** True when the square lies inside [side]'s palace (files 3..5, ranks 0..2 for red, 7..9 for black). */
    fun inPalace(side: Side): Boolean =
        file in 3..5 && if (side == Side.RED) rank <= 2 else rank >= 7

    /** True when the square is on [side]'s own side of the river. */
    fun onOwnHalf(side: Side): Boolean = if (side == Side.RED) rank <= 4 else rank >= 5

    override fun toString(): String = iccs

    companion object {
        const val FILES = 9
        const val RANKS = 10

        /** Parses `h2`, `H2` or a full-width variant; returns `null` when malformed or off-board. */
        fun fromIccs(s: String): Square? {
            val t = s.trim()
            if (t.length != 2) return null
            val f = t[0].lowercaseChar() - 'a'
            val r = t[1] - '0'
            val sq = Square(f, r)
            return if (sq.isValid()) sq else null
        }

        fun fromIndex(index: Int): Square = Square(index % 9, index / 9)

        /** All 90 squares in index order (rank-major). */
        val ALL: List<Square> = (0 until 90).map { fromIndex(it) }
    }
}
