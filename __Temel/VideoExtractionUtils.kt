// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

/**
 * Gelişmiş Video Extraction Sistemi
 * Tüm modüller için ortak video bulma ve açma metodları
 */
object VideoExtractionUtils {
    
    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "Accept-Encoding" to "gzip, deflate",
        "Connection" to "keep-alive",
        "Upgrade-Insecure-Requests" to "1"
    )

    /**
     * Ana video extraction metodu - tüm yöntemleri dener
     */
    suspend fun extractVideoLinks(
        url: String,
        referer: String,
        logTag: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d(logTag, "Starting advanced video extraction for: $url")
        var foundLinks = false

        try {
            val document = app.get(url, referer = referer, headers = commonHeaders).document
            
            // 1. Iframe tabanlı video bulma
            foundLinks = extractFromIframes(document, url, referer, logTag, subtitleCallback, callback) || foundLinks
            
            // 2. Direct video element'leri
            if (!foundLinks) {
                foundLinks = extractFromVideoElements(document, url, referer, logTag, callback) || foundLinks
            }
            
            // 3. JavaScript içindeki video URL'leri
            if (!foundLinks) {
                foundLinks = extractFromScripts(document, url, referer, logTag, callback) || foundLinks
            }
            
            // 4. Data attribute'ları
            if (!foundLinks) {
                foundLinks = extractFromDataAttributes(document, url, referer, logTag, callback) || foundLinks
            }
            
            // 5. AJAX endpoint'leri
            if (!foundLinks) {
                foundLinks = extractFromAjaxEndpoints(url, referer, logTag, callback) || foundLinks
            }
            
            // 6. Meta tag'ler
            if (!foundLinks) {
                foundLinks = extractFromMetaTags(document, url, referer, logTag, callback) || foundLinks
            }
            
            // 7. Alternative player URL'leri
            if (!foundLinks) {
                foundLinks = extractFromAlternativePlayers(document, url, referer, logTag, subtitleCallback, callback) || foundLinks
            }
            
        } catch (e: Exception) {
            Log.e(logTag, "Error in video extraction: ${e.message}", e)
        }
        
        Log.d(logTag, "Video extraction completed. Found links: $foundLinks")
        return foundLinks
    }

    /**
     * Iframe'lerden video bağlantıları çıkarma
     */
    private suspend fun extractFromIframes(
        document: Document,
        pageUrl: String,
        referer: String,
        logTag: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        val iframeSelectors = listOf(
            "iframe[src*='player']",
            "iframe[src*='embed']",
            "iframe[data-src*='player']",
            "iframe[data-src*='embed']",
            "div.video-player iframe",
            "div.player iframe",
            "div.embed-player iframe",
            ".player-container iframe",
            "#video-player iframe",
            "iframe"
        )
        
        for (selector in iframeSelectors) {
            document.select(selector).forEach { iframe ->
                val iframeSrc = fixUrlNull(iframe.attr("src")) 
                    ?: fixUrlNull(iframe.attr("data-src"))
                    ?: fixUrlNull(iframe.attr("data-lazy-src"))
                
                if (iframeSrc != null && iframeSrc.isNotEmpty()) {
                    Log.d(logTag, "Found iframe: $iframeSrc")
                    try {
                        loadExtractor(iframeSrc, referer, subtitleCallback, callback)
                        foundLinks = true
                    } catch (e: Exception) {
                        Log.d(logTag, "Error loading iframe $iframeSrc: ${e.message}")
                    }
                }
            }
        }
        
        return foundLinks
    }

    /**
     * Direct video element'lerinden bağlantı çıkarma
     */
    private suspend fun extractFromVideoElements(
        document: Document,
        pageUrl: String,
        referer: String,
        logTag: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        document.select("video source, video").forEach { video ->
            val videoSrc = fixUrlNull(video.attr("src"))
            if (videoSrc != null && videoSrc.isNotEmpty()) {
                Log.d(logTag, "Found direct video: $videoSrc")
                callback.invoke(
                    newExtractorLink(
                        source = "Direct Video",
                        name = "Direct Video",
                        url = videoSrc,
                        type = if (videoSrc.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                    ) {
                        headers = mapOf("Referer" to referer)
                        quality = Qualities.Unknown.value
                    }
                )
                foundLinks = true
            }
        }
        
        return foundLinks
    }

    /**
     * JavaScript kodundan video URL'leri çıkarma
     */
    private suspend fun extractFromScripts(
        document: Document,
        pageUrl: String,
        referer: String,
        logTag: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        val videoPatterns = listOf(
            """["']([^"']*\.m3u8[^"']*)["']""",
            """["']([^"']*\.mp4[^"']*)["']""",
            """source['"]\s*:\s*["']([^"']+)["']""",
            """file['"]\s*:\s*["']([^"']+)["']""",
            """url['"]\s*:\s*["']([^"']+)["']""",
            """src['"]\s*:\s*["']([^"']+)["']""",
            """video['"]\s*:\s*["']([^"']+)["']""",
            """stream['"]\s*:\s*["']([^"']+)["']""",
            """playlist['"]\s*:\s*["']([^"']+)["']"""
        )
        
        document.select("script").forEach { script ->
            val scriptText = script.data()
            if (scriptText.contains("video") || scriptText.contains("source") || 
                scriptText.contains(".m3u8") || scriptText.contains(".mp4")) {
                
                for (pattern in videoPatterns) {
                    try {
                        val matches = Regex(pattern).findAll(scriptText)
                        for (match in matches) {
                            val videoUrl = match.groupValues[1]
                            if (isValidVideoUrl(videoUrl)) {
                                val fullUrl = if (videoUrl.startsWith("http")) videoUrl 
                                            else if (videoUrl.startsWith("//")) "https:$videoUrl"
                                            else "$referer/$videoUrl"
                                            
                                Log.d(logTag, "Found script video URL: $fullUrl")
                                callback.invoke(
                                    newExtractorLink(
                                        source = "Script Video",
                                        name = "Script Video",
                                        url = fullUrl,
                                        type = if (fullUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                                    ) {
                                        headers = mapOf("Referer" to referer)
                                        quality = Qualities.Unknown.value
                                    }
                                )
                                foundLinks = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(logTag, "Error parsing script pattern $pattern: ${e.message}")
                    }
                }
            }
        }
        
        return foundLinks
    }

    /**
     * Data attribute'larından video bağlantıları çıkarma
     */
    private suspend fun extractFromDataAttributes(
        document: Document,
        pageUrl: String,
        referer: String,
        logTag: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        val dataAttributes = listOf(
            "data-video-url", "data-video", "data-src", "data-stream",
            "data-file", "data-source", "data-link", "data-embed"
        )
        
        for (attr in dataAttributes) {
            document.select("*[$attr]").forEach { element ->
                val videoUrl = element.attr(attr)
                if (isValidVideoUrl(videoUrl)) {
                    val fullUrl = if (videoUrl.startsWith("http")) videoUrl else "$referer/$videoUrl"
                    Log.d(logTag, "Found data attribute video: $fullUrl")
                    callback.invoke(
                        newExtractorLink(
                            source = "Data Attribute",
                            name = "Data Video",
                            url = fullUrl,
                            type = if (fullUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                        ) {
                            headers = mapOf("Referer" to referer)
                            quality = Qualities.Unknown.value
                        }
                    )
                    foundLinks = true
                }
            }
        }
        
        return foundLinks
    }

    /**
     * AJAX endpoint'lerinden video bağlantıları çıkarma
     */
    private suspend fun extractFromAjaxEndpoints(
        pageUrl: String,
        referer: String,
        logTag: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        val ajaxPaths = listOf(
            "/ajax/video/", "/ajax/episode/", "/ajax/player/", 
            "/api/video/", "/api/episode/", "/player/ajax/"
        )
        
        val episodeId = pageUrl.substringAfterLast("/").substringBefore("?").substringBefore("#")
        
        for (path in ajaxPaths) {
            try {
                val ajaxUrl = "${pageUrl.substringBefore("/", pageUrl.indexOf("://") + 3)}$path$episodeId"
                val response = app.get(
                    ajaxUrl,
                    headers = commonHeaders + mapOf("X-Requested-With" to "XMLHttpRequest"),
                    referer = referer
                ).text
                
                val videoMatch = Regex("""["'](?:video|url|source|file)["']\s*:\s*["']([^"']+)["']""").find(response)
                videoMatch?.groupValues?.get(1)?.let { videoUrl ->
                    if (isValidVideoUrl(videoUrl)) {
                        Log.d(logTag, "Found AJAX video URL: $videoUrl")
                        callback.invoke(
                            newExtractorLink(
                                source = "AJAX Video",
                                name = "AJAX Video",
                                url = videoUrl,
                                type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                            ) {
                                headers = mapOf("Referer" to referer)
                                quality = Qualities.Unknown.value
                            }
                        )
                        foundLinks = true
                    }
                }
            } catch (e: Exception) {
                Log.d(logTag, "Error with AJAX endpoint $path: ${e.message}")
            }
        }
        
        return foundLinks
    }

    /**
     * Meta tag'lerden video bağlantıları çıkarma
     */
    private suspend fun extractFromMetaTags(
        document: Document,
        pageUrl: String,
        referer: String,
        logTag: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        val metaSelectors = listOf(
            "meta[property='og:video']",
            "meta[property='og:video:url']",
            "meta[name='video']",
            "meta[property='video']"
        )
        
        for (selector in metaSelectors) {
            document.select(selector).forEach { meta ->
                val videoUrl = meta.attr("content")
                if (isValidVideoUrl(videoUrl)) {
                    Log.d(logTag, "Found meta video URL: $videoUrl")
                    callback.invoke(
                        newExtractorLink(
                            source = "Meta Video",
                            name = "Meta Video",
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                        ) {
                            headers = mapOf("Referer" to referer)
                            quality = Qualities.Unknown.value
                        }
                    )
                    foundLinks = true
                }
            }
        }
        
        return foundLinks
    }

    /**
     * Alternative player URL'lerinden video çıkarma
     */
    private suspend fun extractFromAlternativePlayers(
        document: Document,
        pageUrl: String,
        referer: String,
        logTag: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        // Player button'ları ve link'leri ara
        document.select("a[href*='player'], button[data-url*='player'], .player-option").forEach { element ->
            val playerUrl = element.attr("href").ifEmpty { element.attr("data-url") }
            if (playerUrl.isNotEmpty() && playerUrl.contains("http")) {
                try {
                    Log.d(logTag, "Trying alternative player: $playerUrl")
                    loadExtractor(playerUrl, referer, subtitleCallback, callback)
                    foundLinks = true
                } catch (e: Exception) {
                    Log.d(logTag, "Error with alternative player $playerUrl: ${e.message}")
                }
            }
        }
        
        return foundLinks
    }

    /**
     * Video URL'nin geçerli olup olmadığını kontrol eder
     */
    private fun isValidVideoUrl(url: String): Boolean {
        return url.isNotEmpty() && 
               (url.startsWith("http") || url.startsWith("//") || url.contains(".m3u8") || url.contains(".mp4")) &&
               !url.contains("javascript:") &&
               !url.contains("data:") &&
               url.length > 10
    }
}
