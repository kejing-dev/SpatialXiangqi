package com.kejing.spatialxiangqi.domain.usecase

import com.kejing.spatialxiangqi.domain.model.GameRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryUseCaseTest {
    private val useCase = LibraryUseCase()
    private fun game(id: String, title: String, date: String, red: String = "", moves: Int = 0) =
        GameRecord(id = id, title = title, red = red, black = "", date = date, result = "*", movesIccs = List(moves) { "h2e2" })

    @Test
    fun newestFirstWhenQueryEmpty() {
        val games = listOf(game("a", "old", "2026-01-01"), game("b", "new", "2026-09-01"))
        assertEquals(listOf("b", "a"), useCase.filter(games, "").map { it.id })
    }

    @Test
    fun queryMatchesTitlePlayersAndDateCaseInsensitively() {
        val games = listOf(game("a", "顺炮对局", "2026-01-01", red = "Me"), game("b", "Other", "2026-09-01"))
        assertEquals(listOf("a"), useCase.filter(games, "me").map { it.id })
        assertEquals(listOf("a"), useCase.filter(games, "顺炮").map { it.id })
        assertEquals(listOf("b"), useCase.filter(games, "2026-09").map { it.id })
    }

    @Test
    fun moveCountRoundsPliesUp() {
        assertEquals(0, useCase.moveCount(game("a", "t", "d", moves = 0)))
        assertEquals(1, useCase.moveCount(game("a", "t", "d", moves = 1)))
        assertEquals(2, useCase.moveCount(game("a", "t", "d", moves = 3)))
    }
}
