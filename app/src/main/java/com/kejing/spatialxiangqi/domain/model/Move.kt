package com.kejing.spatialxiangqi.domain.model

/**
 * A move of [piece] from [from] to [to], optionally capturing [captured].
 * Two moves are considered the same move when their [from] and [to] squares agree, see [sameSquares].
 */
data class Move(val from: Square, val to: Square, val piece: Piece, val captured: Piece? = null) {

    /** Lowercase ICCS pair, e.g. `h2e2`. */
    val iccs: String
        get() = from.iccs + to.iccs

    val isCapture: Boolean
        get() = captured != null

    val side: Side
        get() = piece.side

    /** True when [other] moves between the same two squares (piece/capture details ignored). */
    fun sameSquares(other: Move?): Boolean = other != null && other.from == from && other.to == to

    override fun toString(): String = iccs
}
