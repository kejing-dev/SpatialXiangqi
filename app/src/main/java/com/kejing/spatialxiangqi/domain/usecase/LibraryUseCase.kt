package com.kejing.spatialxiangqi.domain.usecase

import com.kejing.spatialxiangqi.domain.model.GameRecord

/** Library filtering and sorting rules (pure). */
class LibraryUseCase {
    /** Newest first; a query matches title, players, opening or date (case-insensitive). */
    fun filter(games: List<GameRecord>, query: String): List<GameRecord> {
        val q = query.trim().lowercase()
        val sorted = games.sortedByDescending { it.date }
        if (q.isEmpty()) return sorted
        return sorted.filter { g ->
            listOf(g.title, g.red, g.black, g.opening, g.date).any { it.lowercase().contains(q) }
        }
    }

    /** Human summary such as "红胜 · 46 回合" is built by the UI; here we expose the ply count. */
    fun moveCount(game: GameRecord): Int = (game.movesIccs.size + 1) / 2
}
