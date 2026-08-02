package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.androidtv.util.popupMenu
import org.koin.java.KoinJavaComponent

class DanmakuAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	private var popup: PopupMenu? = null

	init {
		initializeWithIcon(R.drawable.ic_select_subtitle)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		val fragment = videoPlayerAdapter.masterOverlayFragment
		val preferences = KoinJavaComponent.get<UserPreferences>(UserPreferences::class.java)
		videoPlayerAdapter.leanbackOverlayFragment.setFading(false)
		dismissPopup()
		popup = popupMenu(context, view, Gravity.END) {
			item(context.getString(R.string.pref_danmaku_enabled)) {
				fragment.toggleDanmaku()
			}.apply { isCheckable = true; isChecked = preferences[UserPreferences.danmakuEnabled] }

			item(context.getString(R.string.danmaku_current_match, fragment.danmakuMatchLabel)) {}.apply {
				isEnabled = false
			}

			item(context.getString(R.string.danmaku_manual_search)) {
				dismissPopup()
				fragment.showDanmakuManualSearch()
			}

			item(context.getString(R.string.danmaku_restore_auto_match)) {
				fragment.clearManualDanmakuMatch()
			}.apply { isEnabled = fragment.hasManualDanmakuMatch() }

			val displayArea = preferences[UserPreferences.danmakuDisplayArea]
			subMenu("${context.getString(R.string.pref_danmaku_display_area)} · $displayArea%") {
				listOf(25, 50, 75, 100).forEach { value ->
					item("$value%") {
						preferences[UserPreferences.danmakuDisplayArea] = value
						fragment.refreshDanmaku()
					}.apply { isCheckable = true; isChecked = displayArea == value }
				}
			}.setGroupCheckable(0, true, true)

			val opacity = preferences[UserPreferences.danmakuOpacity]
			subMenu("${context.getString(R.string.pref_danmaku_opacity)} · $opacity%") {
				listOf(50, 75, 90, 100).forEach { value ->
					item("$value%") {
						preferences[UserPreferences.danmakuOpacity] = value
						fragment.refreshDanmaku()
					}.apply { isCheckable = true; isChecked = opacity == value }
				}
			}.setGroupCheckable(0, true, true)

			val fontSize = preferences[UserPreferences.danmakuFontSize]
			subMenu("${context.getString(R.string.pref_danmaku_font_size)} · $fontSize") {
				listOf(18, 24, 26, 32, 40).forEach { value ->
					item(value.toString()) {
						preferences[UserPreferences.danmakuFontSize] = value
						fragment.refreshDanmaku()
					}.apply { isCheckable = true; isChecked = fontSize == value }
				}
			}.setGroupCheckable(0, true, true)

			val speed = preferences[UserPreferences.danmakuSpeed]
			subMenu("${context.getString(R.string.pref_danmaku_speed)} · ${"%.2f".format(speed)}x") {
				listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
					item("${"%.2f".format(value)}x") {
						preferences[UserPreferences.danmakuSpeed] = value
						fragment.refreshDanmaku()
					}.apply { isCheckable = true; isChecked = speed == value }
				}
			}.setGroupCheckable(0, true, true)

			val offsetMs = preferences[UserPreferences.danmakuTimeOffset]
			subMenu("${context.getString(R.string.pref_danmaku_time_offset)} · ${formatOffset(offsetMs)}") {
				listOf(-2_000L, -1_000L, 0L, 1_000L, 2_000L).forEach { value ->
					item(formatOffset(value)) {
						preferences[UserPreferences.danmakuTimeOffset] = value
						fragment.refreshDanmaku()
					}.apply { isCheckable = true; isChecked = offsetMs == value }
				}
			}.setGroupCheckable(0, true, true)
		}
		popup?.setOnDismissListener {
			videoPlayerAdapter.leanbackOverlayFragment.setFading(true)
			popup = null
		}
		popup?.show()
	}

	fun dismissPopup() {
		popup?.dismiss()
	}

	private fun formatOffset(valueMs: Long): String = "%+.1fs".format(valueMs / 1000f)
}
