package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class RtallyProvider : MainAPI() {
    override var mainUrl = "https://rtally.net"
    override var name = "Rtally"
    override val hasMainPage = true
    override val hasSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AnimeMovie, TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/series/" to "TV Series",
        "$mainUrl/anime/" to "Anime",
        "$mainUrl/asian-drama/" to "Asian Drama",
        "$mainUrl/trending/" to "Trending",
        "$mainUrl/top-rated/" to "Top Rated",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}page/$page/"
        val doc = app.get(url).document
        val items = doc.select("article, div.item, .film-item").mapNotNull { it.toSearchResult() }
        val hasNext = doc.selectFirst("a.next, a[rel=next], .pagination a.next") != null
        return newHomePageResponse(request.name, items, hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2, h3, .title, .film-title")?.text()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        val type = when {
            href.contains("/anime/") -> TvType.Anime
            href.contains("/asian") || href.contains("/drama/") -> TvType.AsianDrama
            href.contains("/series/") -> TvType.TvSeries
            else -> TvType.Movie
        }
        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${query.replace(" ", "+")}").document
        return doc.select("article, div.item, .film-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .entry-title, .film-title")?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".poster img, .film-poster img, .wp-post-image")?.attr("src")
        val plot = doc.selectFirst(".description, .synopsis, .entry-content > p")?.text()?.trim()
        val year = doc.selectFirst(".year, .release-date")?.text()?.take(4)?.toIntOrNull()
        val tags = doc.select(".genres a, .genre a").map { it.text() }
        val rating = doc.selectFirst(".rating, .imdb-rating")?.text()
            ?.replace(Regex("[^0-9.]"), "")?.toFloatOrNull()?.times(1000)?.toInt()

        val isAnime = url.contains("/anime/")
        val isDrama = url.contains("/asian") || url.contains("/drama/")
        val isSeries = url.contains("/series/") || isAnime || isDrama

        if (isSeries) {
            val episodes = doc.select("a[href*=episode], .ep-item a, .episode-list a")
                .mapIndexed { i, ep ->
                    val epTitle = ep.text().ifBlank { "Episode ${i + 1}" }
                    Episode(ep.attr("href"), epTitle)
                }
            val tvType = when {
                isAnime -> TvType.Anime
                isDrama -> TvType.AsianDrama
                else -> TvType.TvSeries
            }
            return newAnimeLoadResponse(title, url, tvType) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
                this.rating = rating
                addEpisodes(DubStatus.None, episodes)
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
        doc.select("iframe[src], iframe[data-src]").forEach { el ->
            val src = el.attr("src").ifBlank { el.attr("data-src") }
            if (src.isNotBlank() && !isAdUrl(src)) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }
        doc.select("source[src]").forEach { src ->
            val url2 = src.attr("src")
            if (url2.isNotBlank() && !isAdUrl(url2)) {
                callback(newExtractorLink(name, name, url2) {
                    this.referer = data
                    this.quality = Qualities.Unknown.value
                })
            }
        }
        return true
    }

    private fun isAdUrl(url: String): Boolean =
        listOf("pop", "ad", "redirect", "tracking", "doubleclick", "googlesyndication", "popunder")
            .any { url.contains(it, ignoreCase = true) }
}
