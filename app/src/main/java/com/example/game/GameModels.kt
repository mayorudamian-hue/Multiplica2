package com.example.game

const val BOARD_COLS = 12
const val BOARD_ROWS = 20

enum class GameMode {
    ARCADE,
    PRACTICE
}

data class FactorDimension(
    val width: Int,
    val height: Int
) {
    val label: String get() = "$width × $height"
}

data class BoardCell(
    val areaValue: Int,
    val blockId: Long,
    val isClearing: Boolean = false
)

data class ActiveRectPiece(
    val area: Int,
    val factors: List<FactorDimension>,
    val factorIndex: Int,
    val x: Int,
    val y: Int
) {
    val currentDim: FactorDimension get() = factors[factorIndex.coerceIn(factors.indices)]
    val width: Int get() = currentDim.width
    val height: Int get() = currentDim.height
    val isPrime: Boolean get() = FactrisEngine.isPrime(area)
}

data class GameFeedback(
    val title: String,
    val subtitle: String,
    val isMultiLine: Boolean = false,
    val id: Long = 0L
)

enum class AdaptiveDifficultyTier {
    SUPPORTIVE,
    BALANCED,
    CHALLENGE
}

data class AdaptiveDifficultyState(
    val tier: AdaptiveDifficultyTier = AdaptiveDifficultyTier.BALANCED,
    val recentSuccessRate: Float = 0.5f,
    val recentOutcomes: List<Boolean> = emptyList(),
    val placementsSinceClear: Int = 0,
    val minFactor: Int = 2,
    val maxFactor: Int = 8,
    val speedFactor: Float = 1.0f
) {
    val successPercentage: Int get() = (recentSuccessRate * 100f).toInt().coerceIn(0, 100)
    val factorRangeLabel: String get() = "$minFactor–$maxFactor"
}

enum class TutorialStep {
    NONE,
    REFACTOR_TO_FIT,
    PLACE_ADJACENT_TO_CLEAR,
    COMBINE_THREE_PIECES,
    COMPLETED
}

data class TutorialTargetZone(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val label: String,
    val isReadyToDrop: Boolean = false
)

data class TutorialState(
    val step: TutorialStep = TutorialStep.NONE,
    val targetZone: TutorialTargetZone? = null,
    val targetFactorIndex: Int? = null
) {
    val isActive: Boolean get() = step != TutorialStep.NONE && step != TutorialStep.COMPLETED
    val stepNumber: Int
        get() = when (step) {
            TutorialStep.REFACTOR_TO_FIT -> 1
            TutorialStep.PLACE_ADJACENT_TO_CLEAR -> 2
            TutorialStep.COMBINE_THREE_PIECES -> 3
            else -> 0
        }
    val totalSteps: Int get() = 3
}

data class GameConfig(
    val mode: GameMode = GameMode.ARCADE,
    val selectedTable: Int = 0, // 0 = Clásico (Todas), 2..10 = múltiplos de esa tabla
    val includePrimes: Boolean = true,
    val showFactorPills: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = true
)

data class GameUiState(
    val board: List<List<BoardCell?>> = emptyList(),
    val activePiece: ActiveRectPiece? = null,
    val nextPiece: ActiveRectPiece? = null,
    val ghostY: Int? = null,
    val score: Int = 0,
    val bestScore: Int = 0,
    val isNewRecord: Boolean = false,
    val level: Int = 1,
    val linesCleared: Int = 0,
    val blocksPlaced: Int = 0,
    val bestMultiClear: Int = 0,
    val dropIntervalMs: Long? = 1050L,
    val isPaused: Boolean = false,
    val isClearingRows: Boolean = false,
    val isGameOver: Boolean = false,
    val config: GameConfig = GameConfig(),
    val adaptiveState: AdaptiveDifficultyState = AdaptiveDifficultyState(),
    val tutorialState: TutorialState = TutorialState(),
    val feedback: GameFeedback? = null
)
