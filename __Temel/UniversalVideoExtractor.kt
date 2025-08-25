package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import java.util.Base64

/**
 * Evrensel Video Çıkarıcı - Tüm Türk siteler için optimized
 * @keyiflerolsun tarafından @KekikAkademi için yazılmıştır
 */
object UniversalVideoExtractor {
    
    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.8,en-US;q=0.5,en;q=0.3"
    )

    /**
     * Ana video çıkarma fonksiyonu - tüm yöntemleri dener
     */
    suspend fun extractVideo(
        url: String,
        mainUrl: String,
        logTag: String = "VIDEO_EXTRACT",
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d(logTag, "Starting video extraction for: $url")
        var foundLinks = false

        try {
            val document = app.get(url, headers = commonHeaders + mapOf("Referer" to mainUrl)).document
            
            // 1. Iframe player'ları
            foundLinks = extractFromIframes(document, url, mainUrl, logTag, subtitleCallback, callback) || foundLinks
            
            // 2. Video element'leri
            if (!foundLinks) {
                foundLinks = extractFromVideoElements(document, url, mainUrl, logTag, callback) || foundLinks
            }
            
            // 3. Script içindeki video pattern'leri
            if (!foundLinks) {
                foundLinks = extractFromScripts(document, url, mainUrl, logTag, callback) || foundLinks
            }
            
            // 4. Data attribute'ları
            if (!foundLinks) {
                foundLinks = extractFromDataAttributes(document, url, mainUrl, logTag, callback) || foundLinks
            }
            
            // 5. AJAX endpoint'leri
            if (!foundLinks) {
                foundLinks = extractFromAjaxEndpoints(url, mainUrl, logTag, callback) || foundLinks
            }
            
            // 6. Alternative player yapıları
            if (!foundLinks) {
                foundLinks = extractFromAlternativePlayers(document, url, mainUrl, logTag, subtitleCallback, callback) || foundLinks
            }
            
        } catch (e: Exception) {
            Log.e(logTag, "Error in video extraction: ${e.message}")
        }
        
        Log.d(logTag, "Video extraction completed. Found links: $foundLinks")
        return foundLinks
    }

    /**
     * 1. Iframe tabanlı video çıkarma
     */
    private suspend fun extractFromIframes(
        document: Document,
        pageUrl: String,
        mainUrl: String,
        logTag: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        val iframeSelectors = listOf(
            "iframe[src]", "iframe[data-src]", "iframe[data-lazy]",
            ".video-player iframe", ".player iframe", ".embed-player iframe",
            "iframe[src*='player']", "iframe[src*='embed']", "iframe[src*='stream']"
        )
        
        for (selector in iframeSelectors) {
            document.select(selector).forEach { iframe ->
                val iframeSrc = fixUrlNull(iframe.attr("src")) ?: 
                               fixUrlNull(iframe.attr("data-src")) ?:
                               fixUrlNull(iframe.attr("data-lazy"))
                
                if (iframeSrc != null) {
                    Log.d(logTag, "Found iframe: $iframeSrc")
                    try {
                        loadExtractor(iframeSrc, mainUrl, subtitleCallback, callback)
                        foundLinks = true
                    } catch (e: Exception) {
                        Log.d(logTag, "Error with iframe $iframeSrc: ${e.message}")
                    }
                }
            }
            if (foundLinks) break
        }
        
        return foundLinks
    }

    /**
     * 2. Direct video element çıkarma
     */
    private suspend fun extractFromVideoElements(
        document: Document,
        pageUrl: String,
        mainUrl: String,
        logTag: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        document.select("video source, video[src]").forEach { source ->
            val videoSrc = fixUrlNull(source.attr("src"))
            if (videoSrc != null) {
                Log.d(logTag, "Found video source: $videoSrc")
                callback.invoke(
                    newExtractorLink(
                        source = "Direct Video",
                        name = "Direct Video",
                        url = videoSrc,
                        type = if (videoSrc.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.MP4
                    ) {
                        headers = commonHeaders + mapOf("Referer" to mainUrl)
                        quality = Qualities.Unknown.value
                    }
                )
                foundLinks = true
            }
        }
        
        return foundLinks
    }

    /**
     * 3. JavaScript pattern'lerinden video çıkarma
     */
    private suspend fun extractFromScripts(
        document: Document,
        pageUrl: String,
        mainUrl: String,
        logTag: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        val videoPatterns = listOf(
            Regex("""["']file["']\s*:\s*["']([^"']+)["']"""),
            Regex("""["']source["']\s*:\s*["']([^"']+)["']"""),
            Regex("""["']src["']\s*:\s*["']([^"']+)["']"""),
            Regex("""["']video["']\s*:\s*["']([^"']+)["']"""),
            Regex("""["']url["']\s*:\s*["']([^"']+)["']"""),
            Regex("""file:\s*["']([^"']+)["']"""),
            Regex("""source:\s*["']([^"']+)["']"""),
            Regex("""src:\s*["']([^"']+)["']"""),
            Regex("""videoUrl\s*[=:]\s*["']([^"']+)["']"""),
            Regex("""player_url\s*[=:]\s*["']([^"']+)["']"""),
            Regex("""["']([^"']*\.m3u8[^"']*)["']"""),
            Regex("""["']([^"']*\.mp4[^"']*)["']""")
        )
        
        document.select("script").forEach { script ->
            val scriptData = script.data()
            
            // Packed script varsa unpack et
            if (scriptData.contains("eval(function") || scriptData.contains("p,a,c,k,e,d")) {
                try {
                    val unpackedScript = getAndUnpack(scriptData)
                    for (pattern in videoPatterns) {
                        val match = pattern.find(unpackedScript)
                        if (match != null) {
                            val videoUrl = match.groupValues[1]
                            if (isValidVideoUrl(videoUrl)) {
                                Log.d(logTag, "Found unpacked video: $videoUrl")
                                createExtractorLink(videoUrl, "Unpacked Script", mainUrl, callback)
                                foundLinks = true
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(logTag, "Error unpacking script: ${e.message}")
                }
            }
            
            // Normal script parsing
            if (!foundLinks) {
                for (pattern in videoPatterns) {
                    val match = pattern.find(scriptData)
                    if (match != null) {
                        val videoUrl = match.groupValues[1]
                        if (isValidVideoUrl(videoUrl)) {
                            Log.d(logTag, "Found script video: $videoUrl")
                            createExtractorLink(videoUrl, "Script Video", mainUrl, callback)
                            foundLinks = true
                            break
                        }
                    }
                }
            }
            
            if (foundLinks) return@forEach
        }
        
        return foundLinks
    }

    /**
     * 4. Data attribute'larından video çıkarma
     */
    private suspend fun extractFromDataAttributes(
        document: Document,
        pageUrl: String,
        mainUrl: String,
        logTag: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        val dataAttributes = listOf(
            "data-video", "data-video-url", "data-src", "data-player",
            "data-episode", "data-stream", "data-file", "data-source"
        )
        
        for (attr in dataAttributes) {
            document.select("*[$attr]").forEach { element ->
                val videoUrl = element.attr(attr)
                if (videoUrl.isNotEmpty() && (videoUrl.startsWith("http") || videoUrl.contains("player"))) {
                    Log.d(logTag, "Found data attribute video: $videoUrl")
                    try {
                        if (videoUrl.contains("player") || videoUrl.contains("embed")) {
                            loadExtractor(videoUrl, mainUrl, { }, callback)
                        } else {
                            createExtractorLink(videoUrl, "Data Attribute", mainUrl, callback)
                        }
                        foundLinks = true
                    } catch (e: Exception) {
                        Log.d(logTag, "Error with data attribute: ${e.message}")
                    }
                }
            }
            if (foundLinks) break
        }
        
        return foundLinks
    }

    /**
     * 5. AJAX endpoint'lerinden video çıkarma
     */
    private suspend fun extractFromAjaxEndpoints(
        pageUrl: String,
        mainUrl: String,
        logTag: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        val episodeId = pageUrl.substringAfterLast("/").substringBefore("?").substringBefore("#")
        val ajaxPaths = listOf(
            "/ajax/video/", "/ajax/episode/", "/ajax/player/", "/api/video/",
            "/api/episode/", "/player/", "/video/", "/embed/"
        )
        
        for (path in ajaxPaths) {
            try {
                val ajaxUrl = "$mainUrl$path$episodeId"
                val response = app.get(
                    ajaxUrl,
                    headers = commonHeaders + mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Referer" to pageUrl
                    )
                ).text
                
                val videoPatterns = listOf(
                    Regex("""["'](?:video|url|source|file|episode)["']\s*:\s*["']([^"']+)["']"""),
                    Regex("""["']([^"']*\.m3u8[^"']*)["']"""),
                    Regex("""["']([^"']*\.mp4[^"']*)["']""")
                )
                
                for (pattern in videoPatterns) {
                    val match = pattern.find(response)
                    if (match != null) {
                        val videoUrl = match.groupValues[1]
                        if (isValidVideoUrl(videoUrl)) {
                            Log.d(logTag, "Found AJAX video: $videoUrl")
                            createExtractorLink(videoUrl, "AJAX Video", mainUrl, callback)
                            foundLinks = true
                            break
                        }
                    }
                }
                
                if (foundLinks) break
            } catch (e: Exception) {
                Log.d(logTag, "Error with AJAX $path: ${e.message}")
            }
        }
        
        return foundLinks
    }

    /**
     * 6. Alternative player yapılarından video çıkarma
     */
    private suspend fun extractFromAlternativePlayers(
        document: Document,
        pageUrl: String,
        mainUrl: String,
        logTag: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        // Alternative player butonları
        document.select("button[data-video], button[data-player], .alternative-link").forEach { button ->
            val videoId = button.attr("data-video").ifEmpty { button.attr("data-player") }
            if (videoId.isNotEmpty()) {
                try {
                    val playerUrl = "$mainUrl/video/$videoId/"
                    val playerResponse = app.get(
                        playerUrl,
                        headers = commonHeaders + mapOf("Referer" to pageUrl)
                    ).text
                    
                    val iframePattern = Regex("""(?:data-src|src)=["']([^"']+)["']""")
                    val iframeMatch = iframePattern.find(playerResponse)
                    if (iframeMatch != null) {
                        val iframeUrl = iframeMatch.groupValues[1].replace("\\", "")
                        Log.d(logTag, "Found alternative player iframe: $iframeUrl")
                        loadExtractor(iframeUrl, mainUrl, subtitleCallback, callback)
                        foundLinks = true
                    }
                } catch (e: Exception) {
                    Log.d(logTag, "Error with alternative player: ${e.message}")
                }
            }
        }
        
        return foundLinks
    }

    /**
     * Video URL'in geçerli olup olmadığını kontrol eder
     */
    private fun isValidVideoUrl(url: String): Boolean {
        return url.isNotEmpty() && 
               (url.startsWith("http") || url.startsWith("//")) &&
               (url.contains(".m3u8") || url.contains(".mp4") || 
                url.contains("player") || url.contains("stream") ||
                url.length > 20)
    }

    /**
     * ExtractorLink oluşturur
     */
    private fun createExtractorLink(
        videoUrl: String,
        sourceName: String,
        mainUrl: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val finalUrl = when {
            videoUrl.startsWith("http") -> videoUrl
            videoUrl.startsWith("//") -> "https:$videoUrl"
            else -> "$mainUrl/$videoUrl"
        }
        
        callback.invoke(
            newExtractorLink(
                source = sourceName,
                name = sourceName,
                url = finalUrl,
                type = if (finalUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.MP4
            ) {
                headers = commonHeaders + mapOf("Referer" to mainUrl)
                quality = Qualities.Unknown.value
            }
        )
    }
}
