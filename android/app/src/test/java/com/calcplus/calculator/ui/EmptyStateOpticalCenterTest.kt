package com.calcplus.calculator.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.calcplus.calculator.core.ui.components.EMPTY_STATE_SPACE_ABOVE
import com.calcplus.calculator.core.ui.components.EMPTY_STATE_SPACE_BELOW
import com.calcplus.calculator.core.ui.components.EmptyStateOpticalCenter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P2 (iteration-2-decisions §2): the empty-state block is optically centered — free vertical
 * space 1.0 above : 1.35 below, centered horizontally. The alignment is pure math, so it is
 * pinned here without rendering (plain JUnit; `androidx.compose.ui.Alignment` needs no Android).
 */
class EmptyStateOpticalCenterTest {
    /** Where a [blockHeight]-tall block lands inside an [areaHeight]-tall area. */
    private fun place(
        blockHeight: Int,
        areaHeight: Int,
        blockWidth: Int = 200,
        areaWidth: Int = 400,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ): IntOffset = EmptyStateOpticalCenter.align(
        size = IntSize(blockWidth, blockHeight),
        space = IntSize(areaWidth, areaHeight),
        layoutDirection = direction,
    )

    @Test
    fun weightsAreTheDecidedOneToOnePointThreeFive() {
        assertEquals(1f, EMPTY_STATE_SPACE_ABOVE, 0f)
        assertEquals(1.35f, EMPTY_STATE_SPACE_BELOW, 0f)
    }

    @Test
    fun freeSpaceSplitsOneAboveToOnePointThreeFiveBelow() {
        // 235 px free → 100 above, 135 below.
        val offset = place(blockHeight = 100, areaHeight = 335)
        assertEquals(100, offset.y)
        assertEquals(135, 335 - 100 - offset.y)
    }

    @Test
    fun ratioHoldsAtOtherSizes() {
        // 470 px free → 200 : 270.
        assertEquals(200, place(blockHeight = 300, areaHeight = 770).y)
        // 47 px free → 20 : 27.
        assertEquals(20, place(blockHeight = 53, areaHeight = 100).y)
    }

    @Test
    fun blockIsCenteredHorizontallyInBothLayoutDirections() {
        assertEquals(100, place(blockHeight = 100, areaHeight = 335).x)
        assertEquals(100, place(blockHeight = 100, areaHeight = 335, direction = LayoutDirection.Rtl).x)
    }

    @Test
    fun blockAsTallAsTheAreaStartsAtTheTop() {
        // The area grows to the block's height when the block is taller (and then scrolls), so
        // "no free space" is the case that matters: no lift, nothing cut off above.
        assertEquals(0, place(blockHeight = 500, areaHeight = 500).y)
    }
}
