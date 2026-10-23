package com.kejing.spatialxiangqi.ui.board.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kejing.spatialxiangqi.R
import com.kejing.spatialxiangqi.domain.model.Piece
import com.kejing.spatialxiangqi.domain.model.PieceType
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.ui.board.BoardEvent
import com.kejing.spatialxiangqi.ui.board.BoardUiState
import com.kejing.spatialxiangqi.ui.board.MoveRow
import com.kejing.spatialxiangqi.ui.board.Phase
import com.kejing.spatialxiangqi.ui.i18n.LocalStrings
import com.kejing.spatialxiangqi.ui.theme.XiangqiColors
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TextArea
import com.pico.spatial.ui.design.windows.Subwindow
import com.pico.spatial.ui.design.windows.SubwindowPlacement
import com.pico.spatial.ui.foundation.geometry.DpOffset3D
import com.pico.spatial.ui.platform.ViewPoint

/** Pulls the side window toward the board (negative x = toward the container for a right placement). */
private val SCROLL_GAP_X = (-56).dp

/** Side window (棋谱卷): header, eval bar, move list, navigation, engine lines and notes. */
@Composable
fun ScoreScroll(state: BoardUiState, onEvent: (BoardEvent) -> Unit) {
    if (!state.scrollOpen) return
    Subwindow(
        placement = SubwindowPlacement.Right,
        followViewpoints = setOf(ViewPoint.Front),
        offset = DpOffset3D(SCROLL_GAP_X, 0.dp, 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().heightIn(max = 1400.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.phase == Phase.SETUP) SetupPalette(state, onEvent) else ScrollContent(state, onEvent)
        }
    }
}

@Composable
private fun ColumnScope.ScrollContent(state: BoardUiState, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    Header(state, onEvent)
    EvalBar(state)
    MoveList(state, onEvent, Modifier.weight(1f, fill = true))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        NavButton(R.drawable.ic_first, s.step, state.canBack) { onEvent(BoardEvent.ToStart) }
        NavButton(R.drawable.ic_prev, s.step, state.canBack) { onEvent(BoardEvent.Back) }
        NavButton(R.drawable.ic_next, s.step, state.canForward) { onEvent(BoardEvent.Forward) }
        NavButton(R.drawable.ic_last, s.step, state.canForward) { onEvent(BoardEvent.ToEnd) }
    }
    if (state.phase == Phase.REVIEW) EngineLines(state)
    if (state.phase == Phase.REVIEW || state.phase == Phase.RESULT) {
        Text(s.notes, style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
        TextArea(
            value = state.comment,
            onValueChange = { onEvent(BoardEvent.SetComment(it)) },
            modifier = Modifier.fillMaxWidth().height(88.dp),
            placeholder = { Text(s.addNote, style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelTertiary) },
        )
    }
    ActionRow(state, onEvent)
}

@Composable
private fun Header(state: BoardUiState, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    val title = state.gameTitle.ifBlank { if (state.vsEngine && state.phase == Phase.PLAY) s.meVsEngine else s.moves }
    val (turn, dot) = when {
        state.phase == Phase.REVIEW -> s.reviewing to XiangqiColors.amber
        state.engineThinking -> s.thinking to XiangqiColors.amber
        state.sideToMove == Side.RED -> s.redToMove to XiangqiColors.cinnabar
        else -> s.blackToMove to XiangqiColors.ink
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = PicoTheme.typography.headlineSmall, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, modifier = Modifier.weight(1f))
            FlatIconButton(R.drawable.ic_close, s.close, size = 22.dp) { onEvent(BoardEvent.SetScrollOpen(false)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Text(turn, style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
            if (state.checkedKing != null) Text(s.check, style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.error)
            Spacer(Modifier.weight(1f))
            Text("${state.currentPly}/${state.moves.size}", style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelTertiary)
        }
    }
}

@Composable
private fun EvalBar(state: BoardUiState) {
    val s = LocalStrings.current
    val f = (1f / (1f + kotlin.math.exp(-state.evalCp / 400f))).coerceIn(0.05f, 0.95f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
            Box(Modifier.weight(f).fillMaxWidth().height(8.dp).background(XiangqiColors.cinnabar))
            Box(Modifier.weight(1f - f).fillMaxWidth().height(8.dp).background(XiangqiColors.ink))
        }
        val cp = state.evalCp
        val text = if (cp >= 0) "+%.1f".format(cp / 100f) else "%.1f".format(cp / 100f)
        Text("${s.evalLabel}  $text", style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelTertiary)
    }
}

@Composable
private fun MoveList(state: BoardUiState, onEvent: (BoardEvent) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val pairs = state.moves.chunked(2)
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentPly, pairs.size) {
        val idx = ((state.currentPly - 1) / 2).coerceAtLeast(0)
        if (pairs.isNotEmpty()) listState.animateScrollToItem(idx.coerceAtMost(pairs.size - 1))
    }
    Column(
        modifier.fillMaxWidth().heightIn(min = 160.dp).clip(RoundedCornerShape(16.dp)).background(PicoTheme.colorScheme.fillTertiary).padding(8.dp),
    ) {
        if (state.moves.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(s.hintTapPiece, style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelTertiary)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(pairs) { i, pair ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.width(34.dp))
                        pair.forEach { row -> MoveCell(row, Modifier.weight(1f)) { onEvent(BoardEvent.JumpToPly(row.ply)) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Text(s.tapMoveToJump, style = PicoTheme.typography.bodyTiny, color = PicoTheme.colorScheme.labelQuaternary, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
    }
}

@Composable
private fun MoveCell(row: MoveRow, modifier: Modifier, onClick: () -> Unit) {
    ClickableRow(modifier = modifier, selected = row.isCurrent, selectedColor = PicoTheme.colorScheme.interaction, onClick = onClick) {
        Text(
            row.text + if (row.hasComment) " ·" else "",
            style = PicoTheme.typography.bodyMedium,
            fontWeight = if (row.isCurrent) FontWeight.Bold else FontWeight.Medium,
            color = when {
                row.isCurrent -> PicoTheme.colorScheme.labelPrimaryLight
                row.isVariation -> PicoTheme.colorScheme.labelSecondary
                else -> PicoTheme.colorScheme.labelPrimary
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun NavButton(iconRes: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    FlatIconButton(iconRes, description, enabled = enabled, size = 28.dp, onClick = onClick)
}

@Composable
private fun EngineLines(state: BoardUiState) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(s.engineLines, style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
        if (state.engineLines.isEmpty()) {
            Text("…", style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelTertiary)
        }
        state.engineLines.forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                val score = line.mateIn?.let { s.mateIn(it) } ?: (if (line.scoreCp >= 0) "+%.1f" else "%.1f").format(line.scoreCp / 100f)
                Text(score, style = PicoTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = PicoTheme.colorScheme.interaction, modifier = Modifier.width(56.dp))
                Text(line.movesText, style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2, modifier = Modifier.weight(1f))
                Text("${s.depth} ${line.depth}", style = PicoTheme.typography.bodyTiny, color = PicoTheme.colorScheme.labelTertiary)
            }
        }
    }
}

@Composable
private fun ActionRow(state: BoardUiState, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.phase) {
            Phase.PLAY -> {
                SecondaryButton(s.review, Modifier.weight(1f)) { onEvent(BoardEvent.EnterReview) }
                SecondaryButton(s.setup, Modifier.weight(1f)) { onEvent(BoardEvent.EnterSetup) }
                SecondaryButton(s.resign, Modifier.weight(1f)) { onEvent(BoardEvent.RequestResign) }
            }
            Phase.REVIEW -> {
                PrimaryButton(s.playFromHere, Modifier.weight(1f)) { onEvent(BoardEvent.PlayFromHere) }
                SecondaryButton(s.setup, Modifier.weight(1f)) { onEvent(BoardEvent.EnterSetup) }
                SecondaryButton(s.save, Modifier.weight(1f)) { onEvent(BoardEvent.SaveGame) }
            }
            Phase.RESULT -> {
                PrimaryButton(s.rematch, Modifier.weight(1f)) { onEvent(BoardEvent.Rematch) }
                SecondaryButton(s.review, Modifier.weight(1f)) { onEvent(BoardEvent.EnterReview) }
                SecondaryButton(s.save, Modifier.weight(1f)) { onEvent(BoardEvent.SaveGame) }
            }
            else -> Unit
        }
    }
}

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.interaction, contentColor = PicoTheme.colorScheme.labelPrimaryLight),
    ) {
        // design-style: inherited-content-color Button
        Text(label, style = PicoTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillSecondary, contentColor = PicoTheme.colorScheme.labelPrimary),
    ) {
        // design-style: inherited-content-color Button
        Text(label, style = PicoTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Piece palette shown in the side window while editing a position. */
@Composable
private fun SetupPalette(state: BoardUiState, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    Text(s.setupPalette, style = PicoTheme.typography.headlineSmall, color = PicoTheme.colorScheme.labelPrimary)
    Text(s.hintSetup, style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelSecondary)
    for (side in Side.entries) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PieceType.entries.forEach { type ->
                val piece = Piece.of(side, type)
                val selected = state.setupPiece == piece
                ClickableRow(modifier = Modifier.weight(1f), selected = selected, onClick = { onEvent(BoardEvent.SetupPick(if (selected) null else piece)) }) {
                    Text(
                        piece.glyph.toString(),
                        style = PicoTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (side == Side.RED) XiangqiColors.cinnabar else PicoTheme.colorScheme.labelPrimary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    SettingRow(s.sideToMove) {
        TextSegments(listOf(s.playRed, s.playBlack), if (state.setupSideToMove == Side.RED) 0 else 1) {
            onEvent(BoardEvent.SetupSideToMove(if (it == 0) Side.RED else Side.BLACK))
        }
    }
    state.setupErrors.forEach { Text(it, style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.error) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(s.setupClear, Modifier.weight(1f)) { onEvent(BoardEvent.SetupClear) }
        SecondaryButton(s.setupInitial, Modifier.weight(1f)) { onEvent(BoardEvent.SetupInitial) }
        PrimaryButton(s.setupDone, Modifier.weight(1f)) { onEvent(BoardEvent.SetupDone) }
    }
}
