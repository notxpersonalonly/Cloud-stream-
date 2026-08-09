package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class CricifyProvider : MainAPI() {
    override var mainUrl = "https://cricify.live"
    override var name = "CricifyProvider"
    override val hasMainPage = true
    override val hasSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "$mainUrl/cricket/" to "Cricket",
        "$mainUrl/football/" to "Football",
        "$mainUrl/live/" to "All Live Sports",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data).document
        val items = doc.select("a.match, div.match-card, article.live").mapNotNull { it.toLiveSearchResult() }
        return newHomePageResponse(request.name, items, false)
    }

    private fun Element.toLiveSearchResult(): LiveSearchResponse? {
        val title = selectFirst("h2, h3, .title, .match-title")?.text()?.trim() ?: return null
        val href = attr("href").ifBlank { selectFirst("a")?.attr("href") } ?: return null
        val poster = selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        return newLiveSearchResponse(title, href, TvType.Live) { posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${query.replace(" ", "+")}").document
        return doc.select("a.match, div.match-card, article").mapNotNull { it.toLiveSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .match-title, .entry-title")?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".poster img, .thumbnail img")?.attr("src")
        val plot = doc.selectFirst(".description, .match-info")?.text()?.trim()
        return newLiveStreamLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        // Collect only stream iframes — filter out ads
        doc.select("iframe[src], iframe[data-src]").forEach { el ->
            val src = el.attr("src").ifBlank { el.attr("data-src") }
            if (src.isNotBlank() && !isAdUrl(src) && isStreamUrl(src)) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }
        // Direct m3u8 links in page source
        val pageHtml = app.get(data).text
        val m3u8Regex = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""")
        m3u8Regex.findAll(pageHtml).forEach { match ->
            val streamUrl = match.value
            if (!isAdUrl(streamUrl)) {
                callback(
                    newExtractorLink(name, name, streamUrl) {
                        this.referer = data
                        this.quality = Qualities.Unknown.value
                        this.type = ExtractorLinkType.M3U8
                    }
                )
            }
        }
        return true
    }

    private fun isStreamUrl(url: String): Boolean {
        return listOf("stream", "live", "m3u8", "embed", "play", "video", "hls")
            .any { url.contains(it, ignoreCase = true) }
    }

    private fun isAdUrl(url: String): Boolean {
        return listOf("pop", "ad", "redirect", "tracking", "doubleclick", "googlesyndication", "popunder", "clickunder")
            .any { url.contains(it, ignoreCase = true) }
    }
}
