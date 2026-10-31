package com.farematrix.clicker

/** Pure geometry shared by the draggable overlay windows. */
internal object OverlayGeometry {
    fun clampPosition(
        x: Int,
        y: Int,
        viewWidth: Int,
        viewHeight: Int,
        displayWidth: Int,
        displayHeight: Int
    ): Pair<Int, Int> {
        val maxX = (displayWidth - viewWidth).coerceAtLeast(0)
        val maxY = (displayHeight - viewHeight).coerceAtLeast(0)
        return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
    }
}
