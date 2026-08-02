package org.jellyfin.androidtv.danmaku

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class DanmuApiClient {
	private val json = Json { ignoreUnknownKeys = true }

	suspend fun match(apiUrl: String, fileName: String): DanmakuMatch = withContext(Dispatchers.IO) {
		val payload = JsonObject(mapOf("fileName" to kotlinx.serialization.json.JsonPrimitive(fileName))).toString()
		val response = request(endpoint(apiUrl, "match"), "POST", payload)
		val root = json.parseToJsonElement(response).jsonObject
		if (root["success"]?.jsonPrimitive?.booleanOrNull == false) {
			throw IOException(root["errorMessage"]?.jsonPrimitive?.contentOrNull ?: "弹幕匹配失败")
		}
		val match = root["matches"]?.jsonArray?.firstOrNull()?.jsonObject
			?: throw IOException("没有匹配到弹幕")
		DanmakuMatch(
			episodeId = match["episodeId"]?.jsonPrimitive?.longOrNull
				?: throw IOException("弹幕源返回了无效的剧集 ID"),
			animeTitle = match["animeTitle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
			episodeTitle = match["episodeTitle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
		)
	}

	suspend fun comments(apiUrl: String, episodeId: Long): List<DanmakuItem> = withContext(Dispatchers.IO) {
		val response = request(endpoint(apiUrl, "comment/$episodeId?withRelated=true&chConvert=1&format=json"))
		val root = json.parseToJsonElement(response).jsonObject
		if (root["success"]?.jsonPrimitive?.booleanOrNull == false) {
			throw IOException(root["errorMessage"]?.jsonPrimitive?.contentOrNull ?: "弹幕加载失败")
		}
		parseDanmakuComments(response)
	}

	suspend fun searchEpisodes(apiUrl: String, anime: String): List<DanmakuAnimeCandidate> = withContext(Dispatchers.IO) {
		val encodedAnime = URLEncoder.encode(anime.trim(), Charsets.UTF_8.name())
		val response = request(endpoint(apiUrl, "search/episodes?anime=$encodedAnime"))
		val root = json.parseToJsonElement(response).jsonObject
		if (root["success"]?.jsonPrimitive?.booleanOrNull == false) {
			throw IOException(root["errorMessage"]?.jsonPrimitive?.contentOrNull ?: "弹幕搜索失败")
		}
		parseDanmakuEpisodeSearch(response)
	}

	private fun endpoint(apiUrl: String, path: String): String {
		return "${normalizeDanmuApiBaseUrl(apiUrl)}/$path"
	}

	private fun request(url: String, method: String = "GET", body: String? = null): String {
		val connection = URL(url).openConnection() as HttpURLConnection
		try {
			connection.requestMethod = method
			connection.connectTimeout = 10_000
			connection.readTimeout = 30_000
			connection.setRequestProperty("Accept", "application/json")
			if (body != null) {
				connection.doOutput = true
				connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
				connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
			}
			val status = connection.responseCode
			val stream = if (status in 200..299) connection.inputStream else connection.errorStream
			val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
			if (status !in 200..299) throw IOException("弹幕源请求失败（HTTP $status）${response.take(160)}")
			return response
		} finally {
			connection.disconnect()
		}
	}
}

internal fun parseDanmakuEpisodeSearch(response: String): List<DanmakuAnimeCandidate> {
	val json = Json { ignoreUnknownKeys = true }
	val root = json.parseToJsonElement(response).jsonObject
	return (root["animes"] as? JsonArray).orEmpty().mapNotNull { animeElement ->
		val animeObject = animeElement as? JsonObject ?: return@mapNotNull null
		val title = animeObject["animeTitle"]?.jsonPrimitive?.contentOrNull.orEmpty()
		if (title.isBlank()) return@mapNotNull null
		val episodes = (animeObject["episodes"] as? JsonArray).orEmpty().mapNotNull { episodeElement ->
			val episodeObject = episodeElement as? JsonObject ?: return@mapNotNull null
			val episodeId = episodeObject["episodeId"]?.jsonPrimitive?.longOrNull
				?: episodeObject["episodeId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
				?: return@mapNotNull null
			DanmakuEpisodeCandidate(
				episodeId = episodeId,
				episodeTitle = episodeObject["episodeTitle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
			)
		}
		if (episodes.isEmpty()) return@mapNotNull null
		DanmakuAnimeCandidate(
			animeTitle = title,
			typeDescription = animeObject["typeDescription"]?.jsonPrimitive?.contentOrNull.orEmpty(),
			episodes = episodes,
		)
	}
}

internal fun normalizeDanmuApiBaseUrl(apiUrl: String): String {
	val base = apiUrl.trim().trimEnd('/')
	if (base.isBlank()) throw IOException("请先在设置中填写弹幕源地址")
	return if (base.endsWith("/api/v2")) base else "$base/api/v2"
}

internal fun parseDanmakuComments(response: String): List<DanmakuItem> {
	val json = Json { ignoreUnknownKeys = true }
	val root = json.parseToJsonElement(response).jsonObject
	return (root["comments"] as? JsonArray).orEmpty().mapNotNull { element ->
		val item = element as? JsonObject ?: return@mapNotNull null
		val packed = item["p"]?.jsonPrimitive?.contentOrNull?.split(',')
		val timeMs = packed?.getOrNull(0)?.toDoubleOrNull()?.times(1000)?.toLong()
			?: item["progress"]?.jsonPrimitive?.longOrNull
			?: return@mapNotNull null
		val text = item["m"]?.jsonPrimitive?.contentOrNull
			?: item["content"]?.jsonPrimitive?.contentOrNull
			?: return@mapNotNull null
		if (text.isBlank()) return@mapNotNull null
		DanmakuItem(
			timeMs = timeMs.coerceAtLeast(0),
			mode = packed?.getOrNull(1)?.toIntOrNull() ?: item["mode"]?.jsonPrimitive?.intOrNull ?: 1,
			color = packed?.getOrNull(2)?.toIntOrNull() ?: item["color"]?.jsonPrimitive?.intOrNull ?: 0xFFFFFF,
			text = text,
		)
	}.sortedBy(DanmakuItem::timeMs)
}
