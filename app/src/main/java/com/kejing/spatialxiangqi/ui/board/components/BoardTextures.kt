package com.kejing.spatialxiangqi.ui.board.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.kejing.spatialxiangqi.R
import com.kejing.spatialxiangqi.domain.model.Piece
import com.kejing.spatialxiangqi.domain.model.PieceFace
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.ui.theme.SkinSpec
import com.kejing.spatialxiangqi.ui.theme.Skins

/**
 * Paints the board face (grid, palaces, markers, 楚河／漢界 calligraphy) and piece faces into
 * bitmaps with Android Canvas, so the 3D board needs no external texture assets and every
 * skin / glyph mode is available at runtime. The river stays clean: no line, no number.
 */
class BoardTextures(context: Context) {
    private val typeface: Typeface = ResourcesCompat.getFont(context, R.font.notosanssc) ?: Typeface.DEFAULT_BOLD

    /**
     * Board face texture. The board has 9 files x 10 ranks; the bitmap covers the wood face
     * including a 1-pitch margin on every side (10 x 11 pitches). Red is at the bottom.
     */
    fun boardFace(skin: SkinSpec, showCoordinates: Boolean, size: Int = 2048): Bitmap {
        val w = size
        val h = (size * 11f / 10f).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pitch = w / 10f
        c.drawColor(skin.boardFace)
        // subtle grain
        val grain = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = skin.boardGrain; alpha = 60; strokeWidth = pitch * 0.02f; style = Paint.Style.STROKE }
        for (i in 1 until 22) {
            val y = i * h / 22f + (i % 3) * pitch * 0.05f
            val p = Path().apply {
                moveTo(0f, y); cubicTo(w * 0.3f, y - pitch * 0.12f, w * 0.6f, y + pitch * 0.15f, w.toFloat(), y - pitch * 0.05f)
            }
            c.drawPath(p, grain)
        }
        val ox = pitch
        val oy = pitch
        fun x(f: Int) = ox + f * pitch
        fun y(r: Int) = oy + (9 - r) * pitch
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = skin.line; strokeWidth = pitch * 0.025f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val border = Paint(line).apply { strokeWidth = pitch * 0.055f }
        // outer border
        c.drawRect(x(0) - pitch * 0.08f, y(9) - pitch * 0.08f, x(8) + pitch * 0.08f, y(0) + pitch * 0.08f, border)
        // ranks
        for (r in 0..9) c.drawLine(x(0), y(r), x(8), y(r), line)
        // files (broken at the river except the edges)
        for (f in 0..8) {
            if (f == 0 || f == 8) c.drawLine(x(f), y(0), x(f), y(9), line)
            else { c.drawLine(x(f), y(0), x(f), y(4), line); c.drawLine(x(f), y(5), x(f), y(9), line) }
        }
        // palaces
        c.drawLine(x(3), y(0), x(5), y(2), line); c.drawLine(x(5), y(0), x(3), y(2), line)
        c.drawLine(x(3), y(7), x(5), y(9), line); c.drawLine(x(5), y(7), x(3), y(9), line)
        // position markers for cannons and pawns
        val g = pitch * 0.07f; val s = pitch * 0.2f
        fun marker(f: Int, r: Int) {
            val cx = x(f); val cy = y(r)
            for (sx in intArrayOf(-1, 1)) {
                if ((f == 0 && sx == -1) || (f == 8 && sx == 1)) continue
                for (sy in intArrayOf(-1, 1)) {
                    c.drawLine(cx + sx * g, cy + sy * (g + s), cx + sx * g, cy + sy * g, line)
                    c.drawLine(cx + sx * g, cy + sy * g, cx + sx * (g + s), cy + sy * g, line)
                }
            }
        }
        for ((f, r) in listOf(1 to 2, 7 to 2, 1 to 7, 7 to 7, 0 to 3, 2 to 3, 4 to 3, 6 to 3, 8 to 3, 0 to 6, 2 to 6, 4 to 6, 6 to 6, 8 to 6)) marker(f, r)
        // river calligraphy only (no line, no number)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = skin.riverText; alpha = 235; typeface = this@BoardTextures.typeface; textSize = pitch * 0.62f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
        val riverMid = (y(5) + y(4)) / 2f
        val baseline = riverMid + text.textSize * 0.36f
        c.drawText("楚河", x(2), baseline, text)
        c.drawText("漢界", x(6), baseline, text)
        if (showCoordinates) {
            val coord = Paint(text).apply { textSize = pitch * 0.26f; alpha = 160; isFakeBoldText = false }
            for (f in 0..8) c.drawText("abcdefghi"[f].toString(), x(f), y(0) + pitch * 0.6f, coord)
            for (r in 0..9) c.drawText(r.toString(), x(0) - pitch * 0.5f, y(r) + coord.textSize * 0.36f, coord)
        }
        return bmp
    }

    /** Piece face: an alpha-cut disc (transparent corners) with an inner ring and the glyph. */
    fun pieceFace(piece: Piece, skin: SkinSpec, face: PieceFace, size: Int = 256): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.TRANSPARENT)
        val cx = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = skin.pieceFace }
        c.drawCircle(cx, cx, size * 0.5f, fill)
        // faint bevel toward the edge
        val bevel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = skin.pieceRim; alpha = 70; style = Paint.Style.STROKE; strokeWidth = size * 0.06f }
        c.drawCircle(cx, cx, size * 0.47f, bevel)
        val color = if (piece.side == Side.RED) skin.red else skin.black
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = size * 0.035f }
        c.drawCircle(cx, cx, size * 0.40f, ring)
        val isLetter = face == PieceFace.LETTER
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; typeface = this@BoardTextures.typeface; isFakeBoldText = true
            textSize = if (isLetter) size * 0.50f else size * 0.52f; textAlign = Paint.Align.CENTER
        }
        val glyph = if (isLetter) piece.type.letter.toString() else piece.type.glyph(piece.side).toString()
        val fm = text.fontMetrics
        val baseline = cx - (fm.ascent + fm.descent) / 2f
        c.drawText(glyph, cx, baseline, text)
        return bmp
    }

    /** Flat accent textures for highlight discs (legal dot / ring / last-move frame). */
    fun disc(color: Int, alpha: Int = 255, size: Int = 128, ring: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.TRANSPARENT)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; this.alpha = alpha }
        if (ring) { p.style = Paint.Style.STROKE; p.strokeWidth = size * 0.12f; c.drawCircle(size / 2f, size / 2f, size * 0.42f, p) }
        else c.drawCircle(size / 2f, size / 2f, size * 0.46f, p)
        return bmp
    }

    fun frame(color: Int, dashed: Boolean, size: Int = 128): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = size * 0.07f }
        if (dashed) p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(size * 0.12f, size * 0.09f), 0f)
        val inset = size * 0.08f
        c.drawRoundRect(RectF(inset, inset, size - inset, size - inset), size * 0.1f, size * 0.1f, p)
        return bmp
    }

    companion object {
        const val AMBER = Skins.AMBER
        const val CINNABAR = Skins.CINNABAR
        const val INK = Skins.INK
    }
}
