package com.example.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.UserPreferences
import com.example.data.UserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = UserPreferencesRepository(application)

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferences()
    )

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var gravityJob: Job? = null
    private var clearJob: Job? = null
    private var feedbackCounter = 1L

    private val soundPlayer: GameSoundPlayer = SoundPoolGameSoundPlayer(application) {
        _uiState.value.config.soundEnabled
    }

    fun updateMode(mode: GameMode) = viewModelScope.launch { prefsRepo.updateGameMode(mode) }
    fun updateTable(table: Int) = viewModelScope.launch { prefsRepo.updateSelectedTable(table) }
    fun updateIncludePrimes(enabled: Boolean) = viewModelScope.launch { prefsRepo.updateIncludePrimes(enabled) }
    fun updateShowFactorPills(enabled: Boolean) = viewModelScope.launch { prefsRepo.updateShowFactorPills(enabled) }
    fun updateHapticsEnabled(enabled: Boolean) = viewModelScope.launch { prefsRepo.updateHapticsEnabled(enabled) }
    fun updateSoundEnabled(enabled: Boolean) = viewModelScope.launch { prefsRepo.updateSoundEnabled(enabled) }

    fun startNewGame(configOverride: GameConfig? = null, forceTutorial: Boolean? = null) {
        gravityJob?.cancel()
        clearJob?.cancel()

        val prefs = preferences.value
        val config = configOverride ?: GameConfig(
            mode = prefs.gameMode,
            selectedTable = prefs.selectedTable,
            includePrimes = prefs.includePrimes,
            showFactorPills = prefs.showFactorPills,
            hapticsEnabled = prefs.hapticsEnabled,
            soundEnabled = prefs.soundEnabled
        )

        val shouldRunTutorial = forceTutorial ?: !prefs.hasCompletedTutorial
        val initialLevel = 1
        val initialAdaptive = FactrisEngine.computeAdaptiveState(
            recentOutcomes = emptyList(),
            placementsSinceClear = 0,
            level = initialLevel,
            selectedTable = config.selectedTable
        )

        if (shouldRunTutorial) {
            val setup = FactrisEngine.tutorialSetupFor(TutorialStep.REFACTOR_TO_FIT)
            _uiState.value = GameUiState(
                board = setup.board,
                activePiece = setup.activePiece,
                nextPiece = setup.nextPiece,
                ghostY = FactrisEngine.computeGhostY(setup.board, setup.activePiece),
                score = 0,
                bestScore = prefs.bestScore,
                isNewRecord = false,
                level = initialLevel,
                linesCleared = 0,
                blocksPlaced = 0,
                bestMultiClear = 0,
                dropIntervalMs = null,
                isPaused = false,
                isClearingRows = false,
                isGameOver = false,
                config = config,
                adaptiveState = initialAdaptive,
                tutorialState = setup.tutorialState,
                feedback = null
            )
            return
        }

        val emptyBoard = FactrisEngine.createEmptyBoard()
        val firstPiece = FactrisEngine.generatePiece(
            config = config,
            level = initialLevel,
            adaptiveState = initialAdaptive
        )
        val upcomingPiece = FactrisEngine.generatePiece(
            config = config,
            level = initialLevel,
            lastArea = firstPiece.area,
            adaptiveState = initialAdaptive
        )

        _uiState.value = GameUiState(
            board = emptyBoard,
            activePiece = firstPiece,
            nextPiece = upcomingPiece,
            ghostY = FactrisEngine.computeGhostY(emptyBoard, firstPiece),
            score = 0,
            bestScore = prefs.bestScore,
            isNewRecord = false,
            level = initialLevel,
            linesCleared = 0,
            blocksPlaced = 0,
            bestMultiClear = 0,
            dropIntervalMs = FactrisEngine.dropIntervalMs(
                mode = config.mode,
                linesCleared = 0,
                recentSuccessRate = initialAdaptive.recentSuccessRate
            ),
            isPaused = false,
            isClearingRows = false,
            isGameOver = false,
            config = config,
            adaptiveState = initialAdaptive,
            tutorialState = TutorialState(step = TutorialStep.NONE),
            feedback = null
        )

        startGravityLoop()
    }

    fun skipTutorial() {
        viewModelScope.launch { prefsRepo.updateTutorialCompleted(true) }
        startNewGame(configOverride = _uiState.value.config, forceTutorial = false)
    }

    fun finishTutorialAndPlay() {
        viewModelScope.launch { prefsRepo.updateTutorialCompleted(true) }
        startNewGame(configOverride = _uiState.value.config, forceTutorial = false)
    }

    fun restartTutorial() {
        startNewGame(configOverride = _uiState.value.config, forceTutorial = true)
    }

    private fun startGravityLoop() {
        gravityJob?.cancel()
        gravityJob = viewModelScope.launch {
            while (true) {
                val state = _uiState.value
                val interval = state.dropIntervalMs
                if (interval == null || state.isPaused || state.isGameOver || state.isClearingRows) {
                    delay(120L)
                    continue
                }
                delay(interval)
                val latest = _uiState.value
                if (!latest.isPaused && !latest.isGameOver && !latest.isClearingRows && latest.dropIntervalMs != null) {
                    stepDown(fromUser = false)
                }
            }
        }
    }

    fun moveLeft() {
        val state = _uiState.value
        if (!canAcceptInput(state)) return
        val piece = state.activePiece ?: return
        if (FactrisEngine.canPlace(state.board, piece.width, piece.height, piece.x - 1, piece.y)) {
            val moved = piece.copy(x = piece.x - 1)
            soundPlayer.playMove()
            _uiState.update {
                it.copy(
                    activePiece = moved,
                    ghostY = FactrisEngine.computeGhostY(it.board, moved)
                )
            }
        }
    }

    fun moveRight() {
        val state = _uiState.value
        if (!canAcceptInput(state)) return
        val piece = state.activePiece ?: return
        if (FactrisEngine.canPlace(state.board, piece.width, piece.height, piece.x + 1, piece.y)) {
            val moved = piece.copy(x = piece.x + 1)
            soundPlayer.playMove()
            _uiState.update {
                it.copy(
                    activePiece = moved,
                    ghostY = FactrisEngine.computeGhostY(it.board, moved)
                )
            }
        }
    }

    fun refactorNext() {
        val state = _uiState.value
        if (!canAcceptInput(state)) return
        val piece = state.activePiece ?: return
        val refactored = FactrisEngine.refactorNext(state.board, piece) ?: return
        applyRefactoredPiece(piece, refactored)
    }

    fun refactorToIndex(index: Int) {
        val state = _uiState.value
        if (!canAcceptInput(state)) return
        val piece = state.activePiece ?: return
        val refactored = FactrisEngine.refactorToIndex(state.board, piece, index) ?: return
        applyRefactoredPiece(piece, refactored)
    }

    private fun applyRefactoredPiece(previousPiece: ActiveRectPiece, refactored: ActiveRectPiece) {
        if (refactored.factorIndex != previousPiece.factorIndex) {
            soundPlayer.playRotate()
        }
        _uiState.update { current ->
            val updatedTutorial = evaluateTutorialAfterRefactor(current.tutorialState, refactored)
            val tutorialFeedback = if (
                current.tutorialState.isActive &&
                updatedTutorial.targetZone?.isReadyToDrop == true &&
                current.tutorialState.targetZone?.isReadyToDrop != true
            ) {
                when (updatedTutorial.step) {
                    TutorialStep.PLACE_ADJACENT_TO_CLEAR -> GameFeedback(
                        title = "¡6×2 listo!",
                        subtitle = "Muévelo a la derecha y suéltalo",
                        isMultiLine = false,
                        id = feedbackCounter++
                    )
                    TutorialStep.COMBINE_THREE_PIECES -> GameFeedback(
                        title = "¡3×2 listo!",
                        subtitle = "Encájalo entre el 8 y el 10",
                        isMultiLine = false,
                        id = feedbackCounter++
                    )
                    else -> null
                }
            } else {
                null
            }

            current.copy(
                activePiece = refactored,
                ghostY = FactrisEngine.computeGhostY(current.board, refactored),
                tutorialState = updatedTutorial,
                feedback = tutorialFeedback ?: current.feedback
            )
        }
    }

    private fun evaluateTutorialAfterRefactor(
        tutorial: TutorialState,
        piece: ActiveRectPiece
    ): TutorialState {
        if (!tutorial.isActive) return tutorial
        return when (tutorial.step) {
            TutorialStep.REFACTOR_TO_FIT -> {
                if (piece.width == 6 && piece.height == 2) {
                    tutorial.copy(
                        step = TutorialStep.PLACE_ADJACENT_TO_CLEAR,
                        targetZone = tutorial.targetZone?.copy(isReadyToDrop = true)
                    )
                } else {
                    tutorial
                }
            }
            TutorialStep.PLACE_ADJACENT_TO_CLEAR -> {
                tutorial.copy(
                    targetZone = tutorial.targetZone?.copy(
                        isReadyToDrop = (piece.width == 6 && piece.height == 2)
                    )
                )
            }
            TutorialStep.COMBINE_THREE_PIECES -> {
                tutorial.copy(
                    targetZone = tutorial.targetZone?.copy(
                        isReadyToDrop = (piece.width == 3 && piece.height == 2)
                    )
                )
            }
            TutorialStep.NONE, TutorialStep.COMPLETED -> tutorial
        }
    }

    fun softDrop() {
        val state = _uiState.value
        if (!canAcceptInput(state)) return
        stepDown(fromUser = true)
    }

    fun hardDrop() {
        val state = _uiState.value
        if (!canAcceptInput(state)) return
        val piece = state.activePiece ?: return
        val targetY = FactrisEngine.computeGhostY(state.board, piece)
        val distance = (targetY - piece.y).coerceAtLeast(0)
        val dropped = piece.copy(y = targetY)
        val isTutorial = state.tutorialState.isActive
        val dropBonus = if (!isTutorial && state.config.mode == GameMode.ARCADE) distance else 0

        _uiState.update {
            val updatedScore = it.score + dropBonus
            it.copy(
                activePiece = dropped,
                score = updatedScore,
                bestScore = if (isTutorial) it.bestScore else maxOf(it.bestScore, updatedScore),
                isNewRecord = if (isTutorial) false else (it.isNewRecord || (updatedScore > preferences.value.bestScore && preferences.value.bestScore > 0))
            )
        }
        lockAndResolveRows(dropped, isHardDrop = true)
    }

    private fun stepDown(fromUser: Boolean) {
        val state = _uiState.value
        val piece = state.activePiece ?: return
        if (FactrisEngine.canPlace(state.board, piece.width, piece.height, piece.x, piece.y + 1)) {
            val moved = piece.copy(y = piece.y + 1)
            if (fromUser) {
                soundPlayer.playSoftDrop()
            }
            val isTutorial = state.tutorialState.isActive
            _uiState.update {
                val bonus = if (!isTutorial && fromUser && it.config.mode == GameMode.ARCADE) 1 else 0
                val updatedScore = it.score + bonus
                it.copy(
                    activePiece = moved,
                    ghostY = FactrisEngine.computeGhostY(it.board, moved),
                    score = updatedScore,
                    bestScore = if (isTutorial) it.bestScore else maxOf(it.bestScore, updatedScore)
                )
            }
        } else {
            lockAndResolveRows(piece, isHardDrop = false)
        }
    }

    private fun lockAndResolveRows(piece: ActiveRectPiece, isHardDrop: Boolean) {
        clearJob?.cancel()
        soundPlayer.playDrop(isHardDrop = isHardDrop)
        clearJob = viewModelScope.launch {
            val boardBeforeLock = _uiState.value.board
            var workingBoard = FactrisEngine.lockPiece(boardBeforeLock, piece)
            _uiState.update {
                it.copy(
                    board = workingBoard,
                    activePiece = null,
                    ghostY = null,
                    blocksPlaced = it.blocksPlaced + 1,
                    isClearingRows = true
                )
            }

            val fullRows = FactrisEngine.findFullRows(workingBoard)
            if (_uiState.value.tutorialState.isActive) {
                resolveTutorialPlacement(
                    workingBoard = workingBoard,
                    fullRows = fullRows,
                    tutorialStep = _uiState.value.tutorialState.step
                )
                return@launch
            }

            var levelUpFeedback: GameFeedback? = null

            if (fullRows.isNotEmpty()) {
                val rowCount = fullRows.size
                soundPlayer.playRowClear(rowCount)
                val gained = FactrisEngine.calculateFactrisScore(rowCount)
                val highlightedBoard = FactrisEngine.markRowsClearing(workingBoard, fullRows)

                val title = when (rowCount) {
                    1 -> "¡FILA COMPLETA!"
                    2 -> "¡DOBLE FILA!"
                    3 -> "¡TRIPLE FILA!"
                    else -> "¡${rowCount}× FACTORIAL!"
                }

                _uiState.update { state ->
                    val newScore = state.score + gained
                    val newLines = state.linesCleared + rowCount
                    val isRecord = state.isNewRecord || (newScore > preferences.value.bestScore && newScore > 0)
                    state.copy(
                        board = highlightedBoard,
                        score = newScore,
                        bestScore = maxOf(state.bestScore, newScore),
                        isNewRecord = isRecord,
                        linesCleared = newLines,
                        bestMultiClear = maxOf(state.bestMultiClear, rowCount),
                        feedback = GameFeedback(
                            title = title,
                            subtitle = "+$gained pts",
                            isMultiLine = rowCount >= 2,
                            id = feedbackCounter++
                        )
                    )
                }

                delay(420L)
                workingBoard = FactrisEngine.clearRows(workingBoard, fullRows)
            }

            val postState = _uiState.value
            var newLevel = postState.level
            if (postState.config.mode == GameMode.ARCADE) {
                val computedLevel = FactrisEngine.levelForLines(postState.linesCleared)
                if (computedLevel > newLevel) {
                    newLevel = computedLevel
                }
            }

            val previousTier = postState.adaptiveState.tier
            val updatedAdaptive = FactrisEngine.recordPlacementOutcome(
                current = postState.adaptiveState,
                boardBeforeLock = boardBeforeLock,
                boardAfterLock = workingBoard,
                rowsCleared = fullRows.size,
                level = newLevel,
                selectedTable = postState.config.selectedTable
            )
            val updatedInterval = FactrisEngine.dropIntervalMs(
                mode = postState.config.mode,
                linesCleared = postState.linesCleared,
                recentSuccessRate = updatedAdaptive.recentSuccessRate
            )

            if (newLevel > postState.level) {
                val speedText = String.format(Locale.US, "%.1fx", FactrisEngine.speedMultiplier(updatedInterval))
                levelUpFeedback = GameFeedback(
                    title = "¡NIVEL $newLevel!",
                    subtitle = "Ritmo $speedText · Factores ${updatedAdaptive.factorRangeLabel}",
                    isMultiLine = true,
                    id = feedbackCounter++
                )
            } else if (fullRows.isEmpty() && updatedAdaptive.tier != previousTier) {
                levelUpFeedback = when (updatedAdaptive.tier) {
                    AdaptiveDifficultyTier.SUPPORTIVE -> GameFeedback(
                        title = "RITMO DE REFUERZO",
                        subtitle = "Factores ${updatedAdaptive.factorRangeLabel} · Caída más pausada",
                        isMultiLine = false,
                        id = feedbackCounter++
                    )
                    AdaptiveDifficultyTier.CHALLENGE -> GameFeedback(
                        title = "¡MODO DESAFÍO!",
                        subtitle = "Factores ${updatedAdaptive.factorRangeLabel} · Mayor ritmo",
                        isMultiLine = true,
                        id = feedbackCounter++
                    )
                    AdaptiveDifficultyTier.BALANCED -> null
                }
            }

            val spawnPiece = postState.nextPiece ?: FactrisEngine.generatePiece(
                config = postState.config,
                level = newLevel,
                adaptiveState = updatedAdaptive
            )
            val centeredSpawn = spawnPiece.copy(
                x = ((BOARD_COLS - spawnPiece.width) / 2).coerceAtLeast(0),
                y = 0
            )
            val upcoming = FactrisEngine.generatePiece(
                config = postState.config,
                level = newLevel,
                lastArea = centeredSpawn.area,
                adaptiveState = updatedAdaptive
            )

            val canSpawn = FactrisEngine.canPlace(
                workingBoard,
                centeredSpawn.width,
                centeredSpawn.height,
                centeredSpawn.x,
                centeredSpawn.y
            )

            if (!canSpawn) {
                soundPlayer.playGameOver()
                prefsRepo.updateBestScoreIfHigher(postState.score)
                _uiState.update {
                    it.copy(
                        board = workingBoard,
                        activePiece = null,
                        ghostY = null,
                        isClearingRows = false,
                        isGameOver = true,
                        level = newLevel,
                        adaptiveState = updatedAdaptive,
                        dropIntervalMs = updatedInterval
                    )
                }
            } else {
                if (postState.score > preferences.value.bestScore) {
                    prefsRepo.updateBestScoreIfHigher(postState.score)
                }
                _uiState.update {
                    it.copy(
                        board = workingBoard,
                        activePiece = centeredSpawn,
                        nextPiece = upcoming,
                        ghostY = FactrisEngine.computeGhostY(workingBoard, centeredSpawn),
                        level = newLevel,
                        adaptiveState = updatedAdaptive,
                        dropIntervalMs = updatedInterval,
                        isClearingRows = false,
                        feedback = levelUpFeedback ?: it.feedback
                    )
                }
            }
        }
    }

    fun togglePause() {
        val state = _uiState.value
        if (state.isGameOver) return
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    fun toggleFactorPillsInGame() {
        _uiState.update {
            val updated = !it.config.showFactorPills
            viewModelScope.launch { prefsRepo.updateShowFactorPills(updated) }
            it.copy(config = it.config.copy(showFactorPills = updated))
        }
    }

    fun toggleHapticsInGame() {
        _uiState.update {
            val updated = !it.config.hapticsEnabled
            viewModelScope.launch { prefsRepo.updateHapticsEnabled(updated) }
            it.copy(config = it.config.copy(hapticsEnabled = updated))
        }
    }

    fun toggleSoundInGame() {
        _uiState.update {
            val updated = !it.config.soundEnabled
            viewModelScope.launch { prefsRepo.updateSoundEnabled(updated) }
            it.copy(config = it.config.copy(soundEnabled = updated))
        }
    }

    fun stopGameLoop() {
        gravityJob?.cancel()
        clearJob?.cancel()
    }

    private suspend fun resolveTutorialPlacement(
        workingBoard: List<List<BoardCell?>>,
        fullRows: List<Int>,
        tutorialStep: TutorialStep
    ) {
        if (fullRows.isNotEmpty()) {
            val rowCount = fullRows.size
            soundPlayer.playRowClear(rowCount)
            val gained = FactrisEngine.calculateFactrisScore(rowCount)
            val highlightedBoard = FactrisEngine.markRowsClearing(workingBoard, fullRows)

            _uiState.update { state ->
                state.copy(
                    board = highlightedBoard,
                    score = state.score + gained,
                    linesCleared = state.linesCleared + rowCount,
                    bestMultiClear = maxOf(state.bestMultiClear, rowCount),
                    feedback = GameFeedback(
                        title = "¡DOBLE FILA!",
                        subtitle = "+$gained pts · ¡Producto completado!",
                        isMultiLine = true,
                        id = feedbackCounter++
                    )
                )
            }

            delay(420L)
            val clearedBoard = FactrisEngine.clearRows(workingBoard, fullRows)

            when (tutorialStep) {
                TutorialStep.REFACTOR_TO_FIT,
                TutorialStep.PLACE_ADJACENT_TO_CLEAR -> {
                    val step3 = FactrisEngine.tutorialSetupFor(TutorialStep.COMBINE_THREE_PIECES)
                    _uiState.update {
                        it.copy(
                            board = step3.board,
                            activePiece = step3.activePiece,
                            nextPiece = step3.nextPiece,
                            ghostY = FactrisEngine.computeGhostY(step3.board, step3.activePiece),
                            isClearingRows = false,
                            tutorialState = step3.tutorialState,
                            feedback = GameFeedback(
                                title = "¡PASO SUPERADO!",
                                subtitle = "Ahora combina 8 + 6 + 10",
                                isMultiLine = false,
                                id = feedbackCounter++
                            )
                        )
                    }
                }
                TutorialStep.COMBINE_THREE_PIECES -> {
                    prefsRepo.updateTutorialCompleted(true)
                    _uiState.update {
                        it.copy(
                            board = clearedBoard,
                            activePiece = null,
                            nextPiece = null,
                            ghostY = null,
                            isClearingRows = false,
                            tutorialState = TutorialState(step = TutorialStep.COMPLETED)
                        )
                    }
                }
                TutorialStep.NONE, TutorialStep.COMPLETED -> Unit
            }
        } else {
            val retryStep = if (tutorialStep == TutorialStep.COMBINE_THREE_PIECES) {
                TutorialStep.COMBINE_THREE_PIECES
            } else {
                tutorialStep
            }
            val retrySetup = FactrisEngine.tutorialSetupFor(retryStep)
            val hintSubtitle = when (retryStep) {
                TutorialStep.REFACTOR_TO_FIT -> "Cambia el 12 a 6×2 antes de soltar"
                TutorialStep.PLACE_ADJACENT_TO_CLEAR -> "Mueve el 6×2 a la zona marcada"
                TutorialStep.COMBINE_THREE_PIECES -> "Cambia el 6 a 3×2 y encájalo al centro"
                else -> "Inténtalo de nuevo"
            }
            _uiState.update {
                it.copy(
                    board = retrySetup.board,
                    activePiece = retrySetup.activePiece,
                    nextPiece = retrySetup.nextPiece,
                    ghostY = FactrisEngine.computeGhostY(retrySetup.board, retrySetup.activePiece),
                    isClearingRows = false,
                    tutorialState = retrySetup.tutorialState,
                    feedback = GameFeedback(
                        title = "¡CASI!",
                        subtitle = hintSubtitle,
                        isMultiLine = false,
                        id = feedbackCounter++
                    )
                )
            }
        }
    }

    private fun canAcceptInput(state: GameUiState): Boolean {
        return !state.isPaused &&
            !state.isGameOver &&
            !state.isClearingRows &&
            state.tutorialState.step != TutorialStep.COMPLETED &&
            state.activePiece != null
    }

    override fun onCleared() {
        super.onCleared()
        gravityJob?.cancel()
        clearJob?.cancel()
        soundPlayer.release()
    }
}
