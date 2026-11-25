package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.attemptAutoSignIn
import com.example.auth.signOut
import com.example.data.FirestoreLeaderboardRepository
import com.example.data.FirestoreVersusRepository
import com.example.data.LeaderboardRepository
import com.example.data.VersusRepository
import com.example.game.GameConfig
import com.example.game.GameViewModel
import com.example.game.LeaderboardViewModel
import com.example.game.VersusViewModel
import com.example.ui.components.StudentProfileDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.GameScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.TablesGuideScreen
import com.example.ui.screens.VersusScreen
import com.example.ui.theme.MultiDropTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class AppDestination {
    HOME,
    GAME,
    TABLES_GUIDE,
    LEADERBOARD,
    VERSUS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MultiDropTheme {
                MultiDropRoot()
            }
        }
    }
}

@Composable
fun MultiDropRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var justSignedOut by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                justSignedOut = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null && !justSignedOut) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    justSignedOut = false
                    currentUser = Firebase.auth.currentUser
                },
                onUnauthenticated = {},
                scope = scope,
                allowAccountSelectionSheet = true
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                justSignedOut = false
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val uid = user.uid
        val defaultDisplayName = user.displayName.orEmpty()
        key(uid) {
            AuthenticatedMultiDropApp(
                currentUserId = uid,
                defaultDisplayName = defaultDisplayName,
                onSignOut = {
                    justSignedOut = true
                    signOut(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = { currentUser = null }
                    )
                }
            )
        }
    }
}

@Composable
fun AuthenticatedMultiDropApp(
    currentUserId: String,
    defaultDisplayName: String = "",
    repositoryOverride: LeaderboardRepository? = null,
    versusRepositoryOverride: VersusRepository? = null,
    gameViewModel: GameViewModel = viewModel(),
    onSignOut: () -> Unit = {}
) {
    val leaderboardViewModel: LeaderboardViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val repo = repositoryOverride ?: run {
                    val app = checkNotNull(this[APPLICATION_KEY]) {
                        "APPLICATION_KEY missing from CreationExtras"
                    }
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    FirestoreLeaderboardRepository(db)
                }
                LeaderboardViewModel(repository = repo, currentUserId = currentUserId)
            }
        }
    )

    val versusViewModel: VersusViewModel = viewModel(
        key = "versus_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val repo = versusRepositoryOverride ?: run {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    FirestoreVersusRepository(db)
                }
                VersusViewModel(
                    application = app,
                    repository = repo,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val preferences by gameViewModel.preferences.collectAsStateWithLifecycle()
    val gameState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val leaderboardState by leaderboardViewModel.leaderboardState.collectAsStateWithLifecycle()
    val myEntry by leaderboardViewModel.myEntry.collectAsStateWithLifecycle()
    val isSavingProfile by leaderboardViewModel.isSavingProfile.collectAsStateWithLifecycle()
    val profileError by leaderboardViewModel.profileError.collectAsStateWithLifecycle()
    val onlineScoreSynced by leaderboardViewModel.onlineScoreSynced.collectAsStateWithLifecycle()

    val openRoomsState by versusViewModel.openRoomsState.collectAsStateWithLifecycle()
    val activeVersusMatch by versusViewModel.activeMatch.collectAsStateWithLifecycle()
    val versusRounds by versusViewModel.matchRounds.collectAsStateWithLifecycle()
    val versusPlayState by versusViewModel.playState.collectAsStateWithLifecycle()

    val resolvedPlayerName = myEntry?.studentName?.takeIf { it.isNotBlank() }
        ?: defaultDisplayName.takeIf { it.isNotBlank() }
        ?: "Estudiante"

    LaunchedEffect(resolvedPlayerName) {
        versusViewModel.updatePlayerName(resolvedPlayerName)
    }

    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var pendingRunScore by rememberSaveable { mutableIntStateOf(0) }
    var pendingRunLevel by rememberSaveable { mutableIntStateOf(1) }
    var pendingRunLines by rememberSaveable { mutableIntStateOf(0) }

    when (destination) {
        AppDestination.HOME -> {
            HomeScreen(
                preferences = preferences,
                studentEntry = myEntry,
                onSelectMode = gameViewModel::updateMode,
                onSelectTable = gameViewModel::updateTable,
                onTogglePrimes = gameViewModel::updateIncludePrimes,
                onToggleFactorPills = gameViewModel::updateShowFactorPills,
                onToggleHaptics = gameViewModel::updateHapticsEnabled,
                onToggleSound = gameViewModel::updateSoundEnabled,
                onStartGame = {
                    leaderboardViewModel.resetRunSyncFlag()
                    gameViewModel.startNewGame()
                    destination = AppDestination.GAME
                },
                onStartTutorial = {
                    leaderboardViewModel.resetRunSyncFlag()
                    gameViewModel.startNewGame(forceTutorial = true)
                    destination = AppDestination.GAME
                },
                onOpenTablesGuide = {
                    destination = AppDestination.TABLES_GUIDE
                },
                onOpenLeaderboard = {
                    destination = AppDestination.LEADERBOARD
                },
                onOpenVersus = {
                    destination = AppDestination.VERSUS
                },
                onEditProfile = {
                    pendingRunScore = 0
                    pendingRunLevel = 1
                    pendingRunLines = 0
                    leaderboardViewModel.clearProfileError()
                    showProfileDialog = true
                },
                onSignOut = {
                    gameViewModel.stopGameLoop()
                    onSignOut()
                }
            )
        }

        AppDestination.GAME -> {
            GameScreen(
                state = gameState,
                isStudentProfileComplete = myEntry?.hasCompleteStudentInfo == true,
                onlineScoreSynced = onlineScoreSynced,
                onMoveLeft = gameViewModel::moveLeft,
                onMoveRight = gameViewModel::moveRight,
                onRefactor = gameViewModel::refactorNext,
                onRefactorToIndex = gameViewModel::refactorToIndex,
                onSoftDrop = gameViewModel::softDrop,
                onHardDrop = gameViewModel::hardDrop,
                onTogglePause = gameViewModel::togglePause,
                onRestart = {
                    leaderboardViewModel.resetRunSyncFlag()
                    gameViewModel.startNewGame(
                        configOverride = gameState.config,
                        forceTutorial = gameState.tutorialState.isActive
                    )
                },
                onToggleFactorPills = gameViewModel::toggleFactorPillsInGame,
                onToggleHaptics = gameViewModel::toggleHapticsInGame,
                onToggleSound = gameViewModel::toggleSoundInGame,
                onSkipTutorial = gameViewModel::skipTutorial,
                onFinishTutorial = {
                    leaderboardViewModel.resetRunSyncFlag()
                    gameViewModel.finishTutorialAndPlay()
                },
                onReplayTutorial = gameViewModel::restartTutorial,
                onGameOverSubmit = { score, level, lines ->
                    leaderboardViewModel.submitRunScoreIfProfileReady(
                        score = score,
                        level = level,
                        linesCleared = lines
                    )
                },
                onEditProfileForScore = { score, level, lines ->
                    pendingRunScore = score
                    pendingRunLevel = level
                    pendingRunLines = lines
                    leaderboardViewModel.clearProfileError()
                    showProfileDialog = true
                },
                onOpenLeaderboard = {
                    gameViewModel.stopGameLoop()
                    destination = AppDestination.LEADERBOARD
                },
                onExitToMenu = {
                    gameViewModel.stopGameLoop()
                    destination = AppDestination.HOME
                }
            )
        }

        AppDestination.TABLES_GUIDE -> {
            TablesGuideScreen(
                initialTable = preferences.selectedTable,
                onBack = { destination = AppDestination.HOME },
                onPracticeTable = { table ->
                    gameViewModel.updateTable(table)
                    leaderboardViewModel.resetRunSyncFlag()
                    gameViewModel.startNewGame(
                        configOverride = GameConfig(
                            mode = preferences.gameMode,
                            selectedTable = table,
                            includePrimes = preferences.includePrimes,
                            showFactorPills = preferences.showFactorPills,
                            hapticsEnabled = preferences.hapticsEnabled,
                            soundEnabled = preferences.soundEnabled
                        ),
                        forceTutorial = false
                    )
                    destination = AppDestination.GAME
                }
            )
        }

        AppDestination.LEADERBOARD -> {
            LeaderboardScreen(
                leaderboardState = leaderboardState,
                myEntry = myEntry,
                currentUserId = currentUserId,
                onEditProfile = {
                    pendingRunScore = 0
                    pendingRunLevel = 1
                    pendingRunLines = 0
                    leaderboardViewModel.clearProfileError()
                    showProfileDialog = true
                },
                onBack = { destination = AppDestination.HOME }
            )
        }

        AppDestination.VERSUS -> {
            VersusScreen(
                currentUserId = currentUserId,
                playerDisplayName = resolvedPlayerName,
                openRoomsState = openRoomsState,
                activeMatch = activeVersusMatch,
                matchRounds = versusRounds,
                playState = versusPlayState,
                hapticsEnabled = preferences.hapticsEnabled,
                onCreateRoom = versusViewModel::createAndEnterRoom,
                onJoinRoom = versusViewModel::joinAndEnterRoom,
                onLeaveMatch = {
                    versusViewModel.leaveCurrentMatch()
                },
                onReturnToLobbyAfterMatch = versusViewModel::returnToLobbyAfterMatch,
                onMoveLeft = versusViewModel::moveLeft,
                onMoveRight = versusViewModel::moveRight,
                onRefactor = versusViewModel::refactorNext,
                onRefactorToIndex = versusViewModel::refactorToIndex,
                onSoftDrop = versusViewModel::softDrop,
                onHardDrop = versusViewModel::hardDrop,
                onClearError = versusViewModel::clearError,
                onExitToHome = {
                    versusViewModel.leaveCurrentMatch {
                        destination = AppDestination.HOME
                    }
                }
            )
        }
    }

    if (showProfileDialog) {
        StudentProfileDialog(
            initialName = myEntry?.studentName?.takeIf { it.isNotBlank() } ?: defaultDisplayName,
            initialCourse = myEntry?.course.orEmpty(),
            initialSchool = myEntry?.school.orEmpty(),
            isSaving = isSavingProfile,
            serverError = profileError,
            onDismiss = { showProfileDialog = false },
            onSave = { input ->
                leaderboardViewModel.saveStudentProfile(
                    profile = input,
                    localBestScore = preferences.bestScore,
                    pendingRunScore = pendingRunScore,
                    pendingRunLevel = pendingRunLevel,
                    pendingRunLines = pendingRunLines,
                    onSaved = {
                        pendingRunScore = 0
                        showProfileDialog = false
                    }
                )
            }
        )
    }
}
