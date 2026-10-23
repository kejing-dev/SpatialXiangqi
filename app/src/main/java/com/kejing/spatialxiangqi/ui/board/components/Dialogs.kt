package com.kejing.spatialxiangqi.ui.board.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kejing.spatialxiangqi.ui.board.BoardEvent
import com.kejing.spatialxiangqi.ui.board.BoardUiState
import com.kejing.spatialxiangqi.ui.board.Dialog
import com.kejing.spatialxiangqi.ui.board.ResultKind
import com.kejing.spatialxiangqi.ui.board.ResultReason
import com.kejing.spatialxiangqi.ui.i18n.LocalStrings
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.AlertDialog

/** Short confirmations (resign / delete / replace board). */
@Composable
fun ConfirmDialog(dialog: Dialog, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    val (title, body, confirm) = when (dialog) {
        Dialog.Resign -> Triple(s.resignTitle, s.resignBody, s.resign)
        is Dialog.DeleteGame -> Triple(s.deleteTitle, s.deleteBody.replace("{title}", dialog.title.ifBlank { s.untitled }), s.delete)
        Dialog.ReplaceBoard -> Triple(s.replaceBoard, s.replaceBoardBody, s.newGame)
    }
    AlertDialog(
        onDismissRequest = { onEvent(BoardEvent.DismissDialog) },
        title = {
            // design-style: inherited-content-color AlertDialog
            Text(title, style = PicoTheme.typography.headlineSmall)
        },
        content = {
            // design-style: inherited-content-color AlertDialog
            Text(body, style = PicoTheme.typography.bodyMedium)
        },
        buttons = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SecondaryButton(s.cancel) { onEvent(BoardEvent.DismissDialog) }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(confirm) { onEvent(BoardEvent.ConfirmDialog) }
            }
        },
    )
}

/** Game over dialog. */
@Composable
fun ResultDialog(state: BoardUiState, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    val result = state.result ?: return
    val title = when (result.kind) {
        ResultKind.RED_WINS -> s.redWins
        ResultKind.BLACK_WINS -> s.blackWins
        ResultKind.DRAW -> s.draw
    }
    val reason = when (result.reason) {
        ResultReason.CHECKMATE -> s.reasonCheckmate
        ResultReason.STALEMATE -> s.reasonStalemate
        ResultReason.RESIGN -> s.reasonResign
        ResultReason.TIMEOUT -> s.reasonTimeout
        ResultReason.REPETITION -> s.reasonRepetition
        ResultReason.MOVE_RULE -> s.reasonMoveRule
    }
    AlertDialog(
        onDismissRequest = { onEvent(BoardEvent.DismissResult) },
        title = {
            // design-style: inherited-content-color AlertDialog
            Text(title, style = PicoTheme.typography.headlineSmall)
        },
        content = {
            // design-style: inherited-content-color AlertDialog
            Text(reason, style = PicoTheme.typography.bodyMedium)
        },
        buttons = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SecondaryButton(s.close) { onEvent(BoardEvent.DismissResult) }
                Spacer(Modifier.width(8.dp))
                SecondaryButton(s.review) { onEvent(BoardEvent.EnterReview) }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(s.rematch) { onEvent(BoardEvent.Rematch) }
            }
        },
    )
}
