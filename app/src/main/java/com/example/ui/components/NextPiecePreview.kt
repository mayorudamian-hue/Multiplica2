package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.ActiveRectPiece
import com.example.ui.theme.BoardCellLight
import com.example.ui.theme.BoardGridLine
import com.example.ui.theme.SpaceGroteskFont
import com.example.ui.theme.tilePaletteFor
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

@Composable
fun NextPiecePreview(
    piece: ActiveRectPiece?,
    modifier: Modifier = Modifier,
    boxSize: Dp = 54.dp
) {
    val textMeasurer = rememberTextMeasurer()
    var showMultiplication by remember { mutableStateOf(true) }

    LaunchedEffect(piece?.area, piece?.factorIndex) {
        if (piece == null) return@LaunchedEffect
        showMultiplication = true
        while (true) {
            delay(1200L)
            showMultiplication = !showMultiplication
        }
    }

    Box(
        modifier = modifier
            .size(boxSize)
            .clip(RoundedCornerShape(12.dp))
            .background(BoardCellLight)
            .border(1.2.dp, BoardGridLine, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (piece != null) {
            Canvas(modifier = Modifier.size(boxSize - 10.dp)) {
                val palette = tilePaletteFor(piece.area)
                val cols = max(piece.width, 1)
                val rows = max(piece.height, 1)
                val cellSize = min(size.width / cols, size.height / rows)
                    .coerceAtMost(size.width / 2f)
                    .coerceAtLeast(3f)

                val rectW = cols * cellSize
                val rectH = rows * cellSize
                val left = (size.width - rectW) / 2f
                val top = (size.height - rectH) / 2f
                val corner = CornerRadius(4f, 4f)

                drawRoundRect(
                    color = palette.fill,
                    topLeft = Offset(left, top),
                    size = Size(rectW, rectH),
                    cornerRadius = corner
                )
                drawRoundRect(
                    color = palette.border,
                    topLeft = Offset(left, top),
                    size = Size(rectW, rectH),
                    cornerRadius = corner,
                    style = Stroke(width = 1.8f)
                )

                val label = if (showMultiplication) {
                    if (piece.width == 1 && piece.height >= 2) {
                        "${piece.width}\n×${piece.height}"
                    } else {
                        "${piece.width}×${piece.height}"
                    }
                } else {
                    piece.area.toString()
                }
                val lines = label.split("\n")
                val maxLen = lines.maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
                var fontSp = min(
                    (rectW * 0.85f / (maxLen * 0.65f)) / density,
                    (rectH * 0.78f / lines.size) / density
                ).coerceIn(7.5f, 15f)

                var layout = textMeasurer.measure(
                    text = label,
                    style = TextStyle(
                        fontFamily = SpaceGroteskFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSp.sp,
                        lineHeight = (fontSp * 1.05f).sp,
                        textAlign = TextAlign.Center,
                        color = palette.content
                    )
                )
                if (layout.size.width > rectW * 0.9f || layout.size.height > rectH * 0.9f) {
                    val scale = min(
                        (rectW * 0.9f) / layout.size.width.coerceAtLeast(1),
                        (rectH * 0.9f) / layout.size.height.coerceAtLeast(1)
                    )
                    fontSp = (fontSp * scale).coerceAtLeast(7f)
                    layout = textMeasurer.measure(
                        text = label,
                        style = TextStyle(
                            fontFamily = SpaceGroteskFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSp.sp,
                            lineHeight = (fontSp * 1.05f).sp,
                            textAlign = TextAlign.Center,
                            color = palette.content
                        )
                    )
                }

                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        left + (rectW - layout.size.width) / 2f,
                        top + (rectH - layout.size.height) / 2f
                    )
                )
            }
        }
    }
}
