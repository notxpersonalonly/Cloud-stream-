package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Watch32 : MainAPI() {
    override var mainUrl = "https://watch32.pro"
    override var name = "Watch32"
    override val hasMainPage = true
    override val hasSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/tvshows/" to "TV Shows",
        "$mainUrl/trending/" to "Trending",
        "$mainUrl/genre/action/" to "Action",
        "$mainUrl/genre/comedy/" to "Comedy",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}page/$page/"
        val doc = app.get(url).document
        val items = doc.select("div.ml-item, article.item, div.item").mapNotNull { it.toSearchResult() }
        val hasNext = doc.selectFirst("a.next, a[rel=next]") != null
        return newHomePageResponse(request.name, items, hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2, h3, .entry-title, span.mli-info h2")?.text()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = selectFirst("img")?.let { it.attr("data-original").ifBlank { it.attr("src") } }
        val isSeries = href.contains("/tvshow/") || href.contains("/series/") || href.contains("/season/")
        return if (isSeries)
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
        else
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${query.replace(" ", "+")}").document
        return doc.select("div.ml-item, article.item, div.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .entry-title, h2.leading-tight")?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".poster img, .movie-img img, img.img-poster")?.attr("src")
        val plot = doc.selectFirst(".description, p.text-sm, .entry-content p")?.text()?.trim()
        val year = doc.selectFirst(".year, .release-date")?.text()?.take(4)?.toIntOrNull()
        val tags = doc.select(".genres a, .tag").map { it.text() }
        val isSeries = url.contains("/tvshow/") || url.contains("/series/")

        if (isSeries) {
            val episodes = doc.select("a[href*=episode], .episodes a, ul.episodelist a")
                .mapIndexed { i, ep ->
                    Episode(ep.attr("href"), ep.text().ifBlank { "Episode ${i + 1}" })
                }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
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
        doc.select("source[src]").forEach { src ->
            val u = src.attr("src")
            if (u.isNotBlank() && !isAdUrl(u)) {
                callback(newExtractorLink(name, name, u) {
                    this.referer = data
                    this.quality = Qualities.Unknown.value
                })
            }
        }
        return true
    }

    private fun isAdUrl(url: String): Boolean =
        listOf("pop", "ad", "redirect", "tracking", "doubleclick", "googlesyndication", "popunder", "clickad")
            .any { url.contains(it, ignoreCase = true) }
}
