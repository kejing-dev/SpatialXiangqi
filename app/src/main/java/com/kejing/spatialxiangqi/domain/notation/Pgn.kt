package com.kejing.spatialxiangqi.domain.notation

import com.kejing.spatialxiangqi.domain.model.GameMeta
import com.kejing.spatialxiangqi.domain.model.GameRecord
import com.kejing.spatialxiangqi.domain.model.GameTree
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.toRecord
import com.kejing.spatialxiangqi.domain.model.toTree
import java.util.UUID

/** Thrown (inside [Result]) by [Pgn.import]. [moveNumber] is the ply that failed, when applicable. */
class PgnParseException(message: String, val moveNumber: Int? = null, val token: String? = null) :
    IllegalArgumentException(message)

/**
 * PGN export/import for xiangqi.
 *
 * Export writes `[Game "Chinese Chess"]`, `[Event]` (the title), `[Red]`, `[Black]`, `[Date]`,
 * `[Result]`, `[FEN]`, `[Format]` and optional `[Opening]`, `[Level]`, `[PlayerSide]` headers, then
 * numbered moves with variations in parentheses, comments in braces and `!`/`?` suffixes.
 *
 * Import accepts full PGN, bare move lists (Chinese, WXF or ICCS, with or without move numbers),
 * or a single FEN line. Failures name the ply: "第 5 手无法解析：炮二平七".
 */
object Pgn {

    fun export(record: GameRecord, style: NotationStyle = NotationStyle.ICCS): String {
        val tree = record.toTree()
        val sb = StringBuilder()
        fun header(key: String, value: String) {
            sb.append('[').append(key).append(" \"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"]\n")
        }
        header("Game", "Chinese Chess")
        header("Event", record.title)
        header("Red", record.red)
        header("Black", record.black)
        header("Date", isoToPgnDate(record.date))
        header("Result", record.result.ifBlank { GameRecord.RESULT_UNKNOWN })
        header("FEN", tree.root.position.toFen())
        header("Format", when (style) { NotationStyle.CHINESE -> "Chinese"; NotationStyle.WXF -> "WXF"; NotationStyle.ICCS -> "ICCS" })
        if (record.opening.isNotBlank()) header("Opening", record.opening)
        record.level?.let { header("Level", it.toString()) }
        record.playerSide?.let { header("PlayerSide", it.name) }
        sb.append('\n')

        val tokens = ArrayList<String>()
        if (tree.root.comment.isNotEmpty()) tokens += "{${escapeComment(tree.root.comment)}}"
        val first = tree.root.children.firstOrNull()
        if (first != null) movetext(first, style, tokens)
        tokens += record.result.ifBlank { GameRecord.RESULT_UNKNOWN }
        sb.append(wrap(tokens)).append('\n')
        return sb.toString()
    }

    /**
     * Parses [text] into a record. [id] is generated when blank. Returns a failure whose message is
     * `第 N 手无法解析：<token>`, `FEN 格式错误` or `没有可导入的内容`.
     */
    fun import(text: String, id: String = ""): Result<GameRecord> = runCatching { parse(text, id) }

    // ------------------------------------------------------------------ export internals

    private fun movetext(start: GameTree.Node, style: NotationStyle, out: MutableList<String>) {
        var n: GameTree.Node? = start
        var needNumber = true
        while (n != null) {
            val move = n.move!!
            val parent = n.parent!!
            if (move.side == Side.RED) out += "${n.moveNumber}."
            else if (needNumber) out += "${n.moveNumber}..."
            val suffix = n.nags.filter { it.matches(SUFFIX_NAG) }.joinToString("")
            out += Notation.format(parent.position, move, style) + suffix
            for (nag in n.nags) if (!nag.matches(SUFFIX_NAG)) out += if (nag.startsWith("$")) nag else "{$nag}"
            if (n.comment.isNotEmpty()) out += "{${escapeComment(n.comment)}}"
            needNumber = false
            if (parent.children.firstOrNull() === n && parent.children.size > 1) {
                for (i in 1 until parent.children.size) {
                    val sub = ArrayList<String>()
                    movetext(parent.children[i], style, sub)
                    out += "(" + sub.joinToString(" ") + ")"
                }
                needNumber = true
            }
            n = n.children.firstOrNull()
        }
    }

    private fun escapeComment(s: String): String = s.replace('{', '（').replace('}', '）')

    private fun wrap(tokens: List<String>, width: Int = 80): String {
        val sb = StringBuilder()
        var lineLen = 0
        for (t in tokens) {
            if (lineLen > 0 && lineLen + 1 + t.length > width) { sb.append('\n'); lineLen = 0 }
            if (lineLen > 0) { sb.append(' '); lineLen++ }
            sb.append(t); lineLen += t.length
        }
        return sb.toString()
    }

    private fun isoToPgnDate(iso: String): String {
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(iso) ?: return iso.ifBlank { "????.??.??" }
        return "${m.groupValues[1]}.${m.groupValues[2]}.${m.groupValues[3]}"
    }

    private fun pgnToIsoDate(pgn: String): String {
        val m = Regex("^(\\d{4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})").find(pgn.trim()) ?: return if (pgn.contains('?')) "" else pgn.trim()
        return "%s-%02d-%02d".format(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }

    // ------------------------------------------------------------------ import internals

    private val SUFFIX_NAG = Regex("^[!?]{1,2}$")
    private val HEADER = Regex("\\[\\s*(\\w+)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"\\s*]")
    private val MOVE_NUMBER = Regex("^(\\d+)(\\.+|…)(.*)$")
    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*", "½-½")
    private val NAG_TOKEN = Regex("^\\$\\d+$")
    private val CJK_RUN = Regex("^[\\u4E00-\\u9FFF0-9１-９]+$")

    private sealed class Token {
        data class Comment(val text: String) : Token()
        object Open : Token()
        object Close : Token()
        data class Word(val text: String) : Token()
    }

    private fun parse(text: String, id: String): GameRecord {
        var src = text.replace("\r\n", "\n").replace('\r', '\n')
        if (src.isBlank()) throw PgnParseException("没有可导入的内容")

        // headers
        val headers = LinkedHashMap<String, String>()
        for (m in HEADER.findAll(src)) {
            headers[m.groupValues[1].lowercase()] = m.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
        }
        src = HEADER.replace(src, " ")

        // start position: [FEN] header, else a FEN line (optionally prefixed with "FEN:")
        var fen: String? = headers["fen"]?.takeIf { it.isNotBlank() }
        val lines = src.split('\n').toMutableList()
        for (i in lines.indices) {
            val line = lines[i].trim()
            val body = line.removePrefix("FEN:").removePrefix("FEN：").removePrefix("fen:").trim()
            if (body.isNotEmpty() && Position.looksLikeFen(body)) {
                if (fen == null) fen = body
                lines[i] = " "
            }
        }
        src = lines.joinToString("\n")

        val startPosition = if (fen != null) {
            Position.fromFen(fen) ?: throw PgnParseException("FEN 格式错误")
        } else Position.initial()

        // movetext
        val tokens = tokenize(src)
        val tree = GameTree(startPosition)
        val stack = ArrayDeque<GameTree.Node>()
        var result: String? = headers["result"]?.takeIf { it in RESULTS }
        var plies = 0
        loop@ for (tk in tokens) {
            when (tk) {
                is Token.Comment -> {
                    val node = tree.current
                    node.comment = if (node.comment.isEmpty()) tk.text else node.comment + " " + tk.text
                }
                Token.Open -> {
                    val cur = tree.current
                    if (cur.parent == null) continue@loop
                    stack.addLast(cur)
                    tree.goTo(cur.parent)
                }
                Token.Close -> {
                    val back = stack.removeLastOrNull() ?: continue@loop
                    tree.goTo(back)
                }
                is Token.Word -> {
                    var word = Notation.normalizeWidth(tk.text)
                    MOVE_NUMBER.matchEntire(word)?.let { word = it.groupValues[3] }
                    if (word.isEmpty()) continue@loop
                    if (word in RESULTS) {
                        if (stack.isEmpty()) { if (result == null) result = if (word == "½-½") GameRecord.RESULT_DRAW else word; break@loop }
                        continue@loop
                    }
                    if (NAG_TOKEN.matches(word)) {
                        tree.current.takeIf { it.parent != null }?.nags?.add(nagSymbol(word))
                        continue@loop
                    }
                    if (word.all { it in "!?" } && tree.current.parent != null) {
                        tree.current.nags += word
                        continue@loop
                    }
                    for (piece in splitConcatenated(word)) {
                        val clean = Notation.stripSuffix(piece)
                        if (clean.isEmpty()) continue
                        val move = Notation.parse(tree.current.position, clean)
                            ?: throw PgnParseException("第 ${tree.current.ply + 1} 手无法解析：$piece", tree.current.ply + 1, piece)
                        val node = tree.play(move)
                        plies++
                        val suffix = Notation.suffixOf(piece).filter { it in "!?" }
                        if (suffix.isNotEmpty()) node.nags += suffix
                    }
                }
            }
        }
        if (plies == 0 && fen == null && headers.isEmpty()) throw PgnParseException("没有可导入的内容")

        tree.toStart(); tree.toEnd()
        val meta = GameMeta(
            title = headers["event"]?.takeIf { it != "?" } ?: headers["title"] ?: "",
            red = headers["red"]?.takeIf { it != "?" } ?: "",
            black = headers["black"]?.takeIf { it != "?" } ?: "",
            date = headers["date"]?.let { pgnToIsoDate(it) } ?: "",
            result = result ?: GameRecord.RESULT_UNKNOWN,
            opening = headers["opening"] ?: headers["eco"] ?: "",
            level = headers["level"]?.toIntOrNull(),
            playerSide = headers["playerside"]?.let { s -> Side.entries.firstOrNull { it.name.equals(s, ignoreCase = true) } },
        )
        return tree.toRecord(id.ifBlank { "import-" + UUID.randomUUID().toString().take(8) }, meta)
    }

    private fun tokenize(src: String): List<Token> {
        val out = ArrayList<Token>()
        val word = StringBuilder()
        fun flush() { if (word.isNotEmpty()) { out += Token.Word(word.toString()); word.setLength(0) } }
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c == '{' -> {
                    flush()
                    val end = src.indexOf('}', i + 1).let { if (it < 0) src.length else it }
                    out += Token.Comment(src.substring(i + 1, end).trim().replace(Regex("\\s+"), " "))
                    i = end + 1
                    continue
                }
                c == '(' || c == '（' -> { flush(); out += Token.Open }
                c == ')' || c == '）' -> { flush(); out += Token.Close }
                c == '%' && (i == 0 || src[i - 1] == '\n') -> { // escape line
                    flush()
                    val end = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                    i = end
                    continue
                }
                c.isWhitespace() || c == ',' || c == ';' || c == '，' || c == '；' || c == '、' -> flush()
                else -> word.append(c)
            }
            i++
        }
        flush()
        return out
    }

    /** `炮二平五馬8進7` -> two tokens; anything else is returned as is. */
    private fun splitConcatenated(word: String): List<String> {
        if (word.length > 4 && word.length % 4 == 0 && CJK_RUN.matches(word)) {
            return word.chunked(4)
        }
        return listOf(word)
    }

    private fun nagSymbol(nag: String): String = when (nag) {
        "$1" -> "!"
        "$2" -> "?"
        "$3" -> "!!"
        "$4" -> "??"
        "$5" -> "!?"
        "$6" -> "?!"
        else -> nag
    }
}
