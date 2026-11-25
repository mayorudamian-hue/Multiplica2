package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.ActiveRectPiece
import com.example.game.BOARD_COLS
import com.example.game.BOARD_ROWS
import com.example.game.BoardCell
import com.example.game.FactorDimension
import com.example.game.FactrisEngine
import com.example.game.GameFeedback
import com.example.game.TutorialTargetZone
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.BlockInkDark
import com.example.ui.theme.BoardCellLight
import com.example.ui.theme.BoardDangerTint
import com.example.ui.theme.BoardGridLine
import com.example.ui.theme.BoardLightBg
import com.example.ui.theme.CoralRose
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.MultiDropTheme
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SpaceGroteskFont
import com.example.ui.theme.tilePaletteFor
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private data class SettledBlockBounds(
    val blockId: Long,
    val displayNumber: Int,
    val minRow: Int,
    val maxRow: Int,
    val minCol: Int,
    val maxCol: Int,
    val isClearing: Boolean
)

@Composable
fun GameBoardCanvas(
    board: List<List<BoardCell?>>,
    activePiece: ActiveRectPiece?,
    ghostY: Int?,
    feedback: GameFeedback?,
    modifier: Modifier = Modifier,
    isPaused: Boolean = false,
    hapticsEnabled: Boolean = false,
    tutorialTarget: TutorialTargetZone? = null,
    onMoveLeft: () -> Unit = {},
    onMoveRight: () -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current
    val currentOnMoveLeft by rememberUpdatedState(onMoveLeft)
    val currentOnMoveRight by rememberUpdatedState(onMoveRight)
    val currentHaptics by rememberUpdatedState(hapticsEnabled)
    var boardWidthPx by remember { mutableIntStateOf(0) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }

    val tutorialPulse = if (tutorialTarget != null) {
        val transition = rememberInfiniteTransition(label = "tutorial_pulse")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tutorial_alpha"
        ).value
    } else {
        0f
    }

    // Alterna entre la multiplicación (ej. 4×3) y el resultado (ej. 12) en la pieza activa
    var showMultiplication by remember { mutableStateOf(true) }
    LaunchedEffect(activePiece?.area, activePiece?.factorIndex, isPaused) {
        if (activePiece == null) return@LaunchedEffect
        showMultiplication = true
        while (!isPaused) {
            delay(1200L)
            showMultiplication = !showMultiplication
        }
    }

    var visibleFeedback by remember { mutableStateOf<GameFeedback?>(null) }
    LaunchedEffect(feedback?.id) {
        if (feedback != null) {
            visibleFeedback = feedback
            delay(1300L)
            if (visibleFeedback?.id == feedback.id) {
                visibleFeedback = null
            }
        }
    }

    val hasDangerStack = remember(board) {
        board.take(5).any { row -> row.any { it != null } }
    }

    val clearingRows = remember(board) {
        (0 until min(board.size, BOARD_ROWS)).filter { r ->
            board[r].any { it?.isClearing == true }
        }
    }
    val clearAnim = remember { Animatable(0f) }
    LaunchedEffect(clearingRows) {
        if (clearingRows.isNotEmpty()) {
            if (currentHaptics) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            clearAnim.snapTo(0f)
            clearAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        } else {
            clearAnim.snapTo(0f)
        }
    }

    // Agrupar bloques fijados por blockId para centrar su número legible dentro de cada bloque
    val settledBlocks = remember(board) {
        val grouped = linkedMapOf<Long, MutableList<Triple<Int, Int, BoardCell>>>()
        for (r in 0 until min(board.size, BOARD_ROWS)) {
            val row = board[r]
            for (c in 0 until min(row.size, BOARD_COLS)) {
                val cell = row[c] ?: continue
                grouped.getOrPut(cell.blockId) { mutableListOf() }.add(Triple(r, c, cell))
            }
        }
        grouped.flatMap { (blockId, entries) ->
            val runs = mutableListOf<MutableList<Triple<Int, Int, BoardCell>>>()
            for (entry in entries.sortedBy { it.first }) {
                val lastRun = runs.lastOrNull()
                if (lastRun == null || entry.first - lastRun.maxOf { it.first } > 1) {
                    runs.add(mutableListOf(entry))
                } else {
                    lastRun.add(entry)
                }
            }
            runs.map { runEntries ->
                SettledBlockBounds(
                    blockId = blockId,
                    displayNumber = runEntries.size,
                    minRow = runEntries.minOf { it.first },
                    maxRow = runEntries.maxOf { it.first },
                    minCol = runEntries.minOf { it.second },
                    maxCol = runEntries.maxOf { it.second },
                    isClearing = runEntries.all { it.third.isClearing }
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .semantics {
                activePiece?.let { stateDescription = "col:${it.x},y:${it.y}" }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { accumulatedDragX = 0f },
                    onDragEnd = { accumulatedDragX = 0f },
                    onDragCancel = { accumulatedDragX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragX += dragAmount
                        val cellPx = if (boardWidthPx > 0) {
                            boardWidthPx.toFloat() / BOARD_COLS
                        } else {
                            28.dp.toPx()
                        }
                        val stepThreshold = (cellPx * 0.85f).coerceAtLeast(16f)
                        while (accumulatedDragX >= stepThreshold) {
                            accumulatedDragX -= stepThreshold
                            if (currentHaptics) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            currentOnMoveRight()
                        }
                        while (accumulatedDragX <= -stepThreshold) {
                            accumulatedDragX += stepThreshold
                            if (currentHaptics) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            currentOnMoveLeft()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val boardRatio = BOARD_COLS.toFloat() / BOARD_ROWS.toFloat()
        val boardModifier = if (maxWidth / maxHeight > boardRatio) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(boardRatio)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(boardRatio)
        }

        Box(
            modifier = boardModifier
                .onSizeChanged { boardWidthPx = it.width }
                .clip(RoundedCornerShape(16.dp))
                .background(BoardLightBg)
                .border(2.dp, BoardGridLine, RoundedCornerShape(16.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellW = size.width / BOARD_COLS
                val cellH = size.height / BOARD_ROWS
                val cellPad = (min(cellW, cellH) * 0.06f).coerceAtLeast(1f)
                val cellRadius = CornerRadius(cellW * 0.16f, cellW * 0.16f)

                // 1. Celdas claras del tablero (cuadrícula completa)
                for (r in 0 until BOARD_ROWS) {
                    val isTopAlertZone = r < 2
                    val emptyCellColor = when {
                        isTopAlertZone && hasDangerStack -> BoardDangerTint
                        isTopAlertZone -> Color(0xFFE2E8F0)
                        else -> BoardCellLight
                    }
                    for (c in 0 until BOARD_COLS) {
                        drawRoundRect(
                            color = emptyCellColor,
                            topLeft = Offset(c * cellW + cellPad, r * cellH + cellPad),
                            size = Size(cellW - cellPad * 2f, cellH - cellPad * 2f),
                            cornerRadius = cellRadius
                        )
                    }
                }

                // Línea divisoria sutil de la zona superior (primeras 2 filas)
                drawLine(
                    color = if (hasDangerStack) CoralRose.copy(alpha = 0.8f) else BoardGridLine,
                    start = Offset(0f, cellH * 2f),
                    end = Offset(size.width, cellH * 2f),
                    strokeWidth = if (hasDangerStack) 2.5f else 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                )

                // 2. Celdas fijadas en el tablero con colores claros (y desvanecimiento/implosión si se están eliminando)
                val clearProgress = clearAnim.value
                for (r in 0 until min(board.size, BOARD_ROWS)) {
                    val row = board[r]
                    for (c in 0 until min(row.size, BOARD_COLS)) {
                        val cell = row[c] ?: continue
                        val palette = tilePaletteFor(cell.areaValue)
                        val left = c * cellW + cellPad
                        val top = r * cellH + cellPad
                        val w = cellW - cellPad * 2f
                        val h = cellH - cellPad * 2f

                        if (cell.isClearing) {
                            val cellAlpha = (1f - clearProgress).coerceIn(0f, 1f)
                            val scale = if (clearProgress < 0.22f) {
                                1f + (clearProgress / 0.22f) * 0.1f
                            } else {
                                (1.1f - ((clearProgress - 0.22f) / 0.78f) * 0.85f).coerceAtLeast(0.2f)
                            }
                            val animW = w * scale
                            val animH = h * scale
                            val animLeft = left + (w - animW) / 2f
                            val animTop = top + (h - animH) / 2f
                            val flashBlend = (1f - clearProgress * 2.5f).coerceIn(0f, 1f)

                            drawRoundRect(
                                color = EmeraldMint.copy(alpha = cellAlpha),
                                topLeft = Offset(animLeft, animTop),
                                size = Size(animW, animH),
                                cornerRadius = cellRadius
                            )
                            if (flashBlend > 0f) {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = flashBlend * 0.75f),
                                    topLeft = Offset(animLeft, animTop),
                                    size = Size(animW, animH),
                                    cornerRadius = cellRadius
                                )
                            }
                        } else {
                            drawRoundRect(
                                color = palette.fill,
                                topLeft = Offset(left, top),
                                size = Size(w, h),
                                cornerRadius = cellRadius
                            )

                            // Brillo superior suave para profundidad táctil
                            drawRoundRect(
                                color = palette.highlight.copy(alpha = 0.65f),
                                topLeft = Offset(left + 1.5f, top + 1.5f),
                                size = Size(w - 3f, min(h * 0.28f, cellH * 0.3f)),
                                cornerRadius = CornerRadius(cellW * 0.1f, cellW * 0.1f)
                            )

                            // Contornos del bloque agrupado
                            val sameLeft = c > 0 && row[c - 1]?.blockId == cell.blockId
                            val sameRight = c + 1 < BOARD_COLS && row[c + 1]?.blockId == cell.blockId
                            val sameTop = r > 0 && board[r - 1][c]?.blockId == cell.blockId
                            val sameBottom = r + 1 < BOARD_ROWS && board[r + 1][c]?.blockId == cell.blockId
                            val edgeColor = palette.border

                            if (!sameLeft) {
                                drawLine(edgeColor, Offset(left, top), Offset(left, top + h), strokeWidth = 2.2f)
                            }
                            if (!sameRight) {
                                drawLine(edgeColor, Offset(left + w, top), Offset(left + w, top + h), strokeWidth = 2.2f)
                            }
                            if (!sameTop) {
                                drawLine(edgeColor, Offset(left, top), Offset(left + w, top), strokeWidth = 2.2f)
                            }
                            if (!sameBottom) {
                                drawLine(edgeColor, Offset(left, top + h), Offset(left + w, top + h), strokeWidth = 2.2f)
                            }
                        }
                    }
                }

                // Onda expansiva y partículas de explosión por cada fila completada
                if (clearingRows.isNotEmpty() && clearProgress > 0f) {
                    val burstAlpha = (1f - clearProgress).coerceIn(0f, 1f)
                    for (r in clearingRows) {
                        val rowCenterY = (r + 0.5f) * cellH
                        val beamHeight = cellH * (0.35f + clearProgress * 0.85f)
                        drawRect(
                            color = EmeraldMint.copy(alpha = burstAlpha * 0.32f),
                            topLeft = Offset(0f, rowCenterY - beamHeight / 2f),
                            size = Size(size.width, beamHeight)
                        )
                        drawLine(
                            color = Color.White.copy(alpha = burstAlpha * 0.85f),
                            start = Offset(0f, rowCenterY),
                            end = Offset(size.width, rowCenterY),
                            strokeWidth = (4f * burstAlpha).coerceAtLeast(1f)
                        )

                        for (c in 0 until BOARD_COLS) {
                            val cell = board[r][c] ?: continue
                            val palette = tilePaletteFor(cell.areaValue)
                            val cx = (c + 0.5f) * cellW
                            val cy = (r + 0.5f) * cellH

                            for (p in 0 until 4) {
                                val seed = (r * 31 + c * 17 + p * 53)
                                val angle = ((seed % 360) * Math.PI / 180.0).toFloat()
                                val speedFactor = 0.7f + ((seed % 7) * 0.18f)
                                val dist = clearProgress * cellW * speedFactor
                                val px = cx + cos(angle) * dist
                                val py = cy + sin(angle) * dist
                                val sparkRadius = (cellW * 0.16f * (1f - clearProgress * 0.75f)).coerceAtLeast(1.5f)
                                val sparkColor = when (p % 3) {
                                    0 -> palette.border
                                    1 -> AmberWarm
                                    else -> EmeraldMint
                                }
                                drawCircle(
                                    color = sparkColor.copy(alpha = burstAlpha),
                                    radius = sparkRadius,
                                    center = Offset(px, py)
                                )
                            }
                        }
                    }
                }

                // Número centrado legible en cada bloque fijado
                for (block in settledBlocks) {
                    val left = block.minCol * cellW
                    val top = block.minRow * cellH
                    val rectW = (block.maxCol - block.minCol + 1) * cellW
                    val rectH = (block.maxRow - block.minRow + 1) * cellH
                    val textAlpha = if (block.isClearing) (1f - clearProgress).coerceIn(0f, 1f) else 1f
                    if (textAlpha > 0.02f) {
                        drawCenteredNumber(
                            number = block.displayNumber,
                            left = left,
                            top = top,
                            rectW = rectW,
                            rectH = rectH,
                            textColor = BlockInkDark.copy(alpha = textAlpha),
                            textMeasurer = textMeasurer
                        )
                    }
                }

                // 2.5 Zona objetivo del tutorial interactivo (resalta dónde encajar con piezas adyacentes)
                if (tutorialTarget != null) {
                    val tLeft = tutorialTarget.x * cellW + cellPad
                    val tTop = tutorialTarget.y * cellH + cellPad
                    val tW = tutorialTarget.width * cellW - cellPad * 2f
                    val tH = tutorialTarget.height * cellH - cellPad * 2f
                    val tCorner = CornerRadius(cellW * 0.22f, cellW * 0.22f)
                    val isAligned = activePiece != null &&
                        tutorialTarget.isReadyToDrop &&
                        activePiece.x == tutorialTarget.x &&
                        activePiece.width == tutorialTarget.width &&
                        activePiece.height == tutorialTarget.height
                    val targetAccent = when {
                        isAligned -> EmeraldMint
                        tutorialTarget.isReadyToDrop -> AmberWarm
                        else -> CyanElectric
                    }

                    drawRoundRect(
                        color = targetAccent.copy(alpha = 0.16f + tutorialPulse * 0.14f),
                        topLeft = Offset(tLeft, tTop),
                        size = Size(tW, tH),
                        cornerRadius = tCorner
                    )
                    drawRoundRect(
                        color = targetAccent.copy(alpha = 0.55f + tutorialPulse * 0.45f),
                        topLeft = Offset(tLeft, tTop),
                        size = Size(tW, tH),
                        cornerRadius = tCorner,
                        style = Stroke(
                            width = if (isAligned) 4.2f else 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 7f))
                        )
                    )
                    drawCenteredLabel(
                        text = if (isAligned) "↓ ${tutorialTarget.label}" else tutorialTarget.label,
                        left = tLeft,
                        top = tTop,
                        rectW = tW,
                        rectH = tH,
                        textColor = BlockInkDark.copy(alpha = 0.85f),
                        textMeasurer = textMeasurer
                    )

                    if (tutorialTarget.isReadyToDrop && activePiece != null && activePiece.x != tutorialTarget.x) {
                        val moveRightHint = activePiece.x < tutorialTarget.x
                        val arrowLabel = if (moveRightHint) "→→" else "←←"
                        val hintTop = ((activePiece.y + activePiece.height + 0.35f) * cellH)
                            .coerceAtMost((tutorialTarget.y - 1.5f) * cellH)
                        drawCenteredLabel(
                            text = arrowLabel,
                            left = tLeft,
                            top = hintTop,
                            rectW = tW,
                            rectH = cellH * 1.2f,
                            textColor = AmberWarm,
                            textMeasurer = textMeasurer
                        )
                    }
                }

                // 3. Silueta fantasma de aterrizaje
                if (activePiece != null && ghostY != null && ghostY >= activePiece.y) {
                    val palette = tilePaletteFor(activePiece.area)
                    val gLeft = activePiece.x * cellW + cellPad
                    val gTop = ghostY * cellH + cellPad
                    val gW = activePiece.width * cellW - cellPad * 2f
                    val gH = activePiece.height * cellH - cellPad * 2f
                    drawRoundRect(
                        color = palette.fill.copy(alpha = 0.35f),
                        topLeft = Offset(gLeft, gTop),
                        size = Size(gW, gH),
                        cornerRadius = CornerRadius(cellW * 0.22f, cellW * 0.22f)
                    )
                    drawRoundRect(
                        color = palette.border,
                        topLeft = Offset(gLeft, gTop),
                        size = Size(gW, gH),
                        cornerRadius = CornerRadius(cellW * 0.22f, cellW * 0.22f),
                        style = Stroke(
                            width = 2.2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                        )
                    )
                }

                // 4. Pieza activa cayendo con color claro, cuadrícula interna y número centrado
                if (activePiece != null) {
                    val palette = tilePaletteFor(activePiece.area)
                    val left = activePiece.x * cellW + cellPad
                    val top = activePiece.y * cellH + cellPad
                    val rectW = activePiece.width * cellW - cellPad * 2f
                    val rectH = activePiece.height * cellH - cellPad * 2f
                    val corner = CornerRadius(cellW * 0.22f, cellW * 0.22f)

                    // Relleno claro luminoso
                    drawRoundRect(
                        color = palette.fill,
                        topLeft = Offset(left, top),
                        size = Size(rectW, rectH),
                        cornerRadius = corner
                    )

                    // Cuadrícula interna de celdas unitarias
                    val innerGridColor = palette.border.copy(alpha = 0.35f)
                    for (c in 1 until activePiece.width) {
                        val lx = (activePiece.x + c) * cellW
                        drawLine(
                            color = innerGridColor,
                            start = Offset(lx, top + 2f),
                            end = Offset(lx, top + rectH - 2f),
                            strokeWidth = 1.4f
                        )
                    }
                    for (r in 1 until activePiece.height) {
                        val ly = (activePiece.y + r) * cellH
                        drawLine(
                            color = innerGridColor,
                            start = Offset(left + 2f, ly),
                            end = Offset(left + rectW - 2f, ly),
                            strokeWidth = 1.4f
                        )
                    }

                    // Brillo superior claro
                    drawRoundRect(
                        color = palette.highlight.copy(alpha = 0.75f),
                        topLeft = Offset(left + 2f, top + 2f),
                        size = Size(rectW - 4f, min(rectH * 0.24f, cellH * 0.35f)),
                        cornerRadius = CornerRadius(cellW * 0.14f, cellW * 0.14f)
                    )

                    // Borde definido para resaltar la pieza activa sobre el tablero claro
                    drawRoundRect(
                        color = palette.border,
                        topLeft = Offset(left, top),
                        size = Size(rectW, rectH),
                        cornerRadius = corner,
                        style = Stroke(width = 3f)
                    )

                    // Etiqueta alternante: multiplicación (ancho×alto) <-> resultado (área)
                    val activeLabel = if (showMultiplication) {
                        when {
                            activePiece.width == 1 && activePiece.height >= 3 ->
                                "${activePiece.width}\n×\n${activePiece.height}"
                            activePiece.width == 1 && activePiece.height == 2 ->
                                "${activePiece.width}\n×${activePiece.height}"
                            activePiece.width >= 3 ->
                                "${activePiece.width} × ${activePiece.height}"
                            else ->
                                "${activePiece.width}×${activePiece.height}"
                        }
                    } else {
                        activePiece.area.toString()
                    }
                    drawCenteredLabel(
                        text = activeLabel,
                        left = left,
                        top = top,
                        rectW = rectW,
                        rectH = rectH,
                        textColor = palette.content,
                        textMeasurer = textMeasurer
                    )
                }
            }

            // Banner flotante de feedback (filas completadas / subida de nivel)
            AnimatedVisibility(
                visible = visibleFeedback != null,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
            ) {
                visibleFeedback?.let { fb ->
                    val accent = if (fb.isMultiLine) AmberWarm else EmeraldMint
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianSurface.copy(alpha = 0.95f))
                            .border(1.5.dp, accent, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = fb.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = accent
                        )
                        Text(
                            text = fb.subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCenteredNumber(
    number: Int,
    left: Float,
    top: Float,
    rectW: Float,
    rectH: Float,
    textColor: Color,
    textMeasurer: TextMeasurer
) {
    drawCenteredLabel(
        text = number.toString(),
        left = left,
        top = top,
        rectW = rectW,
        rectH = rectH,
        textColor = textColor,
        textMeasurer = textMeasurer
    )
}

private fun DrawScope.drawCenteredLabel(
    text: String,
    left: Float,
    top: Float,
    rectW: Float,
    rectH: Float,
    textColor: Color,
    textMeasurer: TextMeasurer
) {
    val lines = text.split("\n")
    val maxLineLen = lines.maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
    val widthPerCharSp = (rectW * 0.85f / (maxLineLen * 0.65f)) / density
    val heightPerLineSp = (rectH * 0.78f / lines.size) / density
    var fontSp = min(widthPerCharSp, heightPerLineSp).coerceIn(8f, 32f)

    var layout = textMeasurer.measure(
        text = text,
        style = TextStyle(
            fontFamily = SpaceGroteskFont,
            fontWeight = FontWeight.Bold,
            fontSize = fontSp.sp,
            lineHeight = (fontSp * 1.05f).sp,
            textAlign = TextAlign.Center,
            color = textColor
        )
    )

    val maxW = rectW * 0.9f
    val maxH = rectH * 0.9f
    if (layout.size.width > maxW || layout.size.height > maxH) {
        val scaleW = if (layout.size.width > 0) maxW / layout.size.width else 1f
        val scaleH = if (layout.size.height > 0) maxH / layout.size.height else 1f
        fontSp = (fontSp * min(scaleW, scaleH)).coerceAtLeast(7.5f)
        layout = textMeasurer.measure(
            text = text,
            style = TextStyle(
                fontFamily = SpaceGroteskFont,
                fontWeight = FontWeight.Bold,
                fontSize = fontSp.sp,
                lineHeight = (fontSp * 1.05f).sp,
                textAlign = TextAlign.Center,
                color = textColor
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

@Preview(showBackground = true)
@Composable
private fun GameBoardCanvasPreview() {
    MultiDropTheme {
        val sampleBoard = FactrisEngine.createEmptyBoard().mapIndexed { r, row ->
            row.mapIndexed { c, _ ->
                when {
                    r in 18..19 && c in 0..5 -> BoardCell(areaValue = 12, blockId = 1L)
                    r in 17..19 && c in 6..7 -> BoardCell(areaValue = 6, blockId = 2L)
                    else -> null
                }
            }
        }
        val active = ActiveRectPiece(
            area = 12,
            factors = FactrisEngine.factorPairsFor(12),
            factorIndex = 2,
            x = 4,
            y = 4
        )
        GameBoardCanvas(
            board = sampleBoard,
            activePiece = active,
            ghostY = 15,
            feedback = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        )
    }
}
