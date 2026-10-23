package com.kejing.spatialxiangqi.domain.model

/** Why a requested move is not allowed. Exposed so the UI can explain a rejected move to the player. */
enum class IllegalReason {
    NOT_YOUR_TURN,
    NO_PIECE,
    OWN_PIECE_ON_TARGET,
    HOBBLED_HORSE,
    BLOCKED_ELEPHANT_EYE,
    ELEPHANT_CANNOT_CROSS_RIVER,
    MUST_STAY_IN_PALACE,
    CANNON_NEEDS_ONE_SCREEN,
    PATH_BLOCKED,
    PAWN_CANNOT_RETREAT,
    PAWN_SIDEWAYS_BEFORE_RIVER,
    KINGS_FACING,
    LEAVES_KING_IN_CHECK,
    INVALID_MOVE_PATTERN;

    /** Short Chinese explanation suitable for a snackbar. */
    val message: String
        get() = when (this) {
            NOT_YOUR_TURN -> "还没轮到这一方走棋"
            NO_PIECE -> "起点没有棋子"
            OWN_PIECE_ON_TARGET -> "目标位置有己方棋子"
            HOBBLED_HORSE -> "蹩马腿"
            BLOCKED_ELEPHANT_EYE -> "塞象眼"
            ELEPHANT_CANNOT_CROSS_RIVER -> "相（象）不能过河"
            MUST_STAY_IN_PALACE -> "将（帅）与士（仕）不能离开九宫"
            CANNON_NEEDS_ONE_SCREEN -> "炮吃子需要隔一个炮架"
            PATH_BLOCKED -> "路径被阻挡"
            PAWN_CANNOT_RETREAT -> "兵（卒）不能后退"
            PAWN_SIDEWAYS_BEFORE_RIVER -> "兵（卒）过河前不能横走"
            KINGS_FACING -> "将帅不能照面"
            LEAVES_KING_IN_CHECK -> "走后己方仍被将军"
            INVALID_MOVE_PATTERN -> "不符合该棋子的走法"
        }
}
