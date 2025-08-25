// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
// import com.keyiflerolsun.UniversalVideoExtractor // Temporarily disabled for build compatibility
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import okhttp3.Interceptor
import okhttp3.Response
import android.util.Base64
import org.jsoup.Jsoup
import java.util.regex.Pattern

class FullHDFilm : MainAPI() {
    override var mainUrl              = "https://fullhdfilm1.us"
    override var name                 = "FullHDFilm"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/"                                     to "🔥 EN SON FİLMLER",
        "${mainUrl}/tur/turkce-altyazili-film-izle"       to "🔥 TÜRKÇE ALTYAZILI",
        "${mainUrl}/tur/netflix-filmleri-izle"		       to "🔥 NETFLİX FİLMLERİ",
        "${mainUrl}/tur/en-iyi-filmler-izle"              to "🔥 EN İYİ FİLMLER",
        "${mainUrl}/category/aile-filmleri-izle"	       to "Aile",
        "${mainUrl}/category/aksiyon-filmleri-izle"       to "Aksiyon",
        "${mainUrl}/category/animasyon-filmleri-izle"	   to "Animasyon",
        "${mainUrl}/category/belgesel-filmleri-izle"	   to "Belgesel",
        "${mainUrl}/category/bilim-kurgu-filmleri-izle"   to "Bilim-Kurgu",
        "${mainUrl}/category/biyografi-filmleri-izle"	   to "Biyografi",
        "${mainUrl}/category/dram-filmleri-izle"		   to "Dram",
        "${mainUrl}/category/fantastik-filmler-izle"	   to "Fantastik",
        "${mainUrl}/category/gerilim-filmleri-izle"	   to "Gerilim",
        "${mainUrl}/category/gizem-filmleri-izle"		   to "Gizem",
        "${mainUrl}/category/komedi-filmleri-izle"		   to "Komedi",
        "${mainUrl}/category/korku-filmleri-izle"		   to "Korku",
        "${mainUrl}/category/macera-filmleri-izle"		   to "Macera",
        "${mainUrl}/category/romantik-filmler-izle"	   to "Romantik",
        "${mainUrl}/category/savas-filmleri-izle"		   to "Savaş",
        "${mainUrl}/category/suc-filmleri-izle"		   to "Suç",
        "${mainUrl}/tur/yerli-film-izle"			       to "Yerli Film"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Referer" to "https://fullhdfilm1.us/"
            )
        val baseUrl = request.data
        val urlpage = if (page == 1) baseUrl else "$baseUrl/page/$page"
        val document = app.get(urlpage, headers=headers).document
        val home     = document.select("div.movie-poster").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("img")?.attr("alt")?.trim() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document

        return document.select("div.movie-poster").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
    
        val title       = document.selectFirst("h1")?.text() ?: return null
    
        val poster      = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
        val description = document.select("#details > div:nth-child(2) > div")?.text()?.trim()
        val tags        = document.select("h4 a").map { it.text() }
        val rating      = document.selectFirst("div.button-custom")?.text()?.trim()?.split(" ")?.first()?.toRatingInt()
        val year        = Regex("""(\d+)""").find(document.selectFirst("div.release")?.text()?.trim() ?: "")?.groupValues?.get(1)?.toIntOrNull()
        val actors = document.selectFirst("div.oyuncular")?.ownText() ?.split(",") ?.map { Actor(it.trim()) } ?: emptyList()
    
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            // this.rating = rating // Deprecated, removed for compatibility
            addActors(actors)
        }
    }

    private fun getIframe(sourceCode: String): String {
        // Base64 kodlu iframe'i içeren script bloğunu yakala
        val base64ScriptRegex = Regex("""<script[^>]*>(PCEtLWJhc2xpazp[^<]*)</script>""")
        val base64Encoded = base64ScriptRegex.find(sourceCode)?.groupValues?.get(1) ?: return ""
    
        return try {
            // Base64 decode
            val decodedHtml = String(Base64.decode(base64Encoded, Base64.DEFAULT), Charsets.UTF_8)
    
            // Jsoup ile parse edip iframe src'sini al
            val iframeSrc = Jsoup.parse(decodedHtml).selectFirst("iframe")?.attr("src")
    
            fixUrlNull(iframeSrc) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractSubtitleUrl(sourceCode: String): String? {
        // playerjsSubtitle değişkenini regex ile bul (genelleştirilmiş)
        val patterns = listOf(
            Pattern.compile("var playerjsSubtitle = \"\\[Türkçe\\](https?://[^\\s\"]+?\\.srt)\";"),
            Pattern.compile("var playerjsSubtitle = \"(https?://[^\\s\"]+?\\.srt)\";"), // Türkçe etiketi olmadan
            Pattern.compile("subtitle:\\s*\"(https?://[^\\s\"]+?\\.srt)\"") // Alternatif subtitle formatı
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(sourceCode)
            if (matcher.find()) {
                val subtitleUrl = matcher.group(1)
                Log.d("FHDF", "Found subtitle URL: $subtitleUrl")
                return subtitleUrl
            }
        }
        Log.d("FHDF", "No subtitle URL found in source code")
        return null
    }

    private suspend fun extractSubtitleFromIframe(iframeUrl: String): String? {
        if (iframeUrl.isEmpty()) return null
        try {
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
                "Referer" to mainUrl
            )
            val iframeResponse = app.get(iframeUrl, headers=headers)
            val iframeSource = iframeResponse.text
            Log.d("FHDF", "Iframe source length: ${iframeSource.length}")
            return extractSubtitleUrl(iframeSource)
        } catch (e: Exception) {
            Log.d("FHDF", "Iframe subtitle extraction error: ${e.message}")
            return null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("FHDF", "data » $data")
        var foundLinks = false

        try {
        // IDM tarzı headers
        val userAgents = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        val headers = mapOf(
            "User-Agent" to userAgents.random(),
            "Accept" to "*/*",
            "Accept-Encoding" to "gzip, deflate, br",
            "Connection" to "keep-alive",
            "DNT" to "1",
            "Cache-Control" to "no-cache",
            "Referer" to mainUrl
        )
        val response = app.get(data, headers=headers)
        val sourceCode = response.text
            val document = response.document
        Log.d("FHDF", "Source code length: ${sourceCode.length}")

            // 1. Original method - Base64 iframe extraction
        var subtitleUrl = extractSubtitleUrl(sourceCode)
        val iframeSrc = getIframe(sourceCode)
        Log.d("FHDF", "Original iframeSrc: $iframeSrc")

        if (subtitleUrl == null && iframeSrc.isNotEmpty()) {
            subtitleUrl = extractSubtitleFromIframe(iframeSrc)
        }

            // Process subtitles
        if (subtitleUrl != null) {
            try {
                val subtitleResponse = app.get(subtitleUrl, headers=headers, allowRedirects=true)
                if (subtitleResponse.isSuccessful) {
                    subtitleCallback(SubtitleFile("Türkçe", subtitleUrl))
                    Log.d("FHDF", "Subtitle added: $subtitleUrl")
                }
            } catch (e: Exception) {
                Log.d("FHDF", "Subtitle URL error: ${e.message}")
            }
        }

        if (iframeSrc.isNotEmpty()) {
                try {
                    loadExtractor(iframeSrc, mainUrl, subtitleCallback, callback)
                    foundLinks = true
                } catch (e: Exception) {
                    Log.d("FHDF", "Error with original iframe: ${e.message}")
                }
            }
            
            // 2. Advanced extraction if original method fails
            if (!foundLinks) {
                Log.d("FHDF", "Trying advanced video extraction...")
                
                // Multiple iframe selectors
                document.select("iframe, div.video-player iframe, div.player iframe, .embed iframe").forEach { iframe ->
                    val src = fixUrlNull(iframe.attr("src")) ?: fixUrlNull(iframe.attr("data-src"))
                    if (src != null && src.isNotEmpty()) {
                        Log.d("FHDF", "Found advanced iframe: $src")
                        try {
                            loadExtractor(src, mainUrl, subtitleCallback, callback)
                            foundLinks = true
                        } catch (e: Exception) {
                            Log.d("FHDF", "Error with advanced iframe: ${e.message}")
                        }
                    }
                }
                
                // Direct video elements
                document.select("video source, video").forEach { video ->
                    val videoSrc = fixUrlNull(video.attr("src"))
                    if (videoSrc != null && videoSrc.isNotEmpty()) {
                        Log.d("FHDF", "Found direct video: $videoSrc")
                        callback.invoke(
                            newExtractorLink(
                                source = "Direct Video",
                                name = "Direct Video",
                                url = videoSrc,
                                type = if (videoSrc.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                            ) {
                                this.headers = mapOf("Referer" to mainUrl)
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        foundLinks = true
                    }
                }
                
                // JavaScript patterns including Base64 variations
                document.select("script").forEach { script ->
                    val scriptText = script.data()
                    
                    // Base64 pattern detection
                    if (scriptText.contains("base64") || scriptText.contains("PCEt") || scriptText.contains("PHNjcmlwdA")) {
                        try {
                            val base64Patterns = listOf(
                                """(PCEt[A-Za-z0-9+/=]+)""",
                                """atob\(['"]([A-Za-z0-9+/=]+)['"]\)""",
                                """base64['"]\s*:\s*['"]([A-Za-z0-9+/=]+)['"]"""
                            )
                            
                            for (pattern in base64Patterns) {
                                val match = Regex(pattern).find(scriptText)
                                if (match != null) {
                                    try {
                                        val decoded = String(Base64.decode(match.groupValues[1], Base64.DEFAULT), Charsets.UTF_8)
                                        val iframeSrcDecoded = Jsoup.parse(decoded).selectFirst("iframe")?.attr("src")
                                        if (iframeSrcDecoded != null && iframeSrcDecoded.isNotEmpty()) {
                                            Log.d("FHDF", "Found Base64 iframe: $iframeSrcDecoded")
                                            loadExtractor(fixUrlNull(iframeSrcDecoded) ?: iframeSrcDecoded, mainUrl, subtitleCallback, callback)
                                            foundLinks = true
                                        }
                                    } catch (e: Exception) {
                                        Log.d("FHDF", "Error decoding Base64: ${e.message}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d("FHDF", "Error processing Base64 script: ${e.message}")
                        }
                    }
                    
                    // Regular video patterns
                    if (scriptText.contains("video") || scriptText.contains(".m3u8") || scriptText.contains(".mp4")) {
                        val patterns = listOf(
                            """["']([^"']*\.m3u8[^"']*)["']""",
                            """["']([^"']*\.mp4[^"']*)["']""",
                            """source['"]\s*:\s*["']([^"']+)["']""",
                            """file['"]\s*:\s*["']([^"']+)["']""",
                            """src['"]\s*:\s*["']([^"']+)["']"""
                        )
                        
                        for (pattern in patterns) {
                            try {
                                val match = Regex(pattern).find(scriptText)
                                if (match != null) {
                                    val videoUrl = match.groupValues[1]
                                    if (videoUrl.isNotEmpty() && (videoUrl.startsWith("http") || videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))) {
                                        val fullUrl = if (videoUrl.startsWith("http")) videoUrl else "$mainUrl/$videoUrl"
                                        Log.d("FHDF", "Found script video: $fullUrl")
                                        callback.invoke(
                                            newExtractorLink(
                                                source = "Script Video",
                                                name = "Script Video",
                                                url = fullUrl,
                                                type = if (fullUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                                            ) {
                                                this.headers = mapOf("Referer" to mainUrl)
                                                this.quality = Qualities.Unknown.value
                                            }
                                        )
                                        foundLinks = true
                                    }
                                }
                            } catch (e: Exception) {
                                Log.d("FHDF", "Error parsing script pattern: ${e.message}")
                            }
                        }
                    }
                }
                
                // Data attributes
                document.select("*[data-video], *[data-src], *[data-player]").forEach { element ->
                    val videoUrl = element.attr("data-video").ifEmpty { 
                        element.attr("data-src").ifEmpty { element.attr("data-player") }
                    }
                    if (videoUrl.isNotEmpty() && (videoUrl.startsWith("http") || videoUrl.contains("player"))) {
                        Log.d("FHDF", "Found data attribute video: $videoUrl")
                        try {
                            loadExtractor(videoUrl, mainUrl, subtitleCallback, callback)
                            foundLinks = true
                        } catch (e: Exception) {
                            Log.d("FHDF", "Error with data attribute: ${e.message}")
                        }
                    }
                }
                
                // Additional subtitle patterns
                val subtitlePatterns = listOf(
                    """subtitle['"]\s*:\s*["']([^"']+\.srt)["']""",
                    """altyazi['"]\s*:\s*["']([^"']+\.srt)["']""",
                    """tracks\s*:\s*\[.*?["']([^"']+\.srt)["']""",
                    """["']([^"']*\.srt[^"']*)["']"""
                )
                
                for (pattern in subtitlePatterns) {
                    try {
                        val match = Regex(pattern, RegexOption.IGNORE_CASE).find(sourceCode)
                        if (match != null) {
                            val subUrl = match.groupValues[1]
                            if (subUrl.isNotEmpty() && subUrl.contains(".srt")) {
                                val fullSubUrl = if (subUrl.startsWith("http")) subUrl else "$mainUrl/$subUrl"
                                Log.d("FHDF", "Found additional subtitle: $fullSubUrl")
                                try {
                                    subtitleCallback(SubtitleFile("Türkçe", fullSubUrl))
                                } catch (e: Exception) {
                                    Log.d("FHDF", "Error adding subtitle: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("FHDF", "Error parsing subtitle pattern: ${e.message}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("FHDF", "Error in loadLinks: ${e.message}")
        }

        // Son çare: Evrensel video extractor (temporarily disabled for build compatibility)
        /* if (!foundLinks) {
            Log.d("FHDF", "Trying UniversalVideoExtractor as last resort...")
            foundLinks = UniversalVideoExtractor.extractVideo(
                url = data,
                mainUrl = mainUrl,
                logTag = "FHDF",
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        } */
        
        Log.d("FHDF", "FullHDFilm extraction completed. Found links: $foundLinks")
        return foundLinks
    }
}
