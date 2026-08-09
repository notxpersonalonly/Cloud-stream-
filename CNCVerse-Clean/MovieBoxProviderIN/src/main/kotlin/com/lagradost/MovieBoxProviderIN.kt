package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class MovieBoxProviderIN : MainAPI() {
    override var mainUrl = "https://moviebox.ng"
    override var name = "MovieBoxProviderIN"
    override val hasMainPage = true
    override val hasSearch = true
    override var lang = "hi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Latest Movies",
        "$mainUrl/series/" to "TV Series",
        "$mainUrl/bollywood/" to "Bollywood",
        "$mainUrl/hollywood/" to "Hollywood",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}page/$page/"
        val doc = app.get(url).document
        val items = doc.select("div.item, article.post, div.ml-item").mapNotNull { it.toSearchResult() }
        val hasNext = doc.selectFirst("a.next.page-numbers, a[rel=next]") != null
        return newHomePageResponse(request.name, items, hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2, h3, .title, .entry-title")?.text()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = selectFirst("img")?.attr("data-src") ?: selectFirst("img")?.attr("src")
        val isSeries = href.contains("/series/") || href.contains("/season/")
        return if (isSeries)
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        else
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${query.replace(" ", "+")}").document
        return doc.select("div.item, article.post, div.ml-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .entry-title")?.text()?.trim() ?: return null
        val poster = doc.selectFirst("div.poster img, .entry-content img")?.attr("src")
        val plot = doc.selectFirst("div.entry-content p, .description")?.text()?.trim()
        val isSeries = url.contains("/series/")

        if (isSeries) {
            val episodes = doc.select("a[href*='/episode/'], .episodelist a").mapIndexed { i, ep ->
                Episode(ep.attr("href"), ep.text().ifBlank { "Episode ${i + 1}" })
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
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
        // Extract direct iframes and video embeds — no ad redirects
        doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
            if (src.isNotBlank() && !isAdUrl(src)) {
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

    private fun isAdUrl(url: String): Boolean {
        val adPatterns = listOf("doubleclick", "googlesyndication", "adservice", "pop", "redirect", "tracking", "analytics")
        return adPatterns.any { url.contains(it, ignoreCase = true) }
    }
}
