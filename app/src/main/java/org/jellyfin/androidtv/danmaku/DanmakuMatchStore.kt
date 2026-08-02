package org.jellyfin.androidtv.danmaku

import android.content.Context
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import timber.log.Timber

class DanmakuMatchStore(context: Context) {
	private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
	private val json = Json { ignoreUnknownKeys = true }

	fun get(itemId: UUID): DanmakuMatch? {
		val value = preferences.getString(itemId.toString(), null) ?: return null
		return runCatching {
			val root = json.parseToJsonElement(value).jsonObject
			DanmakuMatch(
				episodeId = root["episodeId"]?.jsonPrimitive?.longOrNull ?: return null,
				animeTitle = root["animeTitle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
				episodeTitle = root["episodeTitle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
			)
		}.onFailure { error ->
			Timber.w(error, "Unable to read saved danmaku match")
		}.getOrNull()
	}

	fun set(itemId: UUID, match: DanmakuMatch) {
		val value = JsonObject(
			mapOf(
				"episodeId" to JsonPrimitive(match.episodeId),
				"animeTitle" to JsonPrimitive(match.animeTitle),
				"episodeTitle" to JsonPrimitive(match.episodeTitle),
			)
		).toString()
		preferences.edit().putString(itemId.toString(), value).apply()
	}

	fun remove(itemId: UUID) {
		preferences.edit().remove(itemId.toString()).apply()
	}

	private companion object {
		const val PREFERENCES_NAME = "danmaku_manual_matches"
	}
}
