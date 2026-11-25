package com.example.data

import android.content.Context
import com.example.R
import com.example.game.VersusEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

interface VersusRepository {
    fun observeOpenMatches(limit: Long = 20L): Flow<List<VersusMatch>>
    fun observeMatch(matchId: String): Flow<VersusMatch?>
    fun observeMatchRounds(matchId: String): Flow<List<VersusRoundResult>>
    suspend fun createMatch(hostName: String): Result<String>
    suspend fun joinMatch(matchId: String, guestName: String): Result<Unit>
    suspend fun submitRoundWin(
        matchId: String,
        roundNumber: Int,
        winnerName: String,
        solveTimeMs: Long,
        attempts: Int
    ): Result<RoundSubmitOutcome>

    suspend fun leaveOrCancelMatch(matchId: String): Result<Unit>
}

class FirestoreVersusRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : VersusRepository {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Debes iniciar sesión con Google para competir en modo Versus.")
    }

    private fun normalizePlayerName(raw: String): String {
        val cleaned = raw.trim()
        return when {
            cleaned.length < 2 -> "Estudiante"
            cleaned.length > 50 -> cleaned.take(50)
            else -> cleaned
        }
    }

    override fun observeOpenMatches(limit: Long): Flow<List<VersusMatch>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(COLLECTION_VERSUS_MATCHES)
                .whereEqualTo("status", VersusMatchStatus.WAITING.wireValue)
                .limit(limit)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VersusMatch::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, COLLECTION_VERSUS_MATCHES)
                    }
                    throw error
                }
        )
    }

    override fun observeMatch(matchId: String): Flow<VersusMatch?> = flow {
        val uid = requireUserId()
        val cleanId = matchId.trim().uppercase(Locale.US)
        require(cleanId.isNotEmpty()) { "Código de sala inválido." }
        val docPath = "$COLLECTION_VERSUS_MATCHES/$cleanId"
        emitAll(
            db.collection(COLLECTION_VERSUS_MATCHES)
                .document(cleanId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            VersusMatch::class.java,
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
                        handleFirestoreError(exceptionToLog, OperationType.GET, docPath)
                    }
                    throw error
                }
        )
    }

    override fun observeMatchRounds(matchId: String): Flow<List<VersusRoundResult>> = flow {
        val uid = requireUserId()
        val cleanId = matchId.trim().uppercase(Locale.US)
        require(cleanId.isNotEmpty()) { "Código de sala inválido." }
        val roundsPath = "$COLLECTION_VERSUS_MATCHES/$cleanId/$SUBCOLLECTION_ROUNDS"
        emitAll(
            db.collection(COLLECTION_VERSUS_MATCHES)
                .document(cleanId)
                .collection(SUBCOLLECTION_ROUNDS)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VersusRoundResult::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.roundNumber }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, roundsPath)
                    }
                    throw error
                }
        )
    }

    override suspend fun createMatch(hostName: String): Result<String> {
        var docPath = COLLECTION_VERSUS_MATCHES
        return try {
            val uid = requireUserId()
            val safeName = normalizePlayerName(hostName)
            val roomCode = VersusEngine.generateRoomCode()
            val seed = VersusEngine.generateSeed()
            val round1 = VersusEngine.challengeForRound(seed, 1)

            val docRef = db.collection(COLLECTION_VERSUS_MATCHES).document(roomCode)
            docPath = docRef.path

            val payload = mapOf(
                "hostUserId" to uid,
                "hostName" to safeName,
                "memberUids" to listOf(uid),
                "status" to VersusMatchStatus.WAITING.wireValue,
                "currentRound" to 1,
                "totalRounds" to VersusEngine.TOTAL_ROUNDS,
                "hostWins" to 0,
                "guestWins" to 0,
                "roundSeed" to seed,
                "targetArea" to round1.targetArea,
                "targetWidth" to round1.targetWidth,
                "targetHeight" to round1.targetHeight,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(roomCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun joinMatch(matchId: String, guestName: String): Result<Unit> {
        val cleanId = matchId.trim().uppercase(Locale.US)
        if (!ROOM_CODE_REGEX.matches(cleanId)) {
            return Result.failure(IllegalArgumentException("Ingresa un código de sala válido (4 a 24 letras o números)."))
        }
        val docRef = db.collection(COLLECTION_VERSUS_MATCHES).document(cleanId)
        return try {
            val uid = requireUserId()
            val safeGuestName = normalizePlayerName(guestName)

            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) {
                    throw IllegalStateException("No se encontró ninguna sala con el código $cleanId.")
                }
                val match = snap.toObject(VersusMatch::class.java)
                    ?: throw IllegalStateException("Datos de sala inválidos.")

                if (match.hostUserId == uid) {
                    return@runTransaction null
                }
                if (match.parsedStatus != VersusMatchStatus.WAITING) {
                    throw IllegalStateException("La sala $cleanId ya no está esperando jugadores.")
                }

                tx.update(
                    docRef,
                    mapOf(
                        "guestUserId" to uid,
                        "guestName" to safeGuestName,
                        "memberUids" to listOf(match.hostUserId, uid),
                        "status" to VersusMatchStatus.ACTIVE.wireValue,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    override suspend fun submitRoundWin(
        matchId: String,
        roundNumber: Int,
        winnerName: String,
        solveTimeMs: Long,
        attempts: Int
    ): Result<RoundSubmitOutcome> {
        val cleanId = matchId.trim().uppercase(Locale.US)
        val matchRef = db.collection(COLLECTION_VERSUS_MATCHES).document(cleanId)
        val roundRef = matchRef.collection(SUBCOLLECTION_ROUNDS).document("round_$roundNumber")
        return try {
            val uid = requireUserId()
            val safeWinnerName = normalizePlayerName(winnerName)
            val safeSolveMs = solveTimeMs.coerceIn(0L, 3_600_000L)
            val safeAttempts = attempts.coerceIn(1, 500)

            val outcome = db.runTransaction { tx ->
                val matchSnap = tx.get(matchRef)
                if (!matchSnap.exists()) {
                    return@runTransaction RoundSubmitOutcome.AlreadyClaimed
                }
                val match = matchSnap.toObject(VersusMatch::class.java)
                    ?: return@runTransaction RoundSubmitOutcome.AlreadyClaimed

                if (match.parsedStatus != VersusMatchStatus.ACTIVE ||
                    match.currentRound != roundNumber ||
                    uid !in match.memberUids
                ) {
                    return@runTransaction RoundSubmitOutcome.AlreadyClaimed
                }

                val roundSnap = tx.get(roundRef)
                if (roundSnap.exists()) {
                    return@runTransaction RoundSubmitOutcome.AlreadyClaimed
                }

                val isHost = (uid == match.hostUserId)
                val newHostWins = if (isHost) match.hostWins + 1 else match.hostWins
                val newGuestWins = if (!isHost) match.guestWins + 1 else match.guestWins
                val matchFinished = newHostWins >= VersusEngine.WINS_TO_CLINCH ||
                    newGuestWins >= VersusEngine.WINS_TO_CLINCH ||
                    roundNumber >= match.totalRounds

                val roundPayload = mapOf(
                    "matchId" to cleanId,
                    "roundNumber" to roundNumber,
                    "targetArea" to match.targetArea,
                    "targetWidth" to match.targetWidth,
                    "targetHeight" to match.targetHeight,
                    "winnerUserId" to uid,
                    "winnerName" to safeWinnerName,
                    "solveTimeMs" to safeSolveMs,
                    "attempts" to safeAttempts,
                    "memberUids" to match.memberUids,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                tx.set(roundRef, roundPayload)

                val matchUpdates = linkedMapOf<String, Any>(
                    "hostWins" to newHostWins,
                    "guestWins" to newGuestWins,
                    "lastRoundWinnerUid" to uid,
                    "lastRoundWinnerName" to safeWinnerName,
                    "lastRoundSolveMs" to safeSolveMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                if (matchFinished) {
                    val overallWinnerUid = when {
                        newHostWins > newGuestWins -> match.hostUserId
                        newGuestWins > newHostWins -> match.guestUserId ?: uid
                        else -> uid
                    }
                    val overallWinnerName = when (overallWinnerUid) {
                        match.hostUserId -> match.hostName
                        match.guestUserId -> match.guestName ?: safeWinnerName
                        else -> safeWinnerName
                    }
                    matchUpdates["status"] = VersusMatchStatus.FINISHED.wireValue
                    matchUpdates["winnerUserId"] = overallWinnerUid
                    matchUpdates["winnerName"] = overallWinnerName
                    tx.update(matchRef, matchUpdates)
                    RoundSubmitOutcome.WonMatch(overallWinnerName, safeSolveMs)
                } else {
                    val nextRound = roundNumber + 1
                    val nextChallenge = VersusEngine.challengeForRound(match.roundSeed, nextRound)
                    matchUpdates["status"] = VersusMatchStatus.ACTIVE.wireValue
                    matchUpdates["currentRound"] = nextRound
                    matchUpdates["targetArea"] = nextChallenge.targetArea
                    matchUpdates["targetWidth"] = nextChallenge.targetWidth
                    matchUpdates["targetHeight"] = nextChallenge.targetHeight
                    tx.update(matchRef, matchUpdates)
                    RoundSubmitOutcome.WonRound(nextRound, safeSolveMs)
                }
            }.await()

            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, roundRef.path)
            Result.failure(e)
        }
    }

    override suspend fun leaveOrCancelMatch(matchId: String): Result<Unit> {
        val cleanId = matchId.trim().uppercase(Locale.US)
        val matchRef = db.collection(COLLECTION_VERSUS_MATCHES).document(cleanId)
        return try {
            val uid = requireUserId()
            db.runTransaction { tx ->
                val snap = tx.get(matchRef)
                if (!snap.exists()) return@runTransaction null
                val match = snap.toObject(VersusMatch::class.java) ?: return@runTransaction null

                when (match.parsedStatus) {
                    VersusMatchStatus.WAITING -> {
                        if (match.hostUserId == uid) {
                            tx.update(
                                matchRef,
                                mapOf(
                                    "status" to VersusMatchStatus.CANCELLED.wireValue,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                    VersusMatchStatus.ACTIVE -> {
                        if (uid in match.memberUids) {
                            val opponentUid = match.memberUids.firstOrNull { it != uid }
                            val opponentName = when (opponentUid) {
                                match.hostUserId -> match.hostName
                                match.guestUserId -> match.guestName ?: "Rival"
                                else -> "Rival"
                            }
                            if (opponentUid != null) {
                                tx.update(
                                    matchRef,
                                    mapOf(
                                        "status" to VersusMatchStatus.FINISHED.wireValue,
                                        "winnerUserId" to opponentUid,
                                        "winnerName" to opponentName,
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    )
                                )
                            }
                        }
                    }
                    VersusMatchStatus.FINISHED, VersusMatchStatus.CANCELLED -> Unit
                }
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, matchRef.path)
            Result.failure(e)
        }
    }

    companion object {
        const val COLLECTION_VERSUS_MATCHES = "versus_matches"
        const val SUBCOLLECTION_ROUNDS = "rounds"
        private val ROOM_CODE_REGEX = Regex("^[a-zA-Z0-9_\\-]{4,24}$")
    }
}
