// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import java.nio.charset.StandardCharsets

class SezonlukDizi : MainAPI() {
    override var mainUrl              = "https://sezonlukdizi6.com"
    override var name                 = "SezonlukDizi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler.asp?siralama_tipi=id&s="          to "Son Eklenenler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&tur=mini&s=" to "Mini Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=2&s="    to "Yerli Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=1&s="    to "Yabancı Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=3&s="    to "Asya Dizileri",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=4&s="    to "Animasyonlar",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=5&s="    to "Animeler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=6&s="    to "Belgeseller",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}").document
        val home     = document.select("div.afis a").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.description")?.text()?.trim() ?: return null
        val href      = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/diziler.asp?adi=${query}").document

        return document.select("div.afis a").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("div.header")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.image img")?.attr("data-src")) ?: return null
        val year        = document.selectFirst("div.extra span")?.text()?.trim()?.split("-")?.first()?.toIntOrNull()
        val description = document.selectFirst("span#tartismayorum-konu")?.text()?.trim()
        val tags        = document.select("div.labels a[href*='tur']").mapNotNull { it.text().trim() }
        val rating      = document.selectFirst("div.dizipuani a div")?.text()?.trim()?.replace(",", ".").toRatingInt()
        val duration    = document.selectXpath("//span[contains(text(), 'Dk.')]").text().trim().substringBefore(" Dk.").toIntOrNull()

        val endpoint    = url.split("/").last()

        val actorsReq  = app.get("${mainUrl}/oyuncular/${endpoint}").document
        val actors     = actorsReq.select("div.doubling div.ui").map {
            Actor(
                it.selectFirst("div.header")!!.text().trim(),
                fixUrlNull(it.selectFirst("img")?.attr("src"))
            )
        }


        val episodesReq = app.get("${mainUrl}/bolumler/${endpoint}").document
        val episodes    = mutableListOf<Episode>()
        for (sezon in episodesReq.select("table.unstackable")) {
            for (bolum in sezon.select("tbody tr")) {
                val epName    = bolum.selectFirst("td:nth-of-type(4) a")?.text()?.trim() ?: continue
                val epHref    = fixUrlNull(bolum.selectFirst("td:nth-of-type(4) a")?.attr("href")) ?: continue
                val epEpisode = bolum.selectFirst("td:nth-of-type(3)")?.text()?.substringBefore(".Bölüm")?.trim()?.toIntOrNull()
                val epSeason  = bolum.selectFirst("td:nth-of-type(2)")?.text()?.substringBefore(".Sezon")?.trim()?.toIntOrNull()

                episodes.add(newEpisode(epHref) {
                    this.name    = epName
                    this.season  = epSeason
                    this.episode = epEpisode
                })
            }
        }


        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
            this.tags      = tags
            this.rating    = rating
            this.duration  = duration
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("SZD", "data » $data")
        val document = app.get(data).document
        val aspData = getAspData()
        val bid      = document.selectFirst("div#dilsec")?.attr("data-id") ?: return false
        Log.d("SZD", "bid » $bid")

        val altyaziResponse = app.post(
            "${mainUrl}/ajax/dataAlternatif${aspData.alternatif}.asp",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            data    = mapOf(
                "bid" to bid,
                "dil" to "1"
            )
        ).parsedSafe<Kaynak>()
        altyaziResponse?.takeIf { it.status == "success" }?.data?.forEach { veri ->
            Log.d("SZD", "dil»1 | veri.baslik » ${veri.baslik}")

            val veriResponse = app.post(
                "${mainUrl}/ajax/dataEmbed${aspData.embed}.asp",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                data    = mapOf("id" to "${veri.id}")
            ).document

            val iframe = fixUrlNull(veriResponse.selectFirst("iframe")?.attr("src")) ?: return@forEach
            Log.d("SZD", "dil»1 | iframe » $iframe")

            // DiziBox tarzı iframe analysis (SezonlukDizi için optimize)
            try {
                val iframeDoc = app.get(iframe, headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer" to "${mainUrl}/"
                )).document
                
                // Script'lerde video ara
                iframeDoc.select("script").forEach { script ->
                    val scriptData = script.data()
                    
                    // CryptoJS AES decrypt (DiziBox tarzı)
                    val cryptMatch = Regex("""CryptoJS\.AES\.decrypt\("(.*?)",\s*"(.*?)"\)""").find(scriptData)
                    if (cryptMatch != null) {
                        try {
                            val encryptedData = cryptMatch.groupValues[1]
                            Log.d("SZD", "Found CryptoJS encrypted data for SezonlukDizi...")
                            
                            if (encryptedData.contains("http") && encryptedData.contains(".m3u8")) {
                                callback.invoke(
                                    newExtractorLink(
                                        source = "AltYazı - ${veri.baslik}",
                                        name = "CryptoJS Decrypted",
                                        url = encryptedData,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        this.headers = mapOf("Referer" to iframe)
                                        this.quality = Qualities.P1080.value
                                    }
                                )
                                return@forEach
                            }
                        } catch (e: Exception) {
                            Log.d("SZD", "CryptoJS decrypt failed: ${e.message}")
                        }
                    }
                    
                    // Base64 + unescape (DiziBox tarzı)
                    val atobMatch = Regex("""unescape\("(.*)"\)""").find(scriptData)
                    if (atobMatch != null) {
                        try {
                            val encodedData = atobMatch.groupValues[1]
                            val decodedData = encodedData.decodeUri()
                            val finalData = String(android.util.Base64.decode(decodedData, android.util.Base64.DEFAULT), StandardCharsets.UTF_8)
                            
                            val videoPattern = Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4))["']""")
                            val videoMatch = videoPattern.find(finalData)
                            if (videoMatch != null) {
                                val videoUrl = videoMatch.groupValues[1]
                                Log.d("SZD", "Found SezonlukDizi Base64 decoded video: $videoUrl")
                                callback.invoke(
                                    newExtractorLink(
                                        source = "AltYazı - ${veri.baslik}",
                                        name = "Base64 Decoded",
                                        url = videoUrl,
                                        type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    ) {
                                        this.headers = mapOf("Referer" to iframe)
                                        this.quality = Qualities.P720.value
                                    }
                                )
                                return@forEach
                            }
                        } catch (e: Exception) {
                            Log.d("SZD", "Base64 decode failed: ${e.message}")
                        }
                    }
                    
                    // Standard video patterns
                    val videoPatterns = listOf(
                        Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']"""),
                        Regex("""source:\s*["']([^"']+\.m3u8[^"']*)["']"""),
                        Regex("""src:\s*["']([^"']+\.m3u8[^"']*)["']""")
                    )
                    
                    for (pattern in videoPatterns) {
                        val match = pattern.find(scriptData)
                        if (match != null) {
                            val videoUrl = match.groupValues[1]
                            if (videoUrl.startsWith("http")) {
                                Log.d("SZD", "Found SezonlukDizi script video: $videoUrl")
                                callback.invoke(
                                    newExtractorLink(
                                        source = "AltYazı - ${veri.baslik}",
                                        name = "Script Video",
                                        url = videoUrl,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        this.headers = mapOf("Referer" to iframe)
                                        this.quality = Qualities.P720.value
                                    }
                                )
                                return@forEach
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("SZD", "DiziBox style extraction failed, falling back to standard...")
            }

            // Fallback: Standard extraction
            loadExtractor(iframe, "${mainUrl}/", subtitleCallback) { link ->
                callback.invoke(
                    ExtractorLink(
                        source        = "AltYazı - ${veri.baslik}",
                        name          = "AltYazı - ${veri.baslik}",
                        url           = link.url,
                        referer       = link.referer,
                        quality       = link.quality,
                        headers       = link.headers,
                        extractorData = link.extractorData,
                        type          = link.type
                    )
                )
            }
        }

        val dublajResponse = app.post(
            "${mainUrl}/ajax/dataAlternatif${aspData.alternatif}.asp",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            data    = mapOf(
                "bid" to bid,
                "dil" to "0"
            )
        ).parsedSafe<Kaynak>()
        dublajResponse?.takeIf { it.status == "success" }?.data?.forEach { veri ->
            Log.d("SZD", "dil»0 | veri.baslik » ${veri.baslik}")

            val veriResponse = app.post(
                "${mainUrl}/ajax/dataEmbed${aspData.embed}.asp",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                data    = mapOf("id" to "${veri.id}")
            ).document

            val iframe = fixUrlNull(veriResponse.selectFirst("iframe")?.attr("src")) ?: return@forEach
            Log.d("SZD", "dil»0 | iframe » $iframe")

            // DiziBox tarzı iframe analysis (Dublaj için optimize)
            try {
                val iframeDoc = app.get(iframe, headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer" to "${mainUrl}/"
                )).document
                
                // Script'lerde video ara
                iframeDoc.select("script").forEach { script ->
                    val scriptData = script.data()
                    
                    // CryptoJS AES decrypt (DiziBox tarzı)
                    val cryptMatch = Regex("""CryptoJS\.AES\.decrypt\("(.*?)",\s*"(.*?)"\)""").find(scriptData)
                    if (cryptMatch != null) {
                        try {
                            val encryptedData = cryptMatch.groupValues[1]
                            Log.d("SZD", "Found CryptoJS encrypted data for Dublaj...")
                            
                            if (encryptedData.contains("http") && encryptedData.contains(".m3u8")) {
                                callback.invoke(
                                    newExtractorLink(
                                        source = "Dublaj - ${veri.baslik}",
                                        name = "CryptoJS Decrypted",
                                        url = encryptedData,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        this.headers = mapOf("Referer" to iframe)
                                        this.quality = Qualities.P1080.value
                                    }
                                )
                                return@forEach
                            }
                        } catch (e: Exception) {
                            Log.d("SZD", "CryptoJS decrypt failed: ${e.message}")
                        }
                    }
                    
                    // Base64 + unescape (DiziBox tarzı)
                    val atobMatch = Regex("""unescape\("(.*)"\)""").find(scriptData)
                    if (atobMatch != null) {
                        try {
                            val encodedData = atobMatch.groupValues[1]
                            val decodedData = encodedData.decodeUri()
                            val finalData = String(android.util.Base64.decode(decodedData, android.util.Base64.DEFAULT), StandardCharsets.UTF_8)
                            
                            val videoPattern = Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4))["']""")
                            val videoMatch = videoPattern.find(finalData)
                            if (videoMatch != null) {
                                val videoUrl = videoMatch.groupValues[1]
                                Log.d("SZD", "Found SezonlukDizi Dublaj Base64 decoded video: $videoUrl")
                                callback.invoke(
                                    newExtractorLink(
                                        source = "Dublaj - ${veri.baslik}",
                                        name = "Base64 Decoded",
                                        url = videoUrl,
                                        type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    ) {
                                        this.headers = mapOf("Referer" to iframe)
                                        this.quality = Qualities.P720.value
                                    }
                                )
                                return@forEach
                            }
                        } catch (e: Exception) {
                            Log.d("SZD", "Base64 decode failed: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("SZD", "DiziBox style extraction failed for Dublaj, falling back to standard...")
            }

            // Fallback: Standard extraction
            loadExtractor(iframe, "${mainUrl}/", subtitleCallback) { link ->
                callback.invoke(
                    ExtractorLink(
                        source        = "Dublaj - ${veri.baslik}",
                        name          = "Dublaj - ${veri.baslik}",
                        url           = link.url,
                        referer       = link.referer,
                        quality       = link.quality,
                        headers       = link.headers,
                        extractorData = link.extractorData,
                        type          = link.type
                    )
                )
            }
        }

        return true
    }

    //Helper function for getting the number (probably some kind of version?) after the dataAlternatif and dataEmbed
    private suspend fun getAspData() : AspData{
        val websiteCustomJavascript = app.get("${this.mainUrl}/js/site.min.js")
        val dataAlternatifAsp = Regex("""dataAlternatif(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(1)
            .toString()
        val dataEmbedAsp = Regex("""dataEmbed(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(1)
            .toString()
        return AspData(dataAlternatifAsp,dataEmbedAsp)
    }
}
