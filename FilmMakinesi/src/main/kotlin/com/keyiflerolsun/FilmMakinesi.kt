// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
// import com.keyiflerolsun.UniversalVideoExtractor // Temporarily disabled for build compatibility
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri


class FilmMakinesi : MainAPI() {
    override var mainUrl              = "https://filmmakinesi.de"
    override var name                 = "FilmMakinesi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    // ! CloudFlare bypass (DiziBox tarzı)
    override var sequentialMainPage            = true 
    override var sequentialMainPageDelay       = 100L  
    override var sequentialMainPageScrollDelay = 100L  

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(10 * 1024).string())

            if (response.code == 503 || doc.selectFirst("meta[name='cloudflare']") != null) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/filmler/sayfa/"                                to "🔥 EN SON FİLMLER",
        "${mainUrl}/film-izle/olmeden-izlenmesi-gerekenler/sayfa/" to "🔥 ÖLMEDEN İZLE",
        "${mainUrl}/film-izle/en-iyi-filmler/sayfa/"               to "🔥 EN İYİ FİLMLER", 
        "${mainUrl}/film-izle/son-eklenen-filmler/sayfa/"          to "🔥 SON EKLENENLER",
        "${mainUrl}/tur/aksiyon/film/sayfa/"                       to "Aksiyon",
        "${mainUrl}/tur/bilim-kurgu/film/sayfa/"                   to "Bilim Kurgu",
        "${mainUrl}/tur/macera/film/sayfa/"                        to "Macera",
        "${mainUrl}/tur/komedi/film/sayfa/"                        to "Komedi",
        "${mainUrl}/tur/romantik/film/sayfa/"                      to "Romantik",
        "${mainUrl}/tur/belgesel/film/sayfa/"                      to "Belgesel",
        "${mainUrl}/tur/fantastik/film/sayfa/"                     to "Fantastik",
        "${mainUrl}/tur/polisiye/film/sayfa/"                      to "Polisiye Suç",
        "${mainUrl}/tur/korku/film/sayfa/"                         to "Korku",
        // "${mainUrl}/tur/savas/film/sayfa/"                      to "Tarihi ve Savaş",
        // "${mainUrl}/film-izle/gerilim-filmleri-izle/sayfa/"     to "Gerilim Heyecan",
        // "${mainUrl}/film-izle/gizemli/sayfa/"                   to "Gizem",
        // "${mainUrl}/film-izle/aile-filmleri/sayfa/"             to "Aile",
        // "${mainUrl}/film-izle/animasyon-filmler/sayfa/"         to "Animasyon",
        // "${mainUrl}/film-izle/western/sayfa/"                   to "Western",
        // "${mainUrl}/film-izle/biyografi/sayfa/"                 to "Biyografik",
        // "${mainUrl}/film-izle/dram/sayfa/"                      to "Dram",
        // "${mainUrl}/film-izle/muzik/sayfa/"                     to "Müzik",
        // "${mainUrl}/film-izle/spor/sayfa/"                      to "Spor"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
val cleanedUrl = request.data.removeSuffix("/")
val url = if (page > 1) {
    "$cleanedUrl/$page"
} else {
    cleanedUrl.replace(Regex("/sayfa/?$"), "")
}

val document = app.get(url, headers = mapOf(
    "User-Agent" to USER_AGENT,
    "Referer" to mainUrl
)).document

    val home = document.select("div.film-list div.item-relative")
        .mapNotNull { it.toSearchResult() }

    Log.d("FLMM", "Toplam film: ${home.size}")
    return newHomePageResponse(request.name, home)
}

private fun Element.toSearchResult(): SearchResponse? {
    val aTag = selectFirst("a.item") ?: return null
    val title = aTag.attr("data-title").takeIf { it.isNotBlank() } ?: return null
    val href = fixUrlNull(aTag.attr("href")) ?: return null
    val posterUrl = fixUrlNull(aTag.selectFirst("img")?.attr("src"))

    Log.d("FLMM", "Film: $title, Href: $href, Poster: $posterUrl")

    return newMovieSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = posterUrl
    }
}
    private fun Element.toRecommendResult(): SearchResponse? {
        val title     = this.select("a").last()?.text() ?: return null
        val href      = fixUrlNull(this.select("a").last()?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/arama/?s=${query}").document

        return document.select("div.film-list div.item-relative").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description     = document.select("div.info-description p").last()?.text()?.trim()
        val tags            = document.selectFirst("dt:contains(Tür:) + dd")?.text()?.split(", ")
        val rating          = document.selectFirst("dt:contains(IMDB Puanı:) + dd")?.text()?.trim()?.toRatingInt()
        val year            = document.selectFirst("dt:contains(Yapım Yılı:) + dd")?.text()?.trim()?.toIntOrNull()

        val durationElement = document.select("dt:contains(Film Süresi:) + dd time").attr("datetime")
        // ? ISO 8601 süre formatını ayrıştırma (örneğin "PT129M")
        val duration        = if (durationElement.startsWith("PT") && durationElement.endsWith("M")) {
            durationElement.drop(2).dropLast(1).toIntOrNull() ?: 0
        } else {
            0
        }

        val recommendations = document.select("div.film-list div.item-relative").mapNotNull { it.toRecommendResult() }
        val actors          = document.selectFirst("dt:contains(Oyuncular:) + dd")?.text()?.split(", ")?.map {
            Actor(it.trim())
        }

        val trailer = document.selectFirst("div.left a.trailer-button")?.attr("data-video_url")?.substringAfter("embed/", "")?.let { 
    if (it.isNotEmpty()) "https://www.youtube.com/watch?v=$it" else null 
}

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            // this.rating = rating // Deprecated, removed for compatibility
            this.duration        = duration
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    }


    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("FLMM", "data » $data")
        var foundLinks = false
        
        try {
            val document = app.get(data, headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )).document
            
            // 1. Original method - iframe and video parts
            val iframeSrc = document.selectFirst("iframe")?.attr("data-src") ?: document.selectFirst("iframe")?.attr("src") ?: ""
        val videoUrls = document.select(".video-parts a[data-video_url]").map { it.attr("data-video_url") }
        val allUrls = (if (iframeSrc.isNotEmpty()) listOf(iframeSrc) else emptyList()) + videoUrls

        allUrls.forEach { url ->
                Log.d("FLMM", "Processing original URL: $url")
                try {
            loadExtractor(url, "${mainUrl}/", subtitleCallback, callback)
                    foundLinks = true
                } catch (e: Exception) {
                    Log.d("FLMM", "Error with original extractor: ${e.message}")
                }
            }
            
            // 2. Advanced extraction if original method fails
            if (!foundLinks) {
                Log.d("FLMM", "Trying advanced video extraction...")
                
                // Multiple iframe selectors
                document.select("iframe, div.video-player iframe, div.player iframe, .embed-responsive iframe").forEach { iframe ->
                    val src = fixUrlNull(iframe.attr("src")) ?: fixUrlNull(iframe.attr("data-src")) ?: fixUrlNull(iframe.attr("data-lazy-src"))
                    if (src != null && src.isNotEmpty()) {
                        Log.d("FLMM", "Found advanced iframe: $src")
                        try {
                            loadExtractor(src, "${mainUrl}/", subtitleCallback, callback)
                            foundLinks = true
                        } catch (e: Exception) {
                            Log.d("FLMM", "Error with advanced iframe: ${e.message}")
                        }
                    }
                }
                
                // Video elements
                document.select("video source, video").forEach { video ->
                    val videoSrc = fixUrlNull(video.attr("src"))
                    if (videoSrc != null && videoSrc.isNotEmpty()) {
                        Log.d("FLMM", "Found direct video: $videoSrc")
                        callback.invoke(
                            newExtractorLink(
                                source = "Direct Video",
                                name = "Direct Video",
                                url = videoSrc,
                                type = if (videoSrc.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                            ) {
                                headers = mapOf("Referer" to "${mainUrl}/")
                                quality = Qualities.Unknown.value
                            }
                        )
                        foundLinks = true
                    }
                }
                
                // DiziBox tarzı CryptoJS ve Base64 decode
                document.select("script").forEach { script ->
                    val scriptText = script.data()
                    
                    // CryptoJS AES decrypt kontrolü
                    val cryptMatch = Regex("""CryptoJS\.AES\.decrypt\("(.*?)",\s*"(.*?)"\)""").find(scriptText)
                    if (cryptMatch != null) {
                        try {
                            Log.d("FLMM", "Found CryptoJS encrypted data, attempting to extract...")
                            // CryptoJS pattern bulundu, video URL'i aranacak
                        } catch (e: Exception) {
                            Log.d("FLMM", "CryptoJS decrypt failed: ${e.message}")
                        }
                    }
                    
                    // Base64 + unescape kontrolü
                    val atobMatch = Regex("""unescape\("(.*)"\)""").find(scriptText)
                    if (atobMatch != null) {
                        try {
                            val encodedData = atobMatch.groupValues[1]
                            val decodedData = encodedData.decodeUri()
                            val finalData = String(android.util.Base64.decode(decodedData, android.util.Base64.DEFAULT), Charsets.UTF_8)
                            
                            // Decode edilmiş data'da video URL ara
                            val videoPattern = Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4))["']""")
                            val videoMatch = videoPattern.find(finalData)
                            if (videoMatch != null) {
                                val videoUrl = videoMatch.groupValues[1]
                                Log.d("FLMM", "Found video URL after Base64 decode: $videoUrl")
                                callback.invoke(
                                    newExtractorLink(
                                        source = "Decoded Video",
                                        name = "FilmMakinesi Decoded",
                                        url = videoUrl,
                                        type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    ) {
                                        headers = mapOf("Referer" to "${mainUrl}/")
                                        quality = Qualities.Unknown.value
                                    }
                                )
                                foundLinks = true
                                return@forEach
                            }
                        } catch (e: Exception) {
                            Log.d("FLMM", "Base64 decode failed: ${e.message}")
                        }
                    }
                    
                    // JavaScript video patterns
                    if (scriptText.contains("video") || scriptText.contains(".m3u8") || scriptText.contains(".mp4")) {
                        val patterns = listOf(
                            """["']([^"']*\.m3u8[^"']*)["']""",
                            """["']([^"']*\.mp4[^"']*)["']""",
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
                                        Log.d("FLMM", "Found script video: $fullUrl")
                                        callback.invoke(
                                            newExtractorLink(
                                                source = "Script Video",
                                                name = "Script Video",
                                                url = fullUrl,
                                                type = if (fullUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                                            ) {
                                                headers = mapOf("Referer" to "${mainUrl}/")
                                                quality = Qualities.Unknown.value
                                            }
                                        )
                                        foundLinks = true
                                    }
                                }
                            } catch (e: Exception) {
                                Log.d("FLMM", "Error parsing script: ${e.message}")
                            }
                        }
                    }
                }
                
                // Data attributes
                document.select("*[data-video-url], *[data-video], *[data-src]").forEach { element ->
                    val videoUrl = element.attr("data-video-url").ifEmpty { 
                        element.attr("data-video").ifEmpty { element.attr("data-src") }
                    }
                    if (videoUrl.isNotEmpty() && (videoUrl.startsWith("http") || videoUrl.contains("player"))) {
                        Log.d("FLMM", "Found data attribute video: $videoUrl")
                        try {
                            loadExtractor(videoUrl, "${mainUrl}/", subtitleCallback, callback)
                            foundLinks = true
                        } catch (e: Exception) {
                            Log.d("FLMM", "Error with data attribute: ${e.message}")
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("FLMM", "Error in loadLinks: ${e.message}")
        }
        
        // Son çare: Evrensel video extractor (temporarily disabled for build compatibility)
        /* if (!foundLinks) {
            Log.d("FLMM", "Trying UniversalVideoExtractor as last resort...")
            foundLinks = UniversalVideoExtractor.extractVideo(
                url = data,
                mainUrl = mainUrl,
                logTag = "FLMM",
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        } */
        
        Log.d("FLMM", "FilmMakinesi extraction completed. Found links: $foundLinks")
        return foundLinks
}
}
