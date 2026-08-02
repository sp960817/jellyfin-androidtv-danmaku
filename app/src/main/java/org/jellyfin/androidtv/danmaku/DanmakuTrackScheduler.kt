package org.jellyfin.androidtv.danmaku

internal data class DanmakuTrackInput(
	val timeMs: Long,
	val mode: Int,
	val widthPx: Float,
)

/**
 * Pre-assigns comments to independent scrolling/top/bottom tracks. A scrolling
 * track becomes reusable as soon as the previous comment's tail has entered
 * the viewport. When a burst exceeds all tracks the comment is filtered instead
 * of being forced onto an occupied track and overlapping existing text.
 */
internal fun assignDanmakuTracks(
	items: List<DanmakuTrackInput>,
	trackCount: Int,
	pixelsPerMs: Float,
	fixedDurationMs: Long,
	gapPx: Float,
): IntArray {
	if (items.isEmpty()) return IntArray(0)
	val safeTrackCount = trackCount.coerceAtLeast(1)
	val safePixelsPerMs = pixelsPerMs.coerceAtLeast(0.001f)
	val scrollingRelease = LongArray(safeTrackCount)
	val topRelease = LongArray(safeTrackCount)
	val bottomRelease = LongArray(safeTrackCount)
	return IntArray(items.size) { index ->
		val item = items[index]
		val releases = when (item.mode) {
			MODE_TOP -> topRelease
			MODE_BOTTOM -> bottomRelease
			else -> scrollingRelease
		}
		val track = releases.indexOfFirst { releaseAt -> releaseAt <= item.timeMs }
		if (track < 0) return@IntArray -1
		val occupiedUntil = when (item.mode) {
			MODE_TOP, MODE_BOTTOM -> item.timeMs + fixedDurationMs
			else -> item.timeMs + ((item.widthPx + gapPx) / safePixelsPerMs).toLong()
		}
		releases[track] = occupiedUntil
		track
	}
}

internal const val MODE_BOTTOM = 4
internal const val MODE_TOP = 5
