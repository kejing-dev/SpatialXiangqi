package com.kejing.spatialxiangqi.domain.model

/** Status of the side to move. Note that in xiangqi a stalemated side loses the game. */
enum class PositionStatus { ONGOING, CHECK, CHECKMATE, STALEMATE }
