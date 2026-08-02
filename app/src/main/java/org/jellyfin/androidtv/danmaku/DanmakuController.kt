package org.jellyfin.androidtv.danmaku

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.playback.PlaybackController
import timber.log.Timber

class DanmakuController @JvmOverloads constructor(
	private val context: Context,
	private val view: DanmakuView,
	private val playbackController: PlaybackController,
	private val preferences: UserPreferences,
	private val apiClient: DanmuApiClient = DanmuApiClient(),
	private val matchStore: DanmakuMatchStore = DanmakuMatchStore(context),
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private var currentItemId: UUID? = null
	private var loadJob: Job? = null
	private var activeDialog: AlertDialog? = null
	private var currentMatch: DanmakuMatch? = null
	private var manualMatchActive = false
	private var released = false

	private val update = object : Runnable {
		override fun run() {
			if (released) return
			view.syncPlayback(
				positionMs = playbackController.realtimeCurrentPosition,
				isPlaying = playbackController.isPlaying,
				playbackSpeed = playbackController.playbackSpeed,
			)
			view.visibility = if (preferences[UserPreferences.danmakuEnabled]) View.VISIBLE else View.GONE
			applyPreferences()
			val item = playbackController.currentlyPlayingItem
			if (item?.id != null && item.id != currentItemId) {
				currentItemId = item.id
				currentMatch = null
				manualMatchActive = false
				view.setItems(emptyList())
				if (preferences[UserPreferences.danmakuEnabled]) loadCurrentItem()
			}
			view.postDelayed(this, PLAYBACK_SYNC_INTERVAL_MS)
		}
	}

	fun start() {
		applyPreferences()
		view.removeCallbacks(update)
		view.post(update)
	}

	fun toggle(): Boolean {
		val enable = !preferences[UserPreferences.danmakuEnabled]
		if (enable && preferences[UserPreferences.danmakuApiUrl].isBlank()) {
			Toast.makeText(context, "请先在 设置 > 播放 > 弹幕 中填写弹幕源地址", Toast.LENGTH_LONG).show()
			return false
		}
		preferences[UserPreferences.danmakuEnabled] = enable
		view.visibility = if (enable) View.VISIBLE else View.GONE
		if (enable) loadCurrentItem()
		Toast.makeText(context, if (enable) "弹幕已开启" else "弹幕已关闭", Toast.LENGTH_SHORT).show()
		return enable
	}

	fun refresh() {
		applyPreferences()
		view.invalidate()
	}

	fun getCurrentMatchLabel(): String = currentMatch?.let { match ->
		listOf(match.animeTitle, match.episodeTitle).filter(String::isNotBlank).joinToString(" · ")
	}.orEmpty().ifBlank { context.getString(R.string.danmaku_match_pending) }

	fun hasManualMatch(): Boolean = manualMatchActive

	fun showManualSearch() {
		val item = playbackController.currentlyPlayingItem ?: return
		val apiUrl = preferences[UserPreferences.danmakuApiUrl]
		if (apiUrl.isBlank()) {
			Toast.makeText(context, R.string.danmaku_api_required, Toast.LENGTH_LONG).show()
			return
		}
		activeDialog?.dismiss()
		val input = EditText(context).apply {
			setText(item.seriesName ?: item.name.orEmpty())
			setSelectAllOnFocus(true)
			isSingleLine = true
			inputType = InputType.TYPE_CLASS_TEXT
			val padding = (DIALOG_INPUT_PADDING_DP * resources.displayMetrics.density).toInt()
			setPadding(padding, padding, padding, padding)
		}
		activeDialog = AlertDialog.Builder(context)
			.setTitle(R.string.danmaku_manual_search)
			.setMessage(R.string.danmaku_manual_search_hint)
			.setView(input)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.danmaku_search) { _, _ -> searchManualMatches(input.text.toString()) }
			.create()
			.apply {
				setOnDismissListener { if (activeDialog === this) activeDialog = null }
				show()
			}
	}

	fun clearManualMatch() {
		val itemId = playbackController.currentlyPlayingItem?.id ?: return
		matchStore.remove(itemId)
		manualMatchActive = false
		currentMatch = null
		view.setItems(emptyList())
		loadCurrentItem()
		Toast.makeText(context, R.string.danmaku_auto_match_restored, Toast.LENGTH_SHORT).show()
	}

	fun release() {
		released = true
		activeDialog?.dismiss()
		activeDialog = null
		view.removeCallbacks(update)
		loadJob?.cancel()
		scope.cancel()
		view.setItems(emptyList())
	}

	private fun loadCurrentItem() {
		val item = playbackController.currentlyPlayingItem ?: return
		val apiUrl = preferences[UserPreferences.danmakuApiUrl]
		if (apiUrl.isBlank()) return
		loadJob?.cancel()
		loadJob = scope.launch {
			try {
				val savedMatch = matchStore.get(item.id)
				val match = savedMatch ?: apiClient.match(apiUrl, item.toDanmakuFileName())
				val comments = apiClient.comments(apiUrl, match.episodeId)
				if (item.id == playbackController.currentlyPlayingItem?.id) {
					currentMatch = match
					manualMatchActive = savedMatch != null
					view.setItems(comments)
					Toast.makeText(context, "已加载 ${comments.size} 条弹幕", Toast.LENGTH_SHORT).show()
				}
			} catch (error: Exception) {
				Timber.w(error, "Unable to load danmaku")
				if (item.id == playbackController.currentlyPlayingItem?.id) {
					view.setItems(emptyList())
					Toast.makeText(context, error.message ?: "弹幕加载失败", Toast.LENGTH_LONG).show()
				}
			}
		}
	}

	private fun searchManualMatches(query: String) {
		if (query.isBlank()) return
		val apiUrl = preferences[UserPreferences.danmakuApiUrl]
		Toast.makeText(context, R.string.danmaku_searching, Toast.LENGTH_SHORT).show()
		loadJob?.cancel()
		loadJob = scope.launch {
			try {
				val candidates = apiClient.searchEpisodes(apiUrl, query)
				if (candidates.isEmpty()) {
					Toast.makeText(context, R.string.danmaku_search_no_results, Toast.LENGTH_LONG).show()
				} else {
					showAnimeCandidates(candidates)
				}
			} catch (error: Exception) {
				Timber.w(error, "Unable to search danmaku episodes")
				Toast.makeText(context, error.message ?: context.getString(R.string.danmaku_search_failed), Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun showAnimeCandidates(candidates: List<DanmakuAnimeCandidate>) {
		if (released) return
		val labels = candidates.map { candidate ->
			buildString {
				append(candidate.animeTitle)
				if (candidate.typeDescription.isNotBlank()) append("\n${candidate.typeDescription}")
				append(context.getString(R.string.danmaku_episode_count, candidate.episodes.size))
			}
		}.toTypedArray()
		activeDialog?.dismiss()
		activeDialog = AlertDialog.Builder(context)
			.setTitle(R.string.danmaku_choose_program)
			.setItems(labels) { _, index -> showEpisodeCandidates(candidates[index]) }
			.setNegativeButton(android.R.string.cancel, null)
			.create()
			.apply {
				setOnDismissListener { if (activeDialog === this) activeDialog = null }
				show()
			}
	}

	private fun showEpisodeCandidates(candidate: DanmakuAnimeCandidate) {
		if (released) return
		val currentEpisode = playbackController.currentlyPlayingItem?.indexNumber?.minus(1) ?: 0
		val initialIndex = currentEpisode.coerceIn(0, candidate.episodes.lastIndex)
		val labels = candidate.episodes.map { episode ->
			episode.episodeTitle.ifBlank { context.getString(R.string.danmaku_episode_unknown) }
		}.toTypedArray()
		activeDialog?.dismiss()
		activeDialog = AlertDialog.Builder(context)
			.setTitle(context.getString(R.string.danmaku_choose_episode, candidate.animeTitle))
			.setSingleChoiceItems(labels, initialIndex) { dialog, index ->
				dialog.dismiss()
				applyManualMatch(candidate, candidate.episodes[index])
			}
			.setNegativeButton(android.R.string.cancel, null)
			.create()
			.apply {
				setOnShowListener { listView.setSelection(initialIndex) }
				setOnDismissListener { if (activeDialog === this) activeDialog = null }
				show()
			}
	}

	private fun applyManualMatch(candidate: DanmakuAnimeCandidate, episode: DanmakuEpisodeCandidate) {
		val item = playbackController.currentlyPlayingItem ?: return
		val apiUrl = preferences[UserPreferences.danmakuApiUrl]
		val match = DanmakuMatch(episode.episodeId, candidate.animeTitle, episode.episodeTitle)
		loadJob?.cancel()
		loadJob = scope.launch {
			try {
				val comments = apiClient.comments(apiUrl, match.episodeId)
				if (item.id == playbackController.currentlyPlayingItem?.id) {
					matchStore.set(item.id, match)
					currentMatch = match
					manualMatchActive = true
					view.setItems(comments)
					Toast.makeText(context, context.getString(R.string.danmaku_manual_match_loaded, comments.size), Toast.LENGTH_SHORT).show()
				}
			} catch (error: Exception) {
				Timber.w(error, "Unable to load manually matched danmaku")
				Toast.makeText(context, error.message ?: context.getString(R.string.danmaku_load_failed), Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun applyPreferences() {
		view.fontSizeSp = preferences[UserPreferences.danmakuFontSize]
		view.opacityPercent = preferences[UserPreferences.danmakuOpacity]
		view.displayAreaPercent = preferences[UserPreferences.danmakuDisplayArea]
		view.speed = preferences[UserPreferences.danmakuSpeed]
		view.timeOffsetMs = preferences[UserPreferences.danmakuTimeOffset]
	}

	private companion object {
		const val PLAYBACK_SYNC_INTERVAL_MS = 250L
		const val DIALOG_INPUT_PADDING_DP = 24
	}
}
