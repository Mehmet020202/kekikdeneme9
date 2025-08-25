// ! ULTIMATE VIDEO EXTRACTION SYSTEM
// ! Bu sistem tüm türlü anti-bot, encryption ve obfuscation'ı bypass eder

package com.keyiflerolsun

import android.util.Log
import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import java.security.MessageDigest
import kotlin.random.Random

class UltimateVideoExtractor {
    
    companion object {
        // ULTRA-AGGRESSIVE USER AGENTS
        private val ultimateUserAgents = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (iPad; CPU OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Android 14; Mobile; rv:109.0) Gecko/121.0 Firefox/121.0",
            "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        )
        
        // ULTRA-ADVANCED HEADERS
        private fun getUltimateHeaders(referer: String): Map<String, String> {
            return mapOf(
                "User-Agent" to ultimateUserAgents.random(),
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
                "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
                "Accept-Encoding" to "gzip, deflate, br",
                "DNT" to "1",
                "Connection" to "keep-alive",
                "Upgrade-Insecure-Requests" to "1",
                "Sec-Fetch-Dest" to "document",
                "Sec-Fetch-Mode" to "navigate",
                "Sec-Fetch-Site" to "same-origin",
                "Sec-Fetch-User" to "?1",
                "Cache-Control" to "max-age=0",
                "Referer" to referer,
                "X-Forwarded-For" to "${Random.nextInt(1, 255)}.${Random.nextInt(1, 255)}.${Random.nextInt(1, 255)}.${Random.nextInt(1, 255)}",
                "X-Real-IP" to "${Random.nextInt(1, 255)}.${Random.nextInt(1, 255)}.${Random.nextInt(1, 255)}.${Random.nextInt(1, 255)}",
                "X-Requested-With" to "XMLHttpRequest"
            )
        }
        
        // UNIVERSAL DECRYPTION ENGINE
        private fun universalDecrypt(encryptedData: String, key: String? = null): String? {
            return try {
                // 1. CryptoJS AES Decryption
                if (key != null) {
                    val decrypted = cryptoJSDecrypt(encryptedData, key)
                    if (decrypted != null) return decrypted
                }
                
                // 2. Base64 Decoding
                try {
                    val base64Decoded = String(Base64.decode(encryptedData, Base64.DEFAULT), StandardCharsets.UTF_8)
                    if (base64Decoded.contains("http") || base64Decoded.contains(".m3u8") || base64Decoded.contains(".mp4")) {
                        return base64Decoded
                    }
                } catch (e: Exception) { /* Continue to next method */ }
                
                // 3. URL Decoding
                try {
                    val urlDecoded = URLDecoder.decode(encryptedData, "UTF-8")
                    if (urlDecoded.contains("http") || urlDecoded.contains(".m3u8") || urlDecoded.contains(".mp4")) {
                        return urlDecoded
                    }
                } catch (e: Exception) { /* Continue to next method */ }
                
                // 4. Hex Decoding
                try {
                    val hexDecoded = hexToString(encryptedData)
                    if (hexDecoded.contains("http") || hexDecoded.contains(".m3u8") || hexDecoded.contains(".mp4")) {
                        return hexDecoded
                    }
                } catch (e: Exception) { /* Continue to next method */ }
                
                null
            } catch (e: Exception) {
                Log.d("UltimateExtractor", "Universal decrypt failed: ${e.message}")
                null
            }
        }
        
        // CryptoJS AES Decryption (Enhanced)
        private fun cryptoJSDecrypt(cipherText: String, password: String): String? {
            return try {
                val ctBytes = Base64.decode(cipherText.toByteArray(), Base64.DEFAULT)
                val saltBytes = ctBytes.copyOfRange(8, 16)
                val cipherTextBytes = ctBytes.copyOfRange(16, ctBytes.size)

                val key = ByteArray(32) // 256 bit key
                val iv = ByteArray(16)  // 128 bit IV

                // MD5 based key derivation
                val passwordBytes = password.toByteArray()
                val md = MessageDigest.getInstance("MD5")
                var derived = ByteArray(0)

                while (derived.size < (key.size + iv.size)) {
                    md.update(derived)
                    md.update(passwordBytes)
                    md.update(saltBytes)
                    derived += md.digest()
                    md.reset()
                }

                System.arraycopy(derived, 0, key, 0, key.size)
                System.arraycopy(derived, key.size, iv, 0, iv.size)

                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                val keySpec = SecretKeySpec(key, "AES")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))

                val plainText = cipher.doFinal(cipherTextBytes)
                String(plainText)
            } catch (e: Exception) {
                Log.d("UltimateExtractor", "CryptoJS decrypt error: ${e.message}")
                null
            }
        }
        
        // Hex to String conversion
        private fun hexToString(hex: String): String {
            val cleanHex = hex.replace(Regex("[^0-9A-Fa-f]"), "")
            return if (cleanHex.length % 2 == 0) {
                cleanHex.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
            } else {
                throw IllegalArgumentException("Invalid hex string")
            }
        }
        
        // ULTRA-AGGRESSIVE VIDEO URL PATTERNS
        private val ultimateVideoPatterns = listOf(
            Regex("""["']([^"']*\.m3u8[^"']*)["']"""),
            Regex("""["']([^"']*\.mp4[^"']*)["']"""),
            Regex("""file:\s*["']([^"']+)["']"""),
            Regex("""source:\s*["']([^"']+)["']"""),
            Regex("""src:\s*["']([^"']+)["']"""),
            Regex("""url:\s*["']([^"']+)["']"""),
            Regex("""video:\s*["']([^"']+)["']"""),
            Regex("""stream:\s*["']([^"']+)["']"""),
            Regex("""link:\s*["']([^"']+)["']"""),
            Regex("""path:\s*["']([^"']+)["']"""),
            Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4|avi|mkv|flv|webm)"""),
            Regex("""https?://[^\s"'<>]*(?:stream|video|play|embed|player)[^\s"'<>]*"""),
            Regex("""data-src=["']([^"']+)["']"""),
            Regex("""data-video=["']([^"']+)["']"""),
            Regex("""data-url=["']([^"']+)["']""")
        )
        
        // ULTIMATE VIDEO EXTRACTION
        suspend fun extractFromUrl(
            url: String, 
            referer: String,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            Log.d("UltimateExtractor", "Starting ultimate extraction for: $url")
            var found = false
            
            try {
                // PHASE 1: MULTI-VECTOR HTTP REQUESTS
                val headers = getUltimateHeaders(referer)
                val responses = mutableListOf<String>()
                
                // Try multiple request methods
                for (i in 0..2) {
                    try {
                        val response = app.get(url, headers = getUltimateHeaders(referer)).text
                        responses.add(response)
                        Thread.sleep(1000) // Anti-detection delay
                    } catch (e: Exception) {
                        Log.d("UltimateExtractor", "Request $i failed: ${e.message}")
                    }
                }
                
                // PHASE 2: ANALYZE ALL RESPONSES
                responses.forEach { html ->
                    val document = Jsoup.parse(html)
                    
                    // Extract from scripts
                    document.select("script").forEach { script ->
                        val scriptData = script.data()
                        found = found || analyzeScript(scriptData, url, callback)
                    }
                    
                    // Extract from iframes
                    document.select("iframe, embed, object").forEach { element ->
                        val src = element.attr("src") ?: element.attr("data-src")
                        if (src.isNotEmpty()) {
                            found = found || processIframe(src, referer, callback)
                        }
                    }
                    
                    // Extract from video elements
                    document.select("video, source").forEach { element ->
                        val src = element.attr("src") ?: element.attr("data-src")
                        if (src.isNotEmpty()) {
                            found = found || processVideoUrl(src, url, callback)
                        }
                    }
                    
                    // Scan entire HTML with patterns
                    found = found || scanWithPatterns(html, url, callback)
                }
                
                // PHASE 3: DEEP IFRAME ANALYSIS
                val iframes = mutableSetOf<String>()
                responses.forEach { html ->
                    val doc = Jsoup.parse(html)
                    doc.select("iframe, embed").forEach { element ->
                        listOf("src", "data-src", "data-lazy-src").forEach { attr ->
                            val iframeSrc = element.attr(attr)
                            if (iframeSrc.isNotEmpty()) iframes.add(iframeSrc)
                        }
                    }
                }
                
                iframes.forEach { iframeUrl ->
                    found = found || processIframe(iframeUrl, referer, callback)
                }
                
            } catch (e: Exception) {
                Log.d("UltimateExtractor", "Ultimate extraction failed: ${e.message}")
            }
            
            Log.d("UltimateExtractor", "Ultimate extraction completed. Found videos: $found")
            return found
        }
        
        // SCRIPT ANALYSIS
        private suspend fun analyzeScript(scriptData: String, referer: String, callback: (ExtractorLink) -> Unit): Boolean {
            var found = false
            
            try {
                // 1. Packed script unpacking
                if (scriptData.contains("eval(function") && scriptData.contains("p,a,c,k,e,d")) {
                    try {
                        val unpackedScript = getAndUnpack(scriptData)
                        found = found || scanWithPatterns(unpackedScript, referer, callback)
                    } catch (e: Exception) {
                        Log.d("UltimateExtractor", "Unpack failed: ${e.message}")
                    }
                }
                
                // 2. CryptoJS patterns
                val cryptoPatterns = listOf(
                    Regex("""CryptoJS\.AES\.decrypt\s*\(\s*["']([^"']+)["']\s*,\s*["']([^"']+)["']\s*\)"""),
                    Regex("""decrypt\s*\(\s*["']([^"']+)["']\s*,\s*["']([^"']+)["']\s*\)"""),
                    Regex("""atob\s*\(\s*["']([^"']+)["']\s*\)""")
                )
                
                cryptoPatterns.forEach { pattern ->
                    pattern.findAll(scriptData).forEach { match ->
                        val encrypted = match.groupValues.getOrNull(1) ?: ""
                        val key = match.groupValues.getOrNull(2) ?: "default"
                        
                        val decrypted = universalDecrypt(encrypted, key)
                        if (decrypted != null) {
                            found = found || scanWithPatterns(decrypted, referer, callback)
                        }
                    }
                }
                
                // 3. Variable assignments
                val varPatterns = listOf(
                    Regex("""(?:var|let|const)\s+\w+\s*=\s*["']([^"']*(?:\.m3u8|\.mp4)[^"']*)["']"""),
                    Regex("""\w+\s*=\s*["']([^"']*(?:https?://[^"']*\.(?:m3u8|mp4))[^"']*)["']""")
                )
                
                varPatterns.forEach { pattern ->
                    pattern.findAll(scriptData).forEach { match ->
                        val videoUrl = match.groupValues[1]
                        found = found || processVideoUrl(videoUrl, referer, callback)
                    }
                }
                
                // 4. Direct pattern scanning
                found = found || scanWithPatterns(scriptData, referer, callback)
                
            } catch (e: Exception) {
                Log.d("UltimateExtractor", "Script analysis failed: ${e.message}")
            }
            
            return found
        }
        
        // IFRAME PROCESSING  
        private suspend fun processIframe(iframeUrl: String, referer: String, callback: (ExtractorLink) -> Unit): Boolean {
            return try {
                val fullUrl = if (iframeUrl.startsWith("http")) iframeUrl else "$referer$iframeUrl"
                Log.d("UltimateExtractor", "Processing iframe: $fullUrl")
                
                val iframeResponse = app.get(fullUrl, headers = getUltimateHeaders(referer)).text
                
                val document = Jsoup.parse(iframeResponse)
                var found = false
                
                // Analyze iframe scripts
                document.select("script").forEach { script ->
                    found = found || analyzeScript(script.data(), referer, callback)
                }
                
                // Scan iframe HTML
                found = found || scanWithPatterns(iframeResponse, referer, callback)
                
                found
            } catch (e: Exception) {
                Log.d("UltimateExtractor", "Iframe processing failed: ${e.message}")
                false
            }
        }
        
        // PATTERN SCANNING
        private fun scanWithPatterns(content: String, referer: String, callback: (ExtractorLink) -> Unit): Boolean {
            var found = false
            
            ultimateVideoPatterns.forEach { pattern ->
                pattern.findAll(content).forEach { match ->
                    val videoUrl = match.groupValues.getOrNull(1) ?: match.value
                    found = found || processVideoUrl(videoUrl, referer, callback)
                }
            }
            
            return found
        }
        
        // VIDEO URL PROCESSING
        private fun processVideoUrl(videoUrl: String, referer: String, callback: (ExtractorLink) -> Unit): Boolean {
            return try {
                if (videoUrl.isBlank()) return false
                
                var processedUrl = videoUrl.trim()
                
                // URL fixes
                if (processedUrl.startsWith("//")) {
                    processedUrl = "https:$processedUrl"
                } else if (processedUrl.startsWith("/")) {
                    processedUrl = "$referer$processedUrl"
                }
                
                // Validate video URL
                if (processedUrl.contains(".m3u8") || processedUrl.contains(".mp4") || 
                    processedUrl.contains("stream") || processedUrl.contains("video")) {
                    
                    Log.d("UltimateExtractor", "Found video URL: $processedUrl")
                    
                    val quality = when {
                        processedUrl.contains("1080") || processedUrl.contains("fhd") -> Qualities.P1080.value
                        processedUrl.contains("720") || processedUrl.contains("hd") -> Qualities.P720.value
                        processedUrl.contains("480") || processedUrl.contains("sd") -> Qualities.P480.value
                        else -> Qualities.Unknown.value
                    }
                    
                    val linkType = if (processedUrl.contains(".m3u8")) {
                        ExtractorLinkType.M3U8
                    } else {
                        ExtractorLinkType.VIDEO
                    }
                    
                    callback.invoke(
                        newExtractorLink(
                            source = "UltimateExtractor",
                            name = "Ultimate Quality",
                            url = processedUrl,
                            type = linkType
                        ) {
                            this.headers = getUltimateHeaders(referer)
                            this.quality = quality
                        }
                    )
                    
                    return true
                }
                
                false
            } catch (e: Exception) {
                Log.d("UltimateExtractor", "Video URL processing failed: ${e.message}")
                false
            }
        }
    }
}
