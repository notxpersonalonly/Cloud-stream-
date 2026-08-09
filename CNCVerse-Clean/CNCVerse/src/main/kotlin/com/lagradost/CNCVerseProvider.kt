package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class CNCVerseProvider : MainAPI() {
    override var mainUrl = "https://netmirror.app"
    override var name = "CNC Verse"
    override val hasMainPage = true
    override val hasSearch = true
    override var lang = "hi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/netflix/" to "Netflix",
        "$mainUrl/prime/" to "Prime Video",
        "$mainUrl/hotstar/" to "Disney+ Hotstar",
        "$mainUrl/sonyliv/" to "SonyLiv",
        "$mainUrl/jiocinema/" to "JioCinema",
        "$mainUrl/zee5/" to "ZEE5",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}page/$page/"
        val doc = app.get(url).document
        val items = doc.select("article, .ml-item, div.item").mapNotNull { it.toSearchResult() }
        val hasNext = doc.selectFirst("a.next, a[rel=next]") != null
        return newHomePageResponse(request.name, items, hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2, h3, .entry-title, .title")?.text()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        val isSeries = href.contains("/series/") || href.contains("/show/") || selectFirst(".type")?.text()?.contains("Series", true) == true
        return if (isSeries)
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
        else
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${query.replace(" ", "+")}").document
        return doc.select("article, .ml-item, div.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .entry-title")?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".poster img, .wp-post-image")?.attr("src")
        val plot = doc.selectFirst(".entry-content p, .description, .synopsis")?.text()?.trim()
        val year = doc.selectFirst(".year, time[datetime]")?.text()?.take(4)?.toIntOrNull()
        val tags = doc.select(".genre a, .categories a").map { it.text() }
        val rating = doc.selectFirst(".rating, .imdb")?.text()?.trim()
            ?.replace(Regex("[^0-9.]"), "")?.toFloatOrNull()?.times(1000)?.toInt()
        val isSeries = url.contains("/series/") || url.contains("/show/")

        if (isSeries) {
            val episodes = doc.select("a[href*=episode], .eps-item a, .episodelist a")
                .mapIndexed { i, ep ->
                    Episode(ep.attr("href"), ep.text().ifBlank { "Episode ${i + 1}" })
                }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
                this.rating = rating
            }
        }
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
            this.rating = rating
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        // Only extract legitimate video iframes — skip all ad/popup iframes
        doc.select("iframe").forEach { el ->
            val src = el.attr("src").ifBlank { el.attr("data-src") }
            if (src.isNotBlank() && isVideoUrl(src) && !isAdUrl(src)) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }
        doc.select("source[src], video[src]").forEach { vid ->
            val src = vid.attr("src")
            if (src.isNotBlank() && !isAdUrl(src)) {
                callback(
                    newExtractorLink(name, name, src) {
                        this.referer = data
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
        return true
    }

    private fun isVideoUrl(url: String): Boolean {
        val videoHosts = listOf(
            "streamtape", "doodstream", "mixdrop", "filemoon", "voe.sx",
            "upstream", "vidcloud", "mega.nz", "streamlare", "fembed",
            "embedsito", "embedrise", "play", "stream", "video", "embed"
        )
        return videoHosts.any { url.contains(it, ignoreCase = true) }
    }

    private fun isAdUrl(url: String): Boolean {
        return listOf("pop", "redirect", "tracking", "doubleclick", "googlesyndication", "adclick", "adservice")
            .any { url.contains(it, ignoreCase = true) }
    }
}
