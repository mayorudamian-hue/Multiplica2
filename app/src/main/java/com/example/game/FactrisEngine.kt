package com.example.game

import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

object FactrisEngine {

    const val BASE_DROP_INTERVAL_MS = 1050L
    const val MIN_DROP_INTERVAL_MS = 200L
    const val MIN_ADAPTIVE_DROP_INTERVAL_MS = 160L
    const val MAX_ADAPTIVE_DROP_INTERVAL_MS = 1400L
    const val ADAPTIVE_WINDOW_SIZE = 8
    const val DROUGHT_MISS_THRESHOLD = 4
    const val DANGER_STACK_HEIGHT = 15

    private var nextBlockId = 1L

    fun nextId(): Long = nextBlockId++

    fun createEmptyBoard(): List<List<BoardCell?>> =
        List(BOARD_ROWS) { List(BOARD_COLS) { null } }

    fun isPrime(n: Int): Boolean {
        if (n < 2) return false
        if (n == 2) return true
        if (n % 2 == 0) return false
        var d = 3
        while (d * d <= n) {
            if (n % d == 0) return false
            d += 2
        }
        return true
    }

    /**
     * Cuenta celdas vacías atrapadas debajo de al menos una celda ocupada en cada columna.
     */
    fun countBoardHoles(board: List<List<BoardCell?>>): Int {
        var holes = 0
        for (c in 0 until BOARD_COLS) {
            var seenBlock = false
            for (r in 0 until BOARD_ROWS) {
                val cell = board.getOrNull(r)?.getOrNull(c)
                if (cell != null) {
                    seenBlock = true
                } else if (seenBlock) {
                    holes++
                }
            }
        }
        return holes
    }

    /**
     * Altura máxima de la pila ocupada desde el fondo del tablero (0 = vacío, 20 = tope).
     */
    fun maxStackHeight(board: List<List<BoardCell?>>): Int {
        for (r in 0 until BOARD_ROWS) {
            if (board[r].any { it != null }) {
                return BOARD_ROWS - r
            }
        }
        return 0
    }

    /**
     * Evalúa el resultado reciente de colocar una pieza y actualiza el estado adaptativo
     * (tasa de acierto reciente, rango de factores y multiplicador de velocidad).
     */
    fun recordPlacementOutcome(
        current: AdaptiveDifficultyState,
        boardBeforeLock: List<List<BoardCell?>>,
        boardAfterLock: List<List<BoardCell?>>,
        rowsCleared: Int,
        level: Int,
        selectedTable: Int
    ): AdaptiveDifficultyState {
        val holesBefore = countBoardHoles(boardBeforeLock)
        val holesAfter = countBoardHoles(boardAfterLock)
        val holesCreated = (holesAfter - holesBefore).coerceAtLeast(0)
        val stackHeight = maxStackHeight(boardAfterLock)

        val updatedOutcomes = current.recentOutcomes.toMutableList()
        val nextPlacementsSinceClear: Int

        if (rowsCleared > 0) {
            nextPlacementsSinceClear = 0
            // Un combo de varias filas refuerza el dominio en la ventana reciente
            repeat(rowsCleared.coerceAtMost(2)) {
                updatedOutcomes.add(true)
            }
        } else {
            nextPlacementsSinceClear = current.placementsSinceClear + 1
            when {
                holesCreated > 0 -> updatedOutcomes.add(false)
                stackHeight >= DANGER_STACK_HEIGHT -> updatedOutcomes.add(false)
                nextPlacementsSinceClear >= DROUGHT_MISS_THRESHOLD -> updatedOutcomes.add(false)
                else -> {
                    // Colocación limpia sin huecos mientras construye una fila: no penaliza
                }
            }
        }

        val trimmedOutcomes = if (updatedOutcomes.size > ADAPTIVE_WINDOW_SIZE) {
            updatedOutcomes.takeLast(ADAPTIVE_WINDOW_SIZE)
        } else {
            updatedOutcomes
        }

        return computeAdaptiveState(
            recentOutcomes = trimmedOutcomes,
            placementsSinceClear = nextPlacementsSinceClear,
            level = level,
            selectedTable = selectedTable
        )
    }

    fun computeAdaptiveState(
        recentOutcomes: List<Boolean>,
        placementsSinceClear: Int = 0,
        level: Int = 1,
        selectedTable: Int = 0
    ): AdaptiveDifficultyState {
        // Suavizado bayesiano (1.5 / 3.0 = 0.50 inicial) para evitar saltos bruscos en la 1ª jugada
        val successes = recentOutcomes.count { it }
        val smoothedRate = ((successes + 1.5f) / (recentOutcomes.size + 3.0f)).coerceIn(0f, 1f)
        val tier = when {
            smoothedRate < 0.38f -> AdaptiveDifficultyTier.SUPPORTIVE
            smoothedRate >= 0.72f -> AdaptiveDifficultyTier.CHALLENGE
            else -> AdaptiveDifficultyTier.BALANCED
        }
        val (minFactor, maxFactor) = adaptiveFactorBounds(
            tier = tier,
            level = level,
            selectedTable = selectedTable
        )
        val speedFactor = adaptiveSpeedMultiplier(smoothedRate)

        return AdaptiveDifficultyState(
            tier = tier,
            recentSuccessRate = smoothedRate,
            recentOutcomes = recentOutcomes,
            placementsSinceClear = placementsSinceClear,
            minFactor = minFactor,
            maxFactor = maxFactor,
            speedFactor = speedFactor
        )
    }

    fun adaptiveFactorBounds(
        tier: AdaptiveDifficultyTier,
        level: Int,
        selectedTable: Int
    ): Pair<Int, Int> {
        if (selectedTable in 2..10) {
            return when (tier) {
                AdaptiveDifficultyTier.SUPPORTIVE -> 1 to 4
                AdaptiveDifficultyTier.BALANCED -> 1 to when {
                    level <= 1 -> 5
                    level == 2 -> 7
                    else -> 9
                }
                AdaptiveDifficultyTier.CHALLENGE -> 2 to 12
            }
        }
        return when (tier) {
            AdaptiveDifficultyTier.SUPPORTIVE -> 2 to 5
            AdaptiveDifficultyTier.BALANCED -> 2 to when {
                level <= 1 -> 6
                level == 2 -> 8
                else -> 9
            }
            AdaptiveDifficultyTier.CHALLENGE -> 3 to 12
        }
    }

    fun adaptiveSpeedMultiplier(recentSuccessRate: Float): Float {
        val rate = recentSuccessRate.coerceIn(0f, 1f)
        return if (rate <= 0.5f) {
            0.78f + (rate / 0.5f) * 0.22f
        } else {
            1.00f + ((rate - 0.5f) / 0.5f) * 0.28f
        }
    }

    /**
     * Devuelve todas las parejas de factores (ancho x alto) de [area] que caben en el tablero,
     * ordenadas de más ancha a más alta (ej. 12x1, 6x2, 4x3, 3x4, 2x6, 1x12).
     */
    fun factorPairsFor(
        area: Int,
        maxWidth: Int = BOARD_COLS,
        maxHeight: Int = BOARD_ROWS
    ): List<FactorDimension> {
        val result = mutableListOf<FactorDimension>()
        for (h in 1..area) {
            if (area % h == 0) {
                val w = area / h
                if (w in 1..maxWidth && h in 1..maxHeight) {
                    result.add(FactorDimension(width = w, height = h))
                }
            }
        }
        return result
    }

    fun canPlace(
        board: List<List<BoardCell?>>,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ): Boolean {
        if (x < 0 || x + width > BOARD_COLS) return false
        if (y + height > BOARD_ROWS) return false
        for (r in 0 until height) {
            val boardR = y + r
            if (boardR < 0) continue
            for (c in 0 until width) {
                val boardC = x + c
                if (board[boardR][boardC] != null) return false
            }
        }
        return true
    }

    /**
     * Refactoriza el rectángulo actual al siguiente par de factores válido,
     * aplicando pequeños ajustes horizontales o verticales (wall-kicks) si toca un borde.
     */
    fun refactorNext(
        board: List<List<BoardCell?>>,
        piece: ActiveRectPiece
    ): ActiveRectPiece? {
        if (piece.factors.size <= 1) return piece
        for (offset in 1 until piece.factors.size) {
            val targetIdx = (piece.factorIndex + offset) % piece.factors.size
            val candidate = tryFitFactorIndex(board, piece, targetIdx)
            if (candidate != null) return candidate
        }
        return null
    }

    fun refactorToIndex(
        board: List<List<BoardCell?>>,
        piece: ActiveRectPiece,
        targetIndex: Int
    ): ActiveRectPiece? {
        if (targetIndex !in piece.factors.indices) return null
        if (targetIndex == piece.factorIndex) return piece
        return tryFitFactorIndex(board, piece, targetIndex)
    }

    private fun tryFitFactorIndex(
        board: List<List<BoardCell?>>,
        piece: ActiveRectPiece,
        targetIndex: Int
    ): ActiveRectPiece? {
        val dim = piece.factors[targetIndex]
        val centerOffset = (piece.width - dim.width) / 2
        val baseX = (piece.x + centerOffset).coerceIn(0, (BOARD_COLS - dim.width).coerceAtLeast(0))

        val xCandidates = buildList {
            add(baseX)
            add(piece.x.coerceIn(0, (BOARD_COLS - dim.width).coerceAtLeast(0)))
            for (delta in 1..BOARD_COLS) {
                if (baseX - delta >= 0) add(baseX - delta)
                if (baseX + delta + dim.width <= BOARD_COLS) add(baseX + delta)
            }
        }.distinct()

        val yCandidates = listOf(piece.y, (piece.y - 1).coerceAtLeast(0), (piece.y - 2).coerceAtLeast(0))

        for (candY in yCandidates) {
            for (candX in xCandidates) {
                if (canPlace(board, dim.width, dim.height, candX, candY)) {
                    return piece.copy(
                        factorIndex = targetIndex,
                        x = candX,
                        y = candY
                    )
                }
            }
        }
        return null
    }

    fun computeGhostY(
        board: List<List<BoardCell?>>,
        piece: ActiveRectPiece
    ): Int {
        var y = piece.y
        while (canPlace(board, piece.width, piece.height, piece.x, y + 1)) {
            y++
        }
        return y
    }

    fun lockPiece(
        board: List<List<BoardCell?>>,
        piece: ActiveRectPiece
    ): List<List<BoardCell?>> {
        val mutable = board.map { it.toMutableList() }.toMutableList()
        val id = nextId()
        for (r in 0 until piece.height) {
            for (c in 0 until piece.width) {
                val boardR = piece.y + r
                val boardC = piece.x + c
                if (boardR in 0 until BOARD_ROWS && boardC in 0 until BOARD_COLS) {
                    mutable[boardR][boardC] = BoardCell(
                        areaValue = piece.area,
                        blockId = id
                    )
                }
            }
        }
        return mutable.map { it.toList() }
    }

    fun findFullRows(board: List<List<BoardCell?>>): List<Int> {
        return (0 until BOARD_ROWS).filter { r ->
            board[r].all { it != null }
        }
    }

    fun markRowsClearing(
        board: List<List<BoardCell?>>,
        rows: List<Int>
    ): List<List<BoardCell?>> {
        val rowSet = rows.toSet()
        return board.mapIndexed { r, row ->
            if (r in rowSet) {
                row.map { it?.copy(isClearing = true) }
            } else {
                row
            }
        }
    }

    fun clearRows(
        board: List<List<BoardCell?>>,
        rowsToClear: List<Int>
    ): List<List<BoardCell?>> {
        if (rowsToClear.isEmpty()) return board
        val clearSet = rowsToClear.toSet()
        val survivingRows = board.filterIndexed { r, _ -> r !in clearSet }
        val emptyTop = List(clearSet.size) { List<BoardCell?>(BOARD_COLS) { null } }
        return emptyTop + survivingRows
    }

    /**
     * Puntuación exponencial fiel a Factris:
     * 1ª fila = 10 pts/celda, 2ª = 20 pts/celda, 3ª = 40 pts/celda, 4ª = 80 pts/celda...
     */
    fun calculateFactrisScore(rowsCleared: Int): Int {
        if (rowsCleared <= 0) return 0
        var total = 0
        var pointsPerTile = 10
        repeat(rowsCleared) {
            total += pointsPerTile * BOARD_COLS
            pointsPerTile *= 2
        }
        return total
    }

    fun levelForLines(linesCleared: Int): Int {
        return 1 + (linesCleared.coerceAtLeast(0) / 4)
    }

    fun dropIntervalMs(
        mode: GameMode,
        linesCleared: Int,
        recentSuccessRate: Float = 0.5f
    ): Long? {
        if (mode == GameMode.PRACTICE) return null
        val safeLines = linesCleared.coerceAtLeast(0)
        val baseInterval = MIN_DROP_INTERVAL_MS +
            (BASE_DROP_INTERVAL_MS - MIN_DROP_INTERVAL_MS) * 0.91.pow(safeLines)
        val speedFactor = adaptiveSpeedMultiplier(recentSuccessRate)
        val adjusted = baseInterval / speedFactor
        return adjusted.roundToLong().coerceIn(
            MIN_ADAPTIVE_DROP_INTERVAL_MS,
            MAX_ADAPTIVE_DROP_INTERVAL_MS
        )
    }

    fun speedMultiplier(dropIntervalMs: Long?): Float {
        if (dropIntervalMs == null || dropIntervalMs <= 0L) return 1f
        return BASE_DROP_INTERVAL_MS.toFloat() / dropIntervalMs.toFloat()
    }

    fun areaPoolFor(
        level: Int,
        selectedTable: Int,
        includePrimes: Boolean,
        adaptiveState: AdaptiveDifficultyState? = null
    ): List<Int> {
        val tier = adaptiveState?.tier ?: AdaptiveDifficultyTier.BALANCED
        val (minFactor, maxFactor) = adaptiveState?.let { it.minFactor to it.maxFactor }
            ?: adaptiveFactorBounds(tier = tier, level = level, selectedTable = selectedTable)

        if (selectedTable in 2..10) {
            val multiples = (minFactor..maxFactor)
                .map { selectedTable * it }
                .filter { area ->
                    factorPairsFor(area).any { it.width <= BOARD_COLS && it.height <= 12 }
                }
            return if (multiples.isNotEmpty()) multiples else listOf(selectedTable)
        }

        // Construir productos compuestos cuyos factores caen dentro del rango adaptativo [minFactor..maxFactor]
        val adaptiveComposites = buildSet {
            for (a in minFactor..maxFactor) {
                for (b in a..maxFactor) {
                    val area = a * b
                    if (area >= 4 && factorPairsFor(area).any { it.width <= BOARD_COLS && it.height <= 12 }) {
                        add(area)
                    }
                }
            }
        }.sorted()

        val composites = when (tier) {
            AdaptiveDifficultyTier.SUPPORTIVE -> adaptiveComposites.filter { it <= 20 }.ifEmpty {
                listOf(4, 6, 8, 9, 10, 12)
            }
            AdaptiveDifficultyTier.BALANCED -> {
                val maxAllowedArea = when {
                    level <= 1 -> 20
                    level == 2 -> 30
                    else -> 40
                }
                adaptiveComposites.filter { it <= maxAllowedArea }.ifEmpty {
                    listOf(4, 6, 8, 9, 10, 12, 16, 18, 20)
                }
            }
            AdaptiveDifficultyTier.CHALLENGE -> {
                adaptiveComposites.filter { it >= 9 }.ifEmpty {
                    listOf(12, 16, 18, 20, 24, 28, 30, 36, 42, 48, 54, 56, 64, 72)
                }
            }
        }

        // En modo de refuerzo (SUPPORTIVE) pausamos los primos para facilitar múltiples factorizaciones
        if (!includePrimes || tier == AdaptiveDifficultyTier.SUPPORTIVE || (tier == AdaptiveDifficultyTier.BALANCED && level <= 1)) {
            return composites
        }

        val primes = when {
            tier == AdaptiveDifficultyTier.CHALLENGE -> listOf(2, 3, 5, 7, 11)
            level == 2 -> listOf(3, 5)
            level == 3 -> listOf(2, 3, 5, 7)
            else -> listOf(2, 3, 5, 7, 11)
        }

        return composites + primes
    }

    fun generatePiece(
        config: GameConfig,
        level: Int,
        lastArea: Int? = null,
        adaptiveState: AdaptiveDifficultyState? = null,
        random: Random = Random.Default
    ): ActiveRectPiece {
        val pool = areaPoolFor(
            level = level,
            selectedTable = config.selectedTable,
            includePrimes = config.includePrimes,
            adaptiveState = adaptiveState
        )
        val filtered = pool.filter { it != lastArea }.ifEmpty { pool }
        val area = filtered[random.nextInt(filtered.size)]
        val factors = factorPairsFor(area)

        val startIdx = if (factors.size >= 3) {
            val midCandidates = factors.indices.filter { idx ->
                val dim = factors[idx]
                dim.width <= BOARD_COLS - 2 && dim.height <= 6
            }
            if (midCandidates.isNotEmpty()) {
                midCandidates[random.nextInt(midCandidates.size)]
            } else {
                random.nextInt(factors.size)
            }
        } else {
            random.nextInt(factors.size)
        }

        val startDim = factors[startIdx]
        val spawnX = ((BOARD_COLS - startDim.width) / 2).coerceAtLeast(0)
        return ActiveRectPiece(
            area = area,
            factors = factors,
            factorIndex = startIdx,
            x = spawnX,
            y = 0
        )
    }

    data class TutorialSetup(
        val board: List<List<BoardCell?>>,
        val activePiece: ActiveRectPiece,
        val nextPiece: ActiveRectPiece,
        val tutorialState: TutorialState
    )

    private fun placeSettledRect(
        mutableBoard: MutableList<MutableList<BoardCell?>>,
        areaValue: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        val blockId = nextId()
        for (r in y until (y + height)) {
            for (c in x until (x + width)) {
                if (r in 0 until BOARD_ROWS && c in 0 until BOARD_COLS) {
                    mutableBoard[r][c] = BoardCell(areaValue = areaValue, blockId = blockId)
                }
            }
        }
    }

    fun tutorialSetupFor(step: TutorialStep): TutorialSetup {
        val mutable = MutableList(BOARD_ROWS) { MutableList<BoardCell?>(BOARD_COLS) { null } }
        val factors12 = factorPairsFor(12)
        val factors6 = factorPairsFor(6)
        val idx12_6x2 = factors12.indexOfFirst { it.width == 6 && it.height == 2 }.coerceAtLeast(0)
        val idx12_4x3 = factors12.indexOfFirst { it.width == 4 && it.height == 3 }.coerceAtLeast(0)
        val idx6_3x2 = factors6.indexOfFirst { it.width == 3 && it.height == 2 }.coerceAtLeast(0)
        val idx6_2x3 = factors6.indexOfFirst { it.width == 2 && it.height == 3 }.coerceAtLeast(0)

        return when (step) {
            TutorialStep.REFACTOR_TO_FIT -> {
                placeSettledRect(mutable, areaValue = 12, x = 0, y = 18, width = 6, height = 2)
                TutorialSetup(
                    board = mutable.map { it.toList() },
                    activePiece = ActiveRectPiece(
                        area = 12,
                        factors = factors12,
                        factorIndex = idx12_4x3,
                        x = 4,
                        y = 1
                    ),
                    nextPiece = ActiveRectPiece(
                        area = 6,
                        factors = factors6,
                        factorIndex = idx6_2x3,
                        x = 5,
                        y = 1
                    ),
                    tutorialState = TutorialState(
                        step = TutorialStep.REFACTOR_TO_FIT,
                        targetZone = TutorialTargetZone(
                            x = 6,
                            y = 18,
                            width = 6,
                            height = 2,
                            label = "6×2 = 12",
                            isReadyToDrop = false
                        ),
                        targetFactorIndex = idx12_6x2
                    )
                )
            }

            TutorialStep.PLACE_ADJACENT_TO_CLEAR -> {
                placeSettledRect(mutable, areaValue = 12, x = 0, y = 18, width = 6, height = 2)
                TutorialSetup(
                    board = mutable.map { it.toList() },
                    activePiece = ActiveRectPiece(
                        area = 12,
                        factors = factors12,
                        factorIndex = idx12_6x2,
                        x = 2,
                        y = 1
                    ),
                    nextPiece = ActiveRectPiece(
                        area = 6,
                        factors = factors6,
                        factorIndex = idx6_2x3,
                        x = 5,
                        y = 1
                    ),
                    tutorialState = TutorialState(
                        step = TutorialStep.PLACE_ADJACENT_TO_CLEAR,
                        targetZone = TutorialTargetZone(
                            x = 6,
                            y = 18,
                            width = 6,
                            height = 2,
                            label = "6×2 = 12",
                            isReadyToDrop = true
                        ),
                        targetFactorIndex = idx12_6x2
                    )
                )
            }

            TutorialStep.COMBINE_THREE_PIECES -> {
                placeSettledRect(mutable, areaValue = 8, x = 0, y = 18, width = 4, height = 2)
                placeSettledRect(mutable, areaValue = 10, x = 7, y = 18, width = 5, height = 2)
                TutorialSetup(
                    board = mutable.map { it.toList() },
                    activePiece = ActiveRectPiece(
                        area = 6,
                        factors = factors6,
                        factorIndex = idx6_2x3,
                        x = 5,
                        y = 1
                    ),
                    nextPiece = ActiveRectPiece(
                        area = 12,
                        factors = factors12,
                        factorIndex = idx12_4x3,
                        x = 4,
                        y = 0
                    ),
                    tutorialState = TutorialState(
                        step = TutorialStep.COMBINE_THREE_PIECES,
                        targetZone = TutorialTargetZone(
                            x = 4,
                            y = 18,
                            width = 3,
                            height = 2,
                            label = "3×2 = 6",
                            isReadyToDrop = false
                        ),
                        targetFactorIndex = idx6_3x2
                    )
                )
            }

            TutorialStep.NONE, TutorialStep.COMPLETED -> {
                TutorialSetup(
                    board = mutable.map { it.toList() },
                    activePiece = ActiveRectPiece(
                        area = 12,
                        factors = factors12,
                        factorIndex = idx12_4x3,
                        x = 4,
                        y = 0
                    ),
                    nextPiece = ActiveRectPiece(
                        area = 6,
                        factors = factors6,
                        factorIndex = idx6_3x2,
                        x = 4,
                        y = 0
                    ),
                    tutorialState = TutorialState(step = step)
                )
            }
        }
    }
}
