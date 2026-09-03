package com.calcplus.calculator.gallery

import androidx.compose.ui.Alignment
import com.calcplus.calculator.feature.gallery.importPillAlignment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P2 (iteration-2-decisions §2): while an import runs into an empty album the empty state stays
 * suppressed and the progress pill is the only content, so it is centered in the grid area; once
 * photos exist it docks at the bottom edge as before.
 */
class ImportPillAlignmentTest {
    @Test
    fun emptyGridCentersThePill() {
        assertEquals(Alignment.Center, importPillAlignment(gridIsEmpty = true))
    }

    @Test
    fun populatedGridDocksThePillAtTheBottom() {
        assertEquals(Alignment.BottomCenter, importPillAlignment(gridIsEmpty = false))
    }
}
