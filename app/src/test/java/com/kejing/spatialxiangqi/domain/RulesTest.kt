package com.kejing.spatialxiangqi.domain

import com.kejing.spatialxiangqi.domain.model.IllegalReason
import com.kejing.spatialxiangqi.domain.model.Piece
import com.kejing.spatialxiangqi.domain.model.PieceType
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.PositionStatus
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.Square
import com.kejing.spatialxiangqi.domain.rules.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    @Test
    fun initialPositionHas44LegalRedMoves() {
        val moves = Rules.allLegalMoves(Position.initial())
        assertEquals(44, moves.size)
        assertEquals(44, moves.map { it.iccs }.toSet().size)
        assertTrue(moves.all { it.piece.side == Side.RED })
    }

    @Test
    fun blackMovesMirrorRedMoves() {
        val blackFirst = fen("rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR b - - 0 1")
        assertEquals(44, Rules.allLegalMoves(blackFirst).size)
        // after 炮二平五 the vacated h2 gives the black cannon on h7 one more square (and no capture on h0)
        val pos = play(Position.initial(), "炮二平五")
        assertEquals(Side.BLACK, pos.sideToMove)
        assertEquals(45, Rules.allLegalMoves(pos).size)
    }

    @Test
    fun horseLegBlocked() {
        val pos = Position.initial()
        assertEquals(IllegalReason.HOBBLED_HORSE, Rules.illegalReason(pos, sq("b0"), sq("d1")))
        assertNull(Rules.illegalReason(pos, sq("b0"), sq("c2")))
        // once the elephant is gone the leg is free
        val open = fen("rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RN1AKABNR w - - 0 1")
        assertNull(Rules.illegalReason(open, sq("b0"), sq("d1")))
    }

    @Test
    fun elephantEyeBlocked() {
        val pos = fen("3k5/9/9/9/9/9/9/9/3N5/2B1K4 w - - 0 1")
        assertEquals(IllegalReason.BLOCKED_ELEPHANT_EYE, Rules.illegalReason(pos, sq("c0"), sq("e2")))
        assertNull(Rules.illegalReason(pos, sq("c0"), sq("a2")))
    }

    @Test
    fun elephantCannotCrossRiver() {
        val pos = fen("3k5/9/9/9/9/2B6/9/9/9/4K4 w - - 0 1")
        assertEquals(IllegalReason.ELEPHANT_CANNOT_CROSS_RIVER, Rules.illegalReason(pos, sq("c4"), sq("e6")))
        assertEquals(IllegalReason.ELEPHANT_CANNOT_CROSS_RIVER, Rules.illegalReason(pos, sq("c4"), sq("a6")))
        assertNull(Rules.illegalReason(pos, sq("c4"), sq("e2")))
        assertEquals(setOf("a2", "e2"), Rules.legalMovesFrom(pos, sq("c4")).map { it.to.iccs }.toSet())
    }

    @Test
    fun cannonCaptureNeedsExactlyOneScreen() {
        val pos = Position.initial()
        assertNull(Rules.illegalReason(pos, sq("b2"), sq("b9")))              // one screen: the black cannon
        assertEquals(IllegalReason.CANNON_NEEDS_ONE_SCREEN, Rules.illegalReason(pos, sq("b2"), sq("b7"))) // zero screens
        assertEquals(IllegalReason.CANNON_NEEDS_ONE_SCREEN, Rules.illegalReason(pos, sq("b2"), sq("b8"))) // non-capture over a screen
        // zero screens: the rook on i0 cannot be taken
        val zero = fen("4k4/9/9/9/9/9/9/9/9/3K1C2r w - - 0 1")
        assertEquals(IllegalReason.CANNON_NEEDS_ONE_SCREEN, Rules.illegalReason(zero, sq("f0"), sq("i0")))
        // one screen along the file (e1) lets the cannon take on e2 but not on e1
        val file = fen("4k4/9/9/9/9/9/9/4p4/4p4/3KC4 w - - 0 1")
        assertNull(Rules.illegalReason(file, sq("e0"), sq("e2")))
        assertEquals(IllegalReason.CANNON_NEEDS_ONE_SCREEN, Rules.illegalReason(file, sq("e0"), sq("e1")))
        val twoScreens = fen("4k4/9/9/9/9/9/9/9/9/3KCppr1 w - - 0 1")
        assertEquals(IllegalReason.CANNON_NEEDS_ONE_SCREEN, Rules.illegalReason(twoScreens, sq("e0"), sq("h0")))
        val oneScreen = fen("4k4/9/9/9/9/9/9/9/9/3KCp1r1 w - - 0 1")
        assertNull(Rules.illegalReason(oneScreen, sq("e0"), sq("h0")))
    }

    @Test
    fun flyingGeneralRule() {
        val pinned = fen("4k4/9/9/9/9/9/9/9/4R4/4K4 w - - 0 1")
        assertEquals(IllegalReason.KINGS_FACING, Rules.illegalReason(pinned, sq("e1"), sq("a1")))
        assertNull(Rules.illegalReason(pinned, sq("e1"), sq("e5")))
        assertFalse(Rules.isInCheck(pinned, Side.RED))
        val open = fen("3k5/9/9/9/9/9/9/9/9/4K4 w - - 0 1")
        assertEquals(IllegalReason.KINGS_FACING, Rules.illegalReason(open, sq("e0"), sq("d0")))
        assertNull(Rules.illegalReason(open, sq("e0"), sq("f0")))
        assertTrue(Rules.kingsFacing(fen("4k4/9/9/9/9/9/9/9/9/4K4 w - - 0 1")))
    }

    @Test
    fun moveLeavingOwnKingInCheckIsIllegal() {
        val pos = fen("4rk3/9/9/9/9/9/9/9/4N4/4K4 w - - 0 1")
        assertEquals(IllegalReason.LEAVES_KING_IN_CHECK, Rules.illegalReason(pos, sq("e1"), sq("g2")))
        assertTrue(Rules.legalMovesFrom(pos, sq("e1")).isEmpty())
        assertFalse(Rules.isLegal(pos, sq("e1"), sq("c2")))
        assertTrue(Rules.isLegal(pos, sq("e0"), sq("d0")))
    }

    @Test
    fun checkmateDetection() {
        val pos = fen(MATE_IN_ONE_FEN)
        assertEquals(PositionStatus.ONGOING, Rules.status(pos))
        val mate = Rules.apply(pos, move(pos, "b1b9"))
        assertEquals(PositionStatus.CHECKMATE, Rules.status(mate))
        assertTrue(Rules.isInCheck(mate, Side.BLACK))
        assertTrue(Rules.allLegalMoves(mate).isEmpty())
        // only one mating move exists
        val mates = Rules.allLegalMoves(pos).filter { Rules.status(Rules.apply(pos, it)) == PositionStatus.CHECKMATE }
        assertEquals(listOf("b1b9"), mates.map { it.iccs })
    }

    @Test
    fun stalemateDetection() {
        val pos = fen(STALEMATE_FEN)
        assertEquals(PositionStatus.STALEMATE, Rules.status(pos))
        assertFalse(Rules.isInCheck(pos, Side.BLACK))
        assertTrue(Rules.allLegalMoves(pos).isEmpty())
    }

    @Test
    fun checkStatus() {
        val pos = fen("4k4/9/9/9/9/9/9/9/9/3KR4 b - - 0 1")
        assertEquals(PositionStatus.CHECK, Rules.status(pos))
        assertEquals(listOf("f9"), Rules.allLegalMoves(pos).map { it.to.iccs })
    }

    @Test
    fun pawnRules() {
        val pos = Position.initial()
        assertEquals(IllegalReason.PAWN_CANNOT_RETREAT, Rules.illegalReason(pos, sq("e3"), sq("e2")))
        assertEquals(IllegalReason.PAWN_SIDEWAYS_BEFORE_RIVER, Rules.illegalReason(pos, sq("e3"), sq("d3")))
        assertNull(Rules.illegalReason(pos, sq("e3"), sq("e4")))
        val crossed = fen("3k5/9/9/9/4P4/9/9/9/9/4K4 w - - 0 1")
        assertEquals(setOf("d5", "f5", "e6"), Rules.legalMovesFrom(crossed, sq("e5")).map { it.to.iccs }.toSet())
        assertEquals(IllegalReason.PAWN_CANNOT_RETREAT, Rules.illegalReason(crossed, sq("e5"), sq("e4")))
    }

    @Test
    fun kingAndAdvisorStayInPalace() {
        val pos = fen("3k5/9/9/9/9/9/9/9/5A3/4K4 w - - 0 1")
        assertEquals(IllegalReason.MUST_STAY_IN_PALACE, Rules.illegalReason(pos, sq("f1"), sq("g2")))
        assertNull(Rules.illegalReason(pos, sq("f1"), sq("e2")))
        val king = fen("3k5/9/9/9/9/9/9/9/9/5K3 w - - 0 1")
        assertEquals(IllegalReason.MUST_STAY_IN_PALACE, Rules.illegalReason(king, sq("f0"), sq("g0")))
        assertEquals(IllegalReason.INVALID_MOVE_PATTERN, Rules.illegalReason(king, sq("f0"), sq("e1")))
    }

    @Test
    fun basicIllegalReasons() {
        val pos = Position.initial()
        assertEquals(IllegalReason.NO_PIECE, Rules.illegalReason(pos, sq("e4"), sq("e5")))
        assertEquals(IllegalReason.NOT_YOUR_TURN, Rules.illegalReason(pos, sq("e6"), sq("e5")))
        assertEquals(IllegalReason.OWN_PIECE_ON_TARGET, Rules.illegalReason(pos, sq("a0"), sq("b0")))
        assertEquals(IllegalReason.PATH_BLOCKED, Rules.illegalReason(pos, sq("a0"), sq("a4")))
        assertEquals(IllegalReason.INVALID_MOVE_PATTERN, Rules.illegalReason(pos, sq("a0"), sq("b1")))
        assertTrue(Rules.legalMovesFrom(pos, sq("e6")).isEmpty())
        assertEquals(1, Rules.legalMovesFrom(pos, sq("e6"), ignoreTurn = true).size)
    }

    @Test
    fun applyUpdatesSideAndClocks() {
        val start = Position.initial()
        val afterRed = Rules.apply(start, move(start, "h2e2"))
        assertEquals(Side.BLACK, afterRed.sideToMove)
        assertEquals(1, afterRed.halfmoveClock)
        assertEquals(1, afterRed.fullmoveNumber)
        assertEquals(Piece.of(Side.RED, PieceType.CANNON), afterRed.pieceAt(sq("e2")))
        assertNull(afterRed.pieceAt(sq("h2")))
        val afterBlack = Rules.apply(afterRed, move(afterRed, "h9g7"))
        assertEquals(Side.RED, afterBlack.sideToMove)
        assertEquals(2, afterBlack.halfmoveClock)
        assertEquals(2, afterBlack.fullmoveNumber)
        val pawn = Rules.apply(afterBlack, move(afterBlack, "e3e4"))
        assertEquals(0, pawn.halfmoveClock)
        val capture = play(Position.initial(), "炮二平五", "馬8進7", "炮五進四")
        assertEquals(0, capture.halfmoveClock)
        assertEquals(4, capture.count(Side.BLACK, PieceType.PAWN))
    }

    @Test
    fun repetitionDetection() {
        val p0 = Position.initial()
        val p1 = Rules.apply(p0, move(p0, "h0g2"))
        val p2 = Rules.apply(p1, move(p1, "h9g7"))
        val p3 = Rules.apply(p2, move(p2, "g2h0"))
        val p4 = Rules.apply(p3, move(p3, "g7h9")) // same placement as p0, red to move
        val history = listOf(p0, p1, p2, p3, p4)
        assertFalse(Rules.isRepetition(history, p4))
        assertTrue(Rules.isRepetition(history, p4, times = 2))
        val p8 = play(p4, "h0g2", "h9g7", "g2h0", "g7h9")
        val longer = history + listOf(play(p4, "h0g2"), play(p4, "h0g2", "h9g7"), play(p4, "h0g2", "h9g7", "g2h0"), p8)
        assertTrue(Rules.isRepetition(longer, p8))
        assertTrue(Rules.isRepetition(longer.dropLast(1), p8)) // history without the current position
    }

    @Test
    fun validateSetupReportsProblems() {
        assertTrue(Rules.validateSetup(Position.initial()).isEmpty())
        val noRedKing = fen("4k4/9/9/9/9/9/9/9/9/9 w - - 0 1")
        assertTrue(Rules.validateSetup(noRedKing).any { it.contains("缺少") && it.contains("帥") })
        val threeRooks = fen("4k4/9/9/9/9/9/9/9/9/RRRK5 w - - 0 1")
        assertTrue(Rules.validateSetup(threeRooks).any { it.contains("俥") && it.contains("超过") })
        val pawnBehind = fen("4k4/9/9/9/9/9/9/9/4P4/3K5 w - - 0 1")
        assertTrue(Rules.validateSetup(pawnBehind).any { it.contains("兵") })
        val elephantOff = fen("4k4/9/9/9/9/9/9/9/3B5/3K5 w - - 0 1")
        assertTrue(Rules.validateSetup(elephantOff).any { it.contains("相") })
        val facing = fen("4k4/9/9/9/9/9/9/9/9/4K4 w - - 0 1")
        assertTrue(Rules.validateSetup(facing).any { it.contains("照面") })
        val oppInCheck = fen("4k4/9/9/9/9/9/9/9/9/3KR4 w - - 0 1")
        assertTrue(Rules.validateSetup(oppInCheck).any { it.contains("将军") })
    }

    @Test
    fun squareHelpers() {
        assertEquals("h2", Square(7, 2).iccs)
        assertEquals(Square(7, 2), Square.fromIccs("H2"))
        assertNull(Square.fromIccs("j2"))
        assertNull(Square.fromIccs("h"))
        assertTrue(Square(4, 0).inPalace(Side.RED))
        assertFalse(Square(4, 3).inPalace(Side.RED))
        assertTrue(Square(4, 8).inPalace(Side.BLACK))
    }
}
