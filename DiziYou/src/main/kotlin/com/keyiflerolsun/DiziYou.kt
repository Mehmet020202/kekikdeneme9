// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class DiziYou : MainAPI() {
    override var mainUrl              = "https://www.diziyou16.com"
    override var name                 = "DiziYou"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)
    
    // ! CloudFlare bypass
    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 250L // ? 0.25 saniye
    override var sequentialMainPageScrollDelay = 250L // ? 0.25 saniye
    
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList())
    
        val document = app.get(mainUrl).document
        val home = ArrayList<HomePageList>()
    
        // 1. Popüler Dizilerden Son Bölümler
        val populer = document.select("div.dsmobil div.listepisodes").mapNotNull { el ->
            val episodeAnchor = el.selectFirst("a") ?: return@mapNotNull null
            val fullEpisodeUrl = fixUrlNull(episodeAnchor.attr("href")) ?: return@mapNotNull null
            val slug = fullEpisodeUrl
                .removePrefix("$mainUrl/")
                .replace(Regex("""-\d+-sezon-\d+-bolum/?$"""), "")
            val href = "$mainUrl/$slug/"
        
            // alt="..." değeri başlık olarak
            val title = episodeAnchor.selectFirst("img[alt]")?.attr("alt")?.trim()
                ?: return@mapNotNull null
        
            // poster görseli (data-src veya src)
            val poster = fixUrlNull(
                episodeAnchor.selectFirst("img.lazy")?.attr("data-src")
                    ?: episodeAnchor.selectFirst("img")?.attr("src")
            )
        
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            }
        }
        if (populer.isNotEmpty()) home.add(HomePageList("Popüler Dizilerden Son Bölümler", populer))
    
        // 2. Son Eklenen Diziler
        val sonEklenen = document.select("div.dsmobil2 div#list-series-main").mapNotNull { el ->
            val href = fixUrlNull(el.selectFirst("div.cat-img-main a")?.attr("href")) ?: return@mapNotNull null
            val poster = fixUrlNull(el.selectFirst("div.cat-img-main img")?.attr("src"))
            val title = el.selectFirst("div.cat-title-main a")?.text()?.trim() ?: return@mapNotNull null
    
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            }
        }
        if (sonEklenen.isNotEmpty()) home.add(HomePageList("Son Eklenen Diziler", sonEklenen))
    
        // 3. Efsane Diziler
        val efsane = document.select("div.incontent div#list-series-main").mapNotNull { el ->
            val href = fixUrlNull(el.selectFirst("div.cat-img-main a")?.attr("href")) ?: return@mapNotNull null
            val poster = fixUrlNull(el.selectFirst("div.cat-img-main img")?.attr("src"))
            val title = el.selectFirst("div.cat-title-main a")?.text()?.trim() ?: return@mapNotNull null
    
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            }
        }
        if (efsane.isNotEmpty()) home.add(HomePageList("Efsane Diziler", efsane))
    
        // 4. Dikkat Çeken Diziler
        val dikkat = document.select("div.incontentyeni div#list-series-main").mapNotNull { el ->
            val href = fixUrlNull(el.selectFirst("div.cat-img-main a")?.attr("href")) ?: return@mapNotNull null
            val poster = fixUrlNull(el.selectFirst("div.cat-img-main img")?.attr("src"))
            val title = el.selectFirst("div.cat-title-main a")?.text()?.trim() ?: return@mapNotNull null
    
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            }
        }
        if (dikkat.isNotEmpty()) home.add(HomePageList("Dikkat Çeken Diziler", dikkat))
    
        return newHomePageResponse(home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("div#categorytitle a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("div#categorytitle a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document

        return document.select("div.incontent div#list-series").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("div.category_image img")?.attr("src"))
        val description     = document.selectFirst("div.diziyou_desc")?.ownText()?.trim()
        val year            = document.selectFirst("span.dizimeta:contains(Yapım Yılı)")?.nextSibling()?.toString()?.trim()?.toIntOrNull()
        val tags            = document.select("div.genres a").map { it.text() }
        val rating          = document.selectFirst("span.dizimeta:contains(IMDB)")?.nextSibling()?.toString()?.trim()?.toRatingInt()
        val actors          = document.selectFirst("span.dizimeta:contains(Oyuncular)")?.nextSibling()?.toString()?.trim()?.split(", ")?.map { Actor(it) }
        val trailer         = document.selectFirst("iframe.trailer-video")?.attr("src")

        val episodes = document.select("div.bolumust").mapNotNull {
            val epName    = it.selectFirst("div.baslik")?.ownText()?.trim() ?: return@mapNotNull null
            val epHref    = it.closest("a")?.attr("href")?.let { href -> fixUrlNull(href) } ?: return@mapNotNull null
            val epEpisode = Regex("""(\d+)\. Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
            val epSeason  = Regex("""(\d+)\. Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            newEpisode(epHref) {
                this.name = it.selectFirst("div.bolumismi")?.text()?.trim()?.replace(Regex("""[()]"""), "")?.trim() ?: epName
                this.season = epSeason
                this.episode = epEpisode
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot      = description
            this.year      = year
            this.tags      = tags
            // this.rating    = rating
            addActors(actors)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZY", "data » $data")
        var foundLinks = false
        
        try {
            val document = app.get(data, headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )).document

            // 1. Original DiziYou method
            val itemId = document.selectFirst("iframe#diziyouPlayer")?.attr("src")?.split("/")?.lastOrNull()?.substringBefore(".html")
            
            if (itemId != null) {
                Log.d("DZY", "itemId » $itemId")
                val subTitles  = mutableListOf<DiziyouSubtitle>()
                val streamUrls = mutableListOf<DiziyouStream>()
                val storage    = mainUrl.replace("www", "storage")

                document.select("span.diziyouOption").forEach {
                    val optId   = it.attr("id")

                    if (optId == "turkceAltyazili") {
                        subTitles.add(DiziyouSubtitle("Turkish", "${storage}/subtitles/${itemId}/tr.vtt"))
                        streamUrls.add(DiziyouStream("Orjinal Dil", "${storage}/episodes/${itemId}/play.m3u8"))
                    }

                    if (optId == "ingilizceAltyazili") {
                        subTitles.add(DiziyouSubtitle("English", "${storage}/subtitles/${itemId}/en.vtt"))
                        streamUrls.add(DiziyouStream("Orjinal Dil", "${storage}/episodes/${itemId}/play.m3u8"))
                    }

                    if (optId == "turkceDublaj") {
                        streamUrls.add(DiziyouStream("Türkçe Dublaj", "${storage}/episodes/${itemId}_tr/play.m3u8"))
                    }
                }
                
                // Process original method results
                for (sub in subTitles) {
                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang = sub.name,
                            url  = sub.url
                        )
                    )
                }

                for (stream in streamUrls) {
                    Log.d("DZY", "Original stream: ${stream.name} - ${stream.url}")
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name   = stream.name,
                            url    = stream.url,
                            type   = ExtractorLinkType.M3U8
                        ) {
                            quality = Qualities.Unknown.value
                            headers = mapOf("Referer" to mainUrl)
                        }
                    )
                    foundLinks = true
                }
            }
            
            // 2. Advanced extraction if original method fails or as backup
            if (!foundLinks) {
                Log.d("DZY", "Trying advanced video extraction...")
                
                // Multiple iframe selectors
                document.select("iframe, div.video-player iframe, div.player iframe, #video iframe").forEach { iframe ->
                    val src = fixUrlNull(iframe.attr("src")) ?: fixUrlNull(iframe.attr("data-src"))
                    if (src != null && src.isNotEmpty()) {
                        Log.d("DZY", "Found advanced iframe: $src")
                        try {
                            loadExtractor(src, "${mainUrl}/", subtitleCallback, callback)
                            foundLinks = true
                        } catch (e: Exception) {
                            Log.d("DZY", "Error with advanced iframe: ${e.message}")
                        }
                    }
                }
                
                // Direct video elements
                document.select("video source, video").forEach { video ->
                    val videoSrc = fixUrlNull(video.attr("src"))
                    if (videoSrc != null && videoSrc.isNotEmpty()) {
                        Log.d("DZY", "Found direct video: $videoSrc")
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
                
                // JavaScript patterns
                document.select("script").forEach { script ->
                    val scriptText = script.data()
                    if (scriptText.contains("m3u8") || scriptText.contains("mp4") || scriptText.contains("play.")) {
                        val patterns = listOf(
                            """["']([^"']*play\.m3u8[^"']*)["']""",
                            """["']([^"']*episodes[^"']*\.m3u8[^"']*)["']""",
                            """["']([^"']*\.m3u8[^"']*)["']""",
                            """["']([^"']*\.mp4[^"']*)["']"""
                        )
                        
                        for (pattern in patterns) {
                            try {
                                val match = Regex(pattern).find(scriptText)
                                if (match != null) {
                                    val videoUrl = match.groupValues[1]
                                    if (videoUrl.isNotEmpty() && (videoUrl.startsWith("http") || videoUrl.contains("play") || videoUrl.contains("episode"))) {
                                        val fullUrl = if (videoUrl.startsWith("http")) videoUrl else "${mainUrl}/$videoUrl"
                                        Log.d("DZY", "Found script video: $fullUrl")
                                        callback.invoke(
                                            newExtractorLink(
                                                source = "Script Video",
                                                name = "Script Video",
                                                url = fullUrl,
                                                type = ExtractorLinkType.M3U8
                                            ) {
                                                headers = mapOf("Referer" to "${mainUrl}/")
                                                quality = Qualities.Unknown.value
                                            }
                                        )
                                        foundLinks = true
                                    }
                                }
                            } catch (e: Exception) {
                                Log.d("DZY", "Error parsing script: ${e.message}")
                            }
                        }
                    }
                }
                
                // Data attributes for video URLs
                document.select("*[data-video], *[data-src], *[data-stream]").forEach { element ->
                    val videoUrl = element.attr("data-video").ifEmpty { 
                        element.attr("data-src").ifEmpty { element.attr("data-stream") }
                    }
                    if (videoUrl.isNotEmpty() && (videoUrl.contains("m3u8") || videoUrl.contains("mp4"))) {
                        val fullUrl = if (videoUrl.startsWith("http")) videoUrl else "${mainUrl}/$videoUrl"
                        Log.d("DZY", "Found data attribute video: $fullUrl")
                        callback.invoke(
                            newExtractorLink(
                                source = "Data Video",
                                name = "Data Video", 
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
            }
            
        } catch (e: Exception) {
            Log.e("DZY", "Error in loadLinks: ${e.message}")
        }

        
        Log.d("DZY", "DiziYou extraction completed. Found links: $foundLinks")
        return foundLinks
    }

    data class DiziyouSubtitle(val name: String, val url: String)
    data class DiziyouStream(val name: String, val url: String)
}
