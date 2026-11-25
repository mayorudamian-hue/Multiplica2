package com.example.game

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LeaderboardEntry
import com.example.data.LeaderboardRepository
import com.example.data.LeaderboardUiState
import com.example.data.StudentProfileInput
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LeaderboardViewModel(
    private val repository: LeaderboardRepository,
    private val currentUserId: String
) : ViewModel() {

    val leaderboardState: StateFlow<LeaderboardUiState> = repository.observeLeaderboard(limit = 50L)
        .map<List<LeaderboardEntry>, LeaderboardUiState> { LeaderboardUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing leaderboard", rootError)
            emit(LeaderboardUiState.Error(rootError.message ?: "No se pudo cargar el ranking en línea"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LeaderboardUiState.Loading
        )

    val myEntry: StateFlow<LeaderboardEntry?> = repository.observeMyEntry(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing student entry", rootError)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    private val _isSavingProfile = MutableStateFlow(false)
    val isSavingProfile: StateFlow<Boolean> = _isSavingProfile.asStateFlow()

    private val _profileError = MutableStateFlow<String?>(null)
    val profileError: StateFlow<String?> = _profileError.asStateFlow()

    private val _onlineScoreSynced = MutableStateFlow(false)
    val onlineScoreSynced: StateFlow<Boolean> = _onlineScoreSynced.asStateFlow()

    fun saveStudentProfile(
        profile: StudentProfileInput,
        localBestScore: Int,
        pendingRunScore: Int = 0,
        pendingRunLevel: Int = 1,
        pendingRunLines: Int = 0,
        onSaved: () -> Unit = {}
    ) {
        if (!profile.isValid) {
            _profileError.value = "Verifica que nombre, curso y escuela tengan la longitud requerida."
            return
        }
        viewModelScope.launch {
            _isSavingProfile.value = true
            _profileError.value = null
            val bestCandidate = maxOf(localBestScore, pendingRunScore)
            val result = repository.saveStudentProfile(
                profile = profile,
                candidateScore = bestCandidate,
                candidateLevel = pendingRunLevel.coerceAtLeast(1),
                candidateLinesCleared = pendingRunLines.coerceAtLeast(0)
            )
            _isSavingProfile.value = false
            result.fold(
                onSuccess = {
                    if (pendingRunScore > 0) {
                        _onlineScoreSynced.value = true
                    }
                    onSaved()
                },
                onFailure = { error ->
                    _profileError.value = error.localizedMessage ?: "No se pudieron guardar tus datos en línea."
                }
            )
        }
    }

    fun submitRunScoreIfProfileReady(
        score: Int,
        level: Int,
        linesCleared: Int
    ) {
        if (score <= 0) return
        val current = myEntry.value ?: return
        if (!current.hasCompleteStudentInfo) return
        val profile = StudentProfileInput(
            studentName = current.studentName,
            course = current.course,
            school = current.school
        )
        viewModelScope.launch {
            val result = repository.submitScoreIfHigher(
                profile = profile,
                score = score,
                level = level,
                linesCleared = linesCleared
            )
            result.onSuccess { updated ->
                if (updated || score <= current.bestScore) {
                    _onlineScoreSynced.value = true
                }
            }
        }
    }

    fun resetRunSyncFlag() {
        _onlineScoreSynced.value = false
    }

    fun clearProfileError() {
        _profileError.value = null
    }

    private companion object {
        const val TAG = "LeaderboardVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
