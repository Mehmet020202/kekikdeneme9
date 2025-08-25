// ! https://github.com/hexated/cloudstream-extensions-hexated/blob/master/Hdfilmcehennemi/src/main/kotlin/com/hexated/Hdfilmcehennemi.kt

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack

import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class HDFilmCehennemi : MainAPI() {
    override var mainUrl              = "https://www.hdfilmcehennemi.nl"
    override var name                 = "HDFilmCehennemi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    // 2025 Güncel Alternatif domain'ler
    private val alternativeDomains = listOf(
        "https://www.hdfilmcehennemi.nl",
        "https://www.hdfilmcehennemi.net",
        "https://www.hdfilmcehennemi.com", 
        "https://www.hdfilmcehennemi.org",
        "https://hdfilmcehennemi.nl",
        "https://hdfilmcehennemi.net",
        "https://hdfilmcehennemi.com",
        "https://www.hdfilmcehennemi.xyz",
        "https://www.hdfilmcehennemi.live",
        "https://www.hdfilmcehennemi.site",
        "https://www.hdfilmcehennemi.app"
    )

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

    // Anti-bot headers
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.8,en-US;q=0.5,en;q=0.3",
        "Referer" to mainUrl
    )

    // Domain'i test et ve çalışan domain'i bul
    private suspend fun findWorkingDomain(): String {
        for (domain in alternativeDomains) {
            try {
                val response = app.get("$domain/", timeout = 15000, headers = headers)
                if (response.isSuccessful) {
                    Log.d("HDCH", "Working domain found: $domain")
                    mainUrl = domain
                    return domain
                }
            } catch (e: Exception) {
                Log.d("HDCH", "Domain $domain failed: ${e.message}")
            }
        }
        Log.d("HDCH", "No working domain found, using default: $mainUrl")
        return mainUrl
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/load/page/sayfano/home/"                                       to "🔥 EN SON FİLMLER",
        "${mainUrl}/load/page/sayfano/home-series/"                                to "🔥 EN SON DİZİLER", 
        "${mainUrl}/load/page/sayfano/categories/nette-ilk-filmler/"               to "🔥 NETTE İLK FİLMLER",
        "${mainUrl}/load/page/sayfano/mostLiked/"                                  to "🔥 EN ÇOK BEĞENİLENLER",
        "${mainUrl}/load/page/sayfano/categories/tavsiye-filmler-izle2/"           to "Tavsiye Filmler",
        "${mainUrl}/load/page/sayfano/imdb7/"                                      to "IMDB 7+ Filmler",
        "${mainUrl}/load/page/sayfano/mostCommented/"                              to "En Çok Yorumlananlar",
        "${mainUrl}/load/page/sayfano/mostLiked/"                                  to "En Çok Beğenilenler",
        "${mainUrl}/load/page/sayfano/genres/aile-filmleri-izleyin-6/"             to "Aile Filmleri",
        "${mainUrl}/load/page/sayfano/genres/aksiyon-filmleri-izleyin-5/"          to "Aksiyon Filmleri",
        "${mainUrl}/load/page/sayfano/genres/animasyon-filmlerini-izleyin-5/"      to "Animasyon Filmleri",
        "${mainUrl}/load/page/sayfano/genres/belgesel-filmlerini-izle-1/"          to "Belgesel Filmleri",
        "${mainUrl}/load/page/sayfano/genres/bilim-kurgu-filmlerini-izleyin-3/"    to "Bilim Kurgu Filmleri",
        "${mainUrl}/load/page/sayfano/genres/komedi-filmlerini-izleyin-1/"         to "Komedi Filmleri",
        "${mainUrl}/load/page/sayfano/genres/korku-filmlerini-izle-4/"             to "Korku Filmleri",
        "${mainUrl}/load/page/sayfano/genres/romantik-filmleri-izle-2/"            to "Romantik Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val workingDomain = findWorkingDomain()
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val url = request.data.replace("sayfano", page.toString()).replace(mainUrl, workingDomain)
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
            "Accept" to "*/*", "X-Requested-With" to "fetch"
        )
        
        try {
            val doc = app.get(url, headers = headers, referer = workingDomain, interceptor = interceptor)
            val home: List<SearchResponse>?
            if (!doc.toString().contains("Sayfa Bulunamadı")) {
                val aa: HDFC = objectMapper.readValue(doc.toString())
                val document = Jsoup.parse(aa.html)

                home = document.select("a").mapNotNull { it.toSearchResult() }
                return newHomePageResponse(request.name, home)
            }
        } catch (e: Exception) {
            Log.d("HDCH", "Error loading main page: ${e.message}")
        }
        return newHomePageResponse(request.name, emptyList())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.attr("title")
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        try {
            val workingDomain = findWorkingDomain()
            val response      = app.get(
                "${workingDomain}/search?q=${query}",
                headers = mapOf("X-Requested-With" to "fetch")
            ).parsedSafe<Results>() ?: return emptyList()
            val searchResults = mutableListOf<SearchResponse>()

            response.results.forEach { resultHtml ->
                val document = Jsoup.parse(resultHtml)

                val title     = document.selectFirst("h4.title")?.text() ?: return@forEach
                val href      = fixUrlNull(document.selectFirst("a")?.attr("href")) ?: return@forEach
                val posterUrl = fixUrlNull(document.selectFirst("img")?.attr("src")) ?: fixUrlNull(document.selectFirst("img")?.attr("data-src"))

                searchResults.add(
                    newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl?.replace("/thumb/", "/list/") }
                )
            }

            return searchResults
        } catch (e: Exception) {
            Log.d("HDCH", "Error in search: ${e.message}")
            return emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        try {
            val workingDomain = findWorkingDomain()
            val document = app.get(url, interceptor = interceptor).document

            val title       = document.selectFirst("h1.section-title")?.text()?.substringBefore(" izle") ?: return null
            val poster      = fixUrlNull(document.select("aside.post-info-poster img.lazyload").lastOrNull()?.attr("data-src"))
            val tags        = document.select("div.post-info-genres a").map { it.text() }
            val year        = document.selectFirst("div.post-info-year-country a")?.text()?.trim()?.toIntOrNull()
            val tvType      = if (document.select("div.seasons").isEmpty()) TvType.Movie else TvType.TvSeries
            val description = document.selectFirst("article.post-info-content > p")?.text()?.trim()
            val score      = document.selectFirst("div.post-info-imdb-rating span")?.text()?.substringBefore("(")?.trim()?.toRatingInt()
            val actors      = document.select("div.post-info-cast a").map {
                Actor(it.selectFirst("strong")!!.text(), it.select("img").attr("data-src"))
            }

            val recommendations = document.select("div.section-slider-container div.slider-slide").mapNotNull {
                    val recName      = it.selectFirst("a")?.attr("title") ?: return@mapNotNull null
                    val recHref      = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                    val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("data-src")) ?: fixUrlNull(it.selectFirst("img")?.attr("src"))

                    newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                        this.posterUrl = recPosterUrl
                    }
                }

            return if (tvType == TvType.TvSeries) {
                val trailer  = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")?.substringAfter("trailer/", "")?.let { if (it.isNotEmpty()) "https://www.youtube.com/watch?v=$it" else null }
                Log.d("HDCH", "Trailer: $trailer")
                val episodes = document.select("div.seasons-tab-content a").mapNotNull {
                    val epName    = it.selectFirst("h4")?.text()?.trim() ?: return@mapNotNull null
                    val epHref    = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                    val epEpisode = Regex("""(\d+)\. ?Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                    val epSeason  = Regex("""(\d+)\. ?Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                    newEpisode(epHref) {
                        this.name = epName
                        this.season = epSeason
                        this.episode = epEpisode
                    }
                }

                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl       = poster
                    this.year            = year
                    this.plot            = description
                    this.tags            = tags
                    // this.rating = score // Deprecated, removed for compatibility
                    this.recommendations = recommendations
                    addActors(actors)
                    addTrailer(trailer)
                }
            } else {
                val trailer = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")?.substringAfter("trailer/", "")?.let { if (it.isNotEmpty()) "https://www.youtube.com/watch?v=$it" else null }
                Log.d("HDCH", "Trailer: $trailer")
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl       = poster
                    this.year            = year
                    this.plot            = description
                    this.tags            = tags
                    // this.rating = score // Deprecated, removed for compatibility
                    this.recommendations = recommendations
                    addActors(actors)
                    addTrailer(trailer)
                }
            }
        } catch (e: Exception) {
            Log.d("HDCH", "Error loading content: ${e.message}")
            return null
        }
    }

    private fun dcHello(base64Input: String): String {
        val decodedOnce = base64Decode(base64Input)
        val reversedString = decodedOnce.reversed()
        val decodedTwice = base64Decode(reversedString)

        val hdchLink    = if (decodedTwice.contains("+")) {
        decodedTwice.substringAfterLast("+")
            } else if (decodedTwice.contains(" ")) {
        decodedTwice.substringAfterLast(" ")
            } else if (decodedTwice.contains("|")){
        decodedTwice.substringAfterLast("|")
            } else {
        decodedTwice
            }
        Log.d("HDCH", "decodedTwice $decodedTwice")
             return hdchLink
        }

    private suspend fun invokeLocalSource(source: String, url: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        try {
            Log.d("HDCH", "Processing player URL: $url")
            
            // URL tipine göre özel işlem
            when {
                url.contains("rapidrame") -> {
                    extractRapidrame(url, source, callback)
                    return
                }
                url.contains("closed") || url.contains("closeload") -> {
                    extractClosed(url, source, callback)
                    return
                }
                url.contains("sibnet") -> {
                    extractSibnet(url, source, callback)
                    return
                }
            }
            
            val playerDoc = app.get(url, headers = headers, referer = mainUrl).document
            
            // 1. Script'lerde video kaynağı ara
            playerDoc.select("script").forEach { script ->
                val scriptData = script.data()
                
                // sources: pattern ara
                if (scriptData.contains("sources:") || scriptData.contains("file:")) {
                    val videoMatch = when {
                        scriptData.contains("file_link=") -> {
                            getAndUnpack(scriptData).substringAfter("file_link=\"").substringBefore("\";")
                        }
                        scriptData.contains("\"file\":") -> {
                            Regex("""["']file["']\s*:\s*["']([^"']+)["']""").find(scriptData)?.groupValues?.get(1) ?: ""
                        }
                        scriptData.contains("source:") -> {
                            Regex("""source:\s*["']([^"']+)["']""").find(scriptData)?.groupValues?.get(1) ?: ""
                        }
                        else -> ""
                    }
                    
                    if (videoMatch.isNotEmpty()) {
                        val finalUrl = when {
                            videoMatch.contains("dc_hello(") -> {
                                val base64Input = videoMatch.substringAfter("dc_hello(\"").substringBefore("\");")
                                dcHello(base64Input)
                            }
                            videoMatch.startsWith("http") -> videoMatch
                            videoMatch.startsWith("//") -> "https:$videoMatch"
                            else -> videoMatch
                        }
                        
                        if (finalUrl.startsWith("http")) {
                            Log.d("HDCH", "Found video URL: $finalUrl")
                            callback.invoke(
                                newExtractorLink(
                                    source = source,
                                    name = "HDFilmCehennemi",
                                    url = finalUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.headers = headers
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            return
                        }
                    }
                }
            }
            
            // 2. iframe'lerde ara
            playerDoc.select("iframe").forEach { iframe ->
                val iframeSrc = fixUrlNull(iframe.attr("src")) ?: fixUrlNull(iframe.attr("data-src"))
                if (iframeSrc != null) {
                    Log.d("HDCH", "Found nested iframe: $iframeSrc")
                    try {
                        val nestedDoc = app.get(iframeSrc, headers = headers, referer = url).document
                        val nestedScript = nestedDoc.select("script").find { it.data().contains("sources:") || it.data().contains("file:") }?.data()
                        if (nestedScript != null) {
                            processVideoScript(nestedScript, mainUrl, source, subtitleCallback, callback)
                        }
                    } catch (e: Exception) {
                        Log.d("HDCH", "Error processing nested iframe: ${e.message}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.d("HDCH", "Error in invokeLocalSource: ${e.message}")
        }
    }

    // Rapidrame extractor
    private suspend fun extractRapidrame(url: String, source: String, callback: (ExtractorLink) -> Unit) {
        try {
            Log.d("HDCH", "Extracting Rapidrame: $url")
            
            val doc = app.get(url, headers = headers).document
            
            // Rapidrame video script'i ara
            val script = doc.select("script").find { 
                it.data().contains("sources:") || it.data().contains("file:") || it.data().contains("video")
            }?.data()
            
            if (script != null) {
                // Farklı Rapidrame formatları
                val videoPatterns = listOf(
                    Regex("""file:\s*["']([^"']+)["']"""),
                    Regex("""source:\s*["']([^"']+)["']"""),
                    Regex("""src:\s*["']([^"']+)["']"""),
                    Regex("""["']file["']\s*:\s*["']([^"']+)["']""")
                )
                
                for (pattern in videoPatterns) {
                    val match = pattern.find(script)
                    if (match != null) {
                        val videoUrl = match.groupValues[1]
                        if (videoUrl.startsWith("http") && (videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))) {
                            Log.d("HDCH", "Rapidrame video found: $videoUrl")
                            callback.invoke(
                                newExtractorLink(
                                    source = "$source (Rapidrame)",
                                    name = "Rapidrame",
                                    url = videoUrl,
                                    type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.M3U8
                                ) {
                                    this.headers = headers + mapOf("Referer" to url)
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            return
                        }
                    }
                }
            }
            
            // Alternatif: iframe içinde ara
            doc.select("iframe").forEach { iframe ->
                val iframeSrc = fixUrlNull(iframe.attr("src"))
                if (iframeSrc != null) {
                    try {
                        val iframeDoc = app.get(iframeSrc, headers = headers, referer = url).document
                        val iframeScript = iframeDoc.select("script").find { it.data().contains("file:") }?.data()
                        if (iframeScript != null) {
                            val videoMatch = Regex("""file:\s*["']([^"']+)["']""").find(iframeScript)
                            if (videoMatch != null) {
                                val videoUrl = videoMatch.groupValues[1]
                                if (videoUrl.startsWith("http")) {
                                    callback.invoke(
                                        newExtractorLink(
                                            source = "$source (Rapidrame)",
                                            name = "Rapidrame",
                                            url = videoUrl,
                                            type = ExtractorLinkType.M3U8
                                        ) {
                                            this.headers = headers + mapOf("Referer" to iframeSrc)
                                            this.quality = Qualities.Unknown.value
                                        }
                                    )
                                    return
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("HDCH", "Error in Rapidrame iframe: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("HDCH", "Error extracting Rapidrame: ${e.message}")
        }
    }

    // Closed/CloseLoad extractor
    private suspend fun extractClosed(url: String, source: String, callback: (ExtractorLink) -> Unit) {
        try {
            Log.d("HDCH", "Extracting Closed: $url")
            
            val doc = app.get(url, headers = headers).document
            
            // Closed player script'i ara
            doc.select("script").forEach { script ->
                val scriptData = script.data()
                
                if (scriptData.contains("eval(function") || scriptData.contains("p,a,c,k,e,d")) {
                    // Packed script'i unpack et
                    try {
                        val unpackedScript = getAndUnpack(scriptData)
                        
                        val videoPatterns = listOf(
                            Regex("""file:\s*["']([^"']+)["']"""),
                            Regex("""source:\s*["']([^"']+)["']"""),
                            Regex("""src:\s*["']([^"']+)["']""")
                        )
                        
                        for (pattern in videoPatterns) {
                            val match = pattern.find(unpackedScript)
                            if (match != null) {
                                val videoUrl = match.groupValues[1]
                                if (videoUrl.startsWith("http")) {
                                    Log.d("HDCH", "Closed video found: $videoUrl")
                                    callback.invoke(
                                        newExtractorLink(
                                            source = "$source (Closed)",
                                            name = "Closed",
                                            url = videoUrl,
                                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.M3U8
                                        ) {
                                            this.headers = headers + mapOf("Referer" to url)
                                            this.quality = Qualities.Unknown.value
                                        }
                                    )
                                    return
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("HDCH", "Error unpacking Closed script: ${e.message}")
                    }
                }
                
                // Normal script format
                if (scriptData.contains("file:") || scriptData.contains("source:")) {
                    val videoMatch = Regex("""(?:file|source):\s*["']([^"']+)["']""").find(scriptData)
                    if (videoMatch != null) {
                        val videoUrl = videoMatch.groupValues[1]
                        if (videoUrl.startsWith("http")) {
                            callback.invoke(
                                newExtractorLink(
                                    source = "$source (Closed)",
                                    name = "Closed",
                                    url = videoUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.headers = headers + mapOf("Referer" to url)
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("HDCH", "Error extracting Closed: ${e.message}")
        }
    }

    // Sibnet extractor  
    private suspend fun extractSibnet(url: String, source: String, callback: (ExtractorLink) -> Unit) {
        try {
            Log.d("HDCH", "Extracting Sibnet: $url")
            
            val doc = app.get(url, headers = headers).document
            
            // Sibnet video URL'ini ara
            val videoElements = doc.select("video source, video")
            for (element in videoElements) {
                val videoSrc = fixUrlNull(element.attr("src"))
                if (videoSrc != null && videoSrc.startsWith("http")) {
                    Log.d("HDCH", "Sibnet video found: $videoSrc")
                    callback.invoke(
                        newExtractorLink(
                            source = "$source (Sibnet)",
                            name = "Sibnet",
                            url = videoSrc,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.headers = headers + mapOf("Referer" to url)
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    return
                }
            }
            
            // Script'lerde ara
            doc.select("script").forEach { script ->
                val scriptData = script.data()
                if (scriptData.contains("player.src") || scriptData.contains("video_url")) {
                    val videoMatch = Regex("""(?:player\.src|video_url)\s*=\s*["']([^"']+)["']""").find(scriptData)
                    if (videoMatch != null) {
                        val videoUrl = videoMatch.groupValues[1]
                        if (videoUrl.startsWith("http")) {
                            callback.invoke(
                                newExtractorLink(
                                    source = "$source (Sibnet)",
                                    name = "Sibnet", 
                                    url = videoUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.headers = headers + mapOf("Referer" to url)
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("HDCH", "Error extracting Sibnet: ${e.message}")
        }
    }

    private suspend fun processVideoScript(script: String, workingDomain: String, source: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        try {
            Log.d("HDCH", "Processing script: ${script.take(200)}...")
            
            // Farklı video data formatlarını dene
            val videoData = when {
                script.contains("file_link=") -> {
                    getAndUnpack(script).substringAfter("file_link=\"").substringBefore("\";")
                }
                script.contains("source:") -> {
                    val sourceMatch = Regex("""source:\s*["']([^"']+)["']""").find(script)
                    sourceMatch?.groupValues?.get(1) ?: ""
                }
                script.contains("file:") -> {
                    val fileMatch = Regex("""file:\s*["']([^"']+)["']""").find(script)
                    fileMatch?.groupValues?.get(1) ?: ""
                }
                else -> ""
            }
            
            Log.d("HDCH", "Video data: $videoData")
            
            if (videoData.isNotEmpty()) {
                val videoUrl = when {
                    videoData.contains("dc_hello(") -> {
                        val base64Input = videoData.substringAfter("dc_hello(\"").substringBefore("\");")
                        dcHello(base64Input).substringAfter("https").let { "https$it" }
                    }
                    videoData.startsWith("http") -> videoData
                    else -> videoData
                }
                
                Log.d("HDCH", "Final video URL: $videoUrl")
                
                // Altyazıları işle
                val subData = script.substringAfter("tracks: [").substringBefore("]")
                AppUtils.tryParseJson<List<SubSource>>("[${subData}]")?.filter { it.kind == "captions"}?.map {
                    val subtitleUrl = "${workingDomain}${it.file}/"
                    val headers = mapOf(
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                        "Referer" to subtitleUrl
                    )
                    val subtitleResponse = app.get(subtitleUrl, headers = headers, allowRedirects=true, interceptor = interceptor)
                    if (subtitleResponse.isSuccessful) {
                        subtitleCallback(SubtitleFile(it.language.toString(), subtitleUrl))
                        Log.d("HDCH", "Subtitle added: $subtitleUrl")
                    }
                }
                
                // Video link'ini ekle
                callback.invoke(
                    newExtractorLink(
                        source = source,
                        name = source,
                        url = videoUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.headers = mapOf(
                            "Referer" to "${workingDomain}/", 
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        )
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        } catch (e: Exception) {
            Log.d("HDCH", "Error processing video script: ${e.message}")
        }
    }

    private suspend fun tryAlternativeVideoPlayer(url: String, workingDomain: String, source: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        try {
            Log.d("HDCH", "Trying alternative video player for: $url")
            
            val document = app.get(url, referer = "${workingDomain}/", interceptor = interceptor).document
            
            // Video element'lerini ara
            val videoElements = document.select("video source")
            if (videoElements.isNotEmpty()) {
                val videoSrc = fixUrlNull(videoElements.first()?.attr("src"))
                if (videoSrc != null) {
                    callback.invoke(
                        newExtractorLink(
                            source = source,
                            name = source,
                            url = videoSrc,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.headers = mapOf("Referer" to "${workingDomain}/")
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    return
                }
            }
            
            // iframe'leri ara
            val iframes = document.select("iframe")
            for (iframe in iframes) {
                val iframeSrc = fixUrlNull(iframe.attr("src")) ?: fixUrlNull(iframe.attr("data-src"))
                if (iframeSrc != null && (iframeSrc.contains("player") || iframeSrc.contains("embed"))) {
                    Log.d("HDCH", "Found iframe: $iframeSrc")
                    val iframeDoc = app.get(iframeSrc, referer = url, interceptor = interceptor).document
                    val iframeScript = iframeDoc.select("script").find { it.data().contains("sources:") }?.data()
                    if (iframeScript != null) {
                        processVideoScript(iframeScript, workingDomain, source, subtitleCallback, callback)
                        return
                    }
                }
            }
            
            // JSON data ara
            val jsonScripts = document.select("script").filter { it.data().contains("\"file\"") || it.data().contains("\"source\"") }
            for (script in jsonScripts) {
                try {
                    val jsonData = script.data()
                    val fileMatch = Regex("""["']file["']\s*:\s*["']([^"']+)["']""").find(jsonData)
                    val sourceMatch = Regex("""["']source["']\s*:\s*["']([^"']+)["']""").find(jsonData)
                    
                    val videoUrl = fileMatch?.groupValues?.get(1) ?: sourceMatch?.groupValues?.get(1)
                    if (videoUrl != null && videoUrl.isNotEmpty()) {
                        callback.invoke(
                            newExtractorLink(
                                source = source,
                                name = source,
                                url = videoUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.headers = mapOf("Referer" to "${workingDomain}/")
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        return
                    }
                } catch (e: Exception) {
                    Log.d("HDCH", "Error parsing JSON script: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.d("HDCH", "Error in alternative video player: ${e.message}")
        }
    }

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    Log.d("HDCH", "Loading links for: $data")
    try {
        val workingDomain = findWorkingDomain()
        val document = app.get(data, headers = headers).document
        var foundLinks = false

        // 1. Ana video player yapısı
        document.select("div.alternative-links").map { element ->
            element to element.attr("data-lang").uppercase()
        }.forEach { (element, langCode) ->
            element.select("button.alternative-link").map { button ->
                val sourceName = button.text().replace("(HDrip Xbet)", "").trim() + " $langCode"
                val videoID = button.attr("data-video")
                sourceName to videoID
            }.forEach { (source, videoID) ->
                if (videoID.isNotEmpty()) {
                    try {
                        Log.d("HDCH", "Processing videoID: $videoID")
                        val videoResponse = app.get(
                            "${workingDomain}/video/$videoID/",
                            headers = headers + mapOf("X-Requested-With" to "XMLHttpRequest"),
                            referer = data
                        ).text
                        
                        // iframe URL'sini bul
                        val iframePattern = Regex("""(?:data-src|src)=\\?"([^"\\]+)""")
                        val iframeMatch = iframePattern.find(videoResponse)
                        var iframe = iframeMatch?.groupValues?.get(1)?.replace("\\", "")
                        
                        if (iframe.isNullOrEmpty()) {
                            // Alternatif iframe pattern
                            val altPattern = Regex("""iframe.*?src=["']([^"']+)["']""")
                            iframe = altPattern.find(videoResponse)?.groupValues?.get(1)
                        }
                        
                        if (!iframe.isNullOrEmpty()) {
                            Log.d("HDCH", "Found iframe: $iframe")
                            
                            // iframe URL'sini düzelt
                            val finalIframe = when {
                                iframe.contains("rapidrame") -> "${workingDomain}/rplayer/?rapidrame_id=" + iframe.substringAfter("rapidrame_id=")
                                iframe.startsWith("/") -> workingDomain + iframe
                                !iframe.startsWith("http") -> "${workingDomain}/$iframe"
                                else -> iframe
                            }
                            
                            invokeLocalSource(source, finalIframe, subtitleCallback, callback)
                            foundLinks = true
                        }
                    } catch (e: Exception) {
                        Log.d("HDCH", "Error processing video $videoID: ${e.message}")
                    }
                }
            }
        }

        // 2. Direct iframe'ler
        if (!foundLinks) {
            document.select("iframe").forEach { iframe ->
                val src = fixUrlNull(iframe.attr("src")) ?: fixUrlNull(iframe.attr("data-src"))
                if (src != null) {
                    Log.d("HDCH", "Found direct iframe: $src")
                    invokeLocalSource("Direct Player", src, subtitleCallback, callback)
                    foundLinks = true
                }
            }
        }

        // 3. Video elementleri
        if (!foundLinks) {
            document.select("video source").forEach { source ->
                val videoSrc = fixUrlNull(source.attr("src"))
                if (videoSrc != null) {
                    Log.d("HDCH", "Found video source: $videoSrc")
                    callback.invoke(
                        newExtractorLink(
                            source = "Direct Video",
                            name = "HDFilmCehennemi",
                            url = videoSrc,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.headers = headers
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    foundLinks = true
                }
            }
        }

        Log.d("HDCH", "Found links: $foundLinks")
        return foundLinks
        
    } catch (e: Exception) {
        Log.d("HDCH", "Error in loadLinks: ${e.message}")
        return false
    }
}
    private data class SubSource(
        @JsonProperty("file")    val file: String?  = null,
        @JsonProperty("label")   val label: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("kind")    val kind: String?  = null
    )

    data class Results(
        @JsonProperty("results") val results: List<String> = arrayListOf()
    )
    data class HDFC(
        @JsonProperty("html") val html: String,
        @JsonProperty("meta") val meta: Meta
    )

    data class Meta(
        @JsonProperty("title") val title: String,
        @JsonProperty("canonical") val canonical: Boolean,
        @JsonProperty("keywords") val keywords: Boolean
    )
}
