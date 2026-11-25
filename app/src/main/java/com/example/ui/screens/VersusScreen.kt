package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.VersusMatch
import com.example.data.VersusMatchStatus
import com.example.data.VersusOpenRoomsUiState
import com.example.data.VersusRoundResult
import com.example.game.TutorialTargetZone
import com.example.game.VersusPlayState
import com.example.ui.components.GameBoardCanvas
import com.example.ui.components.GameControlsBar
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
import java.util.Locale

@Composable
fun VersusScreen(
    currentUserId: String,
    playerDisplayName: String,
    openRoomsState: VersusOpenRoomsUiState,
    activeMatch: VersusMatch?,
    matchRounds: List<VersusRoundResult>,
    playState: VersusPlayState,
    hapticsEnabled: Boolean,
    onCreateRoom: (String) -> Unit,
    onJoinRoom: (String, String) -> Unit,
    onLeaveMatch: () -> Unit,
    onReturnToLobbyAfterMatch: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRefactor: () -> Unit,
    onRefactorToIndex: (Int) -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
    onClearError: () -> Unit,
    onExitToHome: () -> Unit
) {
    BackHandler {
        when (activeMatch?.parsedStatus) {
            null -> onExitToHome()
            VersusMatchStatus.WAITING,
            VersusMatchStatus.ACTIVE -> onLeaveMatch()
            VersusMatchStatus.FINISHED,
            VersusMatchStatus.CANCELLED -> onReturnToLobbyAfterMatch()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when (activeMatch?.parsedStatus) {
            null -> {
                VersusLobbyView(
                    playerDisplayName = playerDisplayName,
                    openRoomsState = openRoomsState,
                    actionLoading = playState.actionLoading,
                    errorMessage = playState.errorMessage,
                    onCreateRoom = { onCreateRoom(playerDisplayName) },
                    onJoinRoom = { code -> onJoinRoom(code, playerDisplayName) },
                    onClearError = onClearError,
                    onBack = onExitToHome
                )
            }

            VersusMatchStatus.WAITING -> {
                VersusWaitingRoomView(
                    match = activeMatch,
                    actionLoading = playState.actionLoading,
                    onCancelRoom = onLeaveMatch
                )
            }

            VersusMatchStatus.ACTIVE -> {
                VersusActiveArenaView(
                    currentUserId = currentUserId,
                    match = activeMatch,
                    matchRounds = matchRounds,
                    playState = playState,
                    hapticsEnabled = hapticsEnabled,
                    onMoveLeft = onMoveLeft,
                    onMoveRight = onMoveRight,
                    onRefactor = onRefactor,
                    onRefactorToIndex = onRefactorToIndex,
                    onSoftDrop = onSoftDrop,
                    onHardDrop = onHardDrop,
                    onLeaveMatch = onLeaveMatch
                )
            }

            VersusMatchStatus.FINISHED,
            VersusMatchStatus.CANCELLED -> {
                VersusFinishedView(
                    currentUserId = currentUserId,
                    match = activeMatch,
                    rounds = matchRounds,
                    onBackToLobby = onReturnToLobbyAfterMatch,
                    onExitHome = {
                        onReturnToLobbyAfterMatch()
                        onExitToHome()
                    }
                )
            }
        }
    }
}

@Composable
private fun VersusLobbyView(
    playerDisplayName: String,
    openRoomsState: VersusOpenRoomsUiState,
    actionLoading: Boolean,
    errorMessage: String?,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit
) {
    var roomCodeInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .testTag("versus_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = TextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.versus_screen_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.versus_screen_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("versus_open_rooms_list")
            ) {
                if (errorMessage != null) {
                    item(key = "error_banner") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CoralRose.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, CoralRose.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CoralRose,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onClearError) {
                                    Text("OK", color = CoralRose)
                                }
                            }
                        }
                    }
                }

                item(key = "create_room_card") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = ObsidianSurface,
                        border = BorderStroke(1.2.dp, CyanElectric.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = CyanElectric
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.versus_create_room_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${stringResource(R.string.versus_create_room_desc)} · Jugador: $playerDisplayName",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Button(
                                onClick = onCreateRoom,
                                enabled = !actionLoading,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanElectric,
                                    contentColor = MidnightBg
                                ),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("versus_create_room_button")
                            ) {
                                if (actionLoading) {
                                    CircularProgressIndicator(
                                        color = MidnightBg,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(Icons.Rounded.AddCircle, contentDescription = null)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.versus_create_room_button),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }

                item(key = "join_code_card") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.versus_join_code_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = roomCodeInput,
                                    onValueChange = { raw ->
                                        roomCodeInput = raw.uppercase(Locale.US).take(12)
                                    },
                                    singleLine = true,
                                    placeholder = {
                                        Text(
                                            text = stringResource(R.string.versus_join_code_hint),
                                            color = TextSecondary
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldMint,
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("versus_join_code_input")
                                )
                                Button(
                                    onClick = { onJoinRoom(roomCodeInput) },
                                    enabled = !actionLoading && roomCodeInput.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldMint,
                                        contentColor = MidnightBg
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("versus_join_code_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.versus_join_code_button),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "open_rooms_header") {
                    Text(
                        text = stringResource(R.string.versus_open_rooms_title).uppercase(Locale.US),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                when (openRoomsState) {
                    VersusOpenRoomsUiState.Loading -> {
                        item(key = "rooms_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = CyanElectric)
                            }
                        }
                    }

                    is VersusOpenRoomsUiState.Error -> {
                        item(key = "rooms_error") {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = ObsidianSurface,
                                border = BorderStroke(1.dp, CoralRose.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = openRoomsState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CoralRose,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    is VersusOpenRoomsUiState.Success -> {
                        if (openRoomsState.rooms.isEmpty()) {
                            item(key = "rooms_empty") {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = ObsidianSurface,
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Group,
                                            contentDescription = null,
                                            tint = TextSecondary
                                        )
                                        Text(
                                            text = stringResource(R.string.versus_open_rooms_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        } else {
                            items(openRoomsState.rooms, key = { it.id }) { room ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = ObsidianSurface,
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("versus_room_card_${room.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = room.hostName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Sala ${room.id} · Meta inicial: ${room.targetLabel}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                        Button(
                                            onClick = { onJoinRoom(room.id) },
                                            enabled = !actionLoading,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AmberWarm,
                                                contentColor = MidnightBg
                                            ),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("versus_join_room_${room.id}")
                                        ) {
                                            Text(
                                                text = stringResource(R.string.versus_join_room_button),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersusWaitingRoomView(
    match: VersusMatch,
    actionLoading: Boolean,
    onCancelRoom: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = ObsidianSurface,
            border = BorderStroke(1.5.dp, CyanElectric.copy(alpha = 0.65f)),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = CyanElectric)
                Text(
                    text = stringResource(R.string.versus_waiting_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.versus_waiting_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SlateSurfaceVariant)
                        .border(1.2.dp, CyanElectric.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(vertical = 14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.versus_room_code_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanElectric
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = match.id,
                        style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("versus_room_code")
                    )
                }

                OutlinedButton(
                    onClick = onCancelRoom,
                    enabled = !actionLoading,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CoralRose.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("versus_cancel_room_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = CoralRose)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.versus_cancel_room_button),
                        color = CoralRose
                    )
                }
            }
        }
    }
}

@Composable
private fun VersusActiveArenaView(
    currentUserId: String,
    match: VersusMatch,
    matchRounds: List<VersusRoundResult>,
    playState: VersusPlayState,
    hapticsEnabled: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRefactor: () -> Unit,
    onRefactorToIndex: (Int) -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
    onLeaveMatch: () -> Unit
) {
    val piece = playState.activePiece
    val isReadyToDrop = piece != null &&
        piece.width == match.targetWidth &&
        piece.height == match.targetHeight

    val targetOverlay = TutorialTargetZone(
        x = playState.gapX,
        y = playState.gapY,
        width = match.targetWidth,
        height = match.targetHeight,
        label = "${match.targetWidth}×${match.targetHeight} = ${match.targetArea}",
        isReadyToDrop = isReadyToDrop
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Marcador en tiempo real 1v1
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.2.dp, CyanElectric.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("versus_scoreboard")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PlayerScoreChip(
                        name = match.hostName,
                        wins = match.hostWins,
                        isYou = currentUserId == match.hostUserId,
                        accent = CyanElectric,
                        modifier = Modifier.weight(1f)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.versus_round_badge_format,
                                match.currentRound,
                                match.totalRounds
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = AmberWarm,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("versus_round_badge")
                        )
                        Text(
                            text = "${match.hostWins} - ${match.guestWins}",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    PlayerScoreChip(
                        name = match.guestName ?: "Rival",
                        wins = match.guestWins,
                        isYou = currentUserId == match.guestUserId,
                        accent = EmeraldMint,
                        alignEnd = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AmberWarm.copy(alpha = 0.16f))
                            .border(1.dp, AmberWarm.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.versus_target_format,
                                match.targetArea,
                                match.targetWidth,
                                match.targetHeight
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = AmberWarm,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("versus_target_badge")
                        )
                    }

                    TextButton(
                        onClick = onLeaveMatch,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("versus_leave_match_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = CoralRose,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.versus_leave_match_button),
                            style = MaterialTheme.typography.labelMedium,
                            color = CoralRose
                        )
                    }
                }
            }
        }

        if (playState.roundBanner != null || matchRounds.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                playState.roundBanner?.let { banner ->
                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(CyanElectric.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = banner,
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanElectric
                            )
                        }
                    }
                }
                items(matchRounds, key = { it.id.ifBlank { "r_${it.roundNumber}" } }) { round ->
                    val secs = round.solveTimeMs / 1000.0
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ObsidianSurface)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "R%d: %s (%.1fs)",
                                round.roundNumber,
                                round.winnerName,
                                secs
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        GameBoardCanvas(
            board = playState.board,
            activePiece = playState.activePiece,
            ghostY = playState.ghostY,
            feedback = playState.feedback,
            isPaused = playState.isSubmittingRound,
            hapticsEnabled = hapticsEnabled,
            tutorialTarget = targetOverlay,
            onMoveLeft = onMoveLeft,
            onMoveRight = onMoveRight,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("versus_board_canvas")
        )

        if (piece != null) {
            FactorPillsStrip(
                piece = piece,
                hapticsEnabled = hapticsEnabled,
                highlightedIndex = if (!isReadyToDrop) playState.targetFactorIndex else null,
                onSelectFactorIndex = onRefactorToIndex
            )
        }

        GameControlsBar(
            hapticsEnabled = hapticsEnabled,
            onMoveLeft = onMoveLeft,
            onMoveRight = onMoveRight,
            onRefactor = onRefactor,
            onSoftDrop = onSoftDrop,
            onHardDrop = onHardDrop
        )
    }
}

@Composable
private fun PlayerScoreChip(
    name: String,
    wins: Int,
    isYou: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    alignEnd: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = if (isYou) "$name (Tú)" else name,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$wins pts",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun VersusFinishedView(
    currentUserId: String,
    match: VersusMatch,
    rounds: List<VersusRoundResult>,
    onBackToLobby: () -> Unit,
    onExitHome: () -> Unit
) {
    val isCancelled = match.parsedStatus == VersusMatchStatus.CANCELLED
    val didYouWin = !isCancelled && match.winnerUserId == currentUserId
    val headline = when {
        isCancelled -> stringResource(R.string.versus_match_cancelled)
        didYouWin -> stringResource(R.string.versus_you_won_match)
        else -> stringResource(
            R.string.versus_opponent_won_match,
            match.winnerName ?: "Rival"
        )
    }
    val accent = when {
        isCancelled -> TextSecondary
        didYouWin -> EmeraldMint
        else -> AmberWarm
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = ObsidianSurface,
                border = BorderStroke(1.5.dp, accent.copy(alpha = 0.75f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("versus_result_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    if (!isCancelled) {
                        Text(
                            text = "${match.hostName} ${match.hostWins} - ${match.guestWins} ${match.guestName ?: "Rival"}",
                            style = MaterialTheme.typography.titleMedium,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.versus_rounds_history_title).uppercase(Locale.US),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("versus_rounds_results_list")
            ) {
                items(rounds, key = { it.id.ifBlank { "res_${it.roundNumber}" } }) { r ->
                    val secs = r.solveTimeMs / 1000.0
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ronda ${r.roundNumber} · ${r.targetArea} (${r.targetWidth}×${r.targetHeight})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Ganador: ${r.winnerName} · Intentos: ${r.attempts}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = CyanElectric,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1fs", secs),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CyanElectric,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onBackToLobby,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanElectric,
                    contentColor = MidnightBg
                ),
                contentPadding = PaddingValues(vertical = 13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("versus_back_to_lobby_button")
            ) {
                Text(
                    text = stringResource(R.string.versus_back_to_lobby),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            OutlinedButton(
                onClick = onExitHome,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("versus_exit_home_button")
            ) {
                Text(stringResource(R.string.versus_exit_home))
            }
        }
    }
}
