package com.kejing.spatialxiangqi.ui.board

import com.kejing.spatialxiangqi.domain.model.AppSettings
import com.kejing.spatialxiangqi.domain.model.BoardMode
import com.kejing.spatialxiangqi.domain.model.GameRecord
import com.kejing.spatialxiangqi.domain.model.Language
import com.kejing.spatialxiangqi.domain.model.Move
import com.kejing.spatialxiangqi.domain.model.Piece
import com.kejing.spatialxiangqi.domain.model.PieceFace
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.Skin
import com.kejing.spatialxiangqi.domain.model.Square
import com.kejing.spatialxiangqi.ui.i18n.Strings
import com.kejing.spatialxiangqi.ui.navigation.BoxSection

/** Top-level interaction phase of the board. */
enum class Phase { HELP, PLAY, REVIEW, SETUP, RESULT }

/** Which modal panel is open (at most one). */
enum class Panel { NONE, BOX, PLACEMENT, HELP }

enum class ResultKind { RED_WINS, BLACK_WINS, DRAW }
enum class ResultReason { CHECKMATE, STALEMATE, RESIGN, TIMEOUT, REPETITION, MOVE_RULE }

data class GameResult(val kind: ResultKind, val reason: ResultReason)

/** One ply in the score scroll. */
data class MoveRow(
    val ply: Int,
    val moveNumber: Int,
    val side: Side,
    val text: String,
    val isCurrent: Boolean,
    val isVariation: Boolean,
    val hasComment: Boolean,
)

/** One engine principal variation, pre-formatted in the current notation. */
data class EngineLineRow(val scoreCp: Int, val mateIn: Int?, val depth: Int, val movesText: String)

/** Blocking confirmations. */
sealed interface Dialog {
    data object Resign : Dialog
    data class DeleteGame(val id: String, val title: String) : Dialog
    data object ReplaceBoard : Dialog
}

/** Transient message, resolved against the active language by the UI. */
class UiMessage(val text: (Strings) -> String)

data class BoardUiState(
    val settings: AppSettings = AppSettings(),
    val phase: Phase = Phase.PLAY,
    val position: Position = Position.initial(),
    val selected: Square? = null,
    val legalTargets: List<Square> = emptyList(),
    val captureTargets: List<Square> = emptyList(),
    val lastMove: Move? = null,
    val hintMove: Move? = null,
    val checkedKing: Square? = null,
    val vsEngine: Boolean = true,
    val playerSide: Side = Side.RED,
    val engineThinking: Boolean = false,
    val moves: List<MoveRow> = emptyList(),
    val currentPly: Int = 0,
    val canBack: Boolean = false,
    val canForward: Boolean = false,
    val canUndo: Boolean = false,
    val evalCp: Int = 0,
    val engineLines: List<EngineLineRow> = emptyList(),
    val comment: String = "",
    val panel: Panel = Panel.NONE,
    val boxSection: BoxSection = BoxSection.LIBRARY,
    val dialog: Dialog? = null,
    val result: GameResult? = null,
    val showResult: Boolean = false,
    val library: List<GameRecord> = emptyList(),
    val libraryQuery: String = "",
    val importText: String = "",
    val exportText: String = "",
    val draftSide: Int = 0,
    val draftLevel: Int = 3,
    val draftEngine: Boolean = true,
    val setupPiece: Piece? = null,
    val setupSideToMove: Side = Side.RED,
    val setupErrors: List<String> = emptyList(),
    val gameTitle: String = "",
    val dirty: Boolean = false,
    val scrollOpen: Boolean = true,
) {
    val mode: BoardMode get() = settings.mode
    val skin: Skin get() = settings.skin
    val language: Language get() = settings.language
    val pieceFace: PieceFace get() = settings.pieceFace
    val sideToMove: Side get() = position.sideToMove
    val isPlayersTurn: Boolean get() = !vsEngine || position.sideToMove == playerSide
}

sealed interface BoardEvent {
    data class TapSquare(val square: Square) : BoardEvent
    data object Undo : BoardEvent
    data object Redo : BoardEvent
    data object Hint : BoardEvent
    data object ToggleEngine : BoardEvent
    data class SetMode(val mode: BoardMode) : BoardEvent
    data class SetSkin(val skin: Skin) : BoardEvent
    data class SetLanguage(val language: Language) : BoardEvent
    data class SetPieceFace(val face: PieceFace) : BoardEvent
    data class SetNotation(val index: Int) : BoardEvent
    data class SetSounds(val on: Boolean) : BoardEvent
    data class SetHaptics(val on: Boolean) : BoardEvent
    data class SetBoardScale(val percent: Int) : BoardEvent
    data class SetOrientation(val degrees: Int) : BoardEvent
    data class SetHeightTrim(val cm: Int) : BoardEvent
    data class SetTilt(val degrees: Int) : BoardEvent
    data class SetShowCoords(val on: Boolean) : BoardEvent
    data class SetReduceMotion(val on: Boolean) : BoardEvent
    data class OpenPanel(val panel: Panel) : BoardEvent
    data object ClosePanel : BoardEvent
    data class SetBoxSection(val section: BoxSection) : BoardEvent
    data class SetDraftSide(val index: Int) : BoardEvent
    data class SetDraftLevel(val level: Int) : BoardEvent
    data class SetDraftEngine(val on: Boolean) : BoardEvent
    data object StartNewGame : BoardEvent
    data object ConfirmDialog : BoardEvent
    data object DismissDialog : BoardEvent
    data object RequestResign : BoardEvent
    data class JumpToPly(val ply: Int) : BoardEvent
    data object ToStart : BoardEvent
    data object Back : BoardEvent
    data object Forward : BoardEvent
    data object ToEnd : BoardEvent
    data object SaveGame : BoardEvent
    data class RequestDeleteGame(val id: String) : BoardEvent
    data class OpenGame(val id: String) : BoardEvent
    data class SetLibraryQuery(val query: String) : BoardEvent
    data class SetImportText(val text: String) : BoardEvent
    data object Import : BoardEvent
    data object Export : BoardEvent
    data object EnterSetup : BoardEvent
    data class SetupPick(val piece: Piece?) : BoardEvent
    data object SetupClear : BoardEvent
    data object SetupInitial : BoardEvent
    data class SetupSideToMove(val side: Side) : BoardEvent
    data object SetupDone : BoardEvent
    data object PlayFromHere : BoardEvent
    data object EnterReview : BoardEvent
    data object Rematch : BoardEvent
    data object HelpDone : BoardEvent
    data class SetComment(val text: String) : BoardEvent
    data object DismissResult : BoardEvent
    data object ToggleScroll : BoardEvent
    data class SetScrollOpen(val open: Boolean) : BoardEvent
}
