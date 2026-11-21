package dev.iptv.player.player

/** Grid maths for the multi-view screen: 2 tiles = 1 row x 2 columns, 3-4 tiles = 2 x 2. */
object MultiViewLayout {
    /** Only 2 or 4 tiles are offered. */
    fun tileCount(requested: Int): Int = if (requested >= 3) 4 else 2

    fun columns(count: Int): Int = if (count <= 1) 1 else 2
    fun rows(count: Int): Int = (count + columns(count) - 1) / columns(count)

    /** D-pad move on the grid (dx/dy in -1..1); stays put at the edges and on empty cells. */
    fun neighbor(index: Int, dx: Int, dy: Int, count: Int): Int {
        val cols = columns(count)
        val r = index / cols + dy
        val c = index % cols + dx
        if (r < 0 || c < 0 || c >= cols) return index
        val target = r * cols + c
        return if (target in 0 until count) target else index
    }

    /** Distinct channel indexes starting at [start], wrapping around; fewer than [count] when the list is short. */
    fun initialChannels(start: Int, size: Int, count: Int): List<Int> {
        if (size <= 0) return emptyList()
        val first = ((start % size) + size) % size
        return List(minOf(count, size)) { (first + it) % size }
    }

    /** Per-tile video size cap so several streams fit in the hardware decoder budget. */
    fun maxVideoHeight(count: Int): Int = if (count <= 2) 720 else 480
    fun maxVideoWidth(count: Int): Int = if (count <= 2) 1280 else 854

    fun volume(tile: Int, focused: Int): Float = if (tile == focused) 1f else 0f
}
