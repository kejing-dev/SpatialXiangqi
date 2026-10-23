package com.kejing.spatialxiangqi.domain.engine

import com.kejing.spatialxiangqi.domain.model.Move
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.rules.Board
import com.kejing.spatialxiangqi.domain.rules.MoveList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

/**
 * Built-in engine: negamax alpha-beta with iterative deepening, MVV-LVA / killer / history move
 * ordering and a capture-only quiescence search on top of [Evaluation].
 *
 * Levels: 1 = depth 1, a random pick among the 5 best moves; 2 = depth 2, among the 3 best;
 * 3 = depth 3 within 300 ms; 4 = depth 4 within 800 ms; 5 = depth 5 within 2 s. Searches run on
 * [Dispatchers.Default] and stop promptly when the calling coroutine is cancelled.
 */
class LocalEngine(private val random: Random = Random.Default) : Engine {

    override suspend fun bestMove(pos: Position, level: Int): Move? = withContext(Dispatchers.Default) {
        val board = Board.from(pos)
        val legal = MoveList()
        board.generateLegal(board.sideToMove, legal)
        if (legal.size == 0) return@withContext null
        if (legal.size == 1) return@withContext board.toMove(legal[0])
        val job = currentCoroutineContext()[Job]
        val packed = when (level.coerceIn(1, 5)) {
            1 -> pickAmongTop(board, depth = 1, topN = 5, timeMs = 1500, job = job)
            2 -> pickAmongTop(board, depth = 2, topN = 3, timeMs = 1500, job = job)
            3 -> best(board, maxDepth = 3, timeMs = 300, job = job)
            4 -> best(board, maxDepth = 4, timeMs = 800, job = job)
            else -> best(board, maxDepth = 5, timeMs = 2000, job = job)
        }
        Board.from(pos).toMove(packed ?: legal[0])
    }

    override suspend fun analyse(pos: Position, multiPv: Int, timeMs: Long): List<EngineLine> =
        withContext(Dispatchers.Default) {
            val board = Board.from(pos)
            val legal = MoveList()
            board.generateLegal(board.sideToMove, legal)
            if (legal.size == 0 || multiPv <= 0) return@withContext emptyList<EngineLine>()
            val search = Search(board, System.nanoTime() + timeMs.coerceAtLeast(1) * 1_000_000L, currentCoroutineContext()[Job])
            val results = search.iterate(maxDepth = ANALYSIS_MAX_DEPTH, keepExact = multiPv, stopOnMate = false)
            val red = pos.sideToMove == Side.RED
            results.results.take(multiPv).map { r ->
                val scoreRed = if (red) r.score else -r.score
                val mateIn = if (abs(r.score) >= Evaluation.MATE_THRESHOLD) {
                    val plies = Evaluation.MATE - abs(r.score)
                    val moves = (plies + 1) / 2
                    if (scoreRed > 0) moves else -moves
                } else null
                EngineLine(toMoves(pos, r.pv), scoreRed, mateIn, results.depth)
            }
        }

    override fun evaluate(pos: Position): Int = Evaluation.evaluate(Board.from(pos), MoveList())

    // ------------------------------------------------------------------ level drivers

    private fun best(board: Board, maxDepth: Int, timeMs: Long, job: Job?): Int? {
        val search = Search(board, System.nanoTime() + timeMs * 1_000_000L, job)
        val results = search.iterate(maxDepth, keepExact = 1, stopOnMate = true).results
        return results.firstOrNull()?.move
    }

    private fun pickAmongTop(board: Board, depth: Int, topN: Int, timeMs: Long, job: Job?): Int? {
        val search = Search(board, System.nanoTime() + timeMs * 1_000_000L, job)
        val results = search.iterate(depth, keepExact = topN, stopOnMate = false).results
        if (results.isEmpty()) return null
        val candidates = results.filter { it.exact }.take(topN).ifEmpty { results.take(topN) }
        return candidates[random.nextInt(candidates.size)].move
    }

    private fun toMoves(pos: Position, pv: IntArray): List<Move> {
        val board = Board.from(pos)
        val out = ArrayList<Move>(pv.size)
        for (m in pv) {
            if (board.cells[Board.moveFrom(m)] == Board.EMPTY) break
            out += board.toMove(m)
            board.make(m)
        }
        return out
    }

    private companion object {
        const val ANALYSIS_MAX_DEPTH = 8
    }
}

/** Thrown internally when the time budget is exhausted; never escapes [Search.iterate]. */
private object TimeUp : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

internal class RootResult(val move: Int, val score: Int, val pv: IntArray, val exact: Boolean)

internal class IterationResult(val results: List<RootResult>, val depth: Int)

/** One search session on a private copy of the board. */
internal class Search(source: Board, private val deadlineNanos: Long, private val job: Job?) {
    private val board = source.copy()
    private val moveLists = Array(MAX_PLY + 2) { MoveList(160) }
    private val orderScores = Array(MAX_PLY + 2) { IntArray(160) }
    private val killers = Array(MAX_PLY + 2) { IntArray(2) }
    private val history = Array(2) { IntArray(90 * 90) }
    private val pvTable = Array(MAX_PLY + 2) { IntArray(MAX_PLY + 2) }
    private val pvLength = IntArray(MAX_PLY + 2)
    private var prevPv = IntArray(0)
    private val scratch = MoveList(160)
    private var timeChecks = false
    var nodes = 0L
        private set

    /**
     * Iterative deepening from depth 1 to [maxDepth]. The first iteration ignores the deadline so
     * that a result always exists; later iterations that run out of time are discarded.
     */
    fun iterate(maxDepth: Int, keepExact: Int, stopOnMate: Boolean): IterationResult {
        var last: List<RootResult> = emptyList()
        var lastDepth = 0
        var order: IntArray? = null
        for (depth in 1..maxDepth) {
            timeChecks = depth > 1
            val res = try {
                searchRoot(depth, keepExact, order)
            } catch (e: TimeUp) {
                break
            }
            last = res
            lastDepth = depth
            order = IntArray(res.size) { res[it].move }
            prevPv = res.firstOrNull()?.pv ?: IntArray(0)
            if (stopOnMate && res.isNotEmpty() && res[0].score >= Evaluation.MATE_THRESHOLD) break
            if (timeChecks && System.nanoTime() >= deadlineNanos) break
        }
        return IterationResult(last, lastDepth)
    }

    private fun searchRoot(depth: Int, keepExact: Int, order: IntArray?): List<RootResult> {
        val side = board.sideToMove
        val moves = moveLists[0]
        moves.clear()
        board.generatePseudo(side, moves)
        val rootMoves: IntArray = if (order != null) {
            // previous ordering first, then anything new (there is nothing new, but stay safe)
            val seen = HashSet<Int>()
            val list = ArrayList<Int>(moves.size)
            for (m in order) if (moves.contains(m) && seen.add(m)) list += m
            for (i in 0 until moves.size) if (seen.add(moves[i])) list += moves[i]
            list.toIntArray()
        } else {
            scoreMoves(moves, 0, side)
            val arr = IntArray(moves.size)
            for (i in 0 until moves.size) { pickBest(moves, 0, i); arr[i] = moves[i] }
            arr
        }
        val exact = ArrayList<RootResult>()
        val bounded = ArrayList<RootResult>()
        for (m in rootMoves) {
            val cap = board.make(m)
            if (board.inCheck(side)) { board.unmake(m, cap); continue }
            val alpha = if (exact.size >= keepExact) exact[keepExact - 1].score else -Evaluation.INF
            pvLength[1] = 1
            val score = try {
                -negamax(depth - 1, 1, -Evaluation.INF, -alpha)
            } finally {
                board.unmake(m, cap)
            }
            val pv = IntArray(pvLength[1]).also { it[0] = m; for (j in 1 until pvLength[1]) it[j] = pvTable[1][j] }
            if (score > alpha) {
                exact += RootResult(m, score, pv, true)
                exact.sortByDescending { it.score }
            } else {
                bounded += RootResult(m, score, pv, false)
            }
        }
        bounded.sortByDescending { it.score }
        return exact + bounded
    }

    private fun checkAbort() {
        if (job != null && !job.isActive) throw CancellationException("engine search cancelled")
        if (timeChecks && System.nanoTime() >= deadlineNanos) throw TimeUp
    }

    private fun evalForSideToMove(): Int {
        val e = Evaluation.evaluate(board, scratch)
        return if (board.sideToMove == Board.RED) e else -e
    }

    private fun negamax(depth: Int, ply: Int, alpha: Int, beta: Int): Int {
        pvLength[ply] = ply
        nodes++
        if ((nodes and 1023L) == 0L) checkAbort()
        if (ply >= MAX_PLY) return evalForSideToMove()
        if (depth <= 0) return quiesce(ply, alpha, beta, 0)

        val side = board.sideToMove
        val moves = moveLists[ply]
        moves.clear()
        board.generatePseudo(side, moves)
        scoreMoves(moves, ply, side)

        var best = -Evaluation.INF
        var a = alpha
        var legal = 0
        for (i in 0 until moves.size) {
            pickBest(moves, ply, i)
            val m = moves[i]
            val cap = board.make(m)
            if (board.inCheck(side)) { board.unmake(m, cap); continue }
            legal++
            val score = try {
                -negamax(depth - 1, ply + 1, -beta, -a)
            } finally {
                board.unmake(m, cap)
            }
            if (score > best) {
                best = score
                if (score > a) {
                    a = score
                    pvTable[ply][ply] = m
                    val childLen = pvLength[ply + 1]
                    for (j in ply + 1 until childLen) pvTable[ply][j] = pvTable[ply + 1][j]
                    pvLength[ply] = childLen
                    if (a >= beta) {
                        if (cap == Board.EMPTY) {
                            val k = killers[ply]
                            if (k[0] != m) { k[1] = k[0]; k[0] = m }
                            val h = history[side]
                            val key = Board.moveFrom(m) * 90 + Board.moveTo(m)
                            h[key] = minOf(h[key] + depth * depth, 20_000)
                        }
                        return best
                    }
                }
            }
        }
        if (legal == 0) return -(Evaluation.MATE - ply) // checkmate or stalemate: both lose in xiangqi
        return best
    }

    private fun quiesce(ply: Int, alpha: Int, beta: Int, qDepth: Int): Int {
        pvLength[ply] = ply
        nodes++
        if ((nodes and 1023L) == 0L) checkAbort()
        val stand = evalForSideToMove()
        if (stand >= beta) return stand
        if (ply >= MAX_PLY - 1 || qDepth >= MAX_QUIESCENCE) return stand
        var a = maxOf(alpha, stand)
        var best = stand

        val side = board.sideToMove
        val moves = moveLists[ply]
        moves.clear()
        board.generatePseudo(side, moves, capturesOnly = true)
        scoreMoves(moves, ply, side)
        for (i in 0 until moves.size) {
            pickBest(moves, ply, i)
            val m = moves[i]
            val victim = Evaluation.pieceValue(board.cells[Board.moveTo(m)], Board.moveTo(m))
            if (stand + victim + DELTA_MARGIN < a) continue // delta pruning
            val cap = board.make(m)
            if (board.inCheck(side)) { board.unmake(m, cap); continue }
            val score = try {
                -quiesce(ply + 1, -beta, -a, qDepth + 1)
            } finally {
                board.unmake(m, cap)
            }
            if (score > best) {
                best = score
                if (score > a) {
                    a = score
                    pvTable[ply][ply] = m
                    val childLen = pvLength[ply + 1]
                    for (j in ply + 1 until childLen) pvTable[ply][j] = pvTable[ply + 1][j]
                    pvLength[ply] = childLen
                    if (a >= beta) return best
                }
            }
        }
        return best
    }

    /** Fills [orderScores] for the moves at [ply]: PV move, captures (MVV-LVA), killers, history. */
    private fun scoreMoves(moves: MoveList, ply: Int, side: Int) {
        var scores = orderScores[ply]
        if (scores.size < moves.size) { scores = IntArray(moves.size); orderScores[ply] = scores }
        val pvMove = if (ply < prevPv.size) prevPv[ply] else -1
        val k = killers[ply]
        for (i in 0 until moves.size) {
            val m = moves[i]
            val to = Board.moveTo(m)
            val victim = board.cells[to]
            scores[i] = when {
                m == pvMove -> 1_000_000
                victim != Board.EMPTY -> {
                    val attacker = board.cells[Board.moveFrom(m)]
                    500_000 + Evaluation.pieceValue(victim, to) * 8 - Evaluation.MATERIAL[Board.typeOf(attacker)] / 10
                }
                m == k[0] -> 400_000
                m == k[1] -> 390_000
                else -> history[side][Board.moveFrom(m) * 90 + to]
            }
        }
    }

    /** Selection step: moves the best-scored remaining move to index [i]. */
    private fun pickBest(moves: MoveList, ply: Int, i: Int) {
        val scores = orderScores[ply]
        var bestIdx = i
        var bestScore = scores[i]
        for (j in i + 1 until moves.size) {
            if (scores[j] > bestScore) { bestScore = scores[j]; bestIdx = j }
        }
        if (bestIdx != i) {
            moves.swap(i, bestIdx)
            val t = scores[i]; scores[i] = scores[bestIdx]; scores[bestIdx] = t
        }
    }

    private companion object {
        const val MAX_PLY = 64
        const val MAX_QUIESCENCE = 8
        const val DELTA_MARGIN = 150
    }
}
