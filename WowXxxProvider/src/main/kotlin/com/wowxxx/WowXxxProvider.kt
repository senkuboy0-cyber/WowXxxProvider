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
        "$mainUrl/latest-updates/"                                  to "🆕 Latest Updates",
        "$mainUrl/networks/brazzers-com/latest-updates/"            to "🔥 Brazzers",
        "$mainUrl/networks/teamskeet-com/latest-updates/"           to "🎬 TeamSkeet",
        "$mainUrl/networks/mylf-com/latest-updates/"                to "💋 MYLF",
        "$mainUrl/networks/rk-com/latest-updates/"                  to "⭐ RK Prime",
        "$mainUrl/networks/mom-lover/latest-updates/"               to "❤️ Mom Lover",
        "$mainUrl/sites/perv-mom/latest-updates/"                   to "Perv Mom",
        "$mainUrl/sites/my-pervy-family/latest-updates/"            to "My Pervy Family",
        "$mainUrl/sites/my-dirty-maid/latest-updates/"              to "My Dirty Maid",
        "$mainUrl/sites/dad-crush/latest-updates/"                  to "Dad Crush",
        "$mainUrl/sites/sis-loves-me/latest-updates/"               to "Sis Loves Me",
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
        val hasNext = doc.selectFirst("a.next, a[rel=next], .pagination .next, a[href*='/${page+1}/']") != null
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

        // সব quality র video URL বের করো
        val videoLinks = doc.select("source[src*='/get_file/'], a[href*='/get_file/']")
            .mapNotNull { el ->
                val src = el.attr("src").ifBlank { el.attr("href") }.trim()
                if (src.isNotBlank()) src else null
            }.filter { it.contains(".mp4") }
            .also { links ->
                // যদি source tag না থাকে page source থেকে regex দিয়ে বের করো
            }

        // regex দিয়ে get_file URL বের করো
        val pageHtml = app.get(url, headers = ua).text
        val regexLinks = Regex("""https://www\.wow\.xxx/get_file/[^\s"'<>]+\.mp4[^\s"'<>]*""")
            .findAll(pageHtml)
            .map { it.value.trimEnd('/') }
            .filter { !it.contains("download=true") }
            .toList()

        val allLinks = (videoLinks + regexLinks).distinct()
        val dataString = allLinks.joinToString("|")

        return newMovieLoadResponse(title, url, TvType.Movie, dataString) {
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
        if (data.isBlank()) return false
        val links = data.split("|").filter { it.startsWith("http") }
        if (links.isEmpty()) return false

        links.forEach { videoUrl ->
            val quality = when {
                videoUrl.contains("2160") -> Qualities.P2160.value
                videoUrl.contains("1080") -> Qualities.P1080.value
                videoUrl.contains("720")  -> Qualities.P720.value
                videoUrl.contains("480")  -> Qualities.P480.value
                videoUrl.contains("360")  -> Qualities.P360.value
                else                      -> Qualities.Unknown.value
            }
            val qualityName = when (quality) {
                Qualities.P2160.value -> "4K"
                Qualities.P1080.value -> "1080p"
                Qualities.P720.value  -> "720p"
                Qualities.P480.value  -> "480p"
                Qualities.P360.value  -> "360p"
                else                  -> "HD"
            }
            callback(newExtractorLink(name, "$name [$qualityName]", videoUrl, ExtractorLinkType.VIDEO) {
                this.quality = quality
                this.headers = ua + mapOf("Referer" to mainUrl)
            })
        }
        return true
    }
}
