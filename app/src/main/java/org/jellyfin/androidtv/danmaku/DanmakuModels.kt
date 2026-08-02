package org.jellyfin.androidtv.danmaku

data class DanmakuItem(
	val timeMs: Long,
	val mode: Int,
	val color: Int,
	val text: String,
)

data class DanmakuMatch(
	val episodeId: Long,
	val animeTitle: String,
	val episodeTitle: String,
)

data class DanmakuEpisodeCandidate(
	val episodeId: Long,
	val episodeTitle: String,
)

data class DanmakuAnimeCandidate(
	val animeTitle: String,
	val typeDescription: String,
	val episodes: List<DanmakuEpisodeCandidate>,
)
