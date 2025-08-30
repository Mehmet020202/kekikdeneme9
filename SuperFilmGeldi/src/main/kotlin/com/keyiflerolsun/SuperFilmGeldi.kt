// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.keyiflerolsun.UltimateVideoExtractor

class SuperFilmGeldi : MainAPI() {
    override var mainUrl              = "https://www.superfilmgeldi7.art"
    override var name                 = "SuperFilmGeldi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/page/"                                        to "🔥 EN SON FİLMLER",
        "${mainUrl}/hdizle/category/imdb-8-uzeri/page/"           to "🔥 IMDB 8+ FİLMLER",
        "${mainUrl}/hdizle/category/netflix-filmleri/page/"       to "🔥 NETFLİX FİLMLERİ", 
        "${mainUrl}/hdizle/category/2024-filmleri/page/"          to "🔥 2024 FİLMLERİ",
        "${mainUrl}/hdizle/category/aksiyon/page/"                to "Aksiyon",
        "${mainUrl}/hdizle/category/animasyon/page/"              to "Animasyon",
        "${mainUrl}/hdizle/category/belgesel/page/"               to "Belgesel",
        "${mainUrl}/hdizle/category/bilim-kurgu/page/"            to "Bilim Kurgu",
        "${mainUrl}/hdizle/category/fantastik/page/"              to "Fantastik",
        "${mainUrl}/hdizle/category/komedi-filmleri/page/"        to "Komedi Filmleri",
        "${mainUrl}/hdizle/category/macera/page/"                 to "Macera",
        "${mainUrl}/hdizle/category/gerilim/page/"                to "Gerilim",
        "${mainUrl}/hdizle/category/suc/page/"                    to "Suç",
        "${mainUrl}/hdizle/category/karete-filmleri/page/"        to "Karete Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}").document
        val home     = document.select("div.movie-preview-content").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun removeUnnecessarySuffixes(title: String): String {
        val unnecessarySuffixes = listOf(
            " izle", 
            " full film", 
            " filmini full",
            " full türkçe",
            " alt yazılı", 
            " altyazılı", 
            " tr dublaj",
            " hd türkçe",
            " türkçe dublaj",
            " yeşilçam ",
            " erotik fil",
            " türkçe",
            " yerli",
        )

        var cleanedTitle = title.trim()

        for (suffix in unnecessarySuffixes) {
            val regex = Regex("${Regex.escape(suffix)}.*$", RegexOption.IGNORE_CASE)
            cleanedTitle = cleanedTitle.replace(regex, "").trim()
        }

        return cleanedTitle
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("span.movie-title a")?.text()?.substringBefore(" izle") ?: return null
        val href      = fixUrlNull(this.selectFirst("span.movie-title a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(removeUnnecessarySuffixes(title), href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}?s=${query}").document

        return document.select("div.movie-preview-content").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("div.title h1")?.text()?.trim()?.substringBefore(" izle") ?: return null
        val poster          = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
        val year            = document.selectFirst("div.release a")?.text()?.toIntOrNull()
        val description     = document.selectFirst("div.excerpt p")?.text()?.trim()
        val tags            = document.select("div.categories a").map { it.text() }
        val rating          = document.selectFirst("span.imdb-rating")?.text()?.trim()?.split(" ")?.first()?.toRatingInt()
        val recommendations = document.select("div.film-content div.existing_item").mapNotNull { it.toSearchResult() }
        val actors          = document.select("div.actor a").map {
            Actor(it.text())
        }

        return newMovieLoadResponse(removeUnnecessarySuffixes(title), url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            // this.rating = rating // Deprecated, removed for compatibility
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("SFG", "🚀 ULTIMATE EXTRACTION STARTED for: $data")
        
        // 🆕 ULTIMATE VIDEO EXTRACTION SYSTEM - PHASE 1
        Log.d("SFG", "Starting Ultimate Video Extraction...")
        val ultimateFound = UltimateVideoExtractor.extractFromUrl(data, mainUrl, callback)
        if (ultimateFound) {
            Log.d("SFG", "✅ ULTIMATE SYSTEM FOUND VIDEOS!")
            return true
        }
        
        Log.d("SFG", "Ultimate system completed, trying fallback methods...")
        var foundLinks = false
        
        try {
            // IDM tarzı headers
            val userAgents = listOf(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            val headers = mapOf(
                "User-Agent" to userAgents.random(),
                "Accept" to "*/*",
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                "DNT" to "1",
                "Cache-Control" to "no-cache"
            )
            val document = app.get(data, headers = headers).document
            
            // 1. Original method - div#vast iframe
            val iframe = fixUrlNull(document.selectFirst("div#vast iframe")?.attr("src"))
            Log.d("SFG", "Original iframe » $iframe")

            if (iframe != null && iframe.contains("mix") && iframe.contains("index.php?data=")) {
                try {
                    val iSource  = app.get(iframe, referer="${mainUrl}/", headers = headers).text
                    val mixPoint = Regex("""videoUrl":"(.*)","videoServer""").find(iSource)?.groupValues?.get(1)?.replace("\\", "")

                    if (mixPoint != null) {
            var endPoint = "?s=0&d="

            if (iframe.contains("mixlion")) {
                endPoint = "?s=3&d="
            } else if (iframe.contains("mixeagle")) {
                endPoint = "?s=1&d="
            }

            val m3uLink = iframe.substringBefore("/player") + mixPoint + endPoint
            Log.d("SFG", "m3uLink » $m3uLink")

            callback.invoke(
                newExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = m3uLink,
				type = ExtractorLinkType.M3U8
            ) {
                                this.quality = Qualities.Unknown.value
                                this.headers = mapOf("Referer" to iframe)
                            }
                        )
                        foundLinks = true
                    }
                } catch (e: Exception) {
                    Log.d("SFG", "Error with mix player: ${e.message}")
                }
            } else if (iframe != null) {
                try {
                    loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
                    foundLinks = true
                } catch (e: Exception) {
                    Log.d("SFG", "Error with iframe extractor: ${e.message}")
                }
            }
            
            // 2. Advanced extraction if original method fails
            if (!foundLinks) {
                Log.d("SFG", "Trying advanced video extraction...")
                
                // Multiple iframe selectors
                document.select("iframe, div.video-player iframe, div.player iframe, #video iframe").forEach { iframeElement ->
                    val src = fixUrlNull(iframeElement.attr("src")) ?: fixUrlNull(iframeElement.attr("data-src"))
                    if (src != null && src.isNotEmpty()) {
                        Log.d("SFG", "Found advanced iframe: $src")
                        try {
                            loadExtractor(src, "${mainUrl}/", subtitleCallback, callback)
                            foundLinks = true
                        } catch (e: Exception) {
                            Log.d("SFG", "Error with advanced iframe: ${e.message}")
                        }
                    }
                }
                
                // Direct video elements
                document.select("video source, video").forEach { video ->
                    val videoSrc = fixUrlNull(video.attr("src"))
                    if (videoSrc != null && videoSrc.isNotEmpty()) {
                        Log.d("SFG", "Found direct video: $videoSrc")
                        callback.invoke(
                            newExtractorLink(
                                source = "Direct Video",
                                name = "Direct Video",
                                url = videoSrc,
                                type = if (videoSrc.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.headers = mapOf("Referer" to "${mainUrl}/")
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        foundLinks = true
                    }
                }
                
                // JavaScript patterns
                document.select("script").forEach { script ->
                    val scriptText = script.data()
                    if (scriptText.contains("video") || scriptText.contains(".m3u8") || scriptText.contains(".mp4") || 
                        scriptText.contains("mixPoint") || scriptText.contains("videoUrl")) {
                        
                        val patterns = listOf(
                            """["']([^"']*\.m3u8[^"']*)["']""",
                            """["']([^"']*\.mp4[^"']*)["']""",
                            """videoUrl['"]\s*:\s*["']([^"']+)["']""",
                            """source['"]\s*:\s*["']([^"']+)["']""",
                            """file['"]\s*:\s*["']([^"']+)["']"""
                        )
                        
                        for (pattern in patterns) {
                            try {
                                val match = Regex(pattern).find(scriptText)
                                if (match != null) {
                                    val videoUrl = match.groupValues[1]
                                    if (videoUrl.isNotEmpty() && (videoUrl.startsWith("http") || videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))) {
                                        val fullUrl = if (videoUrl.startsWith("http")) videoUrl else "${mainUrl}/$videoUrl"
                                        Log.d("SFG", "Found script video: $fullUrl")
                                        callback.invoke(
                                            newExtractorLink(
                                                source = "Script Video",
                                                name = "Script Video",
                                                url = fullUrl,
                                                type = if (fullUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                            ) {
                                                this.headers = mapOf("Referer" to "${mainUrl}/")
                                                this.quality = Qualities.Unknown.value
                                            }
                                        )
                                        foundLinks = true
                                    }
                                }
                            } catch (e: Exception) {
                                Log.d("SFG", "Error parsing script pattern: ${e.message}")
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("SFG", "Error in loadLinks: ${e.message}")
        }

        Log.d("SFG", "SuperFilmGeldi extraction completed. Found links: $foundLinks")
        return foundLinks
    }
}