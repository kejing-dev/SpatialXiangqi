package com.kejing.spatialxiangqi.domain.model

/** The two sides of a xiangqi game. Red moves first and sits at the bottom (ranks 0..4). */
enum class Side {
    RED, BLACK;

    val opponent: Side
        get() = if (this == RED) BLACK else RED

    /** Rank direction of "forward" for this side: red advances towards rank 9, black towards rank 0. */
    val forward: Int
        get() = if (this == RED) 1 else -1

    /** Chinese name used in UI strings and PGN error messages. */
    val chineseName: String
        get() = if (this == RED) "红方" else "黑方"
}
