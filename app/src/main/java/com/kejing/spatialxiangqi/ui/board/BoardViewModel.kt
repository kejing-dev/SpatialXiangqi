package com.kejing.spatialxiangqi.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kejing.spatialxiangqi.data.repository.GameRepository
import com.kejing.spatialxiangqi.data.repository.SettingsRepository
import com.kejing.spatialxiangqi.domain.engine.Engine
import com.kejing.spatialxiangqi.domain.model.AppSettings
import com.kejing.spatialxiangqi.domain.model.GameMeta
import com.kejing.spatialxiangqi.domain.model.GameRecord
import com.kejing.spatialxiangqi.domain.model.GameTree
import com.kejing.spatialxiangqi.domain.model.Move
import com.kejing.spatialxiangqi.domain.model.Piece
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.PositionStatus
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.Square
import com.kejing.spatialxiangqi.domain.model.toRecord
import com.kejing.spatialxiangqi.domain.model.toTree
import com.kejing.spatialxiangqi.domain.notation.Notation
import com.kejing.spatialxiangqi.domain.notation.NotationStyle
import com.kejing.spatialxiangqi.domain.notation.Pgn
import com.kejing.spatialxiangqi.domain.rules.Rules
import com.kejing.spatialxiangqi.domain.usecase.EngineLevelUseCase
import com.kejing.spatialxiangqi.domain.usecase.LibraryUseCase
import com.kejing.spatialxiangqi.ui.i18n.stringsFor
import com.kejing.spatialxiangqi.ui.navigation.BoxSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

/**
 * Single source of truth for the board. Every input (spatial taps, toolbar, sheets, adb debug
 * commands) arrives as a [BoardEvent]; the 3D scene and the 2D chrome only render [BoardUiState].
 */
class BoardViewModel(
    private val settingsRepository: SettingsRepository,
    private val gameRepository: GameRepository,
    private val engine: Engine,
    private val levelUseCase: EngineLevelUseCase = EngineLevelUseCase(),
    private val libraryUseCase: LibraryUseCase = LibraryUseCase(),
    private val minThinkMillis: Long = 350L,
    private val analysisDelayMillis: Long = 200L,
    private val random: Random = Random.Default,
) : ViewModel() {

    private var tree = GameTree()
    private var setupPosition: Position = Position.initial()
    private var engineJob: Job? = null
    private var analysisJob: Job? = null
    private var gameId: String = newId()

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<BoardUiState> = _state

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s -> _state.update { derive(it.copy(settings = s)) } }
        }
        viewModelScope.launch {
            gameRepository.games.collect { g -> _state.update { it.copy(library = libraryUseCase.filter(g, it.libraryQuery)) } }
        }
    }

    private fun initialState(): BoardUiState {
        val settings = settingsRepository.settings.value
        val firstRun = !settings.firstRunDone
        return derive(
            BoardUiState(
                settings = settings,
                phase = if (firstRun) Phase.HELP else Phase.PLAY,
                panel = if (firstRun) Panel.HELP else Panel.NONE,
                vsEngine = settings.engineOn,
                draftLevel = settings.level,
                draftEngine = settings.engineOn,
            )
        )
    }

    fun onEvent(event: BoardEvent) {
        when (event) {
            is BoardEvent.TapSquare -> tapSquare(event.square)
            BoardEvent.Undo -> undo()
            BoardEvent.Redo -> if (current.phase in listOf(Phase.PLAY, Phase.REVIEW) && !current.engineThinking) { tree.forward(); refresh() }
            BoardEvent.Hint -> hint()
            BoardEvent.ToggleEngine -> toggleEngine()
            is BoardEvent.SetMode -> updateSettings { it.copy(mode = event.mode) }
            is BoardEvent.SetSkin -> updateSettings { it.copy(skin = event.skin) }
            is BoardEvent.SetLanguage -> updateSettings { it.copy(language = event.language) }
            is BoardEvent.SetPieceFace -> updateSettings { it.copy(pieceFace = event.face) }
            is BoardEvent.SetNotation -> updateSettings { it.copy(notation = event.index.coerceIn(0, 2)) }
            is BoardEvent.SetSounds -> updateSettings { it.copy(sounds = event.on) }
            is BoardEvent.SetHaptics -> updateSettings { it.copy(haptics = event.on) }
            is BoardEvent.SetBoardScale -> updateSettings { it.copy(boardScalePercent = event.percent.coerceIn(80, 140)) }
            is BoardEvent.SetOrientation -> updateSettings { it.copy(orientationDeg = ((event.degrees % 360) + 360) % 360) }
            is BoardEvent.SetHeightTrim -> updateSettings { it.copy(heightTrimCm = event.cm.coerceIn(-1, 1)) }
            is BoardEvent.SetTilt -> updateSettings { it.copy(tiltDeg = event.degrees.coerceIn(0, 20)) }
            is BoardEvent.SetShowCoords -> updateSettings { it.copy(showCoordinates = event.on) }
            is BoardEvent.SetReduceMotion -> updateSettings { it.copy(reduceMotion = event.on) }
            is BoardEvent.OpenPanel -> _state.update { it.copy(panel = event.panel, dialog = null) }
            BoardEvent.ClosePanel -> closePanel()
            is BoardEvent.SetBoxSection -> _state.update { it.copy(boxSection = event.section) }
            is BoardEvent.SetDraftSide -> _state.update { it.copy(draftSide = event.index.coerceIn(0, 2)) }
            is BoardEvent.SetDraftLevel -> _state.update { it.copy(draftLevel = levelUseCase.clamp(event.level)) }
            is BoardEvent.SetDraftEngine -> _state.update { it.copy(draftEngine = event.on) }
            BoardEvent.StartNewGame -> requestNewGame()
            BoardEvent.ConfirmDialog -> confirmDialog()
            BoardEvent.DismissDialog -> _state.update { it.copy(dialog = null) }
            BoardEvent.RequestResign -> if (current.phase == Phase.PLAY) _state.update { it.copy(dialog = Dialog.Resign) }
            is BoardEvent.JumpToPly -> jumpToPly(event.ply)
            BoardEvent.ToStart -> navigate { tree.toStart() }
            BoardEvent.Back -> navigate { tree.back() }
            BoardEvent.Forward -> navigate { tree.forward() }
            BoardEvent.ToEnd -> navigate { tree.toEnd() }
            BoardEvent.SaveGame -> saveGame()
            is BoardEvent.RequestDeleteGame -> {
                val title = current.library.firstOrNull { it.id == event.id }?.title ?: ""
                _state.update { it.copy(dialog = Dialog.DeleteGame(event.id, title)) }
            }
            is BoardEvent.OpenGame -> openGame(event.id)
            is BoardEvent.SetLibraryQuery -> _state.update {
                it.copy(libraryQuery = event.query, library = libraryUseCase.filter(gameRepository.games.value, event.query))
            }
            is BoardEvent.SetImportText -> _state.update { it.copy(importText = event.text) }
            BoardEvent.Import -> importText()
            BoardEvent.Export -> exportText()
            BoardEvent.EnterSetup -> enterSetup()
            is BoardEvent.SetupPick -> _state.update { it.copy(setupPiece = event.piece) }
            BoardEvent.SetupClear -> { setupPosition = Position(emptyMap(), current.setupSideToMove); refresh() }
            BoardEvent.SetupInitial -> { setupPosition = Position.initial(); refresh() }
            is BoardEvent.SetupSideToMove -> { setupPosition = setupPosition.copy(sideToMove = event.side); _state.update { it.copy(setupSideToMove = event.side) }; refresh() }
            BoardEvent.SetupDone -> setupDone()
            BoardEvent.PlayFromHere -> playFromHere()
            BoardEvent.EnterReview -> enterReview()
            BoardEvent.Rematch -> startNewGame()
            BoardEvent.HelpDone -> {
                settingsRepository.update { it.copy(firstRunDone = true) }
                _state.update { it.copy(panel = Panel.NONE, phase = if (it.phase == Phase.HELP) Phase.PLAY else it.phase) }
            }
            is BoardEvent.SetComment -> { tree.current.comment = event.text; _state.update { it.copy(comment = event.text, dirty = true) } }
            BoardEvent.DismissResult -> _state.update { it.copy(showResult = false) }
            BoardEvent.ToggleScroll -> _state.update { it.copy(scrollOpen = !it.scrollOpen) }
            is BoardEvent.SetScrollOpen -> _state.update { it.copy(scrollOpen = event.open) }
        }
    }

    // ---------------------------------------------------------------- board interaction

    private val current: BoardUiState get() = _state.value

    private fun tapSquare(square: Square) {
        val s = current
        when (s.phase) {
            Phase.SETUP -> { placeInSetup(square); return }
            Phase.HELP, Phase.RESULT -> return
            Phase.PLAY, Phase.REVIEW -> Unit
        }
        if (s.engineThinking) return
        if (s.phase == Phase.PLAY && !s.isPlayersTurn) { message { it.hintEngineTurn }; return }
        val pos = tree.current.position
        val piece = pos.pieceAt(square)
        val selected = s.selected
        if (selected == null) {
            if (piece == null) return
            if (piece.side != pos.sideToMove) { message { it.illegal["NOT_YOUR_TURN"] ?: "" }; return }
            select(square)
            return
        }
        if (square == selected) { clearSelection(); return }
        if (piece != null && piece.side == pos.sideToMove) { select(square); return }
        val move = Rules.moveOrNull(pos, selected, square)
        if (move == null) {
            val reason = Rules.illegalReason(pos, selected, square)
            if (reason != null) message { it.illegal[reason.name] ?: reason.message }
            return
        }
        playMove(move)
    }

    private fun select(square: Square) {
        val pos = tree.current.position
        val legal = Rules.legalMovesFrom(pos, square)
        _state.update {
            it.copy(
                selected = square,
                legalTargets = legal.map { m -> m.to },
                captureTargets = legal.filter { m -> m.captured != null }.map { m -> m.to },
                hintMove = null,
            )
        }
    }

    private fun clearSelection() {
        _state.update { it.copy(selected = null, legalTargets = emptyList(), captureTargets = emptyList()) }
    }

    private fun playMove(move: Move, byEngine: Boolean = false) {
        tree.play(move)
        val pos = tree.current.position
        var s = derive(current.copy(selected = null, legalTargets = emptyList(), captureTargets = emptyList(), hintMove = null, dirty = true, engineThinking = false))
        val status = Rules.status(pos)
        val result = when {
            s.phase != Phase.PLAY -> null
            status == PositionStatus.CHECKMATE -> GameResult(winner(pos.sideToMove.opponent), ResultReason.CHECKMATE)
            status == PositionStatus.STALEMATE -> GameResult(winner(pos.sideToMove.opponent), ResultReason.STALEMATE)
            tree.positionHistory().count { it.samePlacement(pos) } >= 3 -> GameResult(ResultKind.DRAW, ResultReason.REPETITION)
            pos.halfmoveClock >= 120 -> GameResult(ResultKind.DRAW, ResultReason.MOVE_RULE)
            else -> null
        }
        if (result != null) {
            s = s.copy(phase = Phase.RESULT, result = result, showResult = true)
        } else if (status == PositionStatus.CHECK) {
            message { it.check }
        }
        _state.value = s
        if (s.phase == Phase.PLAY && s.vsEngine && pos.sideToMove != s.playerSide && !byEngine) engineTurn()
        if (s.phase == Phase.REVIEW) restartAnalysis()
    }

    private fun winner(side: Side) = if (side == Side.RED) ResultKind.RED_WINS else ResultKind.BLACK_WINS

    private fun engineTurn() {
        engineJob?.cancel()
        engineJob = viewModelScope.launch {
            _state.update { it.copy(engineThinking = true) }
            val pos = tree.current.position
            val spec = levelUseCase.spec(settingsRepository.settings.value.level)
            val started = System.currentTimeMillis()
            val move = engine.bestMove(pos, spec.level)
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < minThinkMillis) delay(minThinkMillis - elapsed)
            if (tree.current.position != pos || current.phase != Phase.PLAY) {
                _state.update { it.copy(engineThinking = false) }
                return@launch
            }
            if (move == null) { _state.update { it.copy(engineThinking = false) }; return@launch }
            playMove(move, byEngine = true)
        }
    }

    private fun undo() {
        val s = current
        when (s.phase) {
            Phase.PLAY, Phase.RESULT -> {
                engineJob?.cancel()
                if (s.phase == Phase.RESULT) _state.update { it.copy(phase = Phase.PLAY, result = null, showResult = false) }
                if (s.vsEngine) {
                    var n = 0
                    do { if (!tree.back()) break; n++ } while (tree.current.position.sideToMove != s.playerSide && n < 2)
                } else tree.back()
                _state.update { it.copy(engineThinking = false, selected = null, legalTargets = emptyList(), captureTargets = emptyList(), hintMove = null) }
                refresh()
            }
            Phase.REVIEW -> navigate { tree.back() }
            else -> Unit
        }
    }

    private fun hint() {
        val s = current
        if (s.phase !in listOf(Phase.PLAY, Phase.REVIEW) || s.engineThinking) return
        if (s.phase == Phase.PLAY && !s.isPlayersTurn) return
        val pos = tree.current.position
        viewModelScope.launch {
            val move = engine.bestMove(pos, 3) ?: return@launch
            if (tree.current.position == pos) {
                _state.update { it.copy(hintMove = move, selected = null, legalTargets = emptyList(), captureTargets = emptyList()) }
            }
        }
    }

    private fun toggleEngine() {
        val on = !current.vsEngine
        settingsRepository.update { it.copy(engineOn = on) }
        _state.update { it.copy(vsEngine = on, draftEngine = on) }
        if (!on) { engineJob?.cancel(); _state.update { it.copy(engineThinking = false) } }
        else if (current.phase == Phase.PLAY && tree.current.position.sideToMove != current.playerSide) engineTurn()
        message { if (on) it.engine else it.engineOff }
    }

    private fun navigate(step: () -> Unit) {
        val s = current
        if (s.phase !in listOf(Phase.PLAY, Phase.REVIEW, Phase.RESULT) || s.engineThinking) return
        step()
        clearSelection()
        refresh()
        if (s.phase == Phase.REVIEW) restartAnalysis()
    }

    private fun jumpToPly(ply: Int) {
        val s = current
        if (s.phase !in listOf(Phase.PLAY, Phase.REVIEW, Phase.RESULT) || s.engineThinking) return
        if (s.phase == Phase.PLAY && s.vsEngine) { enterReview() }
        val target = lineNodes().firstOrNull { it.ply == ply }
        if (ply <= 0) tree.toStart() else if (target != null) tree.goTo(target) else return
        clearSelection()
        refresh()
        restartAnalysis()
    }

    // ---------------------------------------------------------------- game lifecycle

    private fun requestNewGame() {
        val s = current
        if (s.dirty && s.phase in listOf(Phase.PLAY, Phase.REVIEW) && !tree.isEmpty()) {
            _state.update { it.copy(dialog = Dialog.ReplaceBoard) }
        } else startNewGame()
    }

    private fun startNewGame() {
        engineJob?.cancel(); analysisJob?.cancel()
        val s = current
        tree = GameTree()
        gameId = newId()
        val side = when (s.draftSide) { 0 -> Side.RED; 1 -> Side.BLACK; else -> if (random.nextBoolean()) Side.RED else Side.BLACK }
        settingsRepository.update { it.copy(level = levelUseCase.clamp(s.draftLevel), engineOn = s.draftEngine) }
        _state.update {
            derive(
                it.copy(
                    phase = Phase.PLAY, panel = Panel.NONE, dialog = null, result = null, showResult = false,
                    vsEngine = s.draftEngine, playerSide = side, engineThinking = false, selected = null,
                    legalTargets = emptyList(), captureTargets = emptyList(), hintMove = null, engineLines = emptyList(),
                    gameTitle = "", dirty = false, exportText = "",
                )
            )
        }
        if (s.draftEngine && side == Side.BLACK) engineTurn()
    }

    private fun confirmDialog() {
        when (val d = current.dialog) {
            Dialog.Resign -> {
                val s = current
                val loser = if (s.vsEngine) s.playerSide else tree.current.position.sideToMove
                _state.update { it.copy(dialog = null, phase = Phase.RESULT, showResult = true, engineThinking = false, result = GameResult(winner(loser.opponent), ResultReason.RESIGN)) }
                engineJob?.cancel()
            }
            is Dialog.DeleteGame -> {
                _state.update { it.copy(dialog = null) }
                viewModelScope.launch { gameRepository.delete(d.id) }
            }
            Dialog.ReplaceBoard -> { _state.update { it.copy(dialog = null) }; startNewGame() }
            null -> Unit
        }
    }

    private fun enterReview() {
        engineJob?.cancel()
        _state.update { it.copy(phase = Phase.REVIEW, showResult = false, engineThinking = false, panel = Panel.NONE) }
        clearSelection()
        refresh()
        restartAnalysis()
    }

    private fun playFromHere() {
        val s = current
        if (s.phase != Phase.REVIEW && s.phase != Phase.RESULT) return
        analysisJob?.cancel()
        val side = tree.current.position.sideToMove
        _state.update { it.copy(phase = Phase.PLAY, vsEngine = true, playerSide = side, result = null, showResult = false, engineLines = emptyList(), panel = Panel.NONE) }
        clearSelection()
        refresh()
    }

    private fun closePanel() {
        _state.update { it.copy(panel = Panel.NONE, setupErrors = emptyList()) }
    }

    // ---------------------------------------------------------------- setup (摆子)

    private fun enterSetup() {
        engineJob?.cancel(); analysisJob?.cancel()
        setupPosition = tree.current.position
        _state.update { it.copy(phase = Phase.SETUP, panel = Panel.NONE, showResult = false, engineThinking = false, setupPiece = null, setupSideToMove = setupPosition.sideToMove, setupErrors = emptyList(), engineLines = emptyList(), scrollOpen = true) }
        clearSelection()
        refresh()
    }

    private fun placeInSetup(square: Square) {
        val piece = current.setupPiece
        val map = HashMap(setupPosition.pieces)
        if (piece == null || map[square] == piece) map.remove(square) else map[square] = piece
        setupPosition = setupPosition.copy(pieces = map)
        _state.update { it.copy(setupErrors = emptyList()) }
        refresh()
    }

    private fun setupDone() {
        val pos = setupPosition.copy(sideToMove = current.setupSideToMove)
        val errors = Rules.validateSetup(pos)
        if (errors.isNotEmpty()) { _state.update { it.copy(setupErrors = errors) }; message { it.setupInvalid }; return }
        tree = GameTree(pos)
        gameId = newId()
        _state.update { it.copy(phase = Phase.REVIEW, setupErrors = emptyList(), setupPiece = null, result = null, dirty = true, gameTitle = "", vsEngine = false) }
        refresh()
        restartAnalysis()
    }

    // ---------------------------------------------------------------- library / import / export

    private fun currentRecord(): GameRecord {
        val s = current
        val st = stringsFor(s.settings.language)
        val date = LocalDate.now().toString()
        val red = if (s.vsEngine) (if (s.playerSide == Side.RED) st.me else st.computer) else ""
        val black = if (s.vsEngine) (if (s.playerSide == Side.BLACK) st.me else st.computer) else ""
        val meta = GameMeta(
            title = s.gameTitle.ifBlank { "${st.gameTitleDefault} $date" },
            red = red, black = black, date = date,
            result = when (s.result?.kind) {
                ResultKind.RED_WINS -> GameRecord.RESULT_RED
                ResultKind.BLACK_WINS -> GameRecord.RESULT_BLACK
                ResultKind.DRAW -> GameRecord.RESULT_DRAW
                null -> GameRecord.RESULT_UNKNOWN
            },
            level = if (s.vsEngine) s.settings.level else null,
            playerSide = if (s.vsEngine) s.playerSide else null,
        )
        return tree.toRecord(gameId, meta)
    }

    private fun saveGame() {
        if (tree.isEmpty()) return
        val record = currentRecord()
        viewModelScope.launch {
            gameRepository.save(record)
            _state.update { it.copy(dirty = false, gameTitle = record.title) }
            message { it.saved }
        }
    }

    private fun openGame(id: String) {
        viewModelScope.launch {
            val record = gameRepository.get(id) ?: return@launch
            loadRecord(record, dirty = false)
        }
    }

    private fun loadRecord(record: GameRecord, dirty: Boolean) {
        engineJob?.cancel(); analysisJob?.cancel()
        tree = record.toTree()
        tree.toEnd()
        gameId = record.id
        _state.update {
            it.copy(
                phase = Phase.REVIEW, panel = Panel.NONE, dialog = null, result = null, showResult = false,
                vsEngine = false, engineThinking = false, selected = null, legalTargets = emptyList(), captureTargets = emptyList(),
                hintMove = null, gameTitle = record.title, dirty = dirty, engineLines = emptyList(),
            )
        }
        refresh()
        restartAnalysis()
    }

    private fun importText() {
        val text = current.importText
        if (text.isBlank()) return
        Pgn.import(text, newId())
            .onSuccess { record -> loadRecord(record, dirty = true); _state.update { it.copy(importText = "") }; message { it.importOk } }
            .onFailure { message { it.importFailed } }
    }

    private fun exportText() {
        if (tree.isEmpty()) return
        val text = Pgn.export(currentRecord(), notationStyle())
        _state.update { it.copy(exportText = text) }
        message { it.exportCopied }
    }

    // ---------------------------------------------------------------- analysis

    private fun restartAnalysis() {
        analysisJob?.cancel()
        if (current.phase != Phase.REVIEW) {
            if (current.engineLines.isNotEmpty()) _state.update { it.copy(engineLines = emptyList()) }
            return
        }
        val pos = tree.current.position
        val style = notationStyle()
        analysisJob = viewModelScope.launch {
            delay(analysisDelayMillis)
            val lines = engine.analyse(pos, 3, 600)
            val rows = lines.map { l -> EngineLineRow(l.scoreCp, l.mateIn, l.depth, formatLine(pos, l.moves, style)) }
            if (tree.current.position == pos) _state.update { it.copy(engineLines = rows) }
        }
    }

    private fun formatLine(pos: Position, moves: List<Move>, style: NotationStyle): String {
        var p = pos
        return moves.take(6).joinToString(" ") { m -> val t = Notation.format(p, m, style); p = Rules.apply(p, m); t }
    }

    // ---------------------------------------------------------------- derived state

    private fun notationStyle(settings: AppSettings = current.settings): NotationStyle =
        NotationStyle.entries[settings.notation.coerceIn(0, 2)]

    private fun lineNodes(): List<GameTree.Node> {
        val path = tree.pathTo(tree.current).filter { it.move != null }.sortedBy { it.ply }
        val rest = tree.current.lineFrom().drop(1)
        return path + rest
    }

    private fun refresh() { _state.update { derive(it) } }

    private fun derive(s: BoardUiState): BoardUiState {
        if (s.phase == Phase.SETUP) {
            return s.copy(
                position = setupPosition, lastMove = null, checkedKing = null, moves = emptyList(), currentPly = 0,
                canBack = false, canForward = false, canUndo = false, evalCp = 0, comment = "",
            )
        }
        val node = tree.current
        val pos = node.position
        val style = notationStyle(s.settings)
        val rows = lineNodes().map { n ->
            MoveRow(
                ply = n.ply, moveNumber = n.moveNumber, side = n.move!!.side,
                text = Notation.format(n.parent!!.position, n.move, style),
                isCurrent = n === node, isVariation = !n.isOnMainLine, hasComment = n.comment.isNotBlank(),
            )
        }
        val inCheck = Rules.isInCheck(pos, pos.sideToMove)
        return s.copy(
            position = pos,
            lastMove = node.move,
            checkedKing = if (inCheck) pos.kingSquare(pos.sideToMove) else null,
            moves = rows,
            currentPly = node.ply,
            canBack = node.parent != null,
            canForward = node.children.isNotEmpty(),
            canUndo = node.parent != null && s.phase in listOf(Phase.PLAY, Phase.REVIEW, Phase.RESULT),
            evalCp = engine.evaluate(pos),
            comment = node.comment,
        )
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsRepository.update(transform)

    private fun message(text: (com.kejing.spatialxiangqi.ui.i18n.Strings) -> String) { _messages.tryEmit(UiMessage(text)) }

    private fun newId(): String = UUID.randomUUID().toString()
}
