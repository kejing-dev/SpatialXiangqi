package com.kejing.spatialxiangqi.domain

import com.kejing.spatialxiangqi.domain.model.GameMeta
import com.kejing.spatialxiangqi.domain.model.GameTree
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.toRecord
import com.kejing.spatialxiangqi.domain.notation.NotationStyle
import com.kejing.spatialxiangqi.domain.notation.Pgn
import com.kejing.spatialxiangqi.domain.notation.PgnParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PgnTest {

    private fun sampleRecord() = run {
        val t = GameTree.fromPosition(Position.initial())
        val n1 = t.play(move(t.current.position, "炮二平五"))
        n1.nags += "!"
        val n2 = t.play(move(t.current.position, "馬8進7"))
        n2.comment = "屏风马，主流应法。"
        t.play(move(t.current.position, "傌二進三"))
        t.play(move(t.current.position, "車9平8"))
        t.goTo(n2)
        val alt = t.play(move(t.current.position, "傌八進七"))
        alt.comment = "先跳左马"
        t.play(move(t.current.position, "卒3進1"))
        t.toStart(); t.toEnd()
        t.toRecord("s1", GameMeta(title = "示范局", red = "红方甲", black = "黑方乙", date = "2026-09-21", result = "1-0", opening = "中炮对屏风马", level = 3, playerSide = Side.RED))
    }

    @Test
    fun exportHasHeadersAndMovetext() {
        val text = Pgn.export(sampleRecord(), NotationStyle.CHINESE)
        assertTrue(text.contains("[Game \"Chinese Chess\"]"))
        assertTrue(text.contains("[Red \"红方甲\"]"))
        assertTrue(text.contains("[Black \"黑方乙\"]"))
        assertTrue(text.contains("[Date \"2026.09.21\"]"))
        assertTrue(text.contains("[Result \"1-0\"]"))
        assertTrue(text.contains("[FEN \"${Position.INITIAL_FEN}\"]"))
        assertTrue(text, text.contains("1. 炮二平五! 馬8进7 {屏风马，主流应法。} 2. 傌二进三 (2. 傌八进七 {先跳左马} 卒3进1) 2... 車9平8 1-0"))
    }

    @Test
    fun exportImportRoundTripInEveryStyle() {
        val record = sampleRecord()
        for (style in NotationStyle.entries) {
            val text = Pgn.export(record, style)
            val imported = Pgn.import(text).getOrThrow()
            assertEquals(style.name, record.movesIccs, imported.movesIccs)
            assertEquals(style.name, record.variations, imported.variations)
            assertEquals(style.name, record.comments, imported.comments)
            assertEquals(style.name, record.nags, imported.nags)
            assertEquals(record.red, imported.red)
            assertEquals(record.black, imported.black)
            assertEquals(record.title, imported.title)
            assertEquals(record.date, imported.date)
            assertEquals(record.result, imported.result)
            assertEquals(record.opening, imported.opening)
            assertEquals(record.startFen, imported.startFen)
            assertEquals(record.level, imported.level)
            assertEquals(record.playerSide, imported.playerSide)
            assertEquals(record, imported.copy(id = record.id))
        }
    }

    @Test
    fun importErrorNamesTheMove() {
        val text = "[Event \"俱乐部赛\"]\n[Date \"2026.09.21\"]\n1. 炮二平五 馬8進7 2. 馬二進三 車9平8 3. 炮二平七 卒7進1"
        val result = Pgn.import(text)
        assertTrue(result.isFailure)
        val e = result.exceptionOrNull() as PgnParseException
        assertEquals("第 5 手无法解析：炮二平七", e.message)
        assertEquals(5, e.moveNumber)
        assertEquals("炮二平七", e.token)
    }

    @Test
    fun importBareMoveLists() {
        val wxf = Pgn.import("1. C2=5 H8+7 2. H2+3 R9=8 3. R1=2 P7+1").getOrThrow()
        assertEquals(listOf("h2e2", "h9g7", "h0g2", "i9h9", "i0h0", "g6g5"), wxf.movesIccs)
        assertEquals("*", wxf.result)
        val chinese = Pgn.import("炮二平五馬8進7\n馬二進三 車9平8").getOrThrow()
        assertEquals(listOf("h2e2", "h9g7", "h0g2", "i9h9"), chinese.movesIccs)
        val iccs = Pgn.import("H2-E2 H9-G7 H0-G2 I9-H9 1-0").getOrThrow()
        assertEquals(4, iccs.movesIccs.size)
        assertEquals("1-0", iccs.result)
        assertTrue(iccs.id.isNotBlank())
    }

    @Test
    fun importSingleFenLine() {
        val record = Pgn.import("  $MATE_IN_ONE_FEN \n").getOrThrow()
        assertTrue(record.movesIccs.isEmpty())
        assertEquals(MATE_IN_ONE_FEN, record.startFen)
        val prefixed = Pgn.import("FEN: 4k4/9/9/9/9/9/9/9/9/3K5 b\nK5=6").getOrThrow()
        assertEquals("4k4/9/9/9/9/9/9/9/9/3K5 b - - 0 1", prefixed.startFen)
        assertEquals(listOf("e9f9"), prefixed.movesIccs)
        val fromHeader = Pgn.import("[FEN \"$MATE_IN_ONE_FEN\"]\n1. B1-B9 1-0").getOrThrow()
        assertEquals(listOf("b1b9"), fromHeader.movesIccs)
        assertEquals("1-0", fromHeader.result)
    }

    @Test
    fun importRejectsEmptyAndBadFen() {
        assertTrue(Pgn.import("   \n").isFailure)
        assertEquals("没有可导入的内容", Pgn.import("").exceptionOrNull()!!.message)
        assertEquals("FEN 格式错误", Pgn.import("[FEN \"4k4/9/9/3K5 w\"]\n1. C2=5").exceptionOrNull()!!.message)
    }

    @Test
    fun importKeepsNagsAndCommentsAndNumericNags() {
        val record = Pgn.import("1. C2=5 \$1 {好棋} H8+7?! 2. H2+3 {稳健} 2... R9=8").getOrThrow()
        assertEquals(listOf("h2e2", "h9g7", "h0g2", "i9h9"), record.movesIccs)
        assertEquals(mapOf(1 to "好棋", 3 to "稳健"), record.comments)
        assertEquals(mapOf(1 to "!", 2 to "?!"), record.nags)
    }
}
