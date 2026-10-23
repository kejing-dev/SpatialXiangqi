package com.kejing.spatialxiangqi.ui.board

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kejing.spatialxiangqi.data.repository.FileGameRepository
import com.kejing.spatialxiangqi.data.repository.PrefsSettingsRepository
import com.kejing.spatialxiangqi.domain.engine.LocalEngine
import com.kejing.spatialxiangqi.domain.model.BoardMode
import com.kejing.spatialxiangqi.domain.model.Language
import com.kejing.spatialxiangqi.domain.model.Piece
import com.kejing.spatialxiangqi.domain.model.PieceFace
import com.kejing.spatialxiangqi.domain.model.PieceType
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.Skin
import com.kejing.spatialxiangqi.domain.model.Square
import com.kejing.spatialxiangqi.platform.DebugCommand
import com.kejing.spatialxiangqi.platform.DebugReceiver
import com.kejing.spatialxiangqi.ui.board.components.Board3D
import com.kejing.spatialxiangqi.ui.board.components.BoardScene
import com.kejing.spatialxiangqi.ui.board.components.BoardToolbar
import com.kejing.spatialxiangqi.ui.board.components.BoxSheet
import com.kejing.spatialxiangqi.ui.board.components.ConfirmDialog
import com.kejing.spatialxiangqi.ui.board.components.HelpSheet
import com.kejing.spatialxiangqi.ui.board.components.PlacementSheet
import com.kejing.spatialxiangqi.ui.board.components.ResultDialog
import com.kejing.spatialxiangqi.ui.board.components.ScoreScroll
import com.kejing.spatialxiangqi.ui.i18n.LocalStrings
import com.kejing.spatialxiangqi.ui.i18n.stringsFor
import com.kejing.spatialxiangqi.ui.navigation.BoxSection
import com.pico.spatial.ui.design.windows.LocalSnackbarHostState
import com.pico.spatial.ui.design.windows.SnackbarHost

/** Stateful shell: wires the ViewModel, the debug channel and transient messages. */
@Composable
fun BoardScreen() {
    val context = LocalContext.current
    val vm: BoardViewModel = viewModel(factory = remember { BoardViewModelFactory(context.applicationContext) })
    val state by vm.state.collectAsStateWithLifecycle()
    val scene = remember { BoardScene(context.applicationContext) }
    var tuningStamp by remember { mutableIntStateOf(0) }

    val debuggable = remember { (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }
    LaunchedEffect(debuggable) {
        if (!debuggable) return@LaunchedEffect
        DebugReceiver.commands.collect { cmd ->
            if (cmd.cmd == "tune") {
                val (k, v) = cmd.arg.split("=", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                if (scene.tuning.apply(k, v)) tuningStamp++
                Log.i(DebugReceiver.TAG, "tune $k=$v stamp=$tuningStamp")
            } else if (cmd.cmd == "state") {
                Log.i(DebugReceiver.TAG, "STATE ${vm.state.value.let { "phase=${it.phase} mode=${it.mode} ply=${it.currentPly} side=${it.sideToMove} sel=${it.selected} panel=${it.panel} vsEngine=${it.vsEngine} thinking=${it.engineThinking} fen=${it.position.toFen()}" }}")
            } else {
                debugEvents(cmd, vm.state.value).forEach { vm.onEvent(it) }
            }
        }
    }

    CompositionLocalProvider(LocalStrings provides stringsFor(state.language)) {
        SnackbarHost {
            val snack = LocalSnackbarHostState.current
            val strings = LocalStrings.current
            LaunchedEffect(Unit) { vm.messages.collect { msg -> snack.show(message = msg.text(strings)) } }
            LaunchedEffect(state.exportText) {
                if (state.exportText.isNotBlank()) {
                    runCatching {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("xiangqi", state.exportText))
                    }
                }
            }
            BoardContent(state = state, scene = scene, tuningStamp = tuningStamp, onEvent = vm::onEvent)
        }
    }
}

/** Stateless composition of the volume, the window chrome and the modal layers. */
@Composable
fun BoardContent(state: BoardUiState, scene: BoardScene, tuningStamp: Int, onEvent: (BoardEvent) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Board3D(state = state, scene = scene, tuningStamp = tuningStamp, onEvent = onEvent)
    }
    BoardToolbar(state, onEvent)
    ScoreScroll(state, onEvent)
    when (state.panel) {
        Panel.BOX -> BoxSheet(state, onEvent)
        Panel.PLACEMENT -> PlacementSheet(state, onEvent)
        Panel.HELP -> HelpSheet(onEvent)
        Panel.NONE -> Unit
    }
    state.dialog?.let { ConfirmDialog(it, onEvent) }
    if (state.showResult && state.result != null) ResultDialog(state, onEvent)
}

/** Maps an adb debug command onto ordinary events (no separate code path into the ViewModel). */
internal fun debugEvents(cmd: DebugCommand, state: BoardUiState): List<BoardEvent> {
    fun sq(s: String) = Square.fromIccs(s)
    return when (cmd.cmd) {
        "move" -> listOfNotNull(sq(cmd.arg.take(2))?.let { BoardEvent.TapSquare(it) }, sq(cmd.arg.drop(2).take(2))?.let { BoardEvent.TapSquare(it) })
        "tap", "select", "tap_square" -> listOfNotNull(sq(cmd.arg)?.let { BoardEvent.TapSquare(it) })
        "undo" -> listOf(BoardEvent.Undo)
        "redo" -> listOf(BoardEvent.Redo)
        "hint" -> listOf(BoardEvent.Hint)
        "engine" -> if ((cmd.arg == "on") != state.vsEngine) listOf(BoardEvent.ToggleEngine) else emptyList()
        "mode" -> listOf(BoardEvent.SetMode(if (cmd.arg == "table") BoardMode.TABLE else BoardMode.WALL))
        "lang" -> listOf(BoardEvent.SetLanguage(if (cmd.arg == "en") Language.EN else Language.ZH))
        "skin" -> listOf(BoardEvent.SetSkin(Skin.entries.firstOrNull { it.name.equals(cmd.arg, true) } ?: Skin.ROSEWOOD))
        "face" -> listOf(BoardEvent.SetPieceFace(if (cmd.arg == "letter") PieceFace.LETTER else PieceFace.GLYPH))
        "notation" -> listOf(BoardEvent.SetNotation(cmd.arg.toIntOrNull() ?: 0))
        "panel" -> listOf(
            when (cmd.arg) {
                "box" -> BoardEvent.OpenPanel(Panel.BOX)
                "placement" -> BoardEvent.OpenPanel(Panel.PLACEMENT)
                "help" -> BoardEvent.OpenPanel(Panel.HELP)
                else -> BoardEvent.ClosePanel
            }
        )
        "section" -> listOf(
            BoardEvent.SetBoxSection(
                when (cmd.arg) { "new" -> BoxSection.NEW_GAME; "io" -> BoxSection.IMPORT_EXPORT; "settings" -> BoxSection.SETTINGS; else -> BoxSection.LIBRARY }
            )
        )
        "newgame" -> {
            val parts = cmd.arg.split(":")
            val side = when (parts.getOrNull(0)) { "black" -> 1; "random" -> 2; else -> 0 }
            val level = parts.getOrNull(1)?.toIntOrNull() ?: state.draftLevel
            val engineOn = parts.getOrNull(2) != "off"
            listOf(BoardEvent.SetDraftSide(side), BoardEvent.SetDraftLevel(level), BoardEvent.SetDraftEngine(engineOn), BoardEvent.StartNewGame, BoardEvent.ConfirmDialog)
        }
        "review" -> listOf(BoardEvent.EnterReview)
        "setup" -> listOf(BoardEvent.EnterSetup)
        "setup_pick" -> listOf(BoardEvent.SetupPick(cmd.arg.takeIf { it.isNotBlank() }?.let { a ->
            val side = if (a[0].isUpperCase()) Side.RED else Side.BLACK
            PieceType.fromLetter(a[0])?.let { Piece.of(side, it) }
        }))
        "setup_done" -> listOf(BoardEvent.SetupDone)
        "setup_clear" -> listOf(BoardEvent.SetupClear)
        "play_here" -> listOf(BoardEvent.PlayFromHere)
        "resign" -> listOf(BoardEvent.RequestResign, BoardEvent.ConfirmDialog)
        "save" -> listOf(BoardEvent.SaveGame)
        "jump" -> listOf(BoardEvent.JumpToPly(cmd.arg.toIntOrNull() ?: 0))
        "back" -> listOf(BoardEvent.Back)
        "forward" -> listOf(BoardEvent.Forward)
        "start" -> listOf(BoardEvent.ToStart)
        "end" -> listOf(BoardEvent.ToEnd)
        "help_done" -> listOf(BoardEvent.HelpDone)
        "import" -> listOf(BoardEvent.SetImportText(cmd.arg), BoardEvent.Import)
        "export" -> listOf(BoardEvent.Export)
        "scale" -> listOf(BoardEvent.SetBoardScale(cmd.arg.toIntOrNull() ?: 100))
        "tilt" -> listOf(BoardEvent.SetTilt(cmd.arg.toIntOrNull() ?: 0))
        "orient" -> listOf(BoardEvent.SetOrientation(cmd.arg.toIntOrNull() ?: 0))
        "coords" -> listOf(BoardEvent.SetShowCoords(cmd.arg == "on"))
        "dismiss" -> listOf(BoardEvent.DismissDialog, BoardEvent.DismissResult)
        "scroll" -> listOf(BoardEvent.SetScrollOpen(cmd.arg != "off"))
        else -> emptyList()
    }
}

private class BoardViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BoardViewModel(PrefsSettingsRepository(context), FileGameRepository(context), LocalEngine()) as T
    }
}
