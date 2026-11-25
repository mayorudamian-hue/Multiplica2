package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.game.ActiveRectPiece
import com.example.game.AdaptiveDifficultyState
import com.example.game.AdaptiveDifficultyTier
import com.example.game.FactrisEngine
import com.example.game.GameMode
import com.example.game.GameUiState
import com.example.game.TutorialState
import com.example.game.TutorialStep
import java.util.Locale
import com.example.ui.components.GameBoardCanvas
import com.example.ui.components.GameControlsBar
import com.example.ui.components.NextPiecePreview
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CoralRose
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun GameScreen(
    state: GameUiState,
    isStudentProfileComplete: Boolean,
    onlineScoreSynced: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRefactor: () -> Unit,
    onRefactorToIndex: (Int) -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
    onTogglePause: () -> Unit,
    onRestart: () -> Unit,
    onToggleFactorPills: () -> Unit,
    onToggleHaptics: () -> Unit,
    onToggleSound: () -> Unit,
    onSkipTutorial: () -> Unit,
    onFinishTutorial: () -> Unit,
    onReplayTutorial: () -> Unit,
    onGameOverSubmit: (score: Int, level: Int, linesCleared: Int) -> Unit,
    onEditProfileForScore: (score: Int, level: Int, linesCleared: Int) -> Unit,
    onOpenLeaderboard: () -> Unit,
    onExitToMenu: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(state.isGameOver) {
        if (state.isGameOver && state.score > 0 && isStudentProfileComplete) {
            onGameOverSubmit(state.score, state.level, state.linesCleared)
        }
    }

    BackHandler {
        if (state.isGameOver || state.isPaused) {
            onExitToMenu()
        } else {
            onTogglePause()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onMoveLeft()
                            true
                        }
                        Key.DirectionRight -> {
                            onMoveRight()
                            true
                        }
                        Key.DirectionUp -> {
                            onRefactor()
                            true
                        }
                        Key.DirectionDown -> {
                            onSoftDrop()
                            true
                        }
                        Key.Spacebar -> {
                            onHardDrop()
                            true
                        }
                        Key.P -> {
                            onTogglePause()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (maxWidth < 600.dp) {
                CompactPortraitGameLayout(
                    state = state,
                    onMoveLeft = onMoveLeft,
                    onMoveRight = onMoveRight,
                    onRefactor = onRefactor,
                    onRefactorToIndex = onRefactorToIndex,
                    onSoftDrop = onSoftDrop,
                    onHardDrop = onHardDrop,
                    onTogglePause = onTogglePause,
                    onSkipTutorial = onSkipTutorial
                )
            } else {
                ExpandedGameLayout(
                    state = state,
                    onMoveLeft = onMoveLeft,
                    onMoveRight = onMoveRight,
                    onRefactor = onRefactor,
                    onRefactorToIndex = onRefactorToIndex,
                    onSoftDrop = onSoftDrop,
                    onHardDrop = onHardDrop,
                    onTogglePause = onTogglePause,
                    onSkipTutorial = onSkipTutorial
                )
            }
        }

        if (state.isPaused && !state.isGameOver) {
            PauseOverlayDialog(
                state = state,
                onResume = onTogglePause,
                onRestart = onRestart,
                onSkipTutorial = onSkipTutorial,
                onToggleFactorPills = onToggleFactorPills,
                onToggleHaptics = onToggleHaptics,
                onToggleSound = onToggleSound,
                onExitToMenu = onExitToMenu
            )
        }

        if (state.tutorialState.step == TutorialStep.COMPLETED) {
            TutorialCompletedDialog(
                onStartGame = onFinishTutorial,
                onReplayTutorial = onReplayTutorial
            )
        }

        if (state.isGameOver) {
            GameOverOverlayDialog(
                state = state,
                isStudentProfileComplete = isStudentProfileComplete,
                onlineScoreSynced = onlineScoreSynced,
                onEditProfileForScore = {
                    onEditProfileForScore(state.score, state.level, state.linesCleared)
                },
                onOpenLeaderboard = onOpenLeaderboard,
                onRestart = onRestart,
                onExitToMenu = onExitToMenu
            )
        }
    }
}

@Composable
private fun CompactPortraitGameLayout(
    state: GameUiState,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRefactor: () -> Unit,
    onRefactorToIndex: (Int) -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
    onTogglePause: () -> Unit,
    onSkipTutorial: () -> Unit
) {
    val speedLabel = speedText(state)
    val piece = state.activePiece

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HUD superior
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onTogglePause,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .testTag("pause_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Pause,
                    contentDescription = stringResource(R.string.action_pause),
                    tint = TextPrimary
                )
            }

            // Área y dimensiones actuales
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianSurface,
                border = BorderStroke(
                    1.4.dp,
                    if (piece?.isPrime == true) CoralRose.copy(alpha = 0.8f) else CyanElectric.copy(alpha = 0.7f)
                ),
                modifier = Modifier.weight(1.25f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (piece?.isPrime == true) stringResource(R.string.hud_prime_badge) else stringResource(R.string.hud_area),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (piece?.isPrime == true) CoralRose else CyanElectric
                        )
                        Text(
                            text = piece?.currentDim?.label ?: "—",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            modifier = Modifier.testTag("hud_dimension_value")
                        )
                    }
                    Text(
                        text = piece?.area?.toString() ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (piece?.isPrime == true) CoralRose else CyanElectric,
                        modifier = Modifier.testTag("hud_area_value")
                    )
                }
            }

            // Puntuación, Filas y Nivel
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.weight(1.05f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = state.score.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        modifier = Modifier.testTag("hud_score_value")
                    )
                    Text(
                        text = "F:${state.linesCleared} · N${state.level} ($speedLabel)",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarm,
                        modifier = Modifier.testTag("hud_speed_value")
                    )
                }
            }

            NextPiecePreview(
                piece = state.nextPiece,
                boxSize = 50.dp
            )
        }

        AdaptiveTelemetryStrip(
            adaptiveState = state.adaptiveState,
            speedLabel = speedLabel
        )

        if (state.tutorialState.isActive) {
            TutorialGuideBanner(
                tutorialState = state.tutorialState,
                onSkipTutorial = onSkipTutorial
            )
        }

        // Tablero 12x20
        GameBoardCanvas(
            board = state.board,
            activePiece = state.activePiece,
            ghostY = state.ghostY,
            feedback = state.feedback,
            isPaused = state.isPaused,
            hapticsEnabled = state.config.hapticsEnabled,
            tutorialTarget = state.tutorialState.targetZone,
            onMoveLeft = onMoveLeft,
            onMoveRight = onMoveRight,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("game_board_canvas")
        )

        // Barra interactiva de factorización
        if ((state.config.showFactorPills || state.tutorialState.isActive) && piece != null) {
            val highlightedFactorIndex = if (
                state.tutorialState.isActive &&
                state.tutorialState.targetZone?.isReadyToDrop != true
            ) {
                state.tutorialState.targetFactorIndex
            } else {
                null
            }
            FactorPillsStrip(
                piece = piece,
                hapticsEnabled = state.config.hapticsEnabled,
                highlightedIndex = highlightedFactorIndex,
                onSelectFactorIndex = onRefactorToIndex
            )
        }

        // Controles táctiles
        GameControlsBar(
            hapticsEnabled = state.config.hapticsEnabled,
            onMoveLeft = onMoveLeft,
            onMoveRight = onMoveRight,
            onRefactor = onRefactor,
            onSoftDrop = onSoftDrop,
            onHardDrop = onHardDrop
        )
    }
}

@Composable
private fun ExpandedGameLayout(
    state: GameUiState,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRefactor: () -> Unit,
    onRefactorToIndex: (Int) -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
    onTogglePause: () -> Unit,
    onSkipTutorial: () -> Unit
) {
    val piece = state.activePiece

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                IconButton(
                    onClick = onTogglePause,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .testTag("pause_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = stringResource(R.string.action_pause),
                        tint = TextPrimary
                    )
                }
            }

            HudStatBox(
                label = if (piece?.isPrime == true) stringResource(R.string.hud_prime_badge) else stringResource(R.string.hud_area),
                value = piece?.let { "${it.area}  (${it.currentDim.label})" } ?: "—",
                accent = if (piece?.isPrime == true) CoralRose else CyanElectric
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HudStatBox(
                    label = stringResource(R.string.hud_score),
                    value = state.score.toString(),
                    accent = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                HudStatBox(
                    label = stringResource(R.string.hud_lines),
                    value = state.linesCleared.toString(),
                    accent = EmeraldMint,
                    modifier = Modifier.weight(1f)
                )
            }

            HudStatBox(
                label = stringResource(R.string.hud_level),
                value = "${state.level} (${speedText(state)})",
                accent = AmberWarm
            )

            AdaptiveTelemetryStrip(
                adaptiveState = state.adaptiveState,
                speedLabel = speedText(state)
            )

            if (state.tutorialState.isActive) {
                TutorialGuideBanner(
                    tutorialState = state.tutorialState,
                    onSkipTutorial = onSkipTutorial
                )
            }

            if ((state.config.showFactorPills || state.tutorialState.isActive) && piece != null) {
                val highlightedFactorIndex = if (
                    state.tutorialState.isActive &&
                    state.tutorialState.targetZone?.isReadyToDrop != true
                ) {
                    state.tutorialState.targetFactorIndex
                } else {
                    null
                }
                FactorPillsStrip(
                    piece = piece,
                    hapticsEnabled = state.config.hapticsEnabled,
                    highlightedIndex = highlightedFactorIndex,
                    onSelectFactorIndex = onRefactorToIndex
                )
            }
        }

        GameBoardCanvas(
            board = state.board,
            activePiece = state.activePiece,
            ghostY = state.ghostY,
            feedback = state.feedback,
            isPaused = state.isPaused,
            hapticsEnabled = state.config.hapticsEnabled,
            tutorialTarget = state.tutorialState.targetZone,
            onMoveLeft = onMoveLeft,
            onMoveRight = onMoveRight,
            modifier = Modifier
                .weight(1.45f)
                .fillMaxHeight()
                .testTag("game_board_canvas")
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.hud_next_piece),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                NextPiecePreview(
                    piece = state.nextPiece,
                    boxSize = 86.dp
                )
            }

            GameControlsBar(
                hapticsEnabled = state.config.hapticsEnabled,
                onMoveLeft = onMoveLeft,
                onMoveRight = onMoveRight,
                onRefactor = onRefactor,
                onSoftDrop = onSoftDrop,
                onHardDrop = onHardDrop
            )
        }
    }
}

@Composable
internal fun FactorPillsStrip(
    piece: ActiveRectPiece,
    hapticsEnabled: Boolean,
    highlightedIndex: Int? = null,
    onSelectFactorIndex: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("factor_pills_strip"),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        itemsIndexed(piece.factors) { idx, dim ->
            val selected = idx == piece.factorIndex
            val isTutorialTarget = highlightedIndex == idx && !selected
            val bgColor = when {
                selected -> CyanElectric.copy(alpha = 0.22f)
                isTutorialTarget -> EmeraldMint.copy(alpha = 0.2f)
                else -> ObsidianSurface
            }
            val borderColor = when {
                selected -> CyanElectric
                isTutorialTarget -> EmeraldMint
                else -> BorderSubtle
            }
            val textColor = when {
                selected -> CyanElectric
                isTutorialTarget -> EmeraldMint
                else -> TextSecondary
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .border(
                        width = if (selected || isTutorialTarget) 1.7.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        if (hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSelectFactorIndex(idx)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("factor_pill_$idx"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dim.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    fontWeight = if (isTutorialTarget) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TutorialGuideBanner(
    tutorialState: TutorialState,
    onSkipTutorial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stepTitle = when (tutorialState.step) {
        TutorialStep.REFACTOR_TO_FIT -> stringResource(R.string.tutorial_step1_title)
        TutorialStep.PLACE_ADJACENT_TO_CLEAR -> stringResource(R.string.tutorial_step2_title)
        TutorialStep.COMBINE_THREE_PIECES -> stringResource(R.string.tutorial_step3_title)
        else -> ""
    }
    val stepDesc = when (tutorialState.step) {
        TutorialStep.REFACTOR_TO_FIT -> stringResource(R.string.tutorial_step1_desc)
        TutorialStep.PLACE_ADJACENT_TO_CLEAR -> stringResource(R.string.tutorial_step2_desc)
        TutorialStep.COMBINE_THREE_PIECES -> stringResource(R.string.tutorial_step3_desc)
        else -> ""
    }
    val accent = if (tutorialState.targetZone?.isReadyToDrop == true) EmeraldMint else CyanElectric

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.4.dp, accent.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("tutorial_guide_card")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.tutorial_badge_format,
                                tutorialState.stepNumber,
                                tutorialState.totalSteps
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("tutorial_step_badge")
                        )
                    }
                    Text(
                        text = stepTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = onSkipTutorial,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("skip_tutorial_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.tutorial_skip),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            Text(
                text = stepDesc,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun AdaptiveTelemetryStrip(
    adaptiveState: AdaptiveDifficultyState,
    speedLabel: String,
    modifier: Modifier = Modifier
) {
    val tierColor = when (adaptiveState.tier) {
        AdaptiveDifficultyTier.SUPPORTIVE -> EmeraldMint
        AdaptiveDifficultyTier.BALANCED -> CyanElectric
        AdaptiveDifficultyTier.CHALLENGE -> AmberWarm
    }
    val tierLabel = when (adaptiveState.tier) {
        AdaptiveDifficultyTier.SUPPORTIVE -> stringResource(R.string.adaptive_tier_supportive)
        AdaptiveDifficultyTier.BALANCED -> stringResource(R.string.adaptive_tier_balanced)
        AdaptiveDifficultyTier.CHALLENGE -> stringResource(R.string.adaptive_tier_challenge)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, tierColor.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hud_adaptive_pill")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tierColor.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = tierLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = tierColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.hud_accuracy_format, adaptiveState.successPercentage),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                modifier = Modifier.testTag("hud_accuracy_value")
            )
            Text(
                text = stringResource(R.string.hud_factors_format, adaptiveState.factorRangeLabel),
                style = MaterialTheme.typography.labelSmall,
                color = tierColor,
                modifier = Modifier.testTag("hud_factor_range_value")
            )
            Text(
                text = speedLabel,
                style = MaterialTheme.typography.labelSmall,
                color = AmberWarm
            )
        }
    }
}

@Composable
private fun HudStatBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = accent
            )
        }
    }
}

@Composable
private fun PauseOverlayDialog(
    state: GameUiState,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onSkipTutorial: () -> Unit,
    onToggleFactorPills: () -> Unit,
    onToggleHaptics: () -> Unit,
    onToggleSound: () -> Unit,
    onExitToMenu: () -> Unit
) {
    Dialog(onDismissRequest = onResume) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = ObsidianSurface,
            border = BorderStroke(1.5.dp, BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pause_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.paused_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.paused_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SlateSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.label_factor_pills),
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                            Switch(
                                checked = state.config.showFactorPills,
                                onCheckedChange = { onToggleFactorPills() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MidnightBg,
                                    checkedTrackColor = CyanElectric
                                )
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.label_haptics),
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                            Switch(
                                checked = state.config.hapticsEnabled,
                                onCheckedChange = { onToggleHaptics() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MidnightBg,
                                    checkedTrackColor = CyanElectric
                                )
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.label_sound_effects),
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                            Switch(
                                checked = state.config.soundEnabled,
                                onCheckedChange = { onToggleSound() },
                                modifier = Modifier.testTag("pause_sound_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MidnightBg,
                                    checkedTrackColor = CyanElectric
                                )
                            )
                        }
                    }
                }

                Button(
                    onClick = onResume,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanElectric,
                        contentColor = MidnightBg
                    ),
                    contentPadding = PaddingValues(vertical = 13.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resume_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_resume),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (state.tutorialState.isActive) {
                    OutlinedButton(
                        onClick = onSkipTutorial,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AmberWarm.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pause_skip_tutorial_button")
                    ) {
                        Text(
                            text = stringResource(R.string.tutorial_skip),
                            color = AmberWarm
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRestart,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("restart_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_restart))
                    }
                    OutlinedButton(
                        onClick = onExitToMenu,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("exit_menu_button")
                    ) {
                        Text(stringResource(R.string.action_exit_menu))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TutorialCompletedDialog(
    onStartGame: () -> Unit,
    onReplayTutorial: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = ObsidianSurface,
            border = BorderStroke(1.5.dp, EmeraldMint.copy(alpha = 0.8f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tutorial_completed_dialog")
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(EmeraldMint.copy(alpha = 0.16f))
                        .border(1.2.dp, EmeraldMint.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = EmeraldMint,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.tutorial_completed_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.tutorial_completed_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        stringResource(R.string.tutorial_completed_chip_1),
                        stringResource(R.string.tutorial_completed_chip_2),
                        stringResource(R.string.tutorial_completed_chip_3)
                    ).forEach { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(CyanElectric.copy(alpha = 0.14f))
                                .border(1.dp, CyanElectric.copy(alpha = 0.45f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanElectric
                            )
                        }
                    }
                }

                Button(
                    onClick = onStartGame,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldMint,
                        contentColor = MidnightBg
                    ),
                    contentPadding = PaddingValues(vertical = 13.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tutorial_start_game_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.tutorial_action_start_game),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                OutlinedButton(
                    onClick = onReplayTutorial,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tutorial_replay_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tutorial_action_replay))
                }
            }
        }
    }
}

@Composable
private fun GameOverOverlayDialog(
    state: GameUiState,
    isStudentProfileComplete: Boolean,
    onlineScoreSynced: Boolean,
    onEditProfileForScore: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onRestart: () -> Unit,
    onExitToMenu: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = ObsidianSurface,
            border = BorderStroke(1.5.dp, CoralRose.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("game_over_dialog")
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.isNewRecord) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AmberWarm.copy(alpha = 0.2f))
                            .border(1.dp, AmberWarm, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.new_record_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberWarm
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.game_over_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.game_over_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SlateSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatRow(
                            label = stringResource(R.string.stat_final_score),
                            value = state.score.toString(),
                            accent = CyanElectric
                        )
                        StatRow(
                            label = stringResource(R.string.stat_level_reached),
                            value = state.level.toString(),
                            accent = TextPrimary
                        )
                        StatRow(
                            label = stringResource(R.string.stat_lines_cleared),
                            value = state.linesCleared.toString(),
                            accent = EmeraldMint
                        )
                        StatRow(
                            label = stringResource(R.string.stat_best_multi),
                            value = if (state.bestMultiClear > 0) "${state.bestMultiClear}×" else "—",
                            accent = AmberWarm
                        )
                        StatRow(
                            label = stringResource(R.string.stat_accuracy),
                            value = "${state.adaptiveState.successPercentage}%",
                            accent = CyanElectric
                        )
                    }
                }

                if (onlineScoreSynced) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldMint.copy(alpha = 0.14f))
                            .border(1.dp, EmeraldMint.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("online_synced_badge"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.leaderboard_synced_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldMint
                        )
                    }
                } else if (!isStudentProfileComplete && state.score > 0) {
                    OutlinedButton(
                        onClick = onEditProfileForScore,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AmberWarm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("publish_score_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = null,
                            tint = AmberWarm,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_publish_score),
                            color = AmberWarm
                        )
                    }
                }

                Button(
                    onClick = onRestart,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanElectric,
                        contentColor = MidnightBg
                    ),
                    contentPadding = PaddingValues(vertical = 13.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("play_again_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_restart),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenLeaderboard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CyanElectric.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("view_leaderboard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Leaderboard,
                            contentDescription = null,
                            tint = CyanElectric,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_leaderboard), color = CyanElectric)
                    }
                    OutlinedButton(
                        onClick = onExitToMenu,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("game_over_exit_button")
                    ) {
                        Text(stringResource(R.string.action_exit_menu))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}

@Composable
private fun speedText(state: GameUiState): String {
    if (state.config.mode == GameMode.PRACTICE) {
        return stringResource(R.string.hud_speed_relaxed)
    }
    if (state.dropIntervalMs == null) return stringResource(R.string.hud_speed_relaxed)
    val multiplier = FactrisEngine.speedMultiplier(state.dropIntervalMs)
    return String.format(Locale.US, "%.1fx", multiplier)
}
