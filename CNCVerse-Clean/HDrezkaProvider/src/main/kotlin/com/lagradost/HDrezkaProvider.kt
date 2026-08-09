package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class HDrezkaProvider : MainAPI() {
    override var mainUrl = "https://hdrezka.ag"
    override var name = "HDrezkaProvider"
    override val hasMainPage = true
    override val hasSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama
    )

    private val headers = mapOf("X-Requested-With" to "XMLHttpRequest")

    override val mainPage = mainPageOf(
        "$mainUrl/films/" to "Movies",
        "$mainUrl/series/" to "Series",
        "$mainUrl/cartoons/" to "Anime & Cartoons",
        "$mainUrl/new/" to "New Releases",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}page/$page/"
        val doc = app.get(url).document
        val items = doc.select("div.b-content__inline_item").mapNotNull { it.toSearchResult() }
        val hasNext = doc.selectFirst("a.b-navigation__next") != null
        return newHomePageResponse(request.name, items, hasNext)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a[href]") ?: return null
        val title = a.attr("title").ifBlank { selectFirst(".b-content__inline_item-link a")?.text() }?.trim() ?: return null
        val href = a.attr("href")
        val poster = selectFirst("img")?.attr("src")
        val isSeries = href.contains("/series/") || href.contains("/cartoons/")
        return if (isSeries)
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
        else
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.post(
            "$mainUrl/search/",
            data = mapOf("do" to "search", "subaction" to "search", "q" to query)
        ).document
        return doc.select("div.b-content__inline_item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("div.b-post__title h1, .b-post__origtitle")?.text()?.trim() ?: return null
        val poster = doc.selectFirst("div.b-sidecover img")?.attr("src")
        val plot = doc.selectFirst("div.b-post__description_text")?.text()?.trim()
        val year = doc.selectFirst("div.b-post__info tr td a[href*=/year/]")?.text()?.trim()?.toIntOrNull()
        val tags = doc.select("span.l_g a").map { it.text() }
        val isSeries = doc.selectFirst("div.b-post__schedule") != null || url.contains("/series/")

        if (isSeries) {
            val episodes = doc.select("li.b-post__schedule_item").map { ep ->
                val epHref = ep.selectFirst("a")?.attr("href") ?: url
                val epTitle = ep.selectFirst(".b-post__schedule_name")?.text()?.trim() ?: ""
                val season = ep.selectFirst(".b-post__schedule_season")?.text()?.trim()?.toIntOrNull()
                val epNum = ep.selectFirst(".b-post__schedule_number")?.text()?.trim()?.toIntOrNull()
                Episode(epHref, epTitle, season, epNum)
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
        val id = doc.selectFirst("[id^=post-]")?.attr("id")?.removePrefix("post-") ?: return false

        // Fetch stream data via AJAX — this is the legitimate video API, not an ad
        val ajaxResponse = app.post(
            "$mainUrl/ajax/get_cdn_series/",
            data = mapOf("id" to id, "translator_id" to "1", "action" to "get_movie"),
            headers = headers
        ).parsedSafe<HdrezkaStreamData>() ?: return false

        val videoUrl = ajaxResponse.url ?: return false
        if (!isAdUrl(videoUrl)) {
            callback(newExtractorLink(name, name, videoUrl) {
                this.referer = data
                this.quality = Qualities.Unknown.value
                this.type = ExtractorLinkType.M3U8
            })
        }
        return true
    }

    data class HdrezkaStreamData(val url: String? = null, val success: Boolean = false)

    private fun isAdUrl(url: String): Boolean =
        listOf("pop", "ad", "redirect", "tracking", "doubleclick", "googlesyndication")
            .any { url.contains(it, ignoreCase = true) }
}
