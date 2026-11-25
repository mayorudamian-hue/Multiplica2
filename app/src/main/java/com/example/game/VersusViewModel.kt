package com.example.game

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RoundSubmitOutcome
import com.example.data.UserPreferences
import com.example.data.UserPreferencesRepository
import com.example.data.VersusMatch
import com.example.data.VersusMatchStatus
import com.example.data.VersusOpenRoomsUiState
import com.example.data.VersusRepository
import com.example.data.VersusRoundResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VersusPlayState(
    val loadedMatchId: String? = null,
    val loadedRoundNumber: Int = 0,
    val board: List<List<BoardCell?>> = FactrisEngine.createEmptyBoard(),
    val activePiece: ActiveRectPiece? = null,
    val ghostY: Int? = null,
    val gapX: Int = 0,
    val gapY: Int = 0,
    val targetFactorIndex: Int? = null,
    val attempts: Int = 1,
    val roundStartElapsedMs: Long = 0L,
    val isClearingRound: Boolean = false,
    val isSubmittingRound: Boolean = false,
    val feedback: GameFeedback? = null,
    val roundBanner: String? = null,
    val actionLoading: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class VersusViewModel(
    application: Application,
    private val repository: VersusRepository,
    val currentUserId: String
) : AndroidViewModel(application) {

    private val prefsRepo = UserPreferencesRepository(application)

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferences()
    )

    private val _activeMatchId = MutableStateFlow<String?>(null)

    private val _playState = MutableStateFlow(VersusPlayState())
    val playState: StateFlow<VersusPlayState> = _playState.asStateFlow()

    val openRoomsState: StateFlow<VersusOpenRoomsUiState> = repository
        .observeOpenMatches(limit = 20L)
        .map<List<VersusMatch>, VersusOpenRoomsUiState> { rooms ->
            VersusOpenRoomsUiState.Success(rooms)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(
                VersusOpenRoomsUiState.Error(
                    firestoreError?.localizedMessage
                        ?: error.localizedMessage
                        ?: "No se pudieron cargar las salas abiertas."
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VersusOpenRoomsUiState.Loading
        )

    val activeMatch: StateFlow<VersusMatch?> = _activeMatchId
        .flatMapLatest { matchId ->
            if (matchId.isNullOrBlank()) {
                flowOf(null)
            } else {
                repository.observeMatch(matchId).catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    _playState.update {
                        it.copy(
                            errorMessage = firestoreError?.localizedMessage
                                ?: error.localizedMessage
                                ?: "Error sincronizando la partida."
                        )
                    }
                    emit(null)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val matchRounds: StateFlow<List<VersusRoundResult>> = _activeMatchId
        .flatMapLatest { matchId ->
            if (matchId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeMatchRounds(matchId).catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(emptyList())
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var roundResolveJob: Job? = null
    private var gravityJob: Job? = null
    private var feedbackCounter = 1L
    private var currentPlayerDisplayName = "Estudiante"

    private val soundPlayer: GameSoundPlayer = SoundPoolGameSoundPlayer(application) {
        preferences.value.soundEnabled
    }

    init {
        viewModelScope.launch {
            activeMatch.collectLatest { match ->
                if (match == null) {
                    gravityJob?.cancel()
                    return@collectLatest
                }
                when (match.parsedStatus) {
                    VersusMatchStatus.ACTIVE -> {
                        val currentPlay = _playState.value
                        if (
                            currentPlay.loadedMatchId != match.id ||
                            currentPlay.loadedRoundNumber != match.currentRound
                        ) {
                            setupRoundForMatch(match)
                        }
                    }
                    VersusMatchStatus.WAITING,
                    VersusMatchStatus.FINISHED,
                    VersusMatchStatus.CANCELLED -> {
                        gravityJob?.cancel()
                    }
                }
            }
        }
    }

    private fun setupRoundForMatch(match: VersusMatch) {
        roundResolveJob?.cancel()
        val roundBoard = VersusEngine.buildRoundBoard(
            roundSeed = match.roundSeed,
            roundNumber = match.currentRound,
            targetArea = match.targetArea,
            targetWidth = match.targetWidth,
            targetHeight = match.targetHeight
        )
        val piece = roundBoard.activePiece
        val targetIdx = piece.factors.indexOfFirst {
            it.width == match.targetWidth && it.height == match.targetHeight
        }.takeIf { it >= 0 }

        val banner = if (match.currentRound > 1 && !match.lastRoundWinnerName.isNullOrBlank()) {
            val prevRound = (match.currentRound - 1).coerceAtLeast(1)
            val secs = (match.lastRoundSolveMs ?: 0L) / 1000.0
            String.format(
                java.util.Locale.US,
                "Ronda %d: ganó %s (%.1fs)",
                prevRound,
                match.lastRoundWinnerName,
                secs
            )
        } else {
            null
        }

        _playState.update {
            it.copy(
                loadedMatchId = match.id,
                loadedRoundNumber = match.currentRound,
                board = roundBoard.board,
                activePiece = piece,
                ghostY = FactrisEngine.computeGhostY(roundBoard.board, piece),
                gapX = roundBoard.gapX,
                gapY = roundBoard.gapY,
                targetFactorIndex = targetIdx,
                attempts = 1,
                roundStartElapsedMs = SystemClock.elapsedRealtime(),
                isClearingRound = false,
                isSubmittingRound = false,
                roundBanner = banner,
                errorMessage = null
            )
        }
        startGentleGravity()
    }

    private fun startGentleGravity() {
        gravityJob?.cancel()
        gravityJob = viewModelScope.launch {
            while (true) {
                delay(1800L)
                val match = activeMatch.value
                val play = _playState.value
                if (
                    match?.parsedStatus == VersusMatchStatus.ACTIVE &&
                    !play.isClearingRound &&
                    !play.isSubmittingRound &&
                    play.activePiece != null
                ) {
                    stepDown()
                }
            }
        }
    }

    fun updatePlayerName(name: String) {
        val trimmed = name.trim()
        if (trimmed.length >= 2) {
            currentPlayerDisplayName = trimmed.take(50)
        }
    }

    fun clearError() {
        _playState.update { it.copy(errorMessage = null) }
    }

    fun createAndEnterRoom(playerName: String) {
        updatePlayerName(playerName)
        if (_playState.value.actionLoading) return
        viewModelScope.launch {
            _playState.update { it.copy(actionLoading = true, errorMessage = null) }
            repository.createMatch(currentPlayerDisplayName)
                .onSuccess { matchId ->
                    _activeMatchId.value = matchId
                    _playState.update {
                        it.copy(actionLoading = false, errorMessage = null)
                    }
                }
                .onFailure { error ->
                    _playState.update {
                        it.copy(
                            actionLoading = false,
                            errorMessage = error.localizedMessage ?: "No se pudo crear la sala."
                        )
                    }
                }
        }
    }

    fun joinAndEnterRoom(roomCode: String, playerName: String) {
        updatePlayerName(playerName)
        val cleanCode = roomCode.trim().uppercase(java.util.Locale.US)
        if (cleanCode.isEmpty()) {
            _playState.update { it.copy(errorMessage = "Ingresa un código de sala.") }
            return
        }
        if (_playState.value.actionLoading) return
        viewModelScope.launch {
            _playState.update { it.copy(actionLoading = true, errorMessage = null) }
            repository.joinMatch(cleanCode, currentPlayerDisplayName)
                .onSuccess {
                    _activeMatchId.value = cleanCode
                    _playState.update {
                        it.copy(actionLoading = false, errorMessage = null)
                    }
                }
                .onFailure { error ->
                    _playState.update {
                        it.copy(
                            actionLoading = false,
                            errorMessage = error.localizedMessage ?: "No se pudo unir a la sala."
                        )
                    }
                }
        }
    }

    fun leaveCurrentMatch(onDone: () -> Unit = {}) {
        val matchId = _activeMatchId.value
        val status = activeMatch.value?.parsedStatus
        roundResolveJob?.cancel()
        gravityJob?.cancel()
        if (matchId.isNullOrBlank() || status == VersusMatchStatus.FINISHED || status == VersusMatchStatus.CANCELLED) {
            resetToLobby()
            onDone()
            return
        }
        viewModelScope.launch {
            _playState.update { it.copy(actionLoading = true) }
            repository.leaveOrCancelMatch(matchId)
            resetToLobby()
            onDone()
        }
    }

    fun returnToLobbyAfterMatch() {
        roundResolveJob?.cancel()
        gravityJob?.cancel()
        resetToLobby()
    }

    private fun resetToLobby() {
        _activeMatchId.value = null
        _playState.value = VersusPlayState()
    }

    fun moveLeft() {
        val play = _playState.value
        if (!canAcceptInput(play)) return
        val piece = play.activePiece ?: return
        if (FactrisEngine.canPlace(play.board, piece.width, piece.height, piece.x - 1, piece.y)) {
            val moved = piece.copy(x = piece.x - 1)
            soundPlayer.playMove()
            _playState.update {
                it.copy(
                    activePiece = moved,
                    ghostY = FactrisEngine.computeGhostY(it.board, moved)
                )
            }
        }
    }

    fun moveRight() {
        val play = _playState.value
        if (!canAcceptInput(play)) return
        val piece = play.activePiece ?: return
        if (FactrisEngine.canPlace(play.board, piece.width, piece.height, piece.x + 1, piece.y)) {
            val moved = piece.copy(x = piece.x + 1)
            soundPlayer.playMove()
            _playState.update {
                it.copy(
                    activePiece = moved,
                    ghostY = FactrisEngine.computeGhostY(it.board, moved)
                )
            }
        }
    }

    fun refactorNext() {
        val play = _playState.value
        if (!canAcceptInput(play)) return
        val piece = play.activePiece ?: return
        val refactored = FactrisEngine.refactorNext(play.board, piece) ?: return
        applyRefactored(piece, refactored)
    }

    fun refactorToIndex(index: Int) {
        val play = _playState.value
        if (!canAcceptInput(play)) return
        val piece = play.activePiece ?: return
        val refactored = FactrisEngine.refactorToIndex(play.board, piece, index) ?: return
        applyRefactored(piece, refactored)
    }

    private fun applyRefactored(previous: ActiveRectPiece, refactored: ActiveRectPiece) {
        if (refactored.factorIndex != previous.factorIndex) {
            soundPlayer.playRotate()
        }
        _playState.update {
            it.copy(
                activePiece = refactored,
                ghostY = FactrisEngine.computeGhostY(it.board, refactored)
            )
        }
    }

    fun softDrop() {
        val play = _playState.value
        if (!canAcceptInput(play)) return
        soundPlayer.playSoftDrop()
        stepDown()
    }

    fun hardDrop() {
        val play = _playState.value
        if (!canAcceptInput(play)) return
        val piece = play.activePiece ?: return
        val targetY = FactrisEngine.computeGhostY(play.board, piece)
        val dropped = piece.copy(y = targetY)
        resolveDroppedPiece(dropped, isHardDrop = true)
    }

    private fun stepDown() {
        val play = _playState.value
        val piece = play.activePiece ?: return
        if (FactrisEngine.canPlace(play.board, piece.width, piece.height, piece.x, piece.y + 1)) {
            val moved = piece.copy(y = piece.y + 1)
            _playState.update {
                it.copy(
                    activePiece = moved,
                    ghostY = FactrisEngine.computeGhostY(it.board, moved)
                )
            }
        } else {
            resolveDroppedPiece(piece, isHardDrop = false)
        }
    }

    private fun resolveDroppedPiece(piece: ActiveRectPiece, isHardDrop: Boolean) {
        val match = activeMatch.value ?: return
        if (match.parsedStatus != VersusMatchStatus.ACTIVE) return

        roundResolveJob?.cancel()
        soundPlayer.playDrop(isHardDrop = isHardDrop)
        roundResolveJob = viewModelScope.launch {
            val currentPlay = _playState.value
            val workingBoard = FactrisEngine.lockPiece(currentPlay.board, piece)
            val fullRows = FactrisEngine.findFullRows(workingBoard)

            if (fullRows.size == match.targetHeight) {
                soundPlayer.playRowClear(fullRows.size)
                val highlighted = FactrisEngine.markRowsClearing(workingBoard, fullRows)
                val solveTimeMs = (SystemClock.elapsedRealtime() - currentPlay.roundStartElapsedMs)
                    .coerceAtLeast(100L)
                _playState.update {
                    it.copy(
                        board = highlighted,
                        activePiece = null,
                        ghostY = null,
                        isClearingRound = true,
                        isSubmittingRound = true,
                        feedback = GameFeedback(
                            title = "¡OBJETIVO RESUELTO!",
                            subtitle = "${match.targetWidth}×${match.targetHeight} = ${match.targetArea}",
                            isMultiLine = true,
                            id = feedbackCounter++
                        )
                    )
                }

                delay(420L)

                val result = repository.submitRoundWin(
                    matchId = match.id,
                    roundNumber = match.currentRound,
                    winnerName = currentPlayerDisplayName,
                    solveTimeMs = solveTimeMs,
                    attempts = currentPlay.attempts
                )

                result
                    .onSuccess { outcome ->
                        val bannerText = when (outcome) {
                            is RoundSubmitOutcome.WonRound ->
                                "¡Ganaste la ronda ${match.currentRound}! Pasando a ronda ${outcome.nextRound}..."
                            is RoundSubmitOutcome.WonMatch ->
                                "¡Victoria en el duelo Versus!"
                            RoundSubmitOutcome.AlreadyClaimed ->
                                "Tu rival completó esa ronda primero."
                        }
                        _playState.update {
                            it.copy(
                                isClearingRound = false,
                                isSubmittingRound = false,
                                roundBanner = bannerText
                            )
                        }
                    }
                    .onFailure { error ->
                        _playState.update {
                            it.copy(
                                isClearingRound = false,
                                isSubmittingRound = false,
                                errorMessage = error.localizedMessage
                                    ?: "No se pudo registrar el resultado de la ronda."
                            )
                        }
                    }
            } else {
                val resetRound = VersusEngine.buildRoundBoard(
                    roundSeed = match.roundSeed,
                    roundNumber = match.currentRound,
                    targetArea = match.targetArea,
                    targetWidth = match.targetWidth,
                    targetHeight = match.targetHeight
                )
                _playState.update {
                    it.copy(
                        board = resetRound.board,
                        activePiece = resetRound.activePiece,
                        ghostY = FactrisEngine.computeGhostY(resetRound.board, resetRound.activePiece),
                        attempts = it.attempts + 1,
                        isClearingRound = false,
                        isSubmittingRound = false,
                        feedback = GameFeedback(
                            title = "¡CASI! (${match.targetWidth}×${match.targetHeight})",
                            subtitle = "Ajusta factores y encaja en la zona marcada",
                            isMultiLine = false,
                            id = feedbackCounter++
                        )
                    )
                }
            }
        }
    }

    private fun canAcceptInput(play: VersusPlayState): Boolean {
        val status = activeMatch.value?.parsedStatus
        return status == VersusMatchStatus.ACTIVE &&
            !play.isClearingRound &&
            !play.isSubmittingRound &&
            play.activePiece != null
    }

    override fun onCleared() {
        super.onCleared()
        roundResolveJob?.cancel()
        gravityJob?.cancel()
        soundPlayer.release()
    }
}
