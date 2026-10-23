package com.kejing.spatialxiangqi.domain

import com.kejing.spatialxiangqi.domain.model.Piece
import com.kejing.spatialxiangqi.domain.model.PieceType
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FenTest {

    @Test
    fun initialPositionRoundTrip() {
        val pos = Position.initial()
        assertEquals(Position.INITIAL_FEN, pos.toFen())
        assertEquals(pos, Position.fromFen(Position.INITIAL_FEN))
        assertEquals(32, pos.pieces.size)
        assertEquals(Piece.of(Side.RED, PieceType.KING), pos.pieceAt(Square(4, 0)))
        assertEquals(Piece.of(Side.BLACK, PieceType.ELEPHANT), pos.pieceAt(Square(2, 9)))
        assertEquals(Piece.of(Side.RED, PieceType.CANNON), pos.pieceAt(Square(1, 2)))
        assertEquals(Piece.of(Side.BLACK, PieceType.PAWN), pos.pieceAt(Square(8, 6)))
        assertEquals(Square(4, 0), pos.kingSquare(Side.RED))
        assertEquals(Square(4, 9), pos.kingSquare(Side.BLACK))
    }

    @Test
    fun customPositionRoundTrip() {
        val fen = "r1bakabr1/9/1cn1c1n2/p1p1p1p1p/9/2P6/P3P1P1P/1C2C1N2/9/RNBAKAB1R b - - 3 7"
        val pos = fen(fen)
        assertEquals(fen, pos.toFen())
        assertEquals(Side.BLACK, pos.sideToMove)
        assertEquals(3, pos.halfmoveClock)
        assertEquals(7, pos.fullmoveNumber)
        assertEquals(fen(MATE_IN_ONE_FEN).toFen(), MATE_IN_ONE_FEN)
    }

    @Test
    fun sideFieldVariants() {
        val board = "4k4/9/9/9/9/9/9/9/9/3K5"
        assertEquals(Side.RED, fen("$board w").sideToMove)
        assertEquals(Side.RED, fen("$board r - - 0 1").sideToMove)
        assertEquals(Side.RED, fen(board).sideToMove)
        assertEquals(Side.BLACK, fen("$board b").sideToMove)
        assertEquals(0, fen(board).halfmoveClock)
        assertEquals(1, fen(board).fullmoveNumber)
    }

    @Test
    fun malformedFenIsRejected() {
        assertNull(Position.fromFen(""))
        assertNull(Position.fromFen("4k4/9/9/9/9/9/9/9/3K5 w"))          // 9 rows
        assertNull(Position.fromFen("4k4/9/9/9/9/9/9/9/9/3K6 w"))        // row adds up to 10
        assertNull(Position.fromFen("4k4/9/9/9/9/9/9/9/9/3K4 w"))        // row adds up to 8
        assertNull(Position.fromFen("4x4/9/9/9/9/9/9/9/9/3K5 w"))        // unknown letter
        assertNull(Position.fromFen("4k4/9/9/9/9/9/9/9/9/3K5 z"))        // unknown side
    }

    @Test
    fun alternativeLettersAccepted() {
        val pos = fen("rheakaehr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RHEAKAEHR w - - 0 1")
        assertEquals(Position.initial(), pos)
        assertTrue(Position.looksLikeFen(Position.INITIAL_FEN))
        assertTrue(Position.looksLikeFen("4k4/9/9/9/9/9/9/9/9/3K5"))
        assertEquals(false, Position.looksLikeFen("1. C2=5 H8+7"))
    }

    @Test
    fun placementAndRepetitionKeyIgnoreClocks() {
        val a = fen("4k4/9/9/9/9/9/9/9/9/3K5 w - - 0 1")
        val b = fen("4k4/9/9/9/9/9/9/9/9/3K5 w - - 7 12")
        assertTrue(a.samePlacement(b))
        assertEquals(a.repetitionKey, b.repetitionKey)
        assertEquals("4k4/9/9/9/9/9/9/9/9/3K5", a.placementFen())
    }
}
