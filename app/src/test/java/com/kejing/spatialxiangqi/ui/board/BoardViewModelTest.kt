package com.kejing.spatialxiangqi.ui.board

import com.kejing.spatialxiangqi.data.repository.FakeGameRepository
import com.kejing.spatialxiangqi.data.repository.FakeSettingsRepository
import com.kejing.spatialxiangqi.domain.engine.Engine
import com.kejing.spatialxiangqi.domain.engine.EngineLine
import com.kejing.spatialxiangqi.domain.model.AppSettings
import com.kejing.spatialxiangqi.domain.model.BoardMode
import com.kejing.spatialxiangqi.domain.model.Language
import com.kejing.spatialxiangqi.domain.model.Move
import com.kejing.spatialxiangqi.domain.model.Piece
import com.kejing.spatialxiangqi.domain.model.PieceType
import com.kejing.spatialxiangqi.domain.model.Position
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.Square
import com.kejing.spatialxiangqi.domain.rules.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /** Deterministic engine: always the first legal move, flat evaluation. */
    private class FakeEngine : Engine {
        var calls = 0
        override suspend fun bestMove(pos: Position, level: Int): Move? { calls++; return Rules.allLegalMoves(pos).firstOrNull() }
        override suspend fun analyse(pos: Position, multiPv: Int, timeMs: Long): List<EngineLine> = emptyList()
        override fun evaluate(pos: Position): Int = 0
    }

    private lateinit var settings: FakeSettingsRepository
    private lateinit var games: FakeGameRepository
    private lateinit var engine: FakeEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings = FakeSettingsRepository(AppSettings(firstRunDone = true))
        games = FakeGameRepository()
        engine = FakeEngine()
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun vm() = BoardViewModel(settings, games, engine, minThinkMillis = 0L, analysisDelayMillis = 0L)
    private fun sq(s: String) = requireNotNull(Square.fromIccs(s))

    @Test
    fun tappingOwnPieceSelectsItAndListsLegalTargets() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(BoardEvent.TapSquare(sq("b2")))   // red cannon
        val s = vm.state.value
        assertEquals(sq("b2"), s.selected)
        assertTrue(s.legalTargets.contains(sq("e2")))
        assertTrue(s.captureTargets.contains(sq("b9")))
    }

    @Test
    fun tapTapPlaysMoveAndEngineReplies() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(BoardEvent.TapSquare(sq("b2")))
        vm.onEvent(BoardEvent.TapSquare(sq("e2")))
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(2, s.currentPly)
        assertEquals(Side.RED, s.sideToMove)
        assertEquals(1, engine.calls)
        assertNotNull(s.lastMove)
        assertEquals(2, s.moves.size)
        assertTrue(s.moves[1].isCurrent)
    }

    @Test
    fun undoAgainstEngineTakesBackBothPlies() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(BoardEvent.TapSquare(sq("b2")))
        vm.onEvent(BoardEvent.TapSquare(sq("e2")))
        advanceUntilIdle()
        vm.onEvent(BoardEvent.Undo)
        advanceUntilIdle()
        assertEquals(0, vm.state.value.currentPly)
        assertEquals(Position.initial(), vm.state.value.position)
    }

    @Test
    fun jumpToPlyInReviewChangesPosition() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(BoardEvent.TapSquare(sq("b2"))); vm.onEvent(BoardEvent.TapSquare(sq("e2")))
        advanceUntilIdle()
        vm.onEvent(BoardEvent.EnterReview)
        vm.onEvent(BoardEvent.JumpToPly(1))
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(Phase.REVIEW, s.phase)
        assertEquals(1, s.currentPly)
        assertEquals(Piece.of(Side.RED, PieceType.CANNON), s.position.pieceAt(sq("e2")))
        assertTrue(s.canForward)
    }

    @Test
    fun newGameAsBlackLetsEngineMoveFirst() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(BoardEvent.SetDraftSide(1))
        vm.onEvent(BoardEvent.SetDraftLevel(5))
        vm.onEvent(BoardEvent.StartNewGame)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(Side.BLACK, s.playerSide)
        assertEquals(1, s.currentPly)
        assertEquals(Side.BLACK, s.sideToMove)
        assertEquals(5, settings.settings.value.level)
    }

    @Test
    fun settingsEventsPersistThroughRepository() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(BoardEvent.SetLanguage(Language.EN))
        vm.onEvent(BoardEvent.SetMode(BoardMode.TABLE))
        vm.onEvent(BoardEvent.SetBoardScale(200))
        advanceUntilIdle()
        assertEquals(Language.EN, settings.settings.value.language)
        assertEquals(BoardMode.TABLE, vm.state.value.mode)
        assertEquals(140, vm.state.value.settings.boardScalePercent)
    }

    @Test
    fun setupPositionThenMateEndsGame() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(BoardEvent.EnterSetup)
        vm.onEvent(BoardEvent.SetupClear)
        fun put(piece: Piece, at: String) { vm.onEvent(BoardEvent.SetupPick(piece)); vm.onEvent(BoardEvent.TapSquare(sq(at))) }
        put(Piece.of(Side.BLACK, PieceType.KING), "e9")
        put(Piece.of(Side.RED, PieceType.KING), "d0")
        put(Piece.of(Side.RED, PieceType.ROOK), "a8")
        put(Piece.of(Side.RED, PieceType.ROOK), "b0")
        vm.onEvent(BoardEvent.SetupSideToMove(Side.RED))
        vm.onEvent(BoardEvent.SetupDone)
        advanceUntilIdle()
        assertEquals(Phase.REVIEW, vm.state.value.phase)
        assertTrue(vm.state.value.setupErrors.isEmpty())
        vm.onEvent(BoardEvent.PlayFromHere)
        vm.onEvent(BoardEvent.TapSquare(sq("b0")))
        vm.onEvent(BoardEvent.TapSquare(sq("b9")))
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(Phase.RESULT, s.phase)
        assertEquals(ResultKind.RED_WINS, s.result?.kind)
        assertEquals(ResultReason.CHECKMATE, s.result?.reason)
        assertTrue(s.showResult)
    }

    @Test
    fun saveThenOpenGameFromLibrary() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(BoardEvent.TapSquare(sq("b2"))); vm.onEvent(BoardEvent.TapSquare(sq("e2")))
        advanceUntilIdle()
        vm.onEvent(BoardEvent.SaveGame)
        advanceUntilIdle()
        assertEquals(1, games.games.value.size)
        assertFalse(vm.state.value.dirty)
        val id = games.games.value.first().id
        vm.onEvent(BoardEvent.StartNewGame)
        advanceUntilIdle()
        assertEquals(0, vm.state.value.currentPly)
        vm.onEvent(BoardEvent.OpenGame(id))
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(Phase.REVIEW, s.phase)
        assertEquals(2, s.currentPly)
        assertFalse(s.vsEngine)
    }

    @Test
    fun illegalTargetKeepsSelectionAndResignEndsGame() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(BoardEvent.TapSquare(sq("b2")))
        vm.onEvent(BoardEvent.TapSquare(sq("b3")))   // cannon cannot move onto... it can; use a5 instead
        vm.onEvent(BoardEvent.TapSquare(sq("b2")))
        vm.onEvent(BoardEvent.TapSquare(sq("b2")))
        assertNull(vm.state.value.selected)
        vm.onEvent(BoardEvent.RequestResign)
        assertEquals(Dialog.Resign, vm.state.value.dialog)
        vm.onEvent(BoardEvent.ConfirmDialog)
        advanceUntilIdle()
        assertEquals(Phase.RESULT, vm.state.value.phase)
        assertEquals(ResultKind.BLACK_WINS, vm.state.value.result?.kind)
    }

    @Test
    fun scoreScrollTogglesAndReopensForSetup() = runTest(dispatcher) {
        val vm = vm()
        assertTrue(vm.state.value.scrollOpen)
        vm.onEvent(BoardEvent.ToggleScroll)
        assertFalse(vm.state.value.scrollOpen)
        vm.onEvent(BoardEvent.EnterSetup)
        assertTrue(vm.state.value.scrollOpen)
        vm.onEvent(BoardEvent.SetScrollOpen(false))
        assertFalse(vm.state.value.scrollOpen)
    }
}
