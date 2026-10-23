package com.kejing.spatialxiangqi.domain.model

/** UI language; the app switches instantly without recreating the window. */
enum class Language { ZH, EN }

/** Board form: 壁挂 (vertical, magnetic pieces) or 桌面 (flat on the desk). */
enum class BoardMode { WALL, TABLE }

/** Visual skin. Only materials, line colors, ledge and ambience change; never sizes. */
enum class Skin { ROSEWOOD, BAMBOO, BATTLEFIELD }

/** Piece face rendering: 汉字 glyphs or Latin letters (K A E H R C P). */
enum class PieceFace { GLYPH, LETTER }

/** Persistent user settings (placement & display + preferences). */
data class AppSettings(
    val language: Language = Language.ZH,
    val mode: BoardMode = BoardMode.WALL,
    val skin: Skin = Skin.ROSEWOOD,
    val pieceFace: PieceFace = PieceFace.GLYPH,
    val notation: Int = 0,               // index into NotationStyle: 0 CHINESE, 1 WXF, 2 ICCS
    val sounds: Boolean = true,
    val haptics: Boolean = true,
    val boardScalePercent: Int = 100,    // 80..140
    val orientationDeg: Int = 0,         // 0 / 90 / 180 / 270 (table mode)
    val heightTrimCm: Int = 0,           // -1 / 0 / +1
    val tiltDeg: Int = 0,                // 0..20 (table mode)
    val showCoordinates: Boolean = false,
    val reduceMotion: Boolean = false,
    val firstRunDone: Boolean = false,
    val engineOn: Boolean = true,
    val level: Int = 3,                  // 1..5
)
