package com.kejing.spatialxiangqi.domain.rules

import com.kejing.spatialxiangqi.domain.model.Move
import com.kejing.spatialxiangqi.domain.model.Piece
import com.kejing.spatialxiangqi.domain.model.PieceType
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.Square

/**
 * Compact mutable board shared by [Rules] and the local engine: 90 cells indexed `rank * 9 + file`,
 * piece codes packed as `(side shl 3) or (typeOrdinal + 1)` (0 = empty), moves packed as
 * `(from shl 7) or to`. The single move generator / attack detector lives here so the rules and the
 * engine can never disagree.
 */
internal class Board {
    val cells = IntArray(90)
    /** 0 = red, 1 = black. */
    var sideToMove = RED
    /** King squares per side, -1 when absent (setup positions). */
    val kingSq = intArrayOf(-1, -1)

    fun copy(): Board {
        val b = Board()
        cells.copyInto(b.cells)
        b.sideToMove = sideToMove
        b.kingSq[0] = kingSq[0]; b.kingSq[1] = kingSq[1]
        return b
    }

    fun set(idx: Int, code: Int) {
        cells[idx] = code
        if (code != EMPTY && typeOf(code) == T_KING) kingSq[sideOf(code)] = idx
    }

    fun recomputeKings() {
        kingSq[0] = -1; kingSq[1] = -1
        for (i in 0 until 90) {
            val c = cells[i]
            if (c != EMPTY && typeOf(c) == T_KING) kingSq[sideOf(c)] = i
        }
    }

    // ------------------------------------------------------------------ make / unmake

    /** Plays [move] (no legality check) and flips the side to move; returns the captured code. */
    fun make(move: Int): Int {
        val from = moveFrom(move); val to = moveTo(move)
        val captured = cells[to]
        val piece = cells[from]
        cells[to] = piece
        cells[from] = EMPTY
        if (typeOf(piece) == T_KING) kingSq[sideOf(piece)] = to
        if (captured != EMPTY && typeOf(captured) == T_KING) kingSq[sideOf(captured)] = -1
        sideToMove = 1 - sideToMove
        return captured
    }

    fun unmake(move: Int, captured: Int) {
        val from = moveFrom(move); val to = moveTo(move)
        val piece = cells[to]
        cells[from] = piece
        cells[to] = captured
        if (typeOf(piece) == T_KING) kingSq[sideOf(piece)] = from
        if (captured != EMPTY && typeOf(captured) == T_KING) kingSq[sideOf(captured)] = to
        sideToMove = 1 - sideToMove
    }

    // ------------------------------------------------------------------ attacks

    /** True when both kings stand on one file with nothing between them (the flying-general rule). */
    fun kingsFacing(): Boolean {
        val r = kingSq[RED]; val b = kingSq[BLACK]
        if (r < 0 || b < 0 || fileOf(r) != fileOf(b)) return false
        var i = minOf(r, b) + 9
        val end = maxOf(r, b)
        while (i < end) { if (cells[i] != EMPTY) return false; i += 9 }
        return true
    }

    /** True when [side]'s king is attacked, or the two kings face each other. */
    fun inCheck(side: Int): Boolean {
        val k = kingSq[side]
        if (k < 0) return false
        return kingsFacing() || isAttacked(k, 1 - side)
    }

    /** True when a piece of [by] could capture on [target] (the flying-general rule is not included). */
    fun isAttacked(target: Int, by: Int): Boolean {
        val tf = fileOf(target); val tr = rankOf(target)
        // rook, cannon and adjacent king along the four orthogonal rays
        for (d in 0 until 4) {
            val df = DF[d]; val dr = DR[d]
            var f = tf + df; var r = tr + dr; var screens = 0
            while (f in 0..8 && r in 0..9) {
                val c = cells[r * 9 + f]
                if (c != EMPTY) {
                    if (sideOf(c) == by) {
                        val t = typeOf(c)
                        if (screens == 0) {
                            if (t == T_ROOK) return true
                            if (t == T_KING && f == tf + df && r == tr + dr && inPalace(by, tf, tr)) return true
                        } else if (t == T_CANNON) return true
                    }
                    screens++
                    if (screens >= 2) break
                }
                f += df; r += dr
            }
        }
        // horse
        val horseCode = code(by, T_HORSE)
        val attackers = HORSE_ATTACKERS[target]
        var i = 0
        while (i < attackers.size) {
            if (cells[attackers[i]] == horseCode && cells[attackers[i + 1]] == EMPTY) return true
            i += 2
        }
        // pawn
        val pawnCode = code(by, T_PAWN)
        val behind = tr - forward(by)
        if (behind in 0..9 && cells[behind * 9 + tf] == pawnCode) return true
        if (crossed(by, tr)) {
            if (tf > 0 && cells[tr * 9 + tf - 1] == pawnCode) return true
            if (tf < 8 && cells[tr * 9 + tf + 1] == pawnCode) return true
        }
        // advisor
        if (inPalace(by, tf, tr)) {
            val advisorCode = code(by, T_ADVISOR)
            for (d in 0 until 4) {
                val f = tf + DIAG_F[d]; val r = tr + DIAG_R[d]
                if (inPalace(by, f, r) && cells[r * 9 + f] == advisorCode) return true
            }
        }
        // elephant
        if (ownHalf(by, tr)) {
            val elephantCode = code(by, T_ELEPHANT)
            for (d in 0 until 4) {
                val f = tf + 2 * DIAG_F[d]; val r = tr + 2 * DIAG_R[d]
                if (f in 0..8 && r in 0..9 && ownHalf(by, r) && cells[r * 9 + f] == elephantCode &&
                    cells[(tr + DIAG_R[d]) * 9 + tf + DIAG_F[d]] == EMPTY
                ) return true
            }
        }
        return false
    }

    // ------------------------------------------------------------------ move generation

    /** Pseudo-legal moves (blockers respected, king safety ignored) for every piece of [side]. */
    fun generatePseudo(side: Int, out: MoveList, capturesOnly: Boolean = false) {
        for (i in 0 until 90) {
            val c = cells[i]
            if (c != EMPTY && sideOf(c) == side) generatePseudoFrom(i, out, capturesOnly)
        }
    }

    /** Pseudo-legal moves of the piece on [from]. */
    fun generatePseudoFrom(from: Int, out: MoveList, capturesOnly: Boolean = false) {
        val piece = cells[from]
        if (piece == EMPTY) return
        val side = sideOf(piece)
        val f = fileOf(from); val r = rankOf(from)
        when (typeOf(piece)) {
            T_KING -> {
                val targets = KING_MOVES[side][from]
                for (to in targets) push(from, to, side, out, capturesOnly)
            }
            T_ADVISOR -> {
                val targets = ADVISOR_MOVES[side][from]
                for (to in targets) push(from, to, side, out, capturesOnly)
            }
            T_ELEPHANT -> {
                val t = ELEPHANT_MOVES[side][from]
                var i = 0
                while (i < t.size) {
                    if (cells[t[i + 1]] == EMPTY) push(from, t[i], side, out, capturesOnly)
                    i += 2
                }
            }
            T_HORSE -> {
                val t = HORSE_MOVES[from]
                var i = 0
                while (i < t.size) {
                    if (cells[t[i + 1]] == EMPTY) push(from, t[i], side, out, capturesOnly)
                    i += 2
                }
            }
            T_ROOK -> {
                for (d in 0 until 4) {
                    var tf = f + DF[d]; var tr = r + DR[d]
                    while (tf in 0..8 && tr in 0..9) {
                        val to = tr * 9 + tf
                        val c = cells[to]
                        if (c == EMPTY) {
                            if (!capturesOnly) out.add(pack(from, to))
                        } else {
                            if (sideOf(c) != side) out.add(pack(from, to))
                            break
                        }
                        tf += DF[d]; tr += DR[d]
                    }
                }
            }
            T_CANNON -> {
                for (d in 0 until 4) {
                    var tf = f + DF[d]; var tr = r + DR[d]; var screen = false
                    while (tf in 0..8 && tr in 0..9) {
                        val to = tr * 9 + tf
                        val c = cells[to]
                        if (!screen) {
                            if (c == EMPTY) {
                                if (!capturesOnly) out.add(pack(from, to))
                            } else screen = true
                        } else if (c != EMPTY) {
                            if (sideOf(c) != side) out.add(pack(from, to))
                            break
                        }
                        tf += DF[d]; tr += DR[d]
                    }
                }
            }
            T_PAWN -> {
                val tr = r + forward(side)
                if (tr in 0..9) push(from, tr * 9 + f, side, out, capturesOnly)
                if (crossed(side, r)) {
                    if (f > 0) push(from, r * 9 + f - 1, side, out, capturesOnly)
                    if (f < 8) push(from, r * 9 + f + 1, side, out, capturesOnly)
                }
            }
        }
    }

    private fun push(from: Int, to: Int, side: Int, out: MoveList, capturesOnly: Boolean) {
        val c = cells[to]
        if (c == EMPTY) {
            if (!capturesOnly) out.add(pack(from, to))
        } else if (sideOf(c) != side) out.add(pack(from, to))
    }

    /** Fully legal moves for [side] (king safety and the flying-general rule enforced). */
    fun generateLegal(side: Int, out: MoveList) {
        val pseudo = MoveList()
        generatePseudo(side, pseudo)
        filterLegal(side, pseudo, out)
    }

    fun generateLegalFrom(from: Int, out: MoveList) {
        val piece = cells[from]
        if (piece == EMPTY) return
        val pseudo = MoveList()
        generatePseudoFrom(from, pseudo)
        filterLegal(sideOf(piece), pseudo, out)
    }

    private fun filterLegal(side: Int, pseudo: MoveList, out: MoveList) {
        val saved = sideToMove
        for (i in 0 until pseudo.size) {
            val m = pseudo[i]
            val cap = make(m)
            val ok = !inCheck(side)
            unmake(m, cap)
            if (ok) out.add(m)
        }
        sideToMove = saved
    }

    fun hasLegalMove(side: Int): Boolean {
        val pseudo = MoveList()
        generatePseudo(side, pseudo)
        val saved = sideToMove
        try {
            for (i in 0 until pseudo.size) {
                val m = pseudo[i]
                val cap = make(m)
                val ok = !inCheck(side)
                unmake(m, cap)
                if (ok) return true
            }
        } finally {
            sideToMove = saved
        }
        return false
    }

    /** Number of pieces strictly between two squares on one file or rank (0 when not aligned). */
    fun between(a: Int, b: Int): Int {
        val fa = fileOf(a); val ra = rankOf(a); val fb = fileOf(b); val rb = rankOf(b)
        var n = 0
        if (fa == fb) {
            for (r in minOf(ra, rb) + 1 until maxOf(ra, rb)) if (cells[r * 9 + fa] != EMPTY) n++
        } else if (ra == rb) {
            for (f in minOf(fa, fb) + 1 until maxOf(fa, fb)) if (cells[ra * 9 + f] != EMPTY) n++
        }
        return n
    }

    // ------------------------------------------------------------------ conversions

    fun toMove(move: Int): Move {
        val from = moveFrom(move); val to = moveTo(move)
        return Move(Square.fromIndex(from), Square.fromIndex(to), toPiece(cells[from])!!, toPiece(cells[to]))
    }

    fun toPosition(halfmoveClock: Int = 0, fullmoveNumber: Int = 1): Position {
        val map = HashMap<Square, Piece>(32)
        for (i in 0 until 90) {
            val p = toPiece(cells[i]) ?: continue
            map[Square.fromIndex(i)] = p
        }
        return Position(map, if (sideToMove == RED) Side.RED else Side.BLACK, halfmoveClock, fullmoveNumber)
    }

    companion object {
        const val EMPTY = 0
        const val RED = 0
        const val BLACK = 1
        const val T_KING = 0
        const val T_ADVISOR = 1
        const val T_ELEPHANT = 2
        const val T_HORSE = 3
        const val T_ROOK = 4
        const val T_CANNON = 5
        const val T_PAWN = 6

        fun code(side: Int, type: Int): Int = (side shl 3) or (type + 1)
        fun code(piece: Piece): Int = code(piece.side.ordinal, piece.type.ordinal)
        fun sideOf(code: Int): Int = code shr 3
        fun typeOf(code: Int): Int = (code and 7) - 1
        fun toPiece(code: Int): Piece? =
            if (code == EMPTY) null else Piece.of(Side.entries[sideOf(code)], PieceType.entries[typeOf(code)])

        fun fileOf(idx: Int): Int = idx % 9
        fun rankOf(idx: Int): Int = idx / 9
        fun index(file: Int, rank: Int): Int = rank * 9 + file
        fun pack(from: Int, to: Int): Int = (from shl 7) or to
        fun moveFrom(move: Int): Int = move shr 7
        fun moveTo(move: Int): Int = move and 127

        fun forward(side: Int): Int = if (side == RED) 1 else -1
        fun inPalace(side: Int, file: Int, rank: Int): Boolean =
            file in 3..5 && if (side == RED) rank in 0..2 else rank in 7..9
        fun ownHalf(side: Int, rank: Int): Boolean = if (side == RED) rank <= 4 else rank >= 5
        fun crossed(side: Int, rank: Int): Boolean = if (side == RED) rank >= 5 else rank <= 4

        val DF = intArrayOf(1, -1, 0, 0)
        val DR = intArrayOf(0, 0, 1, -1)
        val DIAG_F = intArrayOf(1, 1, -1, -1)
        val DIAG_R = intArrayOf(1, -1, 1, -1)

        /** Horse jumps as (df, dr, legDf, legDr) quadruples. */
        private val HORSE_JUMPS = arrayOf(
            intArrayOf(1, 2, 0, 1), intArrayOf(-1, 2, 0, 1), intArrayOf(1, -2, 0, -1), intArrayOf(-1, -2, 0, -1),
            intArrayOf(2, 1, 1, 0), intArrayOf(2, -1, 1, 0), intArrayOf(-2, 1, -1, 0), intArrayOf(-2, -1, -1, 0),
        )

        /** Per square: flat (target, leg) pairs. */
        val HORSE_MOVES: Array<IntArray> = Array(90) { from ->
            val f = fileOf(from); val r = rankOf(from)
            val list = ArrayList<Int>()
            for (j in HORSE_JUMPS) {
                val tf = f + j[0]; val tr = r + j[1]
                if (tf in 0..8 && tr in 0..9) { list.add(index(tf, tr)); list.add(index(f + j[2], r + j[3])) }
            }
            list.toIntArray()
        }

        /** Per square: flat (origin, leg) pairs of horses that would attack this square. */
        val HORSE_ATTACKERS: Array<IntArray> = Array(90) { target ->
            val tf = fileOf(target); val tr = rankOf(target)
            val list = ArrayList<Int>()
            for (j in HORSE_JUMPS) {
                val of = tf - j[0]; val or = tr - j[1]
                if (of in 0..8 && or in 0..9) { list.add(index(of, or)); list.add(index(of + j[2], or + j[3])) }
            }
            list.toIntArray()
        }

        val KING_MOVES: Array<Array<IntArray>> = Array(2) { side ->
            Array(90) { from ->
                val f = fileOf(from); val r = rankOf(from)
                if (!inPalace(side, f, r)) IntArray(0) else {
                    val list = ArrayList<Int>()
                    for (d in 0 until 4) {
                        val tf = f + DF[d]; val tr = r + DR[d]
                        if (inPalace(side, tf, tr)) list.add(index(tf, tr))
                    }
                    list.toIntArray()
                }
            }
        }

        val ADVISOR_MOVES: Array<Array<IntArray>> = Array(2) { side ->
            Array(90) { from ->
                val f = fileOf(from); val r = rankOf(from)
                if (!inPalace(side, f, r)) IntArray(0) else {
                    val list = ArrayList<Int>()
                    for (d in 0 until 4) {
                        val tf = f + DIAG_F[d]; val tr = r + DIAG_R[d]
                        if (inPalace(side, tf, tr)) list.add(index(tf, tr))
                    }
                    list.toIntArray()
                }
            }
        }

        /** Per side and square: flat (target, eye) pairs. */
        val ELEPHANT_MOVES: Array<Array<IntArray>> = Array(2) { side ->
            Array(90) { from ->
                val f = fileOf(from); val r = rankOf(from)
                val list = ArrayList<Int>()
                for (d in 0 until 4) {
                    val tf = f + 2 * DIAG_F[d]; val tr = r + 2 * DIAG_R[d]
                    if (tf in 0..8 && tr in 0..9 && ownHalf(side, tr)) {
                        list.add(index(tf, tr)); list.add(index(f + DIAG_F[d], r + DIAG_R[d]))
                    }
                }
                list.toIntArray()
            }
        }

        fun from(position: Position): Board {
            val b = Board()
            for ((sq, piece) in position.pieces) {
                if (sq.isValid()) b.set(sq.index, code(piece))
            }
            b.sideToMove = position.sideToMove.ordinal
            return b
        }
    }
}

/** Growable int list used for packed moves (avoids boxing in the search). */
internal class MoveList(capacity: Int = 128) {
    var items = IntArray(capacity)
        private set
    var size = 0
        private set

    fun add(move: Int) {
        if (size == items.size) items = items.copyOf(size * 2)
        items[size++] = move
    }

    operator fun get(i: Int): Int = items[i]

    operator fun set(i: Int, value: Int) { items[i] = value }

    fun clear() { size = 0 }

    fun contains(move: Int): Boolean {
        for (i in 0 until size) if (items[i] == move) return true
        return false
    }

    fun swap(i: Int, j: Int) { val t = items[i]; items[i] = items[j]; items[j] = t }
}
