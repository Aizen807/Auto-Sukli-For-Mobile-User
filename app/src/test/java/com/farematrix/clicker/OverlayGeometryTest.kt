package com.farematrix.clicker

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayGeometryTest {
    @Test
    fun denominationTargetCanMovePastSplitPaneBoundaryOnLandscapeDisplay() {
        // The split pane ends at x=700, but overlay coordinates span the full display.
        assertEquals(
            Pair(1_800, 400),
            OverlayGeometry.clampPosition(1_800, 400, 88, 88, 2_340, 1_080)
        )
    }

    @Test
    fun floatingControllerCanMoveAcrossLandscapeDisplay() {
        assertEquals(
            Pair(1_700, 300),
            OverlayGeometry.clampPosition(1_700, 300, 160, 620, 2_340, 1_080)
        )
    }

    @Test
    fun clampsWindowsToTheDisplayEdges() {
        assertEquals(
            Pair(2_252, 992),
            OverlayGeometry.clampPosition(2_300, 1_040, 88, 88, 2_340, 1_080)
        )
    }
}
