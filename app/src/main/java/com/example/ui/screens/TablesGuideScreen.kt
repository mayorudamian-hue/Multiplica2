package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.tilePaletteFor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TablesGuideScreen(
    initialTable: Int,
    onBack: () -> Unit,
    onPracticeTable: (Int) -> Unit
) {
    BackHandler { onBack() }

    var selectedTable by remember {
        mutableIntStateOf(if (initialTable in 2..10) initialTable else 7)
    }
    val palette = tilePaletteFor(selectedTable)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .testTag("tables_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = TextPrimary
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.tables_guide_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.tables_guide_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Selector de tabla 2..10
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (t in 2..10) {
                    val p = tilePaletteFor(t)
                    val isSelected = selectedTable == t
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) p.fill else ObsidianSurface)
                            .border(
                                width = if (isSelected) 1.6.dp else 1.dp,
                                color = if (isSelected) p.border else BorderSubtle,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedTable = t }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("guide_table_$t"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "×$t",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) p.content else TextPrimary
                        )
                    }
                }
            }

            // Tarjetas de productos en 2 columnas
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (rowIdx in 0 until 5) {
                    val leftMult = rowIdx + 1
                    val rightMult = rowIdx + 6
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EquationCard(
                            a = selectedTable,
                            b = leftMult,
                            accent = palette.fill,
                            modifier = Modifier.weight(1f)
                        )
                        EquationCard(
                            a = selectedTable,
                            b = rightMult,
                            accent = palette.fill,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Surface(
            color = ObsidianSurface,
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
                    onClick = { onPracticeTable(selectedTable) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanElectric,
                        contentColor = MidnightBg
                    ),
                    contentPadding = PaddingValues(vertical = 15.dp),
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .fillMaxWidth()
                        .testTag("practice_selected_table_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(R.string.action_practice_table)} (×$selectedTable)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun EquationCard(
    a: Int,
    b: Int,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SlateSurfaceVariant,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$a × $b",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.2f))
                    .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${a * b}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }
    }
}
