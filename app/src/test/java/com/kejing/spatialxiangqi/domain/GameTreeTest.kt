package com.kejing.spatialxiangqi.domain

import com.kejing.spatialxiangqi.domain.model.GameMeta
import com.kejing.spatialxiangqi.domain.model.GameTree
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.toRecord
import com.kejing.spatialxiangqi.domain.model.toTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTreeTest {

    private fun tree(): GameTree = GameTree.fromPosition(Position.initial())

    @Test
    fun playBackForward() {
        val t = tree()
        val n1 = t.play(move(t.current.position, "炮二平五"))
        val n2 = t.play(move(t.current.position, "馬8進7"))
        assertEquals(2, t.current.ply)
        assertSame(n2, t.current)
        assertEquals(Side.RED, t.current.position.sideToMove)
        assertTrue(t.back())
        assertSame(n1, t.current)
        assertTrue(t.forward())
        assertSame(n2, t.current)
        assertFalse(t.forward())
        t.toStart()
        assertSame(t.root, t.current)
        assertFalse(t.back())
        t.toEnd()
        assertSame(n2, t.current)
        assertEquals(listOf("h2e2", "h9g7"), t.mainLine().map { it.move!!.iccs })
        assertEquals(listOf(n1, n2), t.pathTo(n2))
    }

    @Test
    fun variationsAreCreatedAndReused() {
        val t = tree()
        val main = t.play(move(t.current.position, "炮二平五"))
        t.back()
        val alt = t.play(move(t.current.position, "兵七进一"))
        assertEquals(2, t.root.children.size)
        assertSame(main, t.root.children[0])
        assertSame(alt, t.root.children[1])
        assertSame(alt, t.current)
        assertFalse(alt.isOnMainLine)
        t.back()
        val again = t.play(move(t.current.position, "H2-E2"))
        assertSame(main, again)
        assertEquals(2, t.root.children.size)
        assertEquals(listOf("h2e2"), t.mainLine().map { it.move!!.iccs })
    }

    @Test
    fun promoteToMainLine() {
        val t = tree()
        t.play(move(t.current.position, "炮二平五"))
        t.back()
        val alt = t.play(move(t.current.position, "兵七进一"))
        val altReply = t.play(move(t.current.position, "炮2平3"))
        t.promoteToMainLine(altReply)
        assertSame(alt, t.root.children[0])
        assertTrue(altReply.isOnMainLine)
        assertEquals(listOf("c3c4", "b7c7"), t.mainLine().map { it.move!!.iccs })
    }

    @Test
    fun positionHistoryFollowsCurrentLine() {
        val t = tree()
        t.play(move(t.current.position, "炮二平五"))
        t.play(move(t.current.position, "馬8進7"))
        t.play(move(t.current.position, "傌二進三"))
        val history = t.positionHistory()
        assertEquals(4, history.size)
        assertEquals(Position.initial(), history.first())
        assertEquals(t.current.position, history.last())
        t.back()
        assertEquals(3, t.positionHistory().size)
    }

    @Test
    fun deleteVariationMovesCurrentToParent() {
        val t = tree()
        val main = t.play(move(t.current.position, "炮二平五"))
        t.back()
        val alt = t.play(move(t.current.position, "兵七进一"))
        t.play(move(t.current.position, "炮2平3"))
        t.deleteVariation(alt)
        assertEquals(listOf(main), t.root.children)
        assertSame(t.root, t.current)
        t.deleteVariation(t.root) // no-op
        assertEquals(1, t.root.children.size)
    }

    @Test
    fun recordRoundTripKeepsVariationsCommentsAndNags() {
        val t = tree()
        t.root.comment = "开局"
        val n1 = t.play(move(t.current.position, "炮二平五"))
        n1.comment = "中炮"
        n1.nags += "!"
        val n2 = t.play(move(t.current.position, "馬8進7"))
        t.play(move(t.current.position, "傌二進三"))
        t.goTo(n2)
        val alt = t.play(move(t.current.position, "傌八進七"))
        alt.comment = "另一种走法"
        t.play(move(t.current.position, "車9平8"))
        t.goTo(alt)
        val nested = t.play(move(t.current.position, "卒7進1"))
        nested.nags += "?"
        t.toStart(); t.toEnd()

        val record = t.toRecord("g1", GameMeta(title = "测试", red = "红", black = "黑", date = "2026-09-22", result = "*", level = 3, playerSide = Side.RED))
        assertEquals(listOf("h2e2", "h9g7", "h0g2"), record.movesIccs)
        assertEquals(mapOf(0 to "开局", 1 to "中炮"), record.comments)
        assertEquals(mapOf(1 to "!"), record.nags)
        assertEquals(2, record.variations.size)
        assertEquals(2, record.variations[0].atPly)
        assertEquals(listOf("b0c2", "i9h9"), record.variations[0].movesIccs)
        assertEquals(mapOf(3 to "另一种走法"), record.variations[0].comments)
        assertEquals(listOf("b0c2", "g6g5"), record.variations[1].movesIccs)
        assertEquals(mapOf(4 to "?"), record.variations[1].nags)

        val back = record.toTree()
        assertEquals(listOf("h2e2", "h9g7", "h0g2"), back.mainLine().map { it.move!!.iccs })
        assertEquals("开局", back.root.comment)
        val b1 = back.root.children[0]
        assertEquals("中炮", b1.comment)
        assertEquals(setOf("!"), b1.nags)
        val b2 = b1.children[0]
        assertEquals(2, b2.children.size)
        val bAlt = b2.children[1]
        assertEquals("b0c2", bAlt.move!!.iccs)
        assertEquals("另一种走法", bAlt.comment)
        assertEquals(listOf("i9h9", "g6g5"), bAlt.children.map { it.move!!.iccs })
        assertEquals(setOf("?"), bAlt.children[1].nags)
        assertEquals(record, back.toRecord("g1", GameMeta(title = "测试", red = "红", black = "黑", date = "2026-09-22", result = "*", level = 3, playerSide = Side.RED)))
    }

    @Test
    fun treeFromCustomFenAndUpperCaseIccs() {
        val t = GameTree.fromFen(MATE_IN_ONE_FEN)!!
        t.play(move(t.current.position, "B1-B9"))
        val record = t.toRecord("m")
        assertEquals(MATE_IN_ONE_FEN, record.startFen)
        val rebuilt = record.copy(movesIccs = listOf("B1-B9")).toTree()
        assertEquals(1, rebuilt.mainLine().size)
        assertEquals("b1b9", rebuilt.mainLine()[0].move!!.iccs)
    }
}
