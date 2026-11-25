package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class VersusMatchStatus(val wireValue: String) {
    WAITING("waiting"),
    ACTIVE("active"),
    FINISHED("finished"),
    CANCELLED("cancelled");

    companion object {
        fun fromWire(raw: String?): VersusMatchStatus {
            return entries.firstOrNull { it.wireValue == raw } ?: WAITING
        }
    }
}

@Keep
@IgnoreExtraProperties
data class VersusMatch(
    @DocumentId val id: String = "",
    val hostUserId: String = "",
    val hostName: String = "",
    val guestUserId: String? = null,
    val guestName: String? = null,
    val memberUids: List<String> = emptyList(),
    val status: String = VersusMatchStatus.WAITING.wireValue,
    val currentRound: Int = 1,
    val totalRounds: Int = 5,
    val hostWins: Int = 0,
    val guestWins: Int = 0,
    val roundSeed: Long = 1L,
    val targetArea: Int = 12,
    val targetWidth: Int = 6,
    val targetHeight: Int = 2,
    val lastRoundWinnerUid: String? = null,
    val lastRoundWinnerName: String? = null,
    val lastRoundSolveMs: Long? = null,
    val winnerUserId: String? = null,
    val winnerName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedStatus: VersusMatchStatus get() = VersusMatchStatus.fromWire(status)
    val targetLabel: String get() = "$targetArea ($targetWidth×$targetHeight)"
}

@Keep
@IgnoreExtraProperties
data class VersusRoundResult(
    @DocumentId val id: String = "",
    val matchId: String = "",
    val roundNumber: Int = 1,
    val targetArea: Int = 12,
    val targetWidth: Int = 6,
    val targetHeight: Int = 2,
    val winnerUserId: String = "",
    val winnerName: String = "",
    val solveTimeMs: Long = 0L,
    val attempts: Int = 1,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

sealed interface VersusOpenRoomsUiState {
    data object Loading : VersusOpenRoomsUiState
    data class Success(val rooms: List<VersusMatch>) : VersusOpenRoomsUiState
    data class Error(val message: String) : VersusOpenRoomsUiState
}

sealed interface RoundSubmitOutcome {
    data class WonRound(val nextRound: Int, val solveTimeMs: Long) : RoundSubmitOutcome
    data class WonMatch(val winnerName: String, val solveTimeMs: Long) : RoundSubmitOutcome
    data object AlreadyClaimed : RoundSubmitOutcome
}
