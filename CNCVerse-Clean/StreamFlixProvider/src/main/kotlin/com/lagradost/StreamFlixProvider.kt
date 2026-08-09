package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class StreamFlixProvider : MainAPI() {
    override var mainUrl = "https://streamflix.com.co"
    override var name = "StreamFlixProvider"
    override val hasMainPage = true
    override val hasSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/tv-shows/" to "TV Shows",
        "$mainUrl/anime/" to "Anime",
        "$mainUrl/trending/" to "Trending",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}page/$page/"
        val doc = app.get(url).document
        val items = doc.select("article, div.item, .film-poster").mapNotNull { it.toSearchResult() }
        val hasNext = doc.selectFirst("a.next, a[rel=next]") != null
        return newHomePageResponse(request.name, items, hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2, h3, .film-name, .entry-title")?.text()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        val type = when {
            href.contains("/anime/") -> TvType.Anime
            href.contains("/tv-shows/") || href.contains("/series/") -> TvType.TvSeries
            else -> TvType.Movie
        }
        return if (type == TvType.Movie)
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
        else
            newTvSeriesSearchResponse(title, href, type) { posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${query.replace(" ", "+")}").document
        return doc.select("article, div.item, .film-poster").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .entry-title, .film-title")?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".poster img, .film-poster img")?.attr("src")
        val plot = doc.selectFirst(".description, .entry-content p, .overview")?.text()?.trim()
        val year = doc.selectFirst(".year, time")?.text()?.take(4)?.toIntOrNull()
        val tags = doc.select(".genres a, .genre a").map { it.text() }
        val isAnime = url.contains("/anime/")
        val isSeries = url.contains("/tv-shows/") || url.contains("/series/") || isAnime

        if (isSeries) {
            val episodes = doc.select("a[href*=episode], .episode-item a, .eps a")
                .mapIndexed { i, ep ->
                    Episode(ep.attr("href"), ep.text().ifBlank { "Episode ${i + 1}" })
                }
            return newTvSeriesLoadResponse(title, url, if (isAnime) TvType.Anime else TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        }
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
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
        return true
    }

    private fun isAdUrl(url: String): Boolean =
        listOf("pop", "ad", "redirect", "tracking", "doubleclick", "googlesyndication", "popunder")
            .any { url.contains(it, ignoreCase = true) }
}
