package com.kejing.spatialxiangqi.domain

import com.kejing.spatialxiangqi.domain.engine.LocalEngine
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.rules.Rules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EngineTest {

    private val engine = LocalEngine(Random(42))

    @Test
    fun bestMoveIsLegalAtEveryLevel() = runBlocking {
        val pos = Position.initial()
        val legal = Rules.allLegalMoves(pos).map { it.iccs }.toSet()
        for (level in 1..5) {
            val started = System.nanoTime()
            val m = engine.bestMove(pos, level)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertNotNull("level $level", m)
            assertTrue("level $level returned ${m!!.iccs}", m.iccs in legal)
            assertTrue("level $level took $elapsedMs ms", elapsedMs < 6000)
        }
        // black to move as well
        val black = play(pos, "炮二平五")
        val bm = engine.bestMove(black, 3)!!
        assertEquals(Side.BLACK, bm.piece.side)
        assertTrue(bm.iccs in Rules.allLegalMoves(black).map { it.iccs })
    }

    @Test
    fun findsMateInOne() = runBlocking {
        val pos = fen(MATE_IN_ONE_FEN)
        for (level in 3..5) {
            assertEquals("level $level", "b1b9", engine.bestMove(pos, level)!!.iccs)
        }
        val lines = engine.analyse(pos, multiPv = 2, timeMs = 500)
        assertEquals("b1b9", lines[0].moves.first().iccs)
        assertEquals(1, lines[0].mateIn)
        assertTrue(lines[0].scoreCp > 90_000)
    }

    @Test
    fun blackMateIsReportedFromRedPerspective() = runBlocking {
        // mirrored position: black rook mates with b8b0 (the red pawn on i6 rules out stalemate wins)
        val pos = fen("5k3/1r7/9/8P/9/9/9/9/r8/4K4 b - - 0 1")
        val lines = engine.analyse(pos, multiPv = 1, timeMs = 500)
        assertEquals("b8b0", lines[0].moves.first().iccs)
        assertEquals(-1, lines[0].mateIn)
        assertTrue(lines[0].scoreCp < -90_000)
        assertEquals("b8b0", engine.bestMove(pos, 4)!!.iccs)
    }

    @Test
    fun analyseReturnsSortedLines() = runBlocking {
        val pos = Position.initial()
        val lines = engine.analyse(pos, multiPv = 3, timeMs = 400)
        assertTrue(lines.size in 1..3)
        val legal = Rules.allLegalMoves(pos).map { it.iccs }.toSet()
        for (line in lines) {
            assertTrue(line.moves.isNotEmpty())
            assertTrue(line.moves.first().iccs in legal)
            assertTrue(line.depth >= 1)
            assertNull(line.mateIn)
        }
        assertEquals(lines.map { it.scoreCp }.sortedDescending(), lines.map { it.scoreCp })
        assertEquals(lines.size, lines.map { it.moves.first().iccs }.toSet().size)
        // for black to move the best line for black (lowest red score) comes first
        val black = play(pos, "炮二平五")
        val blackLines = engine.analyse(black, multiPv = 3, timeMs = 300)
        assertEquals(blackLines.map { it.scoreCp }.sorted(), blackLines.map { it.scoreCp })
        // the principal variation must be a playable sequence
        var p = black
        for (m in blackLines[0].moves) {
            assertTrue(Rules.isLegal(p, m.from, m.to))
            p = Rules.apply(p, m)
        }
    }

    @Test
    fun staticEvaluationIsSymmetricAndCountsMaterial() {
        assertEquals(0, engine.evaluate(Position.initial()))
        val upRook = fen("1nbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1")
        assertTrue(engine.evaluate(upRook) > 800)
        val downCannon = fen("rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C7/9/RNBAKABNR w - - 0 1")
        assertTrue(engine.evaluate(downCannon) < -350)
    }

    @Test
    fun returnsNullWithoutLegalMoves() = runBlocking {
        assertNull(engine.bestMove(fen(STALEMATE_FEN), 3))
        assertTrue(engine.analyse(fen(STALEMATE_FEN)).isEmpty())
        val mated = Rules.apply(fen(MATE_IN_ONE_FEN), move(fen(MATE_IN_ONE_FEN), "b1b9"))
        assertNull(engine.bestMove(mated, 1))
    }

    @Test
    fun searchIsCancellable() = runBlocking {
        val pos = play(Position.initial(), "炮二平五", "馬8進7", "傌二進三", "車9平8")
        var cancelled = false
        val job: Job = launch {
            try {
                engine.analyse(pos, multiPv = 3, timeMs = 8000) // depth 8 takes far longer than the cancel delay
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        delay(60)
        val started = System.nanoTime()
        job.cancel()
        withTimeout(1500) { job.join() }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("cancel took $elapsedMs ms", elapsedMs < 1000)
        assertTrue(cancelled)
        // bestMove goes through the same search and is cancellable the same way
        val job2 = launch { engine.bestMove(pos, 5) }
        delay(20)
        job2.cancel()
        withTimeout(1500) { job2.join() }
        assertTrue(job2.isCancelled)
    }

    @Test
    fun seededRandomMakesLowLevelsReproducible() = runBlocking {
        val pos = play(Position.initial(), "炮二平五", "馬8進7")
        val a = LocalEngine(Random(7)).bestMove(pos, 1)!!
        val b = LocalEngine(Random(7)).bestMove(pos, 1)!!
        assertEquals(a, b)
        val picks = (1..12).map { LocalEngine(Random(it)).bestMove(pos, 1)!!.iccs }.toSet()
        assertTrue("level 1 should vary its choice, got $picks", picks.size > 1)
    }
}
