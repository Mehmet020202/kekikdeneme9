// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class AnizmNet : MainAPI() {
    override var mainUrl              = "https://anizm.net"
    override var name                 = "AnizmNet"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    // ! CloudFlare bypass
    override var sequentialMainPage = true
    override var sequentialMainPageDelay       = 150L
    override var sequentialMainPageScrollDelay = 150L

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.html().contains("Just a moment") || doc.html().contains("Checking your browser")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/son-eklenen" to "🔥 EN SON ANİMELER",
        "${mainUrl}/son-bolumler" to "🔥 EN SON BÖLÜMLER", 
        "${mainUrl}/yeni-eklenen" to "🔥 YENİ EKLENEN ANİMELER",
        "${mainUrl}/populer" to "🔥 POPÜLER ANİMELER",
        "${mainUrl}/anime-listesi" to "Anime Listesi",
        "${mainUrl}/tam-bolum" to "Tam Bölüm",
        "${mainUrl}/film" to "Anime Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val url = if (page == 1) request.data else "${request.data}?sayfa=$page"
            val document = app.get(url, interceptor = interceptor).document
            val home = document.select("div.anime-card, div.anime-item, article.anime, .anime-list-item").mapNotNull { it.toSearchResult() }

            return newHomePageResponse(request.name, home)
        } catch (e: Exception) {
            Log.d("ANIZM", "Error loading main page: ${e.message}")
            return newHomePageResponse(request.name, emptyList())
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2, h3, .title, .anime-title")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src")) ?: 
                       fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        try {
            val document = app.get("${mainUrl}/arama?q=${query}", interceptor = interceptor).document
            return document.select("div.anime-card, div.anime-item, article.anime, .anime-list-item").mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            Log.d("ANIZM", "Error in search: ${e.message}")
            return emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        try {
            val document = app.get(url, interceptor = interceptor).document

            val title = document.selectFirst("h1, .anime-title")?.text()?.trim() ?: return null
            val poster = fixUrlNull(document.selectFirst("img.poster, .anime-poster img")?.attr("src")) ?:
                        fixUrlNull(document.selectFirst("img.poster, .anime-poster img")?.attr("data-src"))
            val description = document.selectFirst(".description, .plot, .synopsis")?.text()?.trim()
            val tags = document.select(".genres a, .tags a").map { it.text() }
            val year = document.selectFirst(".year, .release-date")?.text()?.trim()?.toIntOrNull()
            val score = document.selectFirst(".rating, .score")?.text()?.trim()?.toRatingInt()

            val episodes = document.select(".episode-list a, .episodes a, .bolum-listesi a").mapNotNull {
                val epName = it.text().trim()
                val epHref = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                val epNumber = Regex("""(\d+)""").find(epName)?.groupValues?.get(1)?.toIntOrNull()

                newEpisode(epHref) {
                    this.name = epName
                    this.episode = epNumber
                }
            }

            val recommendations = document.select(".similar-anime a, .recommendations a").mapNotNull {
                val recName = it.text().trim()
                val recHref = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                val recPoster = fixUrlNull(it.selectFirst("img")?.attr("src"))

                newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                    this.posterUrl = recPoster
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = score
                this.recommendations = recommendations
            }
        } catch (e: Exception) {
            Log.d("ANIZM", "Error loading content: ${e.message}")
            return null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("ANIZM", "data » $data")
        try {
            val document = app.get(data, interceptor = interceptor).document
            var foundLinks = false

            // 1. Video player iframe'leri
            document.select("div.video-player iframe, div.player iframe, iframe[src*='player'], iframe").forEach { iframe ->
                val iframeSrc = fixUrlNull(iframe.attr("src")) ?: fixUrlNull(iframe.attr("data-src"))
                if (iframeSrc != null) {
                    Log.d("ANIZM", "Found iframe: $iframeSrc")
                    loadExtractor(iframeSrc, "${mainUrl}/", subtitleCallback, callback)
                    foundLinks = true
                }
            }

            // 2. Video element'leri
            if (!foundLinks) {
                document.select("video source, video").forEach { source ->
                    val videoSrc = fixUrlNull(source.attr("src"))
                    if (videoSrc != null) {
                        Log.d("ANIZM", "Found video source: $videoSrc")
                        callback.invoke(
                            newExtractorLink(
                                source = "Direct Video",
                                name = "Direct Video",
                                url = videoSrc,
                                type = ExtractorLinkType.M3U8
                            ) {
                                headers = mapOf("Referer" to "${mainUrl}/")
                                quality = Qualities.Unknown.value
                            }
                        )
                        foundLinks = true
                    }
                }
            }
            
            // 4. AnizmNet özel pattern'leri
            if (!foundLinks) {
                Log.d("ANIZM", "Trying AnizmNet specific patterns...")
                
                // AnizmNet'e özel iframe selectors
                document.select("iframe[src*='anizm'], iframe[src*='animiz'], .anime-player iframe, #anime-player iframe").forEach { iframe ->
                    val src = fixUrlNull(iframe.attr("src")) ?: fixUrlNull(iframe.attr("data-src"))
                    if (src != null && src.isNotEmpty()) {
                        Log.d("ANIZM", "Found AnizmNet iframe: $src")
                        try {
                            loadExtractor(src, "${mainUrl}/", subtitleCallback, callback)
                            foundLinks = true
                        } catch (e: Exception) {
                            Log.d("ANIZM", "Error with AnizmNet iframe: ${e.message}")
                        }
                    }
                }
                
                // AnizmNet JavaScript patterns
                document.select("script").forEach { script ->
                    val scriptText = script.data()
                    if (scriptText.contains("anime") || scriptText.contains("video") || scriptText.contains(".m3u8") || 
                        scriptText.contains("player") || scriptText.contains("episode")) {
                        
                        val patterns = listOf(
                            """["']([^"']*\.m3u8[^"']*)["']""",
                            """["']([^"']*\.mp4[^"']*)["']""",
                            """video['"]\s*:\s*["']([^"']+)["']""",
                            """episode['"]\s*:\s*["']([^"']+)["']""",
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
                                        val fullUrl = if (videoUrl.startsWith("http")) videoUrl else "${mainUrl}/$videoUrl"
                                        Log.d("ANIZM", "Found AnizmNet script video: $fullUrl")
                                        callback.invoke(
                                            newExtractorLink(
                                                source = "AnizmNet Script",
                                                name = "AnizmNet Script",
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
                                Log.d("ANIZM", "Error parsing AnizmNet script: ${e.message}")
                            }
                        }
                    }
                }
                
                // AnizmNet data attributes
                document.select("*[data-video], *[data-episode], *[data-anime], *[data-src]").forEach { element ->
                    val videoUrl = element.attr("data-video").ifEmpty { 
                        element.attr("data-episode").ifEmpty { 
                            element.attr("data-anime").ifEmpty { element.attr("data-src") }
                        }
                    }
                    if (videoUrl.isNotEmpty() && (videoUrl.startsWith("http") || videoUrl.contains("player") || videoUrl.contains("episode"))) {
                        Log.d("ANIZM", "Found AnizmNet data video: $videoUrl")
                        try {
                            loadExtractor(videoUrl, "${mainUrl}/", subtitleCallback, callback)
                            foundLinks = true
                        } catch (e: Exception) {
                            Log.d("ANIZM", "Error with AnizmNet data attribute: ${e.message}")
                        }
                    }
                }
                
                // AnizmNet AJAX endpoints
                try {
                    val episodeId = data.substringAfterLast("/").substringBefore("?").substringBefore("#")
                    val ajaxPaths = listOf("/ajax/anime/", "/ajax/episode/", "/api/episode/", "/player/")
                    
                    for (path in ajaxPaths) {
                        try {
                            val ajaxUrl = "${mainUrl}$path$episodeId"
                            val response = app.get(
                                ajaxUrl,
                                headers = mapOf(
                                    "X-Requested-With" to "XMLHttpRequest",
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                                ),
                                referer = data,
                                interceptor = interceptor
                            ).text
                            
                            val videoMatch = Regex("""["'](?:video|url|source|file|episode)["']\s*:\s*["']([^"']+)["']""").find(response)
                            videoMatch?.groupValues?.get(1)?.let { videoUrl ->
                                if (videoUrl.isNotEmpty() && (videoUrl.startsWith("http") || videoUrl.contains(".m3u8"))) {
                                    Log.d("ANIZM", "Found AnizmNet AJAX video: $videoUrl")
                                    callback.invoke(
                                        newExtractorLink(
                                            source = "AnizmNet AJAX",
                                            name = "AnizmNet AJAX",
                                            url = videoUrl,
                                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                                        ) {
                                            headers = mapOf("Referer" to "${mainUrl}/")
                                            quality = Qualities.Unknown.value
                                        }
                                    )
                                    foundLinks = true
                                }
                            }
                        } catch (e: Exception) {
                            Log.d("ANIZM", "Error with AnizmNet AJAX $path: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.d("ANIZM", "Error with AnizmNet AJAX requests: ${e.message}")
                }
            }

            // 3. JSON data yapısı
            if (!foundLinks) {
                document.select("script").filter { it.data().contains("\"file\"") || it.data().contains("\"source\"") || it.data().contains("\"url\"") }.forEach { script ->
                    try {
                        val jsonData = script.data()
                        val fileMatch = Regex("""["']file["']\s*:\s*["']([^"']+)["']""").find(jsonData)
                        val sourceMatch = Regex("""["']source["']\s*:\s*["']([^"']+)["']""").find(jsonData)
                        val urlMatch = Regex("""["']url["']\s*:\s*["']([^"']+)["']""").find(jsonData)
                        
                        val videoUrl = fileMatch?.groupValues?.get(1) ?: sourceMatch?.groupValues?.get(1) ?: urlMatch?.groupValues?.get(1)
                        if (videoUrl != null && videoUrl.isNotEmpty()) {
                            Log.d("ANIZM", "Found JSON video URL: $videoUrl")
                            callback.invoke(
                                newExtractorLink(
                                    source = "JSON Video",
                                    name = "JSON Video",
                                    url = videoUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    headers = mapOf("Referer" to "${mainUrl}/")
                                    quality = Qualities.Unknown.value
                                }
                            )
                            foundLinks = true
                        }
                    } catch (e: Exception) {
                        Log.d("ANIZM", "Error parsing JSON script: ${e.message}")
                    }
                }
            }

            // 4. AJAX video data
            if (!foundLinks) {
                try {
                    val episodeId = data.substringAfterLast("/")
                    val ajaxResponse = app.get(
                        "${mainUrl}/ajax/episode/$episodeId", 
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                        referer = data,
                        interceptor = interceptor
                    ).text
                    
                    val videoMatch = Regex("""["']video["']\s*:\s*["']([^"']+)["']""").find(ajaxResponse)
                    val videoUrl = videoMatch?.groupValues?.get(1)
                    if (videoUrl != null && videoUrl.isNotEmpty()) {
                        Log.d("ANIZM", "Found AJAX video URL: $videoUrl")
                        callback.invoke(
                            newExtractorLink(
                                source = "AJAX Video",
                                name = "AJAX Video",
                                url = videoUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                headers = mapOf("Referer" to "${mainUrl}/")
                                quality = Qualities.Unknown.value
                            }
                        )
                        foundLinks = true
                    }
                } catch (e: Exception) {
                    Log.d("ANIZM", "Error with AJAX request: ${e.message}")
                }
            }

            // 5. Tüm script'leri tara
            if (!foundLinks) {
                document.select("script").forEach { script ->
                    try {
                        val scriptData = script.data()
                        // Video URL pattern'lerini ara
                        val patterns = listOf(
                            Regex("""["']video["']\s*:\s*["']([^"']+)["']"""),
                            Regex("""["']file["']\s*:\s*["']([^"']+)["']"""),
                            Regex("""["']source["']\s*:\s*["']([^"']+)["']"""),
                            Regex("""["']url["']\s*:\s*["']([^"']+)["']"""),
                            Regex("""src\s*=\s*["']([^"']*\.m3u8[^"']*)["']"""),
                            Regex("""src\s*=\s*["']([^"']*\.mp4[^"']*)["']""")
                        )
                        
                        for (pattern in patterns) {
                            val match = pattern.find(scriptData)
                            if (match != null) {
                                val videoUrl = match.groupValues[1]
                                if (videoUrl.isNotEmpty() && (videoUrl.contains("http") || videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))) {
                                    Log.d("ANIZM", "Found video URL in script: $videoUrl")
                                    callback.invoke(
                                        newExtractorLink(
                                            source = "Script Video",
                                            name = "Script Video",
                                            url = videoUrl,
                                            type = ExtractorLinkType.M3U8
                                        ) {
                                            headers = mapOf("Referer" to "${mainUrl}/")
                                            quality = Qualities.Unknown.value
                                        }
                                    )
                                    foundLinks = true
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("ANIZM", "Error parsing script: ${e.message}")
                    }
                }
            }

            return foundLinks
        } catch (e: Exception) {
            Log.d("ANIZM", "Error in loadLinks: ${e.message}")
            return false
        }
    }
}
