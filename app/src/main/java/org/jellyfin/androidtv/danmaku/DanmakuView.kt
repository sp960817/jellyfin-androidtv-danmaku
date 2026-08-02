package org.jellyfin.androidtv.danmaku

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.abs
import kotlin.math.max

class DanmakuView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : View(context, attrs) {
	private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
		style = Paint.Style.FILL
	}
	private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		typeface = textPaint.typeface
		style = Paint.Style.STROKE
		strokeWidth = resources.displayMetrics.density * 2f
		color = Color.BLACK
	}

	private data class PreparedItem(
		val item: DanmakuItem,
		val widthPx: Float,
		val track: Int,
	)

	private var anchorPositionMs = 0L
	private var anchorUptimeMs = 0L
	private var anchorPlaybackSpeed = 1f
	private var playbackRunning = false
	private var hasPlaybackAnchor = false
	private var layoutDirty = true
	private var preparedItems: List<PreparedItem> = emptyList()
	private var maximumLifetimeMs = FIXED_DURATION
	private val widthCache = HashMap<String, Float>()

	var timeOffsetMs: Long = 0
	var fontSizeSp: Int = 26
		set(value) {
			if (field == value) return
			field = value
			markLayoutDirty()
		}
	var opacityPercent: Int = 90
		set(value) {
			if (field == value) return
			field = value
			invalidate()
		}
	var displayAreaPercent: Int = 50
		set(value) {
			if (field == value) return
			field = value
			markLayoutDirty()
		}
	var speed: Float = 1f
		set(value) {
			if (field == value) return
			field = value
			markLayoutDirty()
		}
	private var items: List<DanmakuItem> = emptyList()

	fun setItems(value: List<DanmakuItem>) {
		items = value.sortedBy(DanmakuItem::timeMs)
		markLayoutDirty()
	}

	fun syncPlayback(positionMs: Long, isPlaying: Boolean, playbackSpeed: Float) {
		val nowUptimeMs = SystemClock.uptimeMillis()
		val safePlaybackSpeed = playbackSpeed.coerceIn(0.25f, 4f)
		val predictedPositionMs = currentPositionMs(nowUptimeMs)
		val shouldSnap = !hasPlaybackAnchor ||
			isPlaying != playbackRunning ||
			abs(safePlaybackSpeed - anchorPlaybackSpeed) > PLAYBACK_SPEED_EPSILON ||
			abs(positionMs - predictedPositionMs) >= SEEK_DISCONTINUITY_MS
		if (shouldSnap || !isPlaying) {
			anchorPositionMs = positionMs
			anchorUptimeMs = nowUptimeMs
			anchorPlaybackSpeed = safePlaybackSpeed
			hasPlaybackAnchor = true
		}
		playbackRunning = isPlaying
		postInvalidateOnAnimation()
	}

	override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
		super.onSizeChanged(width, height, oldWidth, oldHeight)
		if (width != oldWidth || height != oldHeight) markLayoutDirty()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		if (items.isEmpty() || width <= 0 || height <= 0) return
		if (layoutDirty) prepareLayout()

		val alpha = (opacityPercent.coerceIn(10, 100) * 255 / 100)
		val textSize = textPaint.textSize
		val rowHeight = textSize + resources.displayMetrics.density * TRACK_VERTICAL_GAP_DP
		val areaHeight = height * displayAreaPercent.coerceIn(20, 100) / 100f
		val now = currentPositionMs() + timeOffsetMs
		val pixelsPerMs = scrollingPixelsPerMs()
		var index = lowerBound(now - maximumLifetimeMs)
		strokePaint.alpha = alpha
		val canvasState = canvas.save()
		canvas.clipRect(0f, 0f, width.toFloat(), areaHeight)
		while (index < preparedItems.size) {
			val prepared = preparedItems[index++]
			val item = prepared.item
			if (item.timeMs > now) break
			val ageMs = now - item.timeMs
			val lifetimeMs = if (item.mode == MODE_TOP || item.mode == MODE_BOTTOM) {
				FIXED_DURATION
			} else {
				((width + prepared.widthPx) / pixelsPerMs).toLong()
			}
			if (ageMs !in 0..lifetimeMs) continue
			val color = item.color or 0xFF000000.toInt()
			textPaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
			val x = when (item.mode) {
				MODE_TOP, MODE_BOTTOM -> (width - prepared.widthPx) / 2f
				else -> width - ageMs * pixelsPerMs
			}
			val y = when (item.mode) {
				MODE_BOTTOM -> areaHeight - prepared.track * rowHeight
				else -> (prepared.track + 1) * rowHeight
			}
			canvas.drawText(item.text, x, y, strokePaint)
			canvas.drawText(item.text, x, y, textPaint)
		}
		canvas.restoreToCount(canvasState)
		if (playbackRunning) postInvalidateOnAnimation()
	}

	private fun prepareLayout() {
		layoutDirty = false
		widthCache.clear()
		val textSize = TypedValue.applyDimension(
			TypedValue.COMPLEX_UNIT_SP,
			fontSizeSp.coerceIn(12, 48).toFloat(),
			resources.displayMetrics,
		)
		textPaint.textSize = textSize
		strokePaint.textSize = textSize
		val rowHeight = textSize + resources.displayMetrics.density * TRACK_VERTICAL_GAP_DP
		val areaHeight = height * displayAreaPercent.coerceIn(20, 100) / 100f
		val trackCount = (areaHeight / rowHeight).toInt().coerceAtLeast(1)
		val pixelsPerMs = scrollingPixelsPerMs()
		val gapPx = resources.displayMetrics.density * TRACK_HORIZONTAL_GAP_DP
		val widths = FloatArray(items.size) { index ->
			widthCache.getOrPut(items[index].text) { textPaint.measureText(items[index].text) }
		}
		val tracks = assignDanmakuTracks(
			items = items.mapIndexed { index, item -> DanmakuTrackInput(item.timeMs, item.mode, widths[index]) },
			trackCount = trackCount,
			pixelsPerMs = pixelsPerMs,
			fixedDurationMs = FIXED_DURATION,
			gapPx = gapPx,
		)
		preparedItems = items.mapIndexedNotNull { index, item ->
			tracks[index].takeIf { track -> track >= 0 }?.let { track -> PreparedItem(item, widths[index], track) }
		}
		maximumLifetimeMs = max(
			FIXED_DURATION,
			widths.maxOfOrNull { itemWidth -> ((width + itemWidth) / pixelsPerMs).toLong() } ?: 0L,
		)
	}

	private fun currentPositionMs(nowUptimeMs: Long = SystemClock.uptimeMillis()): Long {
		if (!playbackRunning) return anchorPositionMs
		return anchorPositionMs + ((nowUptimeMs - anchorUptimeMs) * anchorPlaybackSpeed).toLong()
	}

	private fun scrollingPixelsPerMs(): Float {
		val screenTraversalMs = BASE_SCREEN_TRAVERSAL_MS / speed.coerceIn(0.5f, 2f)
		return width.coerceAtLeast(1) / screenTraversalMs
	}

	private fun lowerBound(timeMs: Long): Int {
		var low = 0
		var high = preparedItems.size
		while (low < high) {
			val middle = (low + high) ushr 1
			if (preparedItems[middle].item.timeMs < timeMs) low = middle + 1 else high = middle
		}
		return low
	}

	private fun markLayoutDirty() {
		layoutDirty = true
		invalidate()
	}

	companion object {
		private const val FIXED_DURATION = 4_000L
		private const val BASE_SCREEN_TRAVERSAL_MS = 8_000f
		private const val TRACK_VERTICAL_GAP_DP = 8f
		private const val TRACK_HORIZONTAL_GAP_DP = 24f
		private const val SEEK_DISCONTINUITY_MS = 400L
		private const val PLAYBACK_SPEED_EPSILON = 0.001f
	}
}
