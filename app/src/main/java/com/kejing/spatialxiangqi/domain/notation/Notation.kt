package com.kejing.spatialxiangqi.domain.notation

import com.kejing.spatialxiangqi.domain.model.Move
import com.kejing.spatialxiangqi.domain.model.Piece
import com.kejing.spatialxiangqi.domain.model.PieceType
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.Square
import com.kejing.spatialxiangqi.domain.rules.Rules
import kotlin.math.abs

/**
 * Move notation.
 *
 * * Chinese 纵线: piece name per side (红 帥仕相傌俥炮兵 / 黑 將士象馬車砲卒), files counted from the
 *   moving side's right (red 一..九, black 1..9), 进/退/平, then either the destination file (traverse,
 *   horse, elephant, advisor) or the step count (vertical rook/cannon/pawn/king moves). Two pieces of
 *   one kind on one file are written 前/后 + name (three: 前/中/后).
 * * WXF: the same structure with letters `K A E H R C P`, `+`/`-`/`=` and `+R`/`-R` (middle `.R`).
 * * ICCS: `H2-E2`; files a..i from red's left, ranks 0..9 from red's bottom.
 *
 * Parsing is tolerant: full-width digits and letters, Chinese numerals for either side, simplified,
 * traditional or variant piece names, `進/上` for 进 and `後/下` for 退, `h2e2` / `H2-E2` / `h2 e2`.
 */
object Notation {

    fun format(pos: Position, move: Move, style: NotationStyle): String = when (style) {
        NotationStyle.CHINESE -> formatChinese(pos, move)
        NotationStyle.WXF -> formatWxf(pos, move)
        NotationStyle.ICCS -> formatIccs(move)
    }

    /** `H2-E2` style (uppercase, dashed). [Move.iccs] gives the compact lowercase `h2e2`. */
    fun formatIccs(move: Move): String = "${move.from.iccs.uppercase()}-${move.to.iccs.uppercase()}"

    fun formatChinese(pos: Position, move: Move): String {
        val p = parts(pos, move)
        val side = p.piece.side
        val name = p.piece.glyph
        val head = if (p.idx >= 0) {
            val tag = when (p.sameCount) {
                2 -> "前后"[p.idx]
                3 -> "前中后"[p.idx]
                else -> "一二三四五"[p.idx]
            }
            "$tag$name"
        } else {
            "$name${numeral(side, p.fromFileNum)}"
        }
        val op = when {
            p.dir == 0 -> '平'
            p.dir > 0 -> '进'
            else -> '退'
        }
        return "$head$op${numeral(side, p.num)}"
    }

    fun formatWxf(pos: Position, move: Move): String {
        val p = parts(pos, move)
        val letter = p.piece.type.letter
        val head = if (p.idx >= 0) {
            val tag = when (p.sameCount) {
                2 -> "+-"[p.idx]
                3 -> "+.-"[p.idx]
                else -> '1' + p.idx
            }
            "$tag$letter"
        } else {
            "$letter${p.fromFileNum}"
        }
        val op = when {
            p.dir == 0 -> '='
            p.dir > 0 -> '+'
            else -> '-'
        }
        return "$head$op${p.num}"
    }

    /**
     * Finds the legal move written as [text] in any of the three styles; `null` when the text does
     * not describe a legal move for the side to move in [pos].
     */
    fun parse(pos: Position, text: String): Move? {
        val raw = normalizeWidth(text).trim()
        if (raw.isEmpty()) return null
        val cleaned = stripSuffix(raw)
        parseIccs(cleaned)?.let { (from, to) ->
            return Rules.moveOrNull(pos, from, to)
        }
        val wanted = canonical(cleaned) ?: return null
        val legal = Rules.allLegalMoves(pos)
        for (m in legal) {
            if (canonical(formatWxf(pos, m)) == wanted) return m
        }
        return null
    }

    /** Parses an ICCS pair such as `H2-E2`, `h2e2` or `h2 e2` into squares (no legality check). */
    fun parseIccs(text: String): Pair<Square, Square>? {
        val m = ICCS_REGEX.matchEntire(normalizeWidth(text).trim()) ?: return null
        val from = Square(m.groupValues[1][0].lowercaseChar() - 'a', m.groupValues[2].toInt())
        val to = Square(m.groupValues[3][0].lowercaseChar() - 'a', m.groupValues[4].toInt())
        return if (from.isValid() && to.isValid() && from != to) from to to else null
    }

    /** File number as written by [side]: red counts 一..九 from its right, black 1..9 from its right. */
    fun fileNumber(side: Side, file: Int): Int = if (side == Side.RED) 9 - file else file + 1

    /** Inverse of [fileNumber]. */
    fun fileFromNumber(side: Side, number: Int): Int = if (side == Side.RED) 9 - number else number - 1

    // ------------------------------------------------------------------ internals

    private class Parts(
        val piece: Piece,
        val sameCount: Int,
        val idx: Int,
        val dir: Int,
        val num: Int,
        val fromFileNum: Int,
    )

    private fun parts(pos: Position, move: Move): Parts {
        val piece = pos.pieceAt(move.from) ?: move.piece
        val side = piece.side
        val same: List<Int> = if (piece.type == PieceType.KING) emptyList() else {
            val ranks = (0..9).filter { r -> pos.pieceAt(move.from.file, r) == piece }
            if (side == Side.RED) ranks.sortedDescending() else ranks.sorted() // front first
        }
        val idx = if (same.size >= 2) same.indexOf(move.from.rank) else -1
        val dr = move.to.rank - move.from.rank
        val straight = piece.type == PieceType.ROOK || piece.type == PieceType.CANNON ||
            piece.type == PieceType.PAWN || piece.type == PieceType.KING
        val dir = when {
            dr == 0 -> 0
            dr * side.forward > 0 -> 1
            else -> -1
        }
        val num = when {
            dr == 0 -> fileNumber(side, move.to.file)
            straight -> abs(dr)
            else -> fileNumber(side, move.to.file)
        }
        return Parts(piece, same.size, idx, dir, num, fileNumber(side, move.from.file))
    }

    private const val CN_DIGITS = "零一二三四五六七八九"

    private fun numeral(side: Side, n: Int): String = if (side == Side.RED) CN_DIGITS[n].toString() else n.toString()

    private val ICCS_REGEX = Regex("^([a-iA-I])(\\d)\\s*[-–—]?\\s*([a-iA-I])(\\d)$")
    private val CANONICAL_REGEX = Regex("^([+\\-.1-5]?)([KAEHRCP])([1-9]?)([+\\-=.])([1-9])$")

    /** Full-width ASCII (U+FF01..U+FF5E) to half-width; ideographic space to space. */
    internal fun normalizeWidth(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when {
                    c.code in 0xFF01..0xFF5E -> (c.code - 0xFEE0).toChar()
                    c == '　' -> ' '
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    /** Removes trailing annotation glyphs (`!`, `?`, `#`, `+`, `=`, `±`, `∓`, `∞`). */
    internal fun stripSuffix(s: String): String = s.trimEnd { it in "!?#+=±∓∞" }

    /** Trailing annotation glyphs of a token, e.g. `!?` of `炮二平五!?`. */
    internal fun suffixOf(s: String): String = s.substring(stripSuffix(s).length)

    /**
     * Canonical `[marker]LETTER[file]OP num` form shared by the Chinese and WXF styles, e.g.
     * 炮二平五 / C2=5 -> `C2=5`, 前炮平五 / +C=5 -> `+C=5`. `null` when the text is not a move.
     */
    internal fun canonical(text: String): String? {
        val sb = StringBuilder()
        for (c0 in normalizeWidth(text)) {
            val c = c0
            if (c.isWhitespace()) continue
            val mapped: String = when (c) {
                '前' -> "+"
                '后', '後' -> "-"
                '中' -> "."
                '进', '進', '上' -> "+"
                '退', '下' -> "-"
                '平' -> "="
                in "一二三四五六七八九" -> (CN_DIGITS.indexOf(c)).toString()
                '０' -> "0"
                'b', 'B' -> "E"
                'n', 'N' -> "H"
                else -> {
                    val type = PieceType.fromGlyph(c)
                    when {
                        type != null -> type.letter.toString()
                        c.isLetter() -> c.uppercaseChar().toString()
                        else -> c.toString()
                    }
                }
            }
            sb.append(mapped)
        }
        val m = CANONICAL_REGEX.matchEntire(sb.toString()) ?: return null
        val op = if (m.groupValues[4] == ".") "=" else m.groupValues[4]
        return m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + op + m.groupValues[5]
    }
}
