package org.jellyfin.androidtv.danmaku

import org.jellyfin.sdk.model.api.BaseItemDto

fun BaseItemDto.toDanmakuFileName(): String {
	val title = seriesName?.takeIf(String::isNotBlank) ?: name.orEmpty()
	return danmakuFileName(title, parentIndexNumber, indexNumber)
}

internal fun danmakuFileName(title: String, season: Int?, episode: Int?): String =
	if (season != null && episode != null) {
		"$title S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
	} else title
