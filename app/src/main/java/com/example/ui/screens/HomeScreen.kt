package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ViewWeek
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LeaderboardEntry
import com.example.data.UserPreferences
import com.example.game.GameMode
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.tilePaletteFor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    preferences: UserPreferences,
    studentEntry: LeaderboardEntry?,
    onSelectMode: (GameMode) -> Unit,
    onSelectTable: (Int) -> Unit,
    onTogglePrimes: (Boolean) -> Unit,
    onToggleFactorPills: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onStartGame: () -> Unit,
    onStartTutorial: () -> Unit,
    onOpenTablesGuide: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenVersus: () -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                HeroHeaderCard(
                    bestScore = maxOf(preferences.bestScore, studentEntry?.bestScore?.toInt() ?: 0),
                    onOpenTablesGuide = onOpenTablesGuide
                )

                StudentLeaderboardBanner(
                    studentEntry = studentEntry,
                    onOpenLeaderboard = onOpenLeaderboard,
                    onEditProfile = onEditProfile,
                    onSignOut = onSignOut
                )

                VersusModeBanner(onOpenVersus = onOpenVersus)

                SectionHeader(title = stringResource(R.string.section_game_mode))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModeCard(
                        title = stringResource(R.string.mode_arcade),
                        subtitle = stringResource(R.string.mode_arcade_desc),
                        icon = Icons.Rounded.Speed,
                        accent = CyanElectric,
                        selected = preferences.gameMode == GameMode.ARCADE,
                        onClick = { onSelectMode(GameMode.ARCADE) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_arcade_card")
                    )
                    ModeCard(
                        title = stringResource(R.string.mode_practice),
                        subtitle = stringResource(R.string.mode_practice_desc),
                        icon = Icons.Rounded.AutoAwesome,
                        accent = EmeraldMint,
                        selected = preferences.gameMode == GameMode.PRACTICE,
                        onClick = { onSelectMode(GameMode.PRACTICE) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_practice_card")
                    )
                }

                SectionHeader(title = stringResource(R.string.section_table_selector))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TableChip(
                        label = stringResource(R.string.table_all),
                        accent = CyanElectric,
                        selected = preferences.selectedTable == 0,
                        onClick = { onSelectTable(0) },
                        modifier = Modifier.testTag("table_chip_0")
                    )
                    for (t in 2..10) {
                        TableChip(
                            label = "×$t",
                            accent = tilePaletteFor(t).fill,
                            selected = preferences.selectedTable == t,
                            onClick = { onSelectTable(t) },
                            modifier = Modifier.testTag("table_chip_$t")
                        )
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        ToggleSettingRow(
                            title = stringResource(R.string.label_include_primes),
                            subtitle = stringResource(R.string.desc_include_primes),
                            icon = Icons.Rounded.Diamond,
                            checked = preferences.includePrimes,
                            onCheckedChange = onTogglePrimes
                        )
                        RowDivider()
                        ToggleSettingRow(
                            title = stringResource(R.string.label_factor_pills),
                            subtitle = stringResource(R.string.desc_factor_pills),
                            icon = Icons.Rounded.ViewWeek,
                            checked = preferences.showFactorPills,
                            onCheckedChange = onToggleFactorPills
                        )
                        RowDivider()
                        ToggleSettingRow(
                            title = stringResource(R.string.label_haptics),
                            subtitle = stringResource(R.string.desc_haptics),
                            icon = Icons.Rounded.TouchApp,
                            checked = preferences.hapticsEnabled,
                            onCheckedChange = onToggleHaptics
                        )
                        RowDivider()
                        ToggleSettingRow(
                            title = stringResource(R.string.label_sound_effects),
                            subtitle = stringResource(R.string.desc_sound_effects),
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                            checked = preferences.soundEnabled,
                            onCheckedChange = onToggleSound,
                            switchModifier = Modifier.testTag("home_sound_switch")
                        )
                    }
                }

                HowToPlayCard(
                    hasCompletedTutorial = preferences.hasCompletedTutorial,
                    onStartTutorial = onStartTutorial
                )

                Spacer(modifier = Modifier.height(6.dp))
            }

            Surface(
                color = ObsidianSurface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onStartGame,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanElectric,
                            contentColor = MidnightBg
                        ),
                        contentPadding = PaddingValues(vertical = 15.dp),
                        modifier = Modifier
                            .widthIn(max = 620.dp)
                            .fillMaxWidth()
                            .testTag("play_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_play),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentLeaderboardBanner(
    studentEntry: LeaderboardEntry?,
    onOpenLeaderboard: () -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    val complete = studentEntry?.hasCompleteStudentInfo == true
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = ObsidianSurface,
        border = BorderStroke(
            1.2.dp,
            if (complete) CyanElectric.copy(alpha = 0.45f) else AmberWarm.copy(alpha = 0.65f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background((if (complete) EmeraldMint else AmberWarm).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = null,
                            tint = if (complete) EmeraldMint else AmberWarm,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (complete) studentEntry.studentName else stringResource(R.string.student_profile_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (complete) {
                                "${studentEntry.course} · ${studentEntry.school}"
                            } else {
                                stringResource(R.string.leaderboard_prompt_profile)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = stringResource(R.string.action_sign_out),
                        tint = TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onEditProfile,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_profile_home_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (complete) {
                            stringResource(R.string.action_edit_student_profile)
                        } else {
                            stringResource(R.string.action_complete_profile)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onOpenLeaderboard,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberWarm,
                        contentColor = MidnightBg
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("leaderboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Leaderboard,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_leaderboard),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BorderSubtle.copy(alpha = 0.6f))
    )
}

@Composable
private fun HeroHeaderCard(
    bestScore: Int,
    onOpenTablesGuide: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(172.dp)
            .clip(MaterialTheme.shapes.large)
            .border(1.2.dp, BorderSubtle, MaterialTheme.shapes.large)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_banner),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MidnightBg.copy(alpha = 0.92f),
                            MidnightBg.copy(alpha = 0.68f),
                            MidnightBg.copy(alpha = 0.35f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ObsidianSurface.copy(alpha = 0.88f))
                        .border(1.dp, AmberWarm.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = AmberWarm,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${stringResource(R.string.label_best_score)}: $bestScore",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberWarm
                    )
                }

                OutlinedButton(
                    onClick = onOpenTablesGuide,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, CyanElectric.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ObsidianSurface.copy(alpha = 0.85f),
                        contentColor = CyanElectric
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("tables_guide_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Calculate,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_tables_guide),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary
    )
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) accent.copy(alpha = 0.14f) else ObsidianSurface
    val borderColor = if (selected) accent else BorderSubtle

    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .border(if (selected) 1.8.dp else 1.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accent else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) accent else TextPrimary
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun TableChip(
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else ObsidianSurface)
            .border(
                width = if (selected) 1.6.dp else 1.dp,
                color = if (selected) accent else BorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else TextPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchModifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyanElectric,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MidnightBg,
                checkedTrackColor = CyanElectric
            )
        )
    }
}

@Composable
private fun HowToPlayCard(
    hasCompletedTutorial: Boolean,
    onStartTutorial: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = ObsidianSurface,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.rules_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AmberWarm.copy(alpha = 0.16f))
                        .border(1.dp, AmberWarm.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "12 = 6×2 · 4×3 · 3×4",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberWarm
                    )
                }
            }
            Text(
                text = stringResource(R.string.rules_step_1),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Text(
                text = stringResource(R.string.rules_step_2),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Text(
                text = stringResource(R.string.rules_step_3),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(2.dp))

            OutlinedButton(
                onClick = onStartTutorial,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.2.dp,
                    if (hasCompletedTutorial) BorderSubtle else CyanElectric.copy(alpha = 0.75f)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_tutorial_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = if (hasCompletedTutorial) TextSecondary else CyanElectric,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasCompletedTutorial) {
                        stringResource(R.string.tutorial_home_replay_button)
                    } else {
                        stringResource(R.string.tutorial_home_button)
                    },
                    color = if (hasCompletedTutorial) TextPrimary else CyanElectric,
                    style = MaterialTheme.typography.labelLarge
                )
                if (hasCompletedTutorial) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(EmeraldMint.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tutorial_completed_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldMint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersusModeBanner(
    onOpenVersus: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = ObsidianSurface,
        border = BorderStroke(1.2.dp, EmeraldMint.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EmeraldMint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = EmeraldMint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.versus_home_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.versus_home_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onOpenVersus,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldMint,
                    contentColor = MidnightBg
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("open_versus_button")
            ) {
                Text(
                    text = stringResource(R.string.versus_home_cta),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
