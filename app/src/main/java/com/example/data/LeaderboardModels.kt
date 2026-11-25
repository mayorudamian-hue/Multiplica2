package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentName: String = "",
    val course: String = "",
    val school: String = "",
    val bestScore: Long = 0L,
    val bestLevel: Long = 1L,
    val linesCleared: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val hasCompleteStudentInfo: Boolean
        get() = studentName.trim().length in 2..50 &&
            course.trim().length in 1..30 &&
            school.trim().length in 2..80
}

data class StudentProfileInput(
    val studentName: String = "",
    val course: String = "",
    val school: String = ""
) {
    val normalizedName: String get() = studentName.trim()
    val normalizedCourse: String get() = course.trim()
    val normalizedSchool: String get() = school.trim()

    val isValid: Boolean
        get() = normalizedName.length in 2..50 &&
            normalizedCourse.length in 1..30 &&
            normalizedSchool.length in 2..80
}

sealed interface LeaderboardUiState {
    data object Loading : LeaderboardUiState
    data class Success(val entries: List<LeaderboardEntry>) : LeaderboardUiState
    data class Error(val message: String) : LeaderboardUiState
}
