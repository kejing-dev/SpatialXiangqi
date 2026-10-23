package com.kejing.spatialxiangqi.domain.usecase

/** Maps the five user-facing difficulty tiers to engine settings (pure). */
class EngineLevelUseCase {
    data class LevelSpec(val level: Int, val thinkMillis: Long)

    /** Levels are 1..5 (入门 / 初级 / 中级 / 高级 / 大师); out-of-range values are clamped. */
    fun spec(level: Int): LevelSpec {
        val clamped = level.coerceIn(1, 5)
        val millis = when (clamped) {
            1 -> 150L
            2 -> 250L
            3 -> 400L
            4 -> 900L
            else -> 2000L
        }
        return LevelSpec(clamped, millis)
    }

    fun clamp(level: Int): Int = level.coerceIn(1, 5)
}
