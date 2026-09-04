package com.calcplus.calculator.feature.pattern

/**
 * The pattern face's pure stroke reducer (decisions §2.4). No Compose, no
 * coordinates leaving the face, nothing logged — just the row-major 3×3 rules
 * that decide which node IDs a stroke produces.
 *
 * Node numbering: `N0 N1 N2` top row, `N3 N4 N5` middle, `N6 N7 N8` bottom.
 */
object PatternGeometry {
    /** Layout constants (decisions §8). */
    const val GRID_MAX_DP = 300f
    const val NODE_HIT_FACTOR = 0.6f
    const val NODE_RESTING_DP = 16f
    const val NODE_SELECTED_DP = 28f
    const val LINE_WIDTH_DP = 6f
    const val LINE_ALPHA = 0.7f
    const val CAPTION_GAP_DP = 32f

    /** The nine token IDs, in row-major order. */
    val tokens: List<String> = (0..8).map { "N$it" }

    fun tokenFor(node: Int): String = tokens[node]

    /**
     * The exhaustive midpoint table from §2.4 — the eight pairs that are two
     * apart in a straight line, plus their reverses.
     */
    private val MIDPOINTS: Map<Pair<Int, Int>, Int> = buildMap {
        listOf(
            Triple(0, 2, 1),
            Triple(3, 5, 4),
            Triple(6, 8, 7),
            Triple(0, 6, 3),
            Triple(1, 7, 4),
            Triple(2, 8, 5),
            Triple(0, 8, 4),
            Triple(2, 6, 4),
        ).forEach { (a, b, mid) ->
            put(a to b, mid)
            put(b to a, mid)
        }
    }

    fun midpoint(from: Int, to: Int): Int? = MIDPOINTS[from to to]

    /**
     * Which nodes the stroke picks up when the finger enters [to], given the
     * nodes already [selected].
     *
     * - An already-selected node yields nothing (a node cannot repeat within a
     *   stroke — which is also why overflow is impossible by construction).
     * - A skipped-over midpoint is auto-selected first, unless it is already in
     *   the path.
     */
    fun nodesToSelect(selected: List<Int>, to: Int): List<Int> {
        if (to in selected) return emptyList()
        val last = selected.lastOrNull() ?: return listOf(to)
        val mid = midpoint(last, to)
        return if (mid != null && mid !in selected) listOf(mid, to) else listOf(to)
    }

    /**
     * The node under a point in grid-local coordinates, or null. Hit area is a
     * square of `cell × NODE_HIT_FACTOR` centered on the node.
     */
    fun nodeAt(x: Float, y: Float, cell: Float): Int? {
        if (cell <= 0f) return null
        val col = (x / cell).toInt()
        val row = (y / cell).toInt()
        if (col !in 0..2 || row !in 0..2) return null
        val half = cell * NODE_HIT_FACTOR / 2f
        val cx = (col + 0.5f) * cell
        val cy = (row + 0.5f) * cell
        val dx = if (x > cx) x - cx else cx - x
        val dy = if (y > cy) y - cy else cy - y
        return if (dx <= half && dy <= half) row * 3 + col else null
    }

    fun centerX(node: Int, cell: Float): Float = (node % 3 + 0.5f) * cell

    fun centerY(node: Int, cell: Float): Float = (node / 3 + 0.5f) * cell
}
