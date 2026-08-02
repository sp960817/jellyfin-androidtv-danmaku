package danmaku

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.danmaku.danmakuFileName
import org.jellyfin.androidtv.danmaku.assignDanmakuTracks
import org.jellyfin.androidtv.danmaku.DanmakuTrackInput
import org.jellyfin.androidtv.danmaku.normalizeDanmuApiBaseUrl
import org.jellyfin.androidtv.danmaku.parseDanmakuComments
import org.jellyfin.androidtv.danmaku.parseDanmakuEpisodeSearch

class DanmakuParsingTests : StringSpec({
	"normalizes root and token API addresses" {
		normalizeDanmuApiBaseUrl("http://10.0.0.2:9321/secret/") shouldBe
			"http://10.0.0.2:9321/secret/api/v2"
		normalizeDanmuApiBaseUrl("https://example.com/api/v2") shouldBe
			"https://example.com/api/v2"
	}

	"parses and sorts both dandanplay comment formats" {
		val comments = parseDanmakuComments(
			"""{"comments":[
				{"progress":2500,"mode":5,"color":65280,"content":"top"},
				{"p":"1.25,1,16777215,qq","m":"scroll"},
				{"p":"bad,1,1,x","m":"ignored"}
			]}"""
		)
		comments shouldHaveSize 2
		comments[0].timeMs shouldBe 1250
		comments[0].text shouldBe "scroll"
		comments[1].mode shouldBe 5
	}

	"uses one title contract for automatic episode matching" {
		danmakuFileName("武林外传", 1, 2) shouldBe "武林外传 S01E02"
		danmakuFileName("流浪地球", null, null) shouldBe "流浪地球"
	}

	"parses manual program and episode search candidates" {
		val candidates = parseDanmakuEpisodeSearch(
			"""{"success":true,"animes":[{"animeTitle":"武林外传","typeDescription":"电视剧","episodes":[{"episodeId":10002,"episodeTitle":"第2集"},{"episodeId":"10003","episodeTitle":"第3集"}]}]}"""
		)
		candidates shouldHaveSize 1
		candidates[0].animeTitle shouldBe "武林外传"
		candidates[0].episodes.map { it.episodeId } shouldBe listOf(10002L, 10003L)
	}

	"reuses a scrolling track after the previous tail enters the screen" {
		val tracks = assignDanmakuTracks(
			items = listOf(
				DanmakuTrackInput(timeMs = 0, mode = 1, widthPx = 100f),
				DanmakuTrackInput(timeMs = 1_300, mode = 1, widthPx = 100f),
			),
			trackCount = 2,
			pixelsPerMs = 0.1f,
			fixedDurationMs = 4_000,
			gapPx = 20f,
		)
		tracks.toList() shouldBe listOf(0, 0)
	}

	"filters burst comments instead of overlapping occupied tracks" {
		val tracks = assignDanmakuTracks(
			items = listOf(
				DanmakuTrackInput(timeMs = 0, mode = 1, widthPx = 100f),
				DanmakuTrackInput(timeMs = 0, mode = 1, widthPx = 100f),
				DanmakuTrackInput(timeMs = 0, mode = 1, widthPx = 100f),
				DanmakuTrackInput(timeMs = 0, mode = 5, widthPx = 100f),
			),
			trackCount = 2,
			pixelsPerMs = 0.1f,
			fixedDurationMs = 4_000,
			gapPx = 20f,
		)
		tracks.toList() shouldBe listOf(0, 1, -1, 0)
	}
})
