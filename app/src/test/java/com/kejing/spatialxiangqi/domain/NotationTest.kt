package com.kejing.spatialxiangqi.domain

import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.notation.Notation
import com.kejing.spatialxiangqi.domain.notation.NotationStyle
import com.kejing.spatialxiangqi.domain.rules.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotationTest {

    private val initial = Position.initial()

    @Test
    fun redCentralCannonInThreeStyles() {
        val m = move(initial, "h2e2")
        assertEquals("炮二平五", Notation.format(initial, m, NotationStyle.CHINESE))
        assertEquals("C2=5", Notation.format(initial, m, NotationStyle.WXF))
        assertEquals("H2-E2", Notation.format(initial, m, NotationStyle.ICCS))
        assertEquals("H2-E2", Notation.formatIccs(m))
        assertEquals("h2e2", m.iccs)
        for (text in listOf("炮二平五", "C2=5", "H2-E2", "h2e2", "h2-e2", "c2=5", "C2.5", "炮２平５", "炮2平5", "砲二平五", "Ｃ２＝５", "炮二平五!", "H2-E2+")) {
            assertEquals("parsing $text", m, Notation.parse(initial, text))
        }
    }

    @Test
    fun blackHorseInThreeStyles() {
        val pos = play(initial, "炮二平五")
        val m = move(pos, "h9g7")
        assertEquals("馬8进7", Notation.format(pos, m, NotationStyle.CHINESE))
        assertEquals("H8+7", Notation.format(pos, m, NotationStyle.WXF))
        assertEquals("H9-G7", Notation.format(pos, m, NotationStyle.ICCS))
        for (text in listOf("馬8進7", "馬8进7", "马8进7", "馬八進七", "H8+7", "h8+7", "H9-G7", "h9g7", "馬８進７")) {
            assertEquals("parsing $text", m, Notation.parse(pos, text))
        }
    }

    @Test
    fun rookTraverseAndVerticalMoves() {
        assertNull(Notation.parse(initial, "車一平二")) // blocked by the horse until 傌二進三
        assertEquals("c3c4", move(initial, "兵七进一").iccs)
        assertEquals("c3c4", move(initial, "P7+1").iccs)
        assertEquals("h0g2", move(initial, "馬二進三").iccs)
        assertEquals("h0g2", move(initial, "H2+3").iccs)
        val pos = play(initial, "炮二平五", "馬8進7", "傌二進三", "車9平8")
        assertEquals("i0h0", move(pos, "車一平二").iccs)
        assertEquals("i0h0", move(pos, "R1=2").iccs)
        assertEquals("i0h0", move(pos, "I0-H0").iccs)
        assertEquals("俥一平二", Notation.format(pos, move(pos, "i0h0"), NotationStyle.CHINESE))
        val up = play(pos, "車一平二", "卒7進1")
        assertEquals("俥二进六", Notation.format(up, move(up, "h0h6"), NotationStyle.CHINESE))
        assertEquals("R2+6", Notation.format(up, move(up, "h0h6"), NotationStyle.WXF))
    }

    @Test
    fun frontAndRearDisambiguation() {
        val red = fen("4k4/9/9/9/9/4C4/9/4C4/9/4K4 w - - 0 1")
        val front = move(red, "e4d4")
        val rear = move(red, "e2d2")
        assertEquals("前炮平六", Notation.format(red, front, NotationStyle.CHINESE))
        assertEquals("+C=6", Notation.format(red, front, NotationStyle.WXF))
        assertEquals("后炮平六", Notation.format(red, rear, NotationStyle.CHINESE))
        assertEquals("-C=6", Notation.format(red, rear, NotationStyle.WXF))
        assertEquals(front, Notation.parse(red, "前炮平六"))
        assertEquals(front, Notation.parse(red, "+C=6"))
        assertEquals(rear, Notation.parse(red, "後炮平六"))
        assertEquals(rear, Notation.parse(red, "-C=6"))
        assertEquals("前炮退一", Notation.format(red, move(red, "e4e3"), NotationStyle.CHINESE))

        // black: the front piece is the one with the lower rank
        val black = fen("4k4/9/4r4/9/4r4/9/9/9/9/3K5 b - - 0 1")
        assertEquals("前車平6", Notation.format(black, move(black, "e5f5"), NotationStyle.CHINESE))
        assertEquals("后車平6", Notation.format(black, move(black, "e7f7"), NotationStyle.CHINESE))
        assertEquals("+R=6", Notation.format(black, move(black, "e5f5"), NotationStyle.WXF))
        assertEquals("e5f5", Notation.parse(black, "前车平六")!!.iccs)
    }

    @Test
    fun everyLegalMoveRoundTripsInEveryStyle() {
        val positions = listOf(
            initial,
            play(initial, "炮二平五", "馬8進7", "傌二進三", "車9平8", "車一平二", "卒7進1"),
            fen("r1bakabr1/9/1cn1c1n2/p1p1p1p1p/9/2P6/P3P1P1P/1C2C1N2/9/RNBAKAB1R b - - 0 1"),
            fen("4k4/9/9/9/9/4C4/9/4C4/9/4K4 w - - 0 1"),
            fen(MATE_IN_ONE_FEN),
        )
        for (pos in positions) {
            for (m in Rules.allLegalMoves(pos)) {
                for (style in NotationStyle.entries) {
                    val text = Notation.format(pos, m, style)
                    assertEquals("$text ($style) in ${pos.toFen()}", m, Notation.parse(pos, text))
                }
            }
        }
    }

    @Test
    fun parseRejectsIllegalOrMalformedText() {
        assertNull(Notation.parse(initial, "馬二進四"))     // leg blocked by the elephant
        assertNull(Notation.parse(initial, "炮二進八"))     // beyond the board
        assertNull(Notation.parse(initial, "H2-E3"))
        assertNull(Notation.parse(initial, "hello"))
        assertNull(Notation.parse(initial, ""))
        assertNotNull(Notation.parse(initial, "炮二平七"))
        assertNotNull(Notation.parse(initial, "馬二進一"))
    }
}
