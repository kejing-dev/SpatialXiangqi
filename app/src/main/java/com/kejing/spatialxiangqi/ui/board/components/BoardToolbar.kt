package com.kejing.spatialxiangqi.ui.board.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kejing.spatialxiangqi.R
import com.kejing.spatialxiangqi.domain.model.BoardMode
import com.kejing.spatialxiangqi.ui.board.BoardEvent
import com.kejing.spatialxiangqi.ui.board.BoardUiState
import com.kejing.spatialxiangqi.ui.board.Panel
import com.kejing.spatialxiangqi.ui.board.Phase
import com.kejing.spatialxiangqi.ui.i18n.LocalStrings
import com.pico.spatial.ui.design.windows.Toolbar

/** Window-level toolbar under the board: mode switch + the six primary actions. */
@Composable
fun BoardToolbar(state: BoardUiState, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    Toolbar(cornerSize = 37.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextSegments(
                options = listOf(s.wall, s.table),
                selected = if (state.mode == BoardMode.WALL) 0 else 1,
                modifier = Modifier.width(200.dp),
                onSelect = { onEvent(BoardEvent.SetMode(if (it == 0) BoardMode.WALL else BoardMode.TABLE)) },
            )
            ToolDivider()
            ToolItem(R.drawable.ic_undo, s.takeback, enabled = state.canUndo && !state.engineThinking) { onEvent(BoardEvent.Undo) }
            ToolItem(R.drawable.ic_bulb, s.hint, enabled = state.phase in listOf(Phase.PLAY, Phase.REVIEW) && !state.engineThinking) { onEvent(BoardEvent.Hint) }
            ToolItem(R.drawable.ic_engine, s.engine, active = state.vsEngine, enabled = state.phase == Phase.PLAY) { onEvent(BoardEvent.ToggleEngine) }
            ToolDivider()
            ToolItem(R.drawable.ic_library, s.moves, active = state.scrollOpen) { onEvent(BoardEvent.ToggleScroll) }
            ToolItem(R.drawable.ic_box, s.box, active = state.panel == Panel.BOX) { onEvent(BoardEvent.OpenPanel(Panel.BOX)) }
            ToolItem(R.drawable.ic_settings, s.placement, active = state.panel == Panel.PLACEMENT) { onEvent(BoardEvent.OpenPanel(Panel.PLACEMENT)) }
            ToolItem(R.drawable.ic_help, s.help, active = state.panel == Panel.HELP) { onEvent(BoardEvent.OpenPanel(Panel.HELP)) }
        }
    }
}
