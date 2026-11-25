package com.example

import com.example.game.ActiveRectPiece
import com.example.game.AdaptiveDifficultyTier
import com.example.game.BOARD_COLS
import com.example.game.BOARD_ROWS
import com.example.game.BoardCell
import com.example.game.FactorDimension
import com.example.game.FactrisEngine
import com.example.game.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FactrisEngineTest {

    @Test
    fun `factorPairsFor 12 returns all factorizations fitting 12x20 board`() {
        val pairs = FactrisEngine.factorPairsFor(12)
        assertEquals(
            listOf(
                FactorDimension(12, 1),
                FactorDimension(6, 2),
                FactorDimension(4, 3),
                FactorDimension(3, 4),
                FactorDimension(2, 6),
                FactorDimension(1, 12)
            ),
            pairs
        )
    }

    @Test
    fun `refactorNext cycles factor pairs and applies wall kick near right edge`() {
        val board = FactrisEngine.createEmptyBoard()
        val factors = FactrisEngine.factorPairsFor(12)
        val piece = ActiveRectPiece(
            area = 12,
            factors = factors,
            factorIndex = 2, // 4x3
            x = 8,
            y = 5
        )
        val next1 = FactrisEngine.refactorNext(board, piece)
        assertNotNull(next1)
        assertEquals(FactorDimension(3, 4), next1!!.currentDim)

        val skinny = ActiveRectPiece(
            area = 12,
            factors = factors,
            factorIndex = 5, // 1x12
            x = 11,
            y = 2
        )
        val morphed = FactrisEngine.refactorNext(board, skinny)
        assertNotNull(morphed)
        assertEquals(FactorDimension(12, 1), morphed!!.currentDim)
        assertEquals(0, morphed.x)
    }

    @Test
    fun `clearRows removes completed rows and drops upper blocks`() {
        val mutable = MutableList(BOARD_ROWS) { MutableList<BoardCell?>(BOARD_COLS) { null } }
        mutable[18][2] = BoardCell(4, 10L)
        mutable[18][3] = BoardCell(4, 10L)
        for (c in 0 until BOARD_COLS) {
            mutable[19][c] = BoardCell(12, 1L)
        }
        val board = mutable.map { it.toList() }

        val fullRows = FactrisEngine.findFullRows(board)
        assertEquals(listOf(19), fullRows)

        val cleared = FactrisEngine.clearRows(board, fullRows)
        assertNull(cleared[18][2])
        assertEquals(4, cleared[19][2]?.areaValue)
        assertEquals(4, cleared[19][3]?.areaValue)
    }

    @Test
    fun `calculateFactrisScore doubles points per tile for simultaneous rows`() {
        assertEquals(120, FactrisEngine.calculateFactrisScore(1))  // 10 * 12
        assertEquals(360, FactrisEngine.calculateFactrisScore(2))  // 120 + 240
        assertEquals(840, FactrisEngine.calculateFactrisScore(3))  // 120 + 240 + 480
        assertEquals(1800, FactrisEngine.calculateFactrisScore(4)) // 120 + 240 + 480 + 960
    }

    @Test
    fun `areaPoolFor specific table returns multiples of that table`() {
        val pool = FactrisEngine.areaPoolFor(level = 2, selectedTable = 6, includePrimes = true)
        assertTrue(pool.isNotEmpty())
        assertTrue(pool.all { it % 6 == 0 })
    }

    @Test
    fun `dropIntervalMs decreases as more rows are cleared and adapts to recent success rate`() {
        val speed0 = FactrisEngine.dropIntervalMs(GameMode.ARCADE, 0)!!
        val speed1 = FactrisEngine.dropIntervalMs(GameMode.ARCADE, 1)!!
        val speed4 = FactrisEngine.dropIntervalMs(GameMode.ARCADE, 4)!!
        val speed12 = FactrisEngine.dropIntervalMs(GameMode.ARCADE, 12)!!
        val speed100 = FactrisEngine.dropIntervalMs(GameMode.ARCADE, 100)!!

        assertTrue(speed1 < speed0)
        assertTrue(speed4 < speed1)
        assertTrue(speed12 < speed4)
        assertTrue(speed100 >= FactrisEngine.MIN_ADAPTIVE_DROP_INTERVAL_MS)
        assertTrue(FactrisEngine.speedMultiplier(speed12) > FactrisEngine.speedMultiplier(speed0))
        assertNull(FactrisEngine.dropIntervalMs(GameMode.PRACTICE, 10))
        assertEquals(2, FactrisEngine.levelForLines(4))
        assertEquals(3, FactrisEngine.levelForLines(8))

        // Adaptive speed: low success rate slows falling speed, high success rate speeds it up
        val supportiveInterval = FactrisEngine.dropIntervalMs(GameMode.ARCADE, 4, recentSuccessRate = 0.25f)!!
        val balancedInterval = FactrisEngine.dropIntervalMs(GameMode.ARCADE, 4, recentSuccessRate = 0.50f)!!
        val challengeInterval = FactrisEngine.dropIntervalMs(GameMode.ARCADE, 4, recentSuccessRate = 0.90f)!!
        assertTrue(supportiveInterval > balancedInterval)
        assertTrue(challengeInterval < balancedInterval)
    }

    @Test
    fun `adaptive difficulty expands factor range on consecutive clears and narrows on misses`() {
        val emptyBoard = FactrisEngine.createEmptyBoard()
        var adaptive = FactrisEngine.computeAdaptiveState(recentOutcomes = emptyList(), level = 1, selectedTable = 0)
        assertEquals(AdaptiveDifficultyTier.BALANCED, adaptive.tier)

        // 4 consecutive row clears push player into CHALLENGE tier with wider factor range (3..12)
        repeat(4) {
            adaptive = FactrisEngine.recordPlacementOutcome(
                current = adaptive,
                boardBeforeLock = emptyBoard,
                boardAfterLock = emptyBoard,
                rowsCleared = 1,
                level = 2,
                selectedTable = 0
            )
        }
        assertEquals(AdaptiveDifficultyTier.CHALLENGE, adaptive.tier)
        assertEquals(3, adaptive.minFactor)
        assertEquals(12, adaptive.maxFactor)
        assertTrue(adaptive.speedFactor > 1.15f)

        val challengeAreas = FactrisEngine.areaPoolFor(
            level = 2,
            selectedTable = 0,
            includePrimes = true,
            adaptiveState = adaptive
        )
        assertTrue(challengeAreas.any { it >= 36 })

        // Specific table mode also expands multiplier range in CHALLENGE
        val challengeTablePool = FactrisEngine.areaPoolFor(
            level = 2,
            selectedTable = 6,
            includePrimes = false,
            adaptiveState = FactrisEngine.computeAdaptiveState(
                recentOutcomes = List(6) { true },
                level = 2,
                selectedTable = 6
            )
        )
        assertTrue(challengeTablePool.contains(72)) // 6 * 12

        // Simulate creating buried holes repeatedly -> drops into SUPPORTIVE tier
        val boardWithHole = MutableList(BOARD_ROWS) { MutableList<BoardCell?>(BOARD_COLS) { null } }
        boardWithHole[18][0] = BoardCell(4, 99L) // leaves (19,0) empty underneath = hole
        val holeyBoard = boardWithHole.map { it.toList() }

        repeat(8) {
            adaptive = FactrisEngine.recordPlacementOutcome(
                current = adaptive,
                boardBeforeLock = emptyBoard,
                boardAfterLock = holeyBoard,
                rowsCleared = 0,
                level = 2,
                selectedTable = 0
            )
        }
        assertEquals(AdaptiveDifficultyTier.SUPPORTIVE, adaptive.tier)
        assertEquals(2, adaptive.minFactor)
        assertEquals(5, adaptive.maxFactor)
        assertTrue(adaptive.speedFactor < 0.90f)

        val supportiveAreas = FactrisEngine.areaPoolFor(
            level = 2,
            selectedTable = 0,
            includePrimes = true,
            adaptiveState = adaptive
        )
        assertTrue(supportiveAreas.all { it <= 20 })
        assertFalse(supportiveAreas.any { FactrisEngine.isPrime(it) })
    }

    @Test
    fun `tutorialSetupFor builds solvable adjacent-piece product scenarios`() {
        val step1 = FactrisEngine.tutorialSetupFor(com.example.game.TutorialStep.REFACTOR_TO_FIT)
        assertEquals(12, step1.activePiece.area)
        assertEquals(FactorDimension(4, 3), step1.activePiece.currentDim)
        assertNotNull(step1.tutorialState.targetFactorIndex)
        val refactoredStep1 = FactrisEngine.refactorToIndex(
            step1.board,
            step1.activePiece,
            step1.tutorialState.targetFactorIndex!!
        )
        assertNotNull(refactoredStep1)
        assertEquals(FactorDimension(6, 2), refactoredStep1!!.currentDim)

        val alignedStep1 = refactoredStep1.copy(x = 6, y = 18)
        val lockedStep1 = FactrisEngine.lockPiece(step1.board, alignedStep1)
        assertEquals(listOf(18, 19), FactrisEngine.findFullRows(lockedStep1))

        val step3 = FactrisEngine.tutorialSetupFor(com.example.game.TutorialStep.COMBINE_THREE_PIECES)
        assertEquals(6, step3.activePiece.area)
        assertEquals(FactorDimension(2, 3), step3.activePiece.currentDim)
        val refactoredStep3 = FactrisEngine.refactorToIndex(
            step3.board,
            step3.activePiece,
            step3.tutorialState.targetFactorIndex!!
        )
        assertNotNull(refactoredStep3)
        assertEquals(FactorDimension(3, 2), refactoredStep3!!.currentDim)
        val alignedStep3 = refactoredStep3.copy(x = 4, y = 18)
        val lockedStep3 = FactrisEngine.lockPiece(step3.board, alignedStep3)
        assertEquals(listOf(18, 19), FactrisEngine.findFullRows(lockedStep3))
    }

    @Test
    fun `VersusEngine generates deterministic shared boards and clears targetHeight rows on solve`() {
        val seed = 424242L
        for (round in 1..com.example.game.VersusEngine.TOTAL_ROUNDS) {
            val spec1 = com.example.game.VersusEngine.challengeForRound(seed, round)
            val spec2 = com.example.game.VersusEngine.challengeForRound(seed, round)
            assertEquals(spec1, spec2)
            assertEquals(spec1.targetArea, spec1.targetWidth * spec1.targetHeight)

            val board1 = com.example.game.VersusEngine.buildRoundBoard(
                roundSeed = seed,
                roundNumber = round,
                targetArea = spec1.targetArea,
                targetWidth = spec1.targetWidth,
                targetHeight = spec1.targetHeight
            )
            val board2 = com.example.game.VersusEngine.buildRoundBoard(
                roundSeed = seed,
                roundNumber = round,
                targetArea = spec1.targetArea,
                targetWidth = spec1.targetWidth,
                targetHeight = spec1.targetHeight
            )
            assertEquals(board1.gapX, board2.gapX)
            assertEquals(board1.gapY, board2.gapY)
            assertEquals(board1.activePiece.area, board2.activePiece.area)
            assertEquals(board1.activePiece.factorIndex, board2.activePiece.factorIndex)

            val targetIdx = board1.activePiece.factors.indexOfFirst {
                it.width == spec1.targetWidth && it.height == spec1.targetHeight
            }
            assertTrue("Target dimensions should exist in factors", targetIdx >= 0)
            val refactored = FactrisEngine.refactorToIndex(board1.board, board1.activePiece, targetIdx)
            assertNotNull(refactored)
            val aligned = refactored!!.copy(x = board1.gapX, y = board1.gapY)
            val locked = FactrisEngine.lockPiece(board1.board, aligned)
            assertEquals(spec1.targetHeight, FactrisEngine.findFullRows(locked).size)
        }
    }
}
