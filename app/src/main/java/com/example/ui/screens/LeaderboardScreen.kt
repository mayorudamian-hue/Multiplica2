package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LeaderboardEntry
import com.example.data.LeaderboardUiState
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
fun LeaderboardScreen(
    leaderboardState: LeaderboardUiState,
    myEntry: LeaderboardEntry?,
    currentUserId: String,
    onEditProfile: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var searchQuery by remember { mutableStateOf("") }
    var filterMySchool by remember { mutableStateOf(false) }

    val mySchool = myEntry?.school?.trim().orEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurface)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            .testTag("leaderboard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.leaderboard_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.leaderboard_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onEditProfile,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, CyanElectric.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .testTag("edit_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.action_edit_student_profile),
                        tint = CyanElectric
                    )
                }
            }

            // Tarjeta del estudiante actual y su posición
            val currentEntries = (leaderboardState as? LeaderboardUiState.Success)?.entries.orEmpty()
            val myRank = currentEntries.indexOfFirst { it.userId == currentUserId }
                .takeIf { it >= 0 }
                ?.plus(1)

            StudentSelfCard(
                myEntry = myEntry,
                rank = myRank,
                onEditProfile = onEditProfile
            )

            // Buscador y filtro por escuela
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.leaderboard_search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanElectric,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = ObsidianSurface,
                    unfocusedContainerColor = ObsidianSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("leaderboard_search_input")
            )

            if (mySchool.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterPill(
                        label = stringResource(R.string.leaderboard_filter_all),
                        selected = !filterMySchool,
                        onClick = { filterMySchool = false },
                        modifier = Modifier.testTag("filter_all_schools")
                    )
                    FilterPill(
                        label = "${stringResource(R.string.leaderboard_filter_my_school)} ($mySchool)",
                        selected = filterMySchool,
                        onClick = { filterMySchool = true },
                        modifier = Modifier.testTag("filter_my_school")
                    )
                }
            }

            when (leaderboardState) {
                is LeaderboardUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CyanElectric)
                    }
                }

                is LeaderboardUiState.Error -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = CoralRose.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, CoralRose.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = leaderboardState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoralRose,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is LeaderboardUiState.Success -> {
                    val normalizedQuery = searchQuery.trim().lowercase()
                    val indexedEntries = leaderboardState.entries.mapIndexed { idx, entry ->
                        (idx + 1) to entry
                    }.filter { (_, entry) ->
                        val matchesSchool = !filterMySchool ||
                            entry.school.equals(mySchool, ignoreCase = true)
                        val matchesQuery = normalizedQuery.isEmpty() ||
                            entry.studentName.lowercase().contains(normalizedQuery) ||
                            entry.course.lowercase().contains(normalizedQuery) ||
                            entry.school.lowercase().contains(normalizedQuery)
                        matchesSchool && matchesQuery
                    }

                    if (indexedEntries.isEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("leaderboard_empty_state")
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EmojiEvents,
                                    contentDescription = null,
                                    tint = AmberWarm,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = stringResource(R.string.leaderboard_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = stringResource(R.string.leaderboard_empty_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("leaderboard_list"),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            itemsIndexed(
                                items = indexedEntries,
                                key = { _, pair -> pair.second.userId }
                            ) { _, (rank, entry) ->
                                LeaderboardStudentCard(
                                    rank = rank,
                                    entry = entry,
                                    isCurrentUser = entry.userId == currentUserId
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentSelfCard(
    myEntry: LeaderboardEntry?,
    rank: Int?,
    onEditProfile: () -> Unit
) {
    val complete = myEntry?.hasCompleteStudentInfo == true
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = ObsidianSurface,
        border = BorderStroke(
            1.2.dp,
            if (complete) CyanElectric.copy(alpha = 0.65f) else AmberWarm.copy(alpha = 0.7f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_self_card")
    ) {
        if (!complete) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.leaderboard_prompt_profile),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = onEditProfile,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberWarm,
                        contentColor = MidnightBg
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_complete_profile),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = myEntry.studentName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${myEntry.course} · ${myEntry.school}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (rank != null) "#$rank" else stringResource(R.string.leaderboard_unranked),
                        style = MaterialTheme.typography.titleLarge,
                        color = AmberWarm,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${myEntry.bestScore} pts",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanElectric
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .background(if (selected) CyanElectric.copy(alpha = 0.2f) else ObsidianSurface)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) CyanElectric else BorderSubtle,
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) CyanElectric else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LeaderboardStudentCard(
    rank: Int,
    entry: LeaderboardEntry,
    isCurrentUser: Boolean
) {
    val rankAccent: Color = when (rank) {
        1 -> AmberWarm
        2 -> CyanElectric
        3 -> EmeraldMint
        else -> TextSecondary
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isCurrentUser) SlateSurfaceVariant else ObsidianSurface,
        border = BorderStroke(
            width = if (isCurrentUser || rank <= 3) 1.5.dp else 1.dp,
            color = if (isCurrentUser) CyanElectric else if (rank <= 3) rankAccent.copy(alpha = 0.55f) else BorderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_row_$rank")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(rankAccent.copy(alpha = 0.16f))
                    .border(1.dp, rankAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = rankAccent
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.studentName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrentUser) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(CyanElectric.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.leaderboard_you_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanElectric
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Class,
                            contentDescription = null,
                            tint = CyanElectric,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = entry.course,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = null,
                            tint = EmeraldMint,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = entry.school,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.bestScore.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = rankAccent
                )
                Text(
                    text = "N${entry.bestLevel} · ${entry.linesCleared}F",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}
