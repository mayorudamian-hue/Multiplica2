package com.example.game

import kotlin.random.Random

data class VersusChallengeSpec(
    val targetArea: Int,
    val targetWidth: Int,
    val targetHeight: Int
)

data class VersusRoundBoard(
    val board: List<List<BoardCell?>>,
    val activePiece: ActiveRectPiece,
    val gapX: Int,
    val gapY: Int,
    val targetArea: Int,
    val targetWidth: Int,
    val targetHeight: Int
)

object VersusEngine {

    const val TOTAL_ROUNDS = 5
    const val WINS_TO_CLINCH = 3

    private val roundPools: List<List<VersusChallengeSpec>> = listOf(
        // Ronda 1: objetivos ágiles de 2 filas
        listOf(
            VersusChallengeSpec(8, 4, 2),
            VersusChallengeSpec(10, 5, 2),
            VersusChallengeSpec(12, 6, 2),
            VersusChallengeSpec(6, 3, 2)
        ),
        // Ronda 2: múltiplos de 3 filas y 2 filas
        listOf(
            VersusChallengeSpec(12, 4, 3),
            VersusChallengeSpec(15, 5, 3),
            VersusChallengeSpec(14, 7, 2),
            VersusChallengeSpec(9, 3, 3)
        ),
        // Ronda 3: áreas medias
        listOf(
            VersusChallengeSpec(16, 4, 4),
            VersusChallengeSpec(18, 6, 3),
            VersusChallengeSpec(20, 5, 4),
            VersusChallengeSpec(12, 3, 4)
        ),
        // Ronda 4: factores mayores
        listOf(
            VersusChallengeSpec(24, 6, 4),
            VersusChallengeSpec(21, 7, 3),
            VersusChallengeSpec(24, 8, 3),
            VersusChallengeSpec(18, 6, 3)
        ),
        // Ronda 5: cierre de alta concentración
        listOf(
            VersusChallengeSpec(28, 7, 4),
            VersusChallengeSpec(32, 8, 4),
            VersusChallengeSpec(24, 6, 4),
            VersusChallengeSpec(20, 5, 4)
        )
    )

    private const val ROOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generateRoomCode(random: Random = Random.Default): String {
        return buildString(6) {
            repeat(6) {
                append(ROOM_ALPHABET[random.nextInt(ROOM_ALPHABET.length)])
            }
        }
    }

    fun generateSeed(random: Random = Random.Default): Long {
        return random.nextLong(100_000L, 999_999_999L)
    }

    fun challengeForRound(roundSeed: Long, roundNumber: Int): VersusChallengeSpec {
        val poolIndex = (roundNumber - 1).coerceIn(roundPools.indices)
        val pool = roundPools[poolIndex]
        val rng = Random(roundSeed + roundNumber * 7919L)
        return pool[rng.nextInt(pool.size)]
    }

    fun buildRoundBoard(
        roundSeed: Long,
        roundNumber: Int,
        targetArea: Int,
        targetWidth: Int,
        targetHeight: Int
    ): VersusRoundBoard {
        val rng = Random(roundSeed + roundNumber * 104729L)
        val remainingCols = (BOARD_COLS - targetWidth).coerceAtLeast(0)

        // Repartir el espacio sobrante a izquierda y derecha para formar bloques adyacentes con área >= 4
        val validLeftSplits = (0..remainingCols).filter { left ->
            val right = remainingCols - left
            (left == 0 || left >= 2) && (right == 0 || right >= 2)
        }
        val leftWidth = if (validLeftSplits.isNotEmpty()) {
            validLeftSplits[rng.nextInt(validLeftSplits.size)]
        } else {
            remainingCols / 2
        }
        val rightWidth = remainingCols - leftWidth
        val gapX = leftWidth
        val gapY = (BOARD_ROWS - targetHeight).coerceIn(0, BOARD_ROWS - 1)

        val mutable = MutableList(BOARD_ROWS) { MutableList<BoardCell?>(BOARD_COLS) { null } }
        var blockSeq = 1000L + roundNumber * 100L

        if (leftWidth > 0) {
            val leftArea = leftWidth * targetHeight
            val leftId = blockSeq++
            for (r in gapY until BOARD_ROWS) {
                for (c in 0 until leftWidth) {
                    mutable[r][c] = BoardCell(areaValue = leftArea, blockId = leftId)
                }
            }
        }

        if (rightWidth > 0) {
            val rightArea = rightWidth * targetHeight
            val rightId = blockSeq++
            val rightStart = gapX + targetWidth
            for (r in gapY until BOARD_ROWS) {
                for (c in rightStart until BOARD_COLS) {
                    mutable[r][c] = BoardCell(areaValue = rightArea, blockId = rightId)
                }
            }
        }

        val factors = FactrisEngine.factorPairsFor(targetArea)
        val targetIndex = factors.indexOfFirst {
            it.width == targetWidth && it.height == targetHeight
        }.coerceAtLeast(0)

        // Iniciar con una factorización distinta a la objetivo para que ambos jugadores deban refactorizar
        val alternateIndices = factors.indices.filter { idx ->
            idx != targetIndex && factors[idx].width <= BOARD_COLS - 2 && factors[idx].height <= 6
        }
        val startIdx = when {
            alternateIndices.isNotEmpty() -> alternateIndices[rng.nextInt(alternateIndices.size)]
            factors.size > 1 -> (targetIndex + 1) % factors.size
            else -> targetIndex
        }

        val startDim = factors[startIdx]
        val maxStartX = (BOARD_COLS - startDim.width).coerceAtLeast(0)
        val spawnX = (maxStartX / 2).coerceIn(0, maxStartX)

        val piece = ActiveRectPiece(
            area = targetArea,
            factors = factors,
            factorIndex = startIdx,
            x = spawnX,
            y = 0
        )

        return VersusRoundBoard(
            board = mutable.map { it.toList() },
            activePiece = piece,
            gapX = gapX,
            gapY = gapY,
            targetArea = targetArea,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )
    }
}
