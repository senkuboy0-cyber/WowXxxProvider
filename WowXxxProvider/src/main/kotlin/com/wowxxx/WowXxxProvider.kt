package com.wowxxx

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class WowXxxProvider : MainAPI() {
    override var mainUrl = "https://www.wow.xxx"
    override var name = "WowXXX"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Others)

    private val ua = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/latest-updates/"                           to "🆕 Latest Updates",
        "$mainUrl/networks/brazzers-com/latest-updates/"     to "🔥 Brazzers",
        "$mainUrl/networks/teamskeet-com/latest-updates/"    to "🎬 TeamSkeet",
        "$mainUrl/networks/mylf-com/latest-updates/"         to "💋 MYLF",
        "$mainUrl/networks/rk-com/latest-updates/"           to "⭐ RK Prime",
        "$mainUrl/networks/mom-lover/latest-updates/"        to "❤️ Mom Lover",
        "$mainUrl/sites/perv-mom/latest-updates/"            to "Perv Mom",
        "$mainUrl/sites/my-pervy-family/latest-updates/"     to "My Pervy Family",
        "$mainUrl/sites/my-dirty-maid/latest-updates/"       to "My Dirty Maid",
        "$mainUrl/sites/dad-crush/latest-updates/"           to "Dad Crush",
        "$mainUrl/sites/sis-loves-me/latest-updates/"        to "Sis Loves Me",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data
                  else request.data.trimEnd('/') + "/$page/"
        val doc = app.get(url, headers = ua).document
        val items = doc.select("div.item, article.item, div.video-item, div.thumb-block").mapNotNull { el ->
            val a = el.selectFirst("a[href*='/videos/']") ?: return@mapNotNull null
            val href = a.attr("abs:href").ifBlank { return@mapNotNull null }
            val title = (el.selectFirst(".title, h2, h3, .video-title")?.text()
                ?: a.attr("title")).trim().ifBlank { return@mapNotNull null }
            val poster = el.selectFirst("img")?.let {
                it.attr("data-src").ifBlank { it.attr("src") }
            }?.let { if (it.startsWith("http")) it else null }
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
        }
        val hasNext = doc.selectFirst("a.next, a[rel=next], .pagination .next") != null
        return newHomePageResponse(request.name, items, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val doc = app.get("$mainUrl/search/?q=$encoded", headers = ua).document
        return doc.select("div.item, div.video-item, div.thumb-block").mapNotNull { el ->
            val a = el.selectFirst("a[href*='/videos/']") ?: return@mapNotNull null
            val href = a.attr("abs:href").ifBlank { return@mapNotNull null }
            val title = (el.selectFirst(".title, h2, h3")?.text()
                ?: a.attr("title")).trim().ifBlank { return@mapNotNull null }
            val poster = el.selectFirst("img")?.let {
                it.attr("data-src").ifBlank { it.attr("src") }
            }?.let { if (it.startsWith("http")) it else null }
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: doc.selectFirst("h1, h2")?.text()?.trim()
            ?: doc.title().trim()
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("video[poster]")?.attr("poster")
        val description = doc.selectFirst("meta[name=description], meta[property=og:description]")
            ?.attr("content")
        // data হিসেবে page URL pass করো — loadLinks এ fresh URL নেওয়া হবে
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank() || !data.startsWith("http")) return false

        // Play করার সময় fresh URL নাও
        val pageHtml = app.get(data, headers = ua).text

        val qualityMap = mapOf(
            "2160" to Qualities.P2160.value,
            "1080" to Qualities.P1080.value,
            "720"  to Qualities.P720.value,
            "480"  to Qualities.P480.value,
            "360"  to Qualities.P360.value,
        )

        val qualityNameMap = mapOf(
            Qualities.P2160.value to "4K",
            Qualities.P1080.value to "1080p",
            Qualities.P720.value  to "720p",
            Qualities.P480.value  to "480p",
            Qualities.P360.value  to "360p",
        )

        // get_file URL বের করো (download=true সহ)
        val links = Regex("""https://www\.wow\.xxx/get_file/[^\s"'<>]+\.mp4[^\s"'<>]*""")
            .findAll(pageHtml)
            .map { it.value }
            .filter { !it.contains("preview") && !it.contains("screenshot") }
            .distinctBy { url ->
                // quality আলাদা করো
                qualityMap.keys.firstOrNull { url.contains(it) } ?: "unknown"
            }
            .toList()

        if (links.isEmpty()) return false

        links.forEach { videoUrl ->
            val qualityKey = qualityMap.keys.firstOrNull { videoUrl.contains(it) }
            val quality = qualityMap[qualityKey] ?: Qualities.Unknown.value
            val qualityName = qualityNameMap[quality] ?: "HD"

            callback(newExtractorLink(name, "$name [$qualityName]", videoUrl, ExtractorLinkType.VIDEO) {
                this.quality = quality
                this.headers = ua + mapOf(
                    "Referer" to mainUrl,
                    "Origin" to mainUrl
                )
            })
        }
        return true
    }
}
