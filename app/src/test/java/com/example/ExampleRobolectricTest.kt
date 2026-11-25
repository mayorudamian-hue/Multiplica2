package com.example

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import com.example.data.LeaderboardEntry
import com.example.data.LeaderboardRepository
import com.example.data.RoundSubmitOutcome
import com.example.data.StudentProfileInput
import com.example.data.UserPreferencesRepository
import com.example.data.VersusMatch
import com.example.data.VersusMatchStatus
import com.example.data.VersusRepository
import com.example.data.VersusRoundResult
import com.example.game.FactrisEngine
import com.example.game.VersusEngine
import com.example.ui.theme.MultiDropTheme
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

private class FakeLeaderboardRepository(
    private val currentUid: String = "student_test_1"
) : LeaderboardRepository {

    private val entriesFlow = MutableStateFlow(
        listOf(
            LeaderboardEntry(
                id = "student_top_1",
                userId = "student_top_1",
                studentName = "Valentina Rojas",
                course = "7° B",
                school = "Liceo Bicentenario",
                bestScore = 4200L,
                bestLevel = 5L,
                linesCleared = 18L
            ),
            LeaderboardEntry(
                id = "student_top_2",
                userId = "student_top_2",
                studentName = "Mateo Silva",
                course = "6° A",
                school = "Escuela Gabriela Mistral",
                bestScore = 3100L,
                bestLevel = 4L,
                linesCleared = 14L
            )
        )
    )

    override fun observeLeaderboard(limit: Long): Flow<List<LeaderboardEntry>> =
        entriesFlow.map { list ->
            list.filter { it.bestScore > 0 }
                .sortedByDescending { it.bestScore }
                .take(limit.toInt())
        }

    override fun observeMyEntry(userId: String): Flow<LeaderboardEntry?> =
        entriesFlow.map { list -> list.firstOrNull { it.userId == userId } }

    override suspend fun saveStudentProfile(
        profile: StudentProfileInput,
        candidateScore: Int,
        candidateLevel: Int,
        candidateLinesCleared: Int
    ): Result<Unit> {
        val list = entriesFlow.value.toMutableList()
        val existingIdx = list.indexOfFirst { it.userId == currentUid }
        val existing = list.getOrNull(existingIdx)
        val bestScore = maxOf(existing?.bestScore ?: 0L, candidateScore.toLong())
        val updated = LeaderboardEntry(
            id = currentUid,
            userId = currentUid,
            studentName = profile.normalizedName,
            course = profile.normalizedCourse,
            school = profile.normalizedSchool,
            bestScore = bestScore,
            bestLevel = maxOf(existing?.bestLevel ?: 1L, candidateLevel.toLong()),
            linesCleared = maxOf(existing?.linesCleared ?: 0L, candidateLinesCleared.toLong())
        )
        if (existingIdx >= 0) {
            list[existingIdx] = updated
        } else {
            list.add(updated)
        }
        entriesFlow.value = list
        return Result.success(Unit)
    }

    override suspend fun submitScoreIfHigher(
        profile: StudentProfileInput,
        score: Int,
        level: Int,
        linesCleared: Int
    ): Result<Boolean> {
        val current = entriesFlow.value.firstOrNull { it.userId == currentUid }
        if (current != null && score <= current.bestScore) return Result.success(false)
        saveStudentProfile(profile, score, level, linesCleared)
        return Result.success(true)
    }
}

private class FakeVersusRepository(
    private val currentUid: String = "student_test_1"
) : VersusRepository {

    private val seed = 424242L
    private val round1Spec = VersusEngine.challengeForRound(seed, 1)

    private val matchesFlow = MutableStateFlow(
        mapOf(
            "MD4K9P" to VersusMatch(
                id = "MD4K9P",
                hostUserId = "rival_uid_2",
                hostName = "Valentina Rojas",
                memberUids = listOf("rival_uid_2"),
                status = VersusMatchStatus.WAITING.wireValue,
                currentRound = 1,
                totalRounds = VersusEngine.TOTAL_ROUNDS,
                hostWins = 0,
                guestWins = 0,
                roundSeed = seed,
                targetArea = round1Spec.targetArea,
                targetWidth = round1Spec.targetWidth,
                targetHeight = round1Spec.targetHeight
            )
        )
    )

    private val roundsFlow = MutableStateFlow<Map<String, List<VersusRoundResult>>>(emptyMap())

    override fun observeOpenMatches(limit: Long): Flow<List<VersusMatch>> =
        matchesFlow.map { map ->
            map.values.filter { it.parsedStatus == VersusMatchStatus.WAITING }.take(limit.toInt())
        }

    override fun observeMatch(matchId: String): Flow<VersusMatch?> =
        matchesFlow.map { map -> map[matchId] }

    override fun observeMatchRounds(matchId: String): Flow<List<VersusRoundResult>> =
        roundsFlow.map { map -> map[matchId].orEmpty().sortedBy { it.roundNumber } }

    override suspend fun createMatch(hostName: String): Result<String> {
        val code = "NEW777"
        val r1 = VersusEngine.challengeForRound(seed, 1)
        val created = VersusMatch(
            id = code,
            hostUserId = currentUid,
            hostName = hostName,
            memberUids = listOf(currentUid),
            status = VersusMatchStatus.WAITING.wireValue,
            currentRound = 1,
            totalRounds = VersusEngine.TOTAL_ROUNDS,
            hostWins = 0,
            guestWins = 0,
            roundSeed = seed,
            targetArea = r1.targetArea,
            targetWidth = r1.targetWidth,
            targetHeight = r1.targetHeight
        )
        matchesFlow.value = matchesFlow.value + (code to created)
        return Result.success(code)
    }

    override suspend fun joinMatch(matchId: String, guestName: String): Result<Unit> {
        val existing = matchesFlow.value[matchId]
            ?: return Result.failure(IllegalStateException("Sala no encontrada"))
        val updated = existing.copy(
            guestUserId = currentUid,
            guestName = guestName,
            memberUids = listOf(existing.hostUserId, currentUid),
            status = VersusMatchStatus.ACTIVE.wireValue
        )
        matchesFlow.value = matchesFlow.value + (matchId to updated)
        return Result.success(Unit)
    }

    override suspend fun submitRoundWin(
        matchId: String,
        roundNumber: Int,
        winnerName: String,
        solveTimeMs: Long,
        attempts: Int
    ): Result<RoundSubmitOutcome> {
        val existing = matchesFlow.value[matchId]
            ?: return Result.success(RoundSubmitOutcome.AlreadyClaimed)
        if (existing.parsedStatus != VersusMatchStatus.ACTIVE || existing.currentRound != roundNumber) {
            return Result.success(RoundSubmitOutcome.AlreadyClaimed)
        }
        val isHost = (currentUid == existing.hostUserId)
        val newHostWins = if (isHost) existing.hostWins + 1 else existing.hostWins
        val newGuestWins = if (!isHost) existing.guestWins + 1 else existing.guestWins
        val finished = newHostWins >= VersusEngine.WINS_TO_CLINCH ||
            newGuestWins >= VersusEngine.WINS_TO_CLINCH ||
            roundNumber >= existing.totalRounds

        val roundResult = VersusRoundResult(
            id = "round_$roundNumber",
            matchId = matchId,
            roundNumber = roundNumber,
            targetArea = existing.targetArea,
            targetWidth = existing.targetWidth,
            targetHeight = existing.targetHeight,
            winnerUserId = currentUid,
            winnerName = winnerName,
            solveTimeMs = solveTimeMs,
            attempts = attempts,
            memberUids = existing.memberUids
        )
        val prevRounds = roundsFlow.value[matchId].orEmpty()
        roundsFlow.value = roundsFlow.value + (matchId to (prevRounds + roundResult))

        return if (finished) {
            val updated = existing.copy(
                hostWins = newHostWins,
                guestWins = newGuestWins,
                status = VersusMatchStatus.FINISHED.wireValue,
                lastRoundWinnerUid = currentUid,
                lastRoundWinnerName = winnerName,
                lastRoundSolveMs = solveTimeMs,
                winnerUserId = currentUid,
                winnerName = winnerName
            )
            matchesFlow.value = matchesFlow.value + (matchId to updated)
            Result.success(RoundSubmitOutcome.WonMatch(winnerName, solveTimeMs))
        } else {
            val nextRound = roundNumber + 1
            val nextSpec = VersusEngine.challengeForRound(existing.roundSeed, nextRound)
            val updated = existing.copy(
                hostWins = newHostWins,
                guestWins = newGuestWins,
                currentRound = nextRound,
                targetArea = nextSpec.targetArea,
                targetWidth = nextSpec.targetWidth,
                targetHeight = nextSpec.targetHeight,
                lastRoundWinnerUid = currentUid,
                lastRoundWinnerName = winnerName,
                lastRoundSolveMs = solveTimeMs
            )
            matchesFlow.value = matchesFlow.value + (matchId to updated)
            Result.success(RoundSubmitOutcome.WonRound(nextRound, solveTimeMs))
        }
    }

    override suspend fun leaveOrCancelMatch(matchId: String): Result<Unit> {
        val existing = matchesFlow.value[matchId] ?: return Result.success(Unit)
        val updated = if (existing.parsedStatus == VersusMatchStatus.WAITING) {
            existing.copy(status = VersusMatchStatus.CANCELLED.wireValue)
        } else {
            existing.copy(
                status = VersusMatchStatus.FINISHED.wireValue,
                winnerUserId = existing.hostUserId,
                winnerName = existing.hostName
            )
        }
        matchesFlow.value = matchesFlow.value + (matchId to updated)
        return Result.success(Unit)
    }

    fun currentMatch(matchId: String): VersusMatch? = matchesFlow.value[matchId]
    fun currentRounds(matchId: String): List<VersusRoundResult> = roundsFlow.value[matchId].orEmpty()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUpFirebaseAndPrefs() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            FirebaseApp.initializeApp(ctx)
        }
        runBlocking {
            UserPreferencesRepository(ctx).updateTutorialCompleted(false)
        }
    }

    private fun launchAuthenticatedApp(
        repo: FakeLeaderboardRepository = FakeLeaderboardRepository(),
        versusRepo: FakeVersusRepository = FakeVersusRepository()
    ) {
        composeRule.setContent {
            MultiDropTheme {
                AuthenticatedMultiDropApp(
                    currentUserId = "student_test_1",
                    defaultDisplayName = "Sofía Martínez",
                    repositoryOverride = repo,
                    versusRepositoryOverride = versusRepo
                )
            }
        }
    }

    private fun readBoardColumn(): Int {
        val stateDesc = composeRule.onNodeWithTag("game_board_canvas")
            .fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]
        return stateDesc.substringAfter("col:").substringBefore(",").toInt()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MultiDrop", appName)
    }

    @Test
    fun `unauthenticated root renders google sign in gate`() {
        composeRule.setContent {
            MultiDropTheme {
                MultiDropRoot()
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("google_id_sign_in_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `home screen launches factris board, refactors piece, and opens pause dialog`() {
        launchAuthenticatedApp()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("game_board_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_area_value").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_dimension_value").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_adaptive_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_accuracy_value").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_factor_range_value").assertIsDisplayed()
        composeRule.onNodeWithTag("control_refactor").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pause_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pause_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("pause_sound_switch").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `horizontal on-screen buttons and swipe gestures move active piece`() {
        launchAuthenticatedApp()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        val startCol = readBoardColumn()

        composeRule.onNodeWithTag("control_left").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(startCol - 1, readBoardColumn())

        composeRule.onNodeWithTag("control_right").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(startCol, readBoardColumn())

        composeRule.onNodeWithTag("game_board_canvas").performTouchInput {
            swipeRight()
        }
        composeRule.waitForIdle()
        val afterSwipeRight = readBoardColumn()
        assertTrue("Swipe right should move piece right", afterSwipeRight > startCol)

        composeRule.onNodeWithTag("game_board_canvas").performTouchInput {
            swipeLeft()
        }
        composeRule.waitForIdle()
        assertTrue("Swipe left should move piece left", readBoardColumn() < afterSwipeRight)
    }

    @Test
    fun `hard drop places block without crashing game loop`() {
        launchAuthenticatedApp()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("control_hard_drop").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("game_board_canvas").assertIsDisplayed()
    }

    @Test
    fun `student can edit profile and view online leaderboard with name, course, and school`() {
        val fakeRepo = FakeLeaderboardRepository()
        launchAuthenticatedApp(fakeRepo)
        composeRule.waitForIdle()

        // Abrir edición de perfil desde Home
        composeRule.onNodeWithTag("edit_profile_home_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("student_profile_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("student_name_input").performTextClearance()
        composeRule.onNodeWithTag("student_name_input").performTextInput("Lucas Contreras")
        composeRule.onNodeWithTag("student_course_input").performTextInput("8° A")
        composeRule.onNodeWithTag("student_school_input").performTextInput("Colegio Los Andes")
        composeRule.onNodeWithTag("save_profile_button").performClick()
        composeRule.waitForIdle()

        // Verificar que la tarjeta del estudiante muestra nombre, curso y escuela
        composeRule.onNodeWithText("Lucas Contreras").assertIsDisplayed()
        composeRule.onNodeWithText("8° A · Colegio Los Andes").assertIsDisplayed()

        // Abrir ranking en línea
        composeRule.onNodeWithTag("leaderboard_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("leaderboard_list").assertIsDisplayed()
        composeRule.onNodeWithText("Valentina Rojas").assertIsDisplayed()
        composeRule.onNodeWithText("7° B").assertIsDisplayed()
        composeRule.onNodeWithText("Liceo Bicentenario").assertIsDisplayed()
    }

    @Test
    fun `first launch runs interactive 3-step adjacent-piece tutorial to completion`() {
        launchAuthenticatedApp()
        composeRule.waitForIdle()

        // Primera partida inicia automáticamente el tutorial interactivo (Paso 1/3)
        composeRule.onNodeWithTag("play_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tutorial_guide_card").assertIsDisplayed()
        composeRule.onNodeWithTag("tutorial_step_badge").assertTextContains("1/3", substring = true)

        // Paso 1: Tocar la píldora 6×2 (index 1) para igualar altura del bloque vecino -> avanza a Paso 2/3
        composeRule.onNodeWithTag("factor_pill_1").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tutorial_step_badge").assertTextContains("2/3", substring = true)

        // Paso 2: Tras centrar 4×3 -> 6×2 en x=3, mover a x=6 y soltar junto al 6×2 izquierdo
        composeRule.onNodeWithTag("control_right").performClick()
        composeRule.onNodeWithTag("control_right").performClick()
        composeRule.onNodeWithTag("control_right").performClick()
        composeRule.waitForIdle()
        assertEquals(6, readBoardColumn())
        composeRule.onNodeWithTag("control_hard_drop").performClick()

        // Esperar animación de desvanecimiento/explosión de filas (420ms) y transición a Paso 3/3
        ShadowLooper.idleMainLooper(600L, TimeUnit.MILLISECONDS)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tutorial_step_badge").assertTextContains("3/3", substring = true)

        // Paso 3: Refactorizar 6 (2×3) a 3×2 (index 1), mover de x=5 a x=4 y encajar entre 8 y 10
        composeRule.onNodeWithTag("factor_pill_1").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("control_left").performClick()
        composeRule.waitForIdle()
        assertEquals(4, readBoardColumn())
        composeRule.onNodeWithTag("control_hard_drop").performClick()

        ShadowLooper.idleMainLooper(600L, TimeUnit.MILLISECONDS)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tutorial_completed_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("tutorial_start_game_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Al iniciar partida tras completar tutorial, ya estamos en partida normal sin banner de tutorial
        assertTrue(
            composeRule.onAllNodesWithTag("tutorial_guide_card").fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithTag("game_board_canvas").assertIsDisplayed()

        // Volver al menú y pulsar Jugar de nuevo -> ya no abre el tutorial automáticamente
        composeRule.onNodeWithTag("pause_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("exit_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play_button").performClick()
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("tutorial_guide_card").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun `skip tutorial button exits tutorial and home button can replay it`() {
        launchAuthenticatedApp()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("play_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tutorial_guide_card").assertIsDisplayed()

        // Saltar tutorial entra directo al juego normal
        composeRule.onNodeWithTag("skip_tutorial_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("tutorial_guide_card").fetchSemanticsNodes().isEmpty()
        )

        // Volver al menú y relanzar tutorial desde HomeScreen
        composeRule.onNodeWithTag("pause_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("exit_menu_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_tutorial_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tutorial_guide_card").assertIsDisplayed()
        composeRule.onNodeWithTag("tutorial_step_badge").assertTextContains("1/3", substring = true)
    }

    @Test
    fun `versus mode allows creating waiting room and cancelling back to lobby`() {
        val fakeVersus = FakeVersusRepository()
        launchAuthenticatedApp(versusRepo = fakeVersus)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("open_versus_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("versus_create_room_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("versus_room_code").assertIsDisplayed()
        composeRule.onNodeWithTag("versus_room_code").assertTextContains("NEW777")

        composeRule.onNodeWithTag("versus_cancel_room_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("versus_create_room_button").assertIsDisplayed()
    }

    @Test
    fun `versus mode joins match, solves shared rounds first, and records results via repository`() {
        val fakeVersus = FakeVersusRepository()
        launchAuthenticatedApp(versusRepo = fakeVersus)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("open_versus_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("versus_open_rooms_list")
            .performScrollToNode(hasTestTag("versus_room_card_MD4K9P"))
        composeRule.onNodeWithTag("versus_join_room_MD4K9P").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Resolver las 3 rondas necesarias para ganar el duelo al mejor de 5
        for (round in 1..3) {
            val match = fakeVersus.currentMatch("MD4K9P")!!
            assertEquals(round, match.currentRound)
            composeRule.onNodeWithTag("versus_scoreboard").assertIsDisplayed()
            composeRule.onNodeWithTag("versus_round_badge").assertTextContains("$round/5", substring = true)

            val expectedBoard = VersusEngine.buildRoundBoard(
                roundSeed = match.roundSeed,
                roundNumber = round,
                targetArea = match.targetArea,
                targetWidth = match.targetWidth,
                targetHeight = match.targetHeight
            )
            val targetIdx = expectedBoard.activePiece.factors.indexOfFirst {
                it.width == match.targetWidth && it.height == match.targetHeight
            }
            assertTrue(targetIdx >= 0)

            composeRule.onNodeWithTag("factor_pill_$targetIdx").performClick()
            composeRule.waitForIdle()

            val currentCol = composeRule.onNodeWithTag("versus_board_canvas")
                .fetchSemanticsNode()
                .config[SemanticsProperties.StateDescription]
                .substringAfter("col:")
                .substringBefore(",")
                .toInt()
            val delta = expectedBoard.gapX - currentCol
            if (delta > 0) {
                repeat(delta) { composeRule.onNodeWithTag("control_right").performClick() }
            } else if (delta < 0) {
                repeat(-delta) { composeRule.onNodeWithTag("control_left").performClick() }
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("control_hard_drop").performClick()
            ShadowLooper.idleMainLooper(600L, TimeUnit.MILLISECONDS)
            composeRule.waitForIdle()
        }

        // Verificar que las 3 rondas quedaron registradas y se muestra la pantalla final del duelo
        assertEquals(3, fakeVersus.currentRounds("MD4K9P").size)
        assertEquals(VersusMatchStatus.FINISHED, fakeVersus.currentMatch("MD4K9P")?.parsedStatus)
        composeRule.onNodeWithTag("versus_result_card").assertIsDisplayed()
        composeRule.onNodeWithTag("versus_rounds_results_list").assertIsDisplayed()
    }
}
