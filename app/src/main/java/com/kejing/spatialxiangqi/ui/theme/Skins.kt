package com.kejing.spatialxiangqi.ui.theme

import android.graphics.Color as AColor
import com.kejing.spatialxiangqi.domain.model.Skin
import com.pico.spatial.core.math.Color4

/**
 * Skin tokens used by the 3D board (Color4 for materials) and the 2D texture painter
 * (ARGB ints). Values come from the design package (design-doc §3 brand tokens).
 */
data class SkinSpec(
    val id: Skin,
    val boardFace: Int,
    val boardGrain: Int,
    val line: Int,
    val ledge: Int,
    val pieceFace: Int,
    val pieceRim: Int,
    val red: Int,
    val black: Int,
    val riverText: Int,
    val brassInlay: Boolean,
) {
    val boardFace4: Color4 get() = boardFace.toColor4()
    val ledge4: Color4 get() = ledge.toColor4()
    val pieceFace4: Color4 get() = pieceFace.toColor4()
    val pieceRim4: Color4 get() = pieceRim.toColor4()
}

fun Int.toColor4(): Color4 = Color4(
    AColor.red(this) / 255f,
    AColor.green(this) / 255f,
    AColor.blue(this) / 255f,
    AColor.alpha(this) / 255f,
)

object Skins {
    val rosewood = SkinSpec(
        id = Skin.ROSEWOOD,
        boardFace = 0xFF5A2E1E.toInt(),
        boardGrain = 0xFF3E1E12.toInt(),
        line = 0xFFC9A24A.toInt(),
        ledge = 0xFF3B1D12.toInt(),
        pieceFace = 0xFFE4C99B.toInt(),
        pieceRim = 0xFF8A6A3A.toInt(),
        red = 0xFFB8322A.toInt(),
        black = 0xFF1F1B18.toInt(),
        riverText = 0xFFC9A24A.toInt(),
        brassInlay = true,
    )
    val bamboo = SkinSpec(
        id = Skin.BAMBOO,
        boardFace = 0xFFD8C79A.toInt(),
        boardGrain = 0xFFB9A66F.toInt(),
        line = 0xFF2F5D3A.toInt(),
        ledge = 0xFF8C7A4A.toInt(),
        pieceFace = 0xFFCBB98A.toInt(),
        pieceRim = 0xFF7E6A3E.toInt(),
        red = 0xFFB8322A.toInt(),
        black = 0xFF1F1B18.toInt(),
        riverText = 0xFF2F5D3A.toInt(),
        brassInlay = false,
    )
    val battlefield = SkinSpec(
        id = Skin.BATTLEFIELD,
        boardFace = 0xFF6B5340.toInt(),
        boardGrain = 0xFF4A3728.toInt(),
        line = 0xFFB08A55.toInt(),
        ledge = 0xFF3A2C22.toInt(),
        pieceFace = 0xFF8C6B3F.toInt(),
        pieceRim = 0xFF3A2A1C.toInt(),
        red = 0xFFC8372D.toInt(),
        black = 0xFF15110E.toInt(),
        riverText = 0xFFB08A55.toInt(),
        brassInlay = false,
    )

    fun of(skin: Skin): SkinSpec = when (skin) {
        Skin.ROSEWOOD -> rosewood
        Skin.BAMBOO -> bamboo
        Skin.BATTLEFIELD -> battlefield
    }

    // Interaction accents shared by every skin (design tokens).
    const val AMBER = 0xFFE8A33D.toInt()
    const val CINNABAR = 0xFFB8322A.toInt()
    const val INK = 0xFF1F1B18.toInt()
    const val PAPER = 0xFFF4ECD8.toInt()
}
