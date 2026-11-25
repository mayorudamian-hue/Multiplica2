package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

interface LeaderboardRepository {
    fun observeLeaderboard(limit: Long = 50L): Flow<List<LeaderboardEntry>>
    fun observeMyEntry(userId: String): Flow<LeaderboardEntry?>
    suspend fun saveStudentProfile(
        profile: StudentProfileInput,
        candidateScore: Int = 0,
        candidateLevel: Int = 1,
        candidateLinesCleared: Int = 0
    ): Result<Unit>

    suspend fun submitScoreIfHigher(
        profile: StudentProfileInput,
        score: Int,
        level: Int,
        linesCleared: Int
    ): Result<Boolean>
}

class FirestoreLeaderboardRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : LeaderboardRepository {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("El estudiante debe iniciar sesión con Google para acceder al ranking.")
    }

    override fun observeLeaderboard(limit: Long): Flow<List<LeaderboardEntry>> = flow {
        val uid = requireUserId()
        val path = COLLECTION_LEADERBOARD
        emitAll(
            db.collection(COLLECTION_LEADERBOARD)
                .whereGreaterThan("bestScore", 0L)
                .orderBy("bestScore", Query.Direction.DESCENDING)
                .limit(limit)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LeaderboardEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeMyEntry(userId: String): Flow<LeaderboardEntry?> = flow {
        val uid = requireUserId()
        check(uid == userId && userId.isNotBlank()) { "Sesión inválida al observar el perfil del estudiante." }
        val path = "$COLLECTION_LEADERBOARD/$uid"
        emitAll(
            db.collection(COLLECTION_LEADERBOARD)
                .document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            LeaderboardEntry::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun saveStudentProfile(
        profile: StudentProfileInput,
        candidateScore: Int,
        candidateLevel: Int,
        candidateLinesCleared: Int
    ): Result<Unit> {
        var docPath = "$COLLECTION_LEADERBOARD/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            require(profile.isValid) { "Completa tu nombre, curso y escuela dentro de los límites permitidos." }
            val safeScore = candidateScore.coerceIn(0, MAX_SCORE).toLong()
            val safeLevel = candidateLevel.coerceIn(1, MAX_LEVEL).toLong()
            val safeLines = candidateLinesCleared.coerceIn(0, MAX_LINES).toLong()

            val docRef = db.collection(COLLECTION_LEADERBOARD).document(uid)
            docPath = docRef.path
            val existing = docRef.get().await()

            if (!existing.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "studentName" to profile.normalizedName,
                    "course" to profile.normalizedCourse,
                    "school" to profile.normalizedSchool,
                    "bestScore" to safeScore,
                    "bestLevel" to safeLevel,
                    "linesCleared" to safeLines,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val currentBest = (existing.getLong("bestScore") ?: 0L).coerceIn(0L, MAX_SCORE.toLong())
                val currentLevel = (existing.getLong("bestLevel") ?: 1L).coerceIn(1L, MAX_LEVEL.toLong())
                val currentLines = (existing.getLong("linesCleared") ?: 0L).coerceIn(0L, MAX_LINES.toLong())

                val nextBest = maxOf(currentBest, safeScore)
                val nextLevel = if (safeScore > currentBest) safeLevel else currentLevel
                val nextLines = if (safeScore > currentBest) safeLines else currentLines

                val updatePayload = mapOf(
                    "studentName" to profile.normalizedName,
                    "course" to profile.normalizedCourse,
                    "school" to profile.normalizedSchool,
                    "bestScore" to nextBest,
                    "bestLevel" to nextLevel,
                    "linesCleared" to nextLines,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun submitScoreIfHigher(
        profile: StudentProfileInput,
        score: Int,
        level: Int,
        linesCleared: Int
    ): Result<Boolean> {
        if (!profile.isValid || score <= 0) {
            return Result.success(false)
        }
        var docPath = "$COLLECTION_LEADERBOARD/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val safeScore = score.coerceIn(1, MAX_SCORE).toLong()
            val safeLevel = level.coerceIn(1, MAX_LEVEL).toLong()
            val safeLines = linesCleared.coerceIn(0, MAX_LINES).toLong()

            val docRef = db.collection(COLLECTION_LEADERBOARD).document(uid)
            docPath = docRef.path
            val existing = docRef.get().await()

            if (!existing.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "studentName" to profile.normalizedName,
                    "course" to profile.normalizedCourse,
                    "school" to profile.normalizedSchool,
                    "bestScore" to safeScore,
                    "bestLevel" to safeLevel,
                    "linesCleared" to safeLines,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
                Result.success(true)
            } else {
                val currentBest = existing.getLong("bestScore") ?: 0L
                if (safeScore > currentBest) {
                    val updatePayload = mapOf(
                        "studentName" to profile.normalizedName,
                        "course" to profile.normalizedCourse,
                        "school" to profile.normalizedSchool,
                        "bestScore" to safeScore,
                        "bestLevel" to safeLevel,
                        "linesCleared" to safeLines,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.update(updatePayload).await()
                    Result.success(true)
                } else {
                    Result.success(false)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        const val COLLECTION_LEADERBOARD = "leaderboard"
        const val MAX_SCORE = 10_000_000
        const val MAX_LEVEL = 999
        const val MAX_LINES = 100_000
    }
}
