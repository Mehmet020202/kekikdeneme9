// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.*
import kotlin.random.Random
import java.nio.charset.Charsets

class KoreanTurk : MainAPI() {
    override var mainUrl              = "https://www.koreanturk.com"
    override var name                 = "KoreanTurk"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "${mainUrl}/bolumler/page/"       to "Son Eklenenler",
        "${mainUrl}/Konu-Aile"            to "Aile",
        "${mainUrl}/Konu-Aksiyon"         to "Aksiyon",
        "${mainUrl}/Konu-Bilim-Kurgu"     to "Bilim Kurgu",
        "${mainUrl}/Konu-Donem"           to "Dönem",
        "${mainUrl}/Konu-Dram"            to "Dram",
        "${mainUrl}/Konu-Fantastik"       to "Fantastik",
        "${mainUrl}/Konu-Genclik"         to "Gençlik",
        "${mainUrl}/Konu-Gerilim"         to "Gerilim",
        "${mainUrl}/Konu-Gizem"           to "Gizem",
        "${mainUrl}/Konu-Hukuk"           to "Hukuk",
        "${mainUrl}/Konu-Komedi"          to "Komedi",
        "${mainUrl}/Konu-Korku"           to "Korku",
        "${mainUrl}/Konu-Medikal"         to "Medikal",
        "${mainUrl}/Konu-Mini-Dizi"       to "Mini Dizi",
        "${mainUrl}/Konu-Okul"            to "Okul",
        "${mainUrl}/Konu-Polisiye-Askeri" to "Polisiye-Askeri",
        "${mainUrl}/Konu-Romantik"        to "Romantik",
        "${mainUrl}/Konu-Romantik-Komedi" to "Romantik Komedi",
        "${mainUrl}/Konu-Suc"             to "Suç",
        "${mainUrl}/Konu-Tarih"           to "Tarih",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.data.contains("Konu-")) {
            val document = app.get(request.data).document
            val home     = document.selectXpath("//img[contains(@onload, 'NcodeImageResizer')]")
                .shuffled(Random(System.nanoTime()))
                .take(12)
                .mapNotNull { it.toKonuResult() }

            return newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = home,
                    isHorizontalImages = false
                ),
                hasNext = false
            )
        } else {
            val document = app.get("${request.data}${page}").document
            val home     = document.select("div.standartbox").mapNotNull { it.toSearchResult() }

            return newHomePageResponse(request.name, home)
        }
    }

    private fun removeEpisodePart(url: String): String {
        val regex = "-[0-9]+(-final)?-bolum-izle\\.html".toRegex()
        return regex.replace(url, "")
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val dizi      = this.selectFirst("h2 span")?.text()?.trim() ?: return null
        val bolum     = this.selectFirst("h2")?.ownText()?.substringBefore(".Bölüm")?.trim()
        val title     = "$dizi | $bolum"

        var href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        if (href.contains("izle.html")) {
            href = removeEpisodePart(href)
        }

        val posterUrl = fixUrlNull(this.selectFirst("div.resimcik img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = posterUrl }
    }

    private fun Element.toKonuResult(): SearchResponse? {
        val title     = this.selectXpath("preceding-sibling::a[1]").text().trim()
        val href      = fixUrlNull(this.selectXpath("preceding-sibling::a[1]").attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/").document

        val searchResults = document.select(".cat-item").mapNotNull {
            val title = it.text()
            val href  = it.firstElementChild()?.attr("href")

            if (title.contains(query, ignoreCase = true) && href != null) {
                // ! i don't want to put posterUrl because already their website slow and getting every page is time consuming
                // * val diziPage  = app.get(href).document
                // * val posterUrl = diziPage.selectFirst("div.resimcik img")?.attr("src")?.removeSuffix("-60x60.jpg") + ".jpg" //Assuming every image has this res, might change in the future
                newTvSeriesSearchResponse(title, href) {
                    this.posterUrl = ""
                }
            } else {
                null
            }
        }

        return searchResults
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("h3")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.resimcik img")?.attr("src"))
        val description = document.selectFirst("[property='og:description']")?.attr("content")?.trim()

        val episodes    = document.select("div.standartbox a").mapNotNull {
            val epName    = it.selectFirst("h2")?.ownText()?.trim() ?: return@mapNotNull null
            val epHref    = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
            val epEpisode = Regex("""(\d+)\.Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
            val epSeason  = Regex("""(\d+)\.Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            newEpisode(epHref) {
                this.name    = epName
                this.season  = epSeason
                this.episode = epEpisode
            }
        }


        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

    // DiziBox tarzı anti-bot headers
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.8,en-US;q=0.5,en;q=0.3",
        "Referer" to mainUrl
    )

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("KRT", "data » $data")
        try {
            val document = app.get(data, headers = headers).document
            var foundLinks = false

            // 1. DiziBox tarzı gelişmiş iframe extraction
            val iframes = mutableListOf<String>()

            document.select("div.filmcik div.tab-pane iframe, iframe").forEach {
                val iframe = fixUrlNull(it.attr("src")) ?: fixUrlNull(it.attr("data-src"))
                if (iframe != null) {
                    iframes.add(iframe)
                    Log.d("KRT", "Found iframe: $iframe")
                }
            }

            document.select("div.filmcik div.tab-pane a, a[href*='player'], a[href*='embed']").forEach {
                val iframe = fixUrlNull(it.attr("href"))
                if (iframe != null && iframe.contains("http")) {
                    iframes.add(iframe)
                    Log.d("KRT", "Found link: $iframe")
                }
            }

            // 2. DiziBox tarzı script analysis
            document.select("script").forEach { script ->
                val scriptData = script.data()
                
                // CryptoJS AES decrypt (DiziBox tarzı)
                val cryptMatch = Regex("""CryptoJS\.AES\.decrypt\("(.*?)",\s*"(.*?)"\)""").find(scriptData)
                if (cryptMatch != null) {
                    try {
                        val encryptedData = cryptMatch.groupValues[1]
                        Log.d("KRT", "Found CryptoJS encrypted data for KoreanTurk...")
                        
                        // DiziBox benzeri CryptoJS decode simulation
                        if (encryptedData.contains("http") && encryptedData.contains(".m3u8")) {
                            callback.invoke(
                                newExtractorLink(
                                    source = "KoreanTurk",
                                    name = "CryptoJS Decrypted",
                                    url = encryptedData,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.headers = headers
                                    this.quality = Qualities.P1080.value
                                }
                            )
                            foundLinks = true
                        }
                    } catch (e: Exception) {
                        Log.d("KRT", "CryptoJS decrypt failed: ${e.message}")
                    }
                }
                
                // Packed script unpack (DiziBox tarzı)
                if (scriptData.contains("eval(function") && scriptData.contains("p,a,c,k,e,d")) {
                    try {
                        val unpackedScript = getAndUnpack(scriptData)
                        val videoPatterns = listOf(
                            Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']"""),
                            Regex("""source:\s*["']([^"']+\.m3u8[^"']*)["']"""),
                            Regex("""src:\s*["']([^"']+\.m3u8[^"']*)["']""")
                        )
                        
                        for (pattern in videoPatterns) {
                            val match = pattern.find(unpackedScript)
                            if (match != null) {
                                val videoUrl = match.groupValues[1]
                                if (videoUrl.startsWith("http")) {
                                    Log.d("KRT", "Found KoreanTurk unpacked video: $videoUrl")
                                    callback.invoke(
                                        newExtractorLink(
                                            source = "KoreanTurk",
                                            name = "Unpacked Video",
                                            url = videoUrl,
                                            type = ExtractorLinkType.M3U8
                                        ) {
                                            this.headers = headers
                                            this.quality = Qualities.P720.value
                                        }
                                    )
                                    foundLinks = true
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("KRT", "Unpack failed: ${e.message}")
                    }
                }
                
                // Base64 + unescape (DiziBox tarzı)
                val atobMatch = Regex("""unescape\("(.*)"\)""").find(scriptData)
                if (atobMatch != null) {
                    try {
                        val encodedData = atobMatch.groupValues[1]
                        val decodedData = encodedData.decodeUri()
                        val finalData = String(android.util.Base64.decode(decodedData, android.util.Base64.DEFAULT), Charsets.UTF_8)
                        
                        val videoPattern = Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4))["']""")
                        val videoMatch = videoPattern.find(finalData)
                        if (videoMatch != null) {
                            val videoUrl = videoMatch.groupValues[1]
                            Log.d("KRT", "Found KoreanTurk Base64 decoded video: $videoUrl")
                            callback.invoke(
                                newExtractorLink(
                                    source = "KoreanTurk",
                                    name = "Base64 Decoded",
                                    url = videoUrl,
                                    type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.headers = headers
                                    this.quality = Qualities.P720.value
                                }
                            )
                            foundLinks = true
                        }
                    } catch (e: Exception) {
                        Log.d("KRT", "Base64 decode failed: ${e.message}")
                    }
                }
            }

            // 3. Standard iframe processing (fallback)
            iframes.forEach { iframe ->
                try {
                    Log.d("KRT", "Processing iframe: $iframe")
                    loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
                    foundLinks = true
                } catch (e: Exception) {
                    Log.d("KRT", "Error processing iframe: ${e.message}")
                }
            }

            return foundLinks
        } catch (e: Exception) {
            Log.d("KRT", "Error in loadLinks: ${e.message}")
            return false
        }
    }
}
