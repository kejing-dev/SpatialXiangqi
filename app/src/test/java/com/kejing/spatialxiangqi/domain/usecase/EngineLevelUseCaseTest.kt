package com.kejing.spatialxiangqi.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineLevelUseCaseTest {
    private val useCase = EngineLevelUseCase()

    @Test
    fun levelsAreClampedToFiveTiers() {
        assertEquals(1, useCase.spec(-3).level)
        assertEquals(5, useCase.spec(99).level)
        assertEquals(3, useCase.clamp(3))
    }

    @Test
    fun thinkTimeGrowsWithLevel() {
        val times = (1..5).map { useCase.spec(it).thinkMillis }
        assertTrue(times.zipWithNext().all { (a, b) -> a < b })
    }
}
