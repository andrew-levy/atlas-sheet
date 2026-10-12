package dev.atlas.sheet

import kotlin.math.abs

/** Pure geometry. Offsets grow downward; visible height = availableHeight - offset. */
internal data class SheetGeometry(
    val availableHeight: Float,
    val offsets: Map<String, Float>,
    val aliases: Map<String, String>,
) {
    fun canonical(id: String): String = aliases[id] ?: id
    val largest: String get() = offsets.minBy { it.value }.key
    val smallest: String get() = offsets.maxBy { it.value }.key

    companion object {
        fun resolve(availableHeight: Float, chromeHeight: Float, heights: List<Pair<String, Float>>): SheetGeometry {
            require(availableHeight.isFinite() && availableHeight > 0f)
            require(chromeHeight.isFinite() && chromeHeight >= 0f)
            require(heights.isNotEmpty()) { "At least one detent is required" }
            require(heights.map { it.first }.distinct().size == heights.size) { "Detent IDs must be unique" }
            val offsets = linkedMapOf<String, Float>()
            val aliases = linkedMapOf<String, String>()
            val minHeight = (chromeHeight + 1f).coerceAtMost(availableHeight)
            heights.forEach { (id, raw) ->
                require(raw.isFinite() && raw > 0f) { "Detent '$id' resolved to invalid height $raw" }
                val offset = availableHeight - raw.coerceIn(minHeight, availableHeight)
                val same = offsets.entries.firstOrNull { abs(it.value - offset) < 0.5f }
                if (same == null) offsets[id] = offset else aliases[id] = same.key
            }
            return SheetGeometry(availableHeight, offsets, aliases)
        }
    }
}

/** One detent per fast fling; slow releases choose the nearest physical anchor. */
internal fun settleTarget(offset: Float, velocity: Float, anchors: Map<String, Float>, threshold: Float): String {
    require(anchors.isNotEmpty())
    val nearest = anchors.minBy { abs(it.value - offset) }.key
    if (abs(velocity) < threshold) return nearest
    return if (velocity > 0f) {
        anchors.filterValues { it > offset + 0.5f }.minByOrNull { it.value }?.key ?: nearest
    } else {
        anchors.filterValues { it < offset - 0.5f }.maxByOrNull { it.value }?.key ?: nearest
    }
}
