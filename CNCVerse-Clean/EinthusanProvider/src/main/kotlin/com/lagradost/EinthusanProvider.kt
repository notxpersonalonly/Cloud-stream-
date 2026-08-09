package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class EinthusanProvider : MainAPI() {
    override var mainUrl = "https://einthusan.tv"
    override var name = "EinthusanProvider"
    override val hasMainPage = true
    override val hasSearch = true
    override var lang = "ta"
    override val supportedTypes = setOf(TvType.Movie)

    private val languages = mapOf(
        "Tamil" to "tamil",
        "Telugu" to "telugu",
        "Hindi" to "hindi",
        "Malayalam" to "malayalam",
        "Kannada" to "kannada",
        "Bengali" to "bengali",
        "Punjabi" to "punjabi",
        "Marathi" to "marathi",
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movie/results/?lang=tamil&query=&find=Recent" to "Tamil Movies",
        "$mainUrl/movie/results/?lang=telugu&query=&find=Recent" to "Telugu Movies",
        "$mainUrl/movie/results/?lang=hindi&query=&find=Recent" to "Hindi Movies",
        "$mainUrl/movie/results/?lang=malayalam&query=&find=Recent" to "Malayalam Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}&p=$page"
        val doc = app.get(url).document
        val items = doc.select("li.item").mapNotNull { it.toSearchResult() }
        val hasNext = doc.selectFirst("a[rel=next], .pagination .next") != null
        return newHomePageResponse(request.name, items, hasNext)
    }

    private fun Element.toSearchResult(): MovieSearchResponse? {
        val a = selectFirst("a[href*=/movie/watch/]") ?: return null
        val title = selectFirst("h3 a, h2 a, p.title")?.text()?.trim() ?: return null
        val href = "$mainUrl${a.attr("href")}"
        val poster = selectFirst("img")?.attr("src")?.let {
            if (it.startsWith("http")) it else "$mainUrl$it"
        }
        return newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/movie/results/?lang=&query=${query.replace(" ", "+")}&find=Search").document
        return doc.select("li.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, h2.fulltitle, #UIMovieSummary h3")?.text()?.trim() ?: return null
        val poster = doc.selectFirst("#UIMovieSummary img, .movie-image img")?.attr("src")?.let {
            if (it.startsWith("http")) it else "$mainUrl$it"
        }
        val plot = doc.selectFirst("p.synopsis, div.info p")?.text()?.trim()
        val year = doc.selectFirst("span.year, .release-year")?.text()?.trim()?.toIntOrNull()
        val tags = doc.select(".genre a, .categories a").map { it.text() }

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
        // Einthusan uses a JSON object in the page for the video URL
        val pageText = app.get(data).text
        val videoJsonRegex = Regex("""EinthusanVideo\.setPlayer\(\{[^}]*"MP4Link"\s*:\s*"([^"]+)"""")
        val mp4Match = videoJsonRegex.find(pageText)
        if (mp4Match != null) {
            val videoUrl = mp4Match.groupValues[1]
            callback(newExtractorLink(name, name, videoUrl) {
                this.referer = data
                this.quality = Qualities.Unknown.value
            })
            return true
        }
        // Fallback: scrape video/source tags
        doc.select("video source[src], source[src]").forEach { src ->
            val u = src.attr("src")
            if (u.isNotBlank()) {
                callback(newExtractorLink(name, name, u) {
                    this.referer = data
                    this.quality = Qualities.Unknown.value
                })
            }
        }
        return true
    }
}
