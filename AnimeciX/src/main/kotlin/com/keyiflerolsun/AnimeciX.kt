// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class AnimeciX : MainAPI() {
    override var mainUrl              = "https://animecix.tv"
    override var name                 = "AnimeciX"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Anime)

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 200L  // ? 0.20 saniye
    override var sequentialMainPageScrollDelay = 200L  // ? 0.20 saniye

    override val mainPage = mainPageOf(
        "${mainUrl}/secure/last-episodes"                          to "Son Eklenen Bölümler",
        "${mainUrl}/secure/titles?type=series&onlyStreamable=true" to "Seriler",
        "${mainUrl}/secure/titles?type=movie&onlyStreamable=true"  to "Filmler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return if (request.data.contains("/last-episodes")) {
            val response = app.get(
                "${mainUrl}/secure/last-episodes?page=$page&perPage=10",
                headers = mapOf(
                    "x-e-h" to "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk"
                )
            ).parsedSafe<LastEpisodesResponse>()?.data ?: emptyList()
    
            val home = response.map {
                val formattedTitle = "S${it.seasonNumber}B${it.episodeNumber} - ${it.titleName}"
                newAnimeSearchResponse(
                    formattedTitle,
                    "${mainUrl}/secure/titles/${it.titleId}?titleId=${it.titleId}",
                    TvType.Anime
                ) {
                    this.posterUrl = fixUrlNull(it.titlePoster)
                }
            }
    
            newHomePageResponse(request.name, home)
        } else {
            val response = app.get(
                "${request.data}&page=${page}&perPage=16",
                headers = mapOf(
                    "x-e-h" to "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk"
                )
            ).parsedSafe<Category>()
    
            val home = response?.pagination?.data?.map { anime ->
                newAnimeSearchResponse(
                    anime.title,
                    "${mainUrl}/secure/titles/${anime.id}?titleId=${anime.id}",
                    TvType.Anime
                ) {
                    this.posterUrl = fixUrlNull(anime.poster)
                }
            } ?: listOf()
    
            newHomePageResponse(request.name, home)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("${mainUrl}/secure/search/${query}?limit=20").parsedSafe<Search>() ?: return listOf()

        return response.results.map { anime ->
            newAnimeSearchResponse(
                anime.title,
                "${mainUrl}/secure/titles/${anime.id}?titleId=${anime.id}",
                TvType.Anime
            ) {
                this.posterUrl = fixUrlNull(anime.poster)
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(
            url,
            headers = mapOf(
                "x-e-h" to "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk"
            )
        ).parsedSafe<Title>() ?: return null
        val episodes = mutableListOf<Episode>()
        val titleId  = url.substringAfter("?titleId=")

        if (response.title.titleType == "anime") {
            for (sezon in response.title.seasons) {
                val sezonResponse = app.get("${mainUrl}/secure/related-videos?episode=1&season=${sezon.number}&videoId=0&titleId=${titleId}").parsedSafe<TitleVideos>() ?: return null
                for (video in sezonResponse.videos) {
                    episodes.add(newEpisode(video.url) {
                        this.name = "${video.seasonNum}. Sezon ${video.episodeNum}. Bölüm"
                        this.season = video.seasonNum
                        this.episode = video.episodeNum
                    })
                }
            }
        } else {
            if (response.title.videos.isNotEmpty()) {
                episodes.add(newEpisode(response.title.videos.first().url) {
                    this.name    = "Filmi İzle"
                    this.season  = 1
                    this.episode = 1
                })
            }
        }


        return newTvSeriesLoadResponse(
            response.title.title,
            "${mainUrl}/secure/titles/${response.title.id}?titleId=${response.title.id}",
            TvType.Anime,
            episodes
        ) {
            this.posterUrl = fixUrlNull(response.title.poster)
            this.year      = response.title.year
            this.plot      = response.title.description
            this.tags      = response.title.tags.map { it.name }
            // this.rating = response.title.rating.toRatingInt() // Deprecated, removed for compatibility
            addActors(response.title.actors.map { Actor(it.name, fixUrlNull(it.poster)) })
            addTrailer(response.title.trailer)
        }
    }
override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    Log.d("ACX", "data » $data")
    val pageUrl = "$mainUrl/$data"
    var foundLinks = false

    try {
        // 1. Ana sayfa analizi ve iframe bulmaya çalış
        val response = app.get(pageUrl, referer = "$mainUrl/", headers = headers)
        val document = response.document
    var iframeLink = response.url
    Log.d("ACX", "iframeLink » $iframeLink")

        // 2. Sayfa içindeki iframe'leri kontrol et
        document.select("iframe").forEach { iframe ->
            val iframeSrc = fixUrlNull(iframe.attr("src")) ?: fixUrlNull(iframe.attr("data-src"))
            if (iframeSrc != null && iframeSrc.isNotEmpty()) {
                Log.d("ACX", "Found iframe in page: $iframeSrc")
                try {
                    loadExtractor(iframeSrc, "$mainUrl/", subtitleCallback, callback)
                    foundLinks = true
                } catch (e: Exception) {
                    Log.d("ACX", "Error loading iframe $iframeSrc: ${e.message}")
                }
            }
        }

        // 3. Eğer iframeLink içinde çift URL varsa düzelt
    val doubleUrlRegex = Regex("https://animecix.tv/(https://animecix.tv/secure/[^\\s]+)")
    val match = doubleUrlRegex.find(iframeLink)
    if (match != null) {
        iframeLink = match.groupValues[1]
        Log.d("ACX", "Corrected iframeLink » $iframeLink")
    }

        // 4. Video element'leri kontrol et
        if (!foundLinks) {
            document.select("video source, video").forEach { video ->
                val videoSrc = fixUrlNull(video.attr("src"))
                if (videoSrc != null && videoSrc.isNotEmpty()) {
                    Log.d("ACX", "Found direct video: $videoSrc")
                    callback.invoke(
                        newExtractorLink(
                            source = "Direct Video",
                            name = "Direct Video",
                            url = videoSrc,
                            type = if (videoSrc.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                        ) {
                            headers = mapOf("Referer" to "$mainUrl/")
                            quality = Qualities.Unknown.value
                        }
                    )
                    foundLinks = true
                }
            }
        }

        // 5. Eğer dizi (best-video) ise yönlendirmeyi takip et
    if (iframeLink.contains("/secure/best-video")) {
            try {
                val redirectResponse = app.get(iframeLink, referer = "$mainUrl/", headers = headers)
        val redirectedUrl = redirectResponse.url
        Log.d("ACX", "Redirected final URL » $redirectedUrl")

        if (redirectedUrl.contains("tau-video")) {
            loadExtractor(redirectedUrl, "$mainUrl/", subtitleCallback, callback)
                    foundLinks = true
        } else {
            Log.d("ACX", "Redirect failed or unexpected URL: $redirectedUrl")
                    // Yine de ekstraktörü dene
                    loadExtractor(redirectedUrl, "$mainUrl/", subtitleCallback, callback)
                    foundLinks = true
                }
            } catch (e: Exception) {
                Log.d("ACX", "Error with redirect: ${e.message}")
            }
        } else if (!foundLinks) {
            try {
                loadExtractor(iframeLink, "$mainUrl/", subtitleCallback, callback)
                foundLinks = true
            } catch (e: Exception) {
                Log.d("ACX", "Error with main extractor: ${e.message}")
            }
        }

        // 6. JavaScript içindeki video linklerini ara
        if (!foundLinks) {
            document.select("script").forEach { script ->
                val scriptText = script.data()
                if (scriptText.contains("video") || scriptText.contains("source") || scriptText.contains(".m3u8") || scriptText.contains(".mp4")) {
                    try {
                        // Farklı video URL pattern'leri dene
                        val patterns = listOf(
                            """["']([^"']*\.m3u8[^"']*)["']""",
                            """["']([^"']*\.mp4[^"']*)["']""",
                            """source['"]\s*:\s*["']([^"']+)["']""",
                            """file['"]\s*:\s*["']([^"']+)["']""",
                            """url['"]\s*:\s*["']([^"']+)["']"""
                        )
                        
                        for (pattern in patterns) {
                            val videoMatch = Regex(pattern).find(scriptText)
                            if (videoMatch != null) {
                                val videoUrl = videoMatch.groupValues[1]
                                if (videoUrl.isNotEmpty() && (videoUrl.startsWith("http") || videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))) {
                                    Log.d("ACX", "Found video URL in script: $videoUrl")
                                    val fullUrl = if (videoUrl.startsWith("http")) videoUrl else "$mainUrl/$videoUrl"
                                    callback.invoke(
                                        newExtractorLink(
                                            source = "Script Video",
                                            name = "Script Video",
                                            url = fullUrl,
                                            type = if (fullUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                                        ) {
                                            headers = mapOf("Referer" to "$mainUrl/")
                                            quality = Qualities.Unknown.value
                                        }
                                    )
                                    foundLinks = true
                                    return@forEach
                                }
                            }
                        }
                        if (foundLinks) return@forEach
                    } catch (e: Exception) {
                        Log.d("ACX", "Error parsing script: ${e.message}")
                    }
                }
            }
        }

        // 7. AJAX video data deneme
        if (!foundLinks) {
            try {
                val episodeId = data.substringAfterLast("/")
                val ajaxResponse = app.get(
                    "${mainUrl}/ajax/episode/$episodeId", 
                    headers = headers + mapOf("X-Requested-With" to "XMLHttpRequest"),
                    referer = pageUrl
                ).text
                
                val videoMatch = Regex("""["']video["']\s*:\s*["']([^"']+)["']""").find(ajaxResponse)
                val videoUrl = videoMatch?.groupValues?.get(1)
                if (videoUrl != null && videoUrl.isNotEmpty()) {
                    Log.d("ACX", "Found AJAX video URL: $videoUrl")
                    callback.invoke(
                        newExtractorLink(
                            source = "AJAX Video",
                            name = "AJAX Video",
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                        ) {
                            headers = mapOf("Referer" to "$mainUrl/")
                            quality = Qualities.Unknown.value
                        }
                    )
                    foundLinks = true
                }
            } catch (e: Exception) {
                Log.d("ACX", "Error with AJAX request: ${e.message}")
            }
        }

    } catch (e: Exception) {
        Log.d("ACX", "Error in loadLinks: ${e.message}")
    }

    return foundLinks
}
}
