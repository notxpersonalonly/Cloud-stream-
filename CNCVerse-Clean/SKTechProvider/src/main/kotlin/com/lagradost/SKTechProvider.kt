package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class SKTechProvider : MainAPI() {
    override var mainUrl = "https://sktech.in"
    override var name = "SKTechProvider"
    override val hasMainPage = true
    override val hasSearch = true
    override var lang = "hi"
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "$mainUrl/live-tv/" to "Live TV",
        "$mainUrl/news/" to "News Channels",
        "$mainUrl/entertainment/" to "Entertainment",
        "$mainUrl/sports/" to "Sports",
        "$mainUrl/movies-channels/" to "Movie Channels",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data).document
        val items = doc.select("article, div.channel-card, .tv-item").mapNotNull { it.toChannelResult() }
        return newHomePageResponse(request.name, items, false)
    }

    private fun Element.toChannelResult(): LiveSearchResponse? {
        val title = selectFirst("h2, h3, .title, .channel-name")?.text()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        return newLiveSearchResponse(title, href, TvType.Live) { posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${query.replace(" ", "+")}").document
        return doc.select("article, div.channel-card, .tv-item").mapNotNull { it.toChannelResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .channel-title, .entry-title")?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".poster img, .logo img, .thumbnail img")?.attr("src")
        return newLiveStreamLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select("iframe[src], iframe[data-src]").forEach { el ->
            val src = el.attr("src").ifBlank { el.attr("data-src") }
            if (src.isNotBlank() && !isAdUrl(src)) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }
        val pageText = app.get(data).text
        Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""").findAll(pageText).forEach {
            val url2 = it.value
            if (!isAdUrl(url2)) {
                callback(newExtractorLink(name, name, url2) {
                    this.referer = data
                    this.quality = Qualities.Unknown.value
                    this.type = ExtractorLinkType.M3U8
                })
            }
        }
        return true
    }

    private fun isAdUrl(url: String): Boolean =
        listOf("pop", "ad", "redirect", "tracking", "doubleclick", "googlesyndication")
            .any { url.contains(it, ignoreCase = true) }
}
