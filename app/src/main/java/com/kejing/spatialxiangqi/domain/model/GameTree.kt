package com.kejing.spatialxiangqi.domain.model

import com.kejing.spatialxiangqi.domain.rules.Rules

/**
 * A game with variations: a tree of [Node]s whose root holds the start position. [current] is the
 * node the UI is looking at. The first child of every node is its main continuation.
 */
class GameTree(startPosition: Position = Position.initial()) {

    class Node(val move: Move?, val parent: Node?, val position: Position) {
        /** Continuations, main line first. */
        val children: MutableList<Node> = ArrayList(1)
        var comment: String = ""
        /** Annotation symbols such as `!`, `?`, `!?` or `$14`. */
        val nags: MutableSet<String> = LinkedHashSet()
        /** Number of moves played from the root to reach this node (root = 0). */
        val ply: Int = if (parent == null) 0 else parent.ply + 1

        val isRoot: Boolean get() = parent == null
        val mainChild: Node? get() = children.firstOrNull()
        /** Move number for display: 1 for both plies of the first move, and so on. */
        val moveNumber: Int get() = (ply + 1) / 2

        /** True when this node is reachable from the root by following first children only. */
        val isOnMainLine: Boolean
            get() {
                var n: Node = this
                while (true) {
                    val p = n.parent ?: return true
                    if (p.children.firstOrNull() !== n) return false
                    n = p
                }
            }

        /** This node followed by its main continuation. */
        fun lineFrom(): List<Node> {
            val out = ArrayList<Node>()
            var n: Node? = this
            while (n != null) { out += n; n = n.children.firstOrNull() }
            return out
        }

        override fun toString(): String = "Node(ply=$ply, move=${move?.iccs ?: "root"})"
    }

    val root: Node = Node(null, null, startPosition)
    var current: Node = root

    val startPosition: Position get() = root.position

    /**
     * Plays [move] from [current]: reuses an existing child with the same from/to squares, otherwise
     * appends a new variation (or the main continuation when there is none). Returns the new current node.
     */
    fun play(move: Move): Node {
        val existing = current.children.firstOrNull { it.move?.sameSquares(move) == true }
        if (existing != null) {
            current = existing
            return existing
        }
        val pos = current.position
        val actual = move.copy(piece = pos.pieceAt(move.from) ?: move.piece, captured = pos.pieceAt(move.to))
        val node = Node(actual, current, Rules.apply(pos, actual))
        current.children += node
        current = node
        return node
    }

    fun goTo(node: Node) {
        current = node
    }

    /** Steps to the parent; false at the root. */
    fun back(): Boolean {
        val p = current.parent ?: return false
        current = p
        return true
    }

    /** Steps to the main child; false at the end of the line. */
    fun forward(): Boolean {
        val c = current.children.firstOrNull() ?: return false
        current = c
        return true
    }

    fun toStart() {
        current = root
    }

    /** Follows the main continuation from [current] to the end of that line. */
    fun toEnd() {
        while (forward()) Unit
    }

    /** Main-line move nodes from the first move to the end (root excluded). */
    fun mainLine(): List<Node> = root.lineFrom().drop(1)

    /** Move nodes from the first move down to [node] inclusive (root excluded). */
    fun pathTo(node: Node): List<Node> {
        val out = ArrayList<Node>()
        var n: Node? = node
        while (n != null && n.parent != null) { out += n; n = n.parent }
        out.reverse()
        return out
    }

    /** Makes the whole path from the root to [node] the main line. */
    fun promoteToMainLine(node: Node) {
        var n: Node = node
        while (true) {
            val p = n.parent ?: break
            val i = p.children.indexOf(n)
            if (i > 0) { p.children.removeAt(i); p.children.add(0, n) }
            n = p
        }
    }

    /** Removes [node] and everything after it. If [current] was inside, it moves to the parent. */
    fun deleteVariation(node: Node) {
        val p = node.parent ?: return
        if (!p.children.remove(node)) return
        var c: Node? = current
        while (c != null) {
            if (c === node) { current = p; break }
            c = c.parent
        }
    }

    /** Positions from the root to [current] inclusive. */
    fun positionHistory(): List<Position> {
        val out = ArrayList<Position>(current.ply + 1)
        out += root.position
        for (n in pathTo(current)) out += n.position
        return out
    }

    /** Every node in pre-order (root first, main lines before their variations). */
    fun allNodes(): List<Node> {
        val out = ArrayList<Node>()
        fun visit(n: Node) { out += n; for (c in n.children) visit(c) }
        visit(root)
        return out
    }

    /** True when no move has been played yet. */
    fun isEmpty(): Boolean = root.children.isEmpty()

    companion object {
        fun fromPosition(pos: Position): GameTree = GameTree(pos)

        fun fromFen(fen: String): GameTree? = Position.fromFen(fen)?.let { GameTree(it) }
    }
}
