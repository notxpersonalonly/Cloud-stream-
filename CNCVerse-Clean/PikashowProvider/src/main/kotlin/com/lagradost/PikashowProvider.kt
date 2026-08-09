package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class PikashowProvider : MainAPI() {
    override var mainUrl = "https://pikashow.com.co"
    override var name = "PikashowProvider"
    override val hasMainPage = true
    override val hasSearch = true
    override var lang = "hi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/web-series/" to "Web Series",
        "$mainUrl/bollywood/" to "Bollywood",
        "$mainUrl/hollywood/" to "Hollywood",
        "$mainUrl/south-indian/" to "South Indian",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}page/$page/"
        val doc = app.get(url).document
        val items = doc.select("article, div.item, .post").mapNotNull { it.toSearchResult() }
        val hasNext = doc.selectFirst("a.next, a[rel=next], .pagination .next") != null
        return newHomePageResponse(request.name, items, hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2, h3, .entry-title, .title")?.text()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        val isSeries = href.contains("/series/") || href.contains("/web-series/")
        return if (isSeries)
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
        else
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${query.replace(" ", "+")}").document
        return doc.select("article, div.item, .post").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .entry-title")?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".poster img, .wp-post-image, .thumbnail img")?.attr("src")
        val plot = doc.selectFirst(".entry-content p, .synopsis, .description")?.text()?.trim()
        val year = doc.selectFirst(".year, time")?.text()?.take(4)?.toIntOrNull()
        val isSeries = url.contains("/series/") || url.contains("/web-series/")

        if (isSeries) {
            val episodes = doc.select("a[href*=episode], .episode a, .eps-list a")
                .mapIndexed { i, ep ->
                    Episode(ep.attr("href"), ep.text().ifBlank { "Episode ${i + 1}" })
                }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
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
        doc.select("a[href$=.mp4], a[href*=download]").forEach { a ->
            val href = a.attr("href")
            if (href.isNotBlank() && !isAdUrl(href)) {
                callback(
                    newExtractorLink(name, a.text().ifBlank { name }, href) {
                        this.referer = data
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
        return true
    }

    private fun isAdUrl(url: String): Boolean {
        return listOf("pop", "redirect", "tracking", "doubleclick", "googlesyndication", "adserv", "popunder")
            .any { url.contains(it, ignoreCase = true) }
    }
}
