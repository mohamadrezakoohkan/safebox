package com.calcplus.calculator.disguise

import com.calcplus.calculator.core.lock.PasscodeRules
import com.calcplus.calculator.core.lock.TokenRecorder
import com.calcplus.calculator.feature.pattern.PatternGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure stroke reducer of decisions §2.4. */
class PatternGeometryTest {
    /** The eight pairs from the spec, each also checked in reverse. */
    private val pairs = listOf(
        Triple(0, 2, 1),
        Triple(3, 5, 4),
        Triple(6, 8, 7),
        Triple(0, 6, 3),
        Triple(1, 7, 4),
        Triple(2, 8, 5),
        Triple(0, 8, 4),
        Triple(2, 6, 4),
    )

    @Test
    fun allEightMidpointPairsAndTheirReverses() {
        pairs.forEach { (a, b, mid) ->
            assertEquals("$a→$b", mid, PatternGeometry.midpoint(a, b))
            assertEquals("$b→$a", mid, PatternGeometry.midpoint(b, a))
        }
    }

    @Test
    fun adjacentAndKnightMovesHaveNoMidpoint() {
        // Neighbours …
        assertNull(PatternGeometry.midpoint(0, 1))
        assertNull(PatternGeometry.midpoint(0, 3))
        assertNull(PatternGeometry.midpoint(0, 4))
        assertNull(PatternGeometry.midpoint(4, 8))
        // … and the "knight" jumps, which pass through no node centre.
        assertNull(PatternGeometry.midpoint(0, 5))
        assertNull(PatternGeometry.midpoint(0, 7))
        assertNull(PatternGeometry.midpoint(1, 6))
        assertNull(PatternGeometry.midpoint(3, 2))
    }

    @Test
    fun aSkippedMidpointIsAutoSelectedFirst() {
        assertEquals(listOf(1, 2), PatternGeometry.nodesToSelect(listOf(0), 2))
        assertEquals(listOf(4, 8), PatternGeometry.nodesToSelect(listOf(0), 8))
    }

    @Test
    fun anAlreadySelectedMidpointIsNotRepeated() {
        // 1 is already in the path, so 0 → 2 only adds 2.
        assertEquals(listOf(2), PatternGeometry.nodesToSelect(listOf(1, 0), 2))
    }

    @Test
    fun enteringAnAlreadySelectedNodeEmitsNothing() {
        assertEquals(emptyList<Int>(), PatternGeometry.nodesToSelect(listOf(0, 1, 2), 1))
        assertEquals(emptyList<Int>(), PatternGeometry.nodesToSelect(listOf(4), 4))
    }

    @Test
    fun theFirstNodeOfAStrokeNeedsNoMidpoint() {
        assertEquals(listOf(8), PatternGeometry.nodesToSelect(emptyList(), 8))
    }

    @Test
    fun aNodeCanNeverRepeatSoAStrokeIsAtMostNineTokens() {
        // Walk every node in an order that forces several midpoints; the path
        // can never exceed the nine nodes, which is what makes overflow
        // impossible by construction.
        val path = mutableListOf<Int>()
        // A deliberately adversarial visiting order.
        listOf(0, 2, 6, 8, 4, 1, 3, 5, 7, 0, 4, 8).forEach { target ->
            path.addAll(PatternGeometry.nodesToSelect(path, target))
        }
        assertEquals(9, path.size)
        assertEquals((0..8).toSet(), path.toSet())
        assertTrue(path.size <= TokenRecorder.MAX_TOKENS)
    }

    @Test
    fun theLongestPossibleStrokeCannotOverflowTheRecorder() {
        val recorder = TokenRecorder()
        val path = mutableListOf<Int>()
        (0..8).forEach { target ->
            PatternGeometry.nodesToSelect(path, target).forEach { node ->
                path.add(node)
                recorder.record(PatternGeometry.tokenFor(node))
            }
        }
        val commit = recorder.takeCommit()
        assertEquals(9, commit.tokens.size)
        assertFalse(commit.overflowed)
        // The 4-token minimum is still the HOST's rule, not the face's.
        assertTrue(PasscodeRules.isValidLength(commit.tokens))
    }

    @Test
    fun aStrokeOfThreeNodesIsTooShortForTheHost() {
        assertFalse(PasscodeRules.isValidLength(listOf("N0", "N1", "N2")))
    }

    // MARK: hit testing

    @Test
    fun theHitAreaIsSixtyPercentOfTheCellAroundEachCentre() {
        val cell = 100f
        // Dead centre of each node.
        assertEquals(0, PatternGeometry.nodeAt(50f, 50f, cell))
        assertEquals(4, PatternGeometry.nodeAt(150f, 150f, cell))
        assertEquals(8, PatternGeometry.nodeAt(250f, 250f, cell))
        // Just inside the 60% square (±30) …
        assertEquals(0, PatternGeometry.nodeAt(79f, 50f, cell))
        // … and just outside it.
        assertNull(PatternGeometry.nodeAt(85f, 50f, cell))
        // Off the grid entirely.
        assertNull(PatternGeometry.nodeAt(-5f, 50f, cell))
        assertNull(PatternGeometry.nodeAt(50f, 305f, cell))
    }

    @Test
    fun nodeCentresAreRowMajor() {
        val cell = 90f
        assertEquals(45f, PatternGeometry.centerX(0, cell))
        assertEquals(45f, PatternGeometry.centerY(0, cell))
        assertEquals(225f, PatternGeometry.centerX(5, cell))
        assertEquals(135f, PatternGeometry.centerY(5, cell))
        assertEquals(45f, PatternGeometry.centerX(6, cell))
        assertEquals(225f, PatternGeometry.centerY(6, cell))
    }
}
