package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class XonProvider : MainAPI() {
    override var mainUrl = "https://xon.to"
    override var name = "XonProvider"
    override val hasMainPage = true
    override val hasSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/movies" to "Movies",
        "$mainUrl/series" to "TV Series",
        "$mainUrl/anime" to "Anime",
        "$mainUrl/trending" to "Trending",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}?page=$page"
        val doc = app.get(url).document
        val items = doc.select("div.flw-item, article, .film-poster").mapNotNull { it.toSearchResult() }
        val hasNext = doc.selectFirst("a[href*=page=${page + 1}], a.next") != null
        return newHomePageResponse(request.name, items, hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2, h3, .film-name, .title")?.text()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href")?.let {
            if (it.startsWith("http")) it else "$mainUrl$it"
        } ?: return null
        val poster = selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        val type = when {
            href.contains("/anime") -> TvType.Anime
            href.contains("/series") || href.contains("/tv/") -> TvType.TvSeries
            else -> TvType.Movie
        }
        return if (type == TvType.Movie)
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
        else
            newTvSeriesSearchResponse(title, href, type) { posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/search?q=${query.replace(" ", "+")}").document
        return doc.select("div.flw-item, article, .film-poster").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .heading-name, .film-name")?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".film-poster img, .detail-img img")?.attr("src")
        val plot = doc.selectFirst(".description, .film-description, .text-expand")?.text()?.trim()
        val year = doc.selectFirst(".item:contains(Released:), .release-date")?.text()?.takeLast(4)?.toIntOrNull()
        val tags = doc.select(".genre-btn, .item:contains(Genre) a").map { it.text() }
        val isAnime = url.contains("/anime")
        val isSeries = url.contains("/series") || url.contains("/tv/") || isAnime

        if (isSeries) {
            val seasons = doc.select("#seasons-tab-pane, .ss-item").map { season ->
                val seasonNum = season.selectFirst(".ss-title")?.text()?.filter { it.isDigit() }?.toIntOrNull() ?: 1
                season.select("a.ep-item").mapIndexed { i, ep ->
                    Episode(
                        ep.attr("href").let { if (it.startsWith("http")) it else "$mainUrl$it" },
                        ep.attr("title").ifBlank { "Episode ${i + 1}" },
                        season = seasonNum,
                        episode = i + 1
                    )
                }
            }.flatten()
            return newTvSeriesLoadResponse(title, url, if (isAnime) TvType.Anime else TvType.TvSeries, seasons) {
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
        listOf("pop", "ad", "redirect", "tracking", "doubleclick", "googlesyndication")
            .any { url.contains(it, ignoreCase = true) }
}
