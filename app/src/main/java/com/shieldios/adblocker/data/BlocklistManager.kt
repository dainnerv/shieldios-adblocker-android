package com.shieldios.adblocker.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BlocklistManager {
    @Volatile
    private var blockedDomains: Set<String> = emptySet()
    private const val CACHE_FILE = "shield_blocklist_cache.txt"

    private val defaultDomains = listOf(
        "doubleclick.net",
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "app-measurement.com",
        "firebaselogging.googleapis.com",
        "firebaselogging-pa.googleapis.com",
        "crashlyticsreports-pa.googleapis.com",
        "telemetry.sdk.inmobi.com",
        "config.inmobi.com",
        "unif-id.ssp.inmobi.com",
        "api2.amplitude.com",
        "c.amazon-adsystem.com",
        "mads.amazon-adsystem.com",
        "config.ads.vungle.com",
        "ads.vungle.com",
        "cdn.confiant-integrations.net",
        "adashx4ae.ut.taobao.com",
        "appsflyersdk.com",
        "an.facebook.com",
        "pixel.facebook.com",
        "ads.tiktok.com",
        "analytics.tiktok.com",
        "ads.unity3d.com",
        "auction.unityads.unity3d.com",
        "adcolony.com",
        "applovin.com",
        "d.applovin.com",
        "rt.applovin.com",
        "ironsrc.com",
        "supersonicads.com",
        "startappservice.com",
        "mopub.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "scorecardresearch.com",
        "branch.io",
        "adjust.com",
        "kochava.com",
        "singular.net"
    )

    suspend fun loadInitialList(context: Context) = withContext(Dispatchers.IO) {
        if (blockedDomains.isNotEmpty()) {
            AdBlockState.setDomainCount(blockedDomains.size)
            return@withContext
        }
        val file = File(context.filesDir, CACHE_FILE)
        val loaded = HashSet<String>(100_000)
        if (file.exists() && file.length() > 0) {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val d = line.trim()
                    if (d.isNotEmpty()) loaded.add(d)
                }
            }
        }
        if (loaded.isEmpty()) {
            loaded.addAll(defaultDomains)
        }
        blockedDomains = loaded
        AdBlockState.setDomainCount(loaded.size)
    }

    suspend fun updateLists(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        AdBlockState.setUpdatingLists(true)
        try {
            val selectedIds = AdBlockState.enabledLists.value
            val sources = AdBlockState.availableLists.filter { selectedIds.contains(it.id) }
            val newSet = HashSet<String>(150_000)
            newSet.addAll(defaultDomains)

            for (source in sources) {
                try {
                    val conn = (URL(source.url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 12_000
                        readTimeout = 20_000
                        setRequestProperty("User-Agent", "ShieldIOS-Android/2.0")
                    }
                    if (conn.responseCode == 200) {
                        BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                            var line = reader.readLine()
                            while (line != null) {
                                parseDomain(line)?.let { newSet.add(it) }
                                line = reader.readLine()
                            }
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {
                }
            }

            blockedDomains = newSet

            val file = File(context.filesDir, CACHE_FILE)
            file.bufferedWriter().use { writer ->
                for (domain in newSet) {
                    writer.write(domain)
                    writer.newLine()
                }
            }

            val stamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            AdBlockState.setDomainCount(newSet.size)
            AdBlockState.setLastUpdated(context, "Actualizada hoy, $stamp")
            Result.success(newSet.size)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            AdBlockState.setUpdatingLists(false)
        }
    }

    private fun parseDomain(rawLine: String): String? {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("!") || line.startsWith("@@")) {
            return null
        }

        if (line.startsWith("||")) {
            val end = line.indexOf('^')
            val candidate = if (end > 2) line.substring(2, end) else line.substring(2)
            return sanitizeDomain(candidate)
        }

        if (line.startsWith("*.")) {
            return sanitizeDomain(line.substring(2))
        }

        if (line.startsWith("0.0.0.0") || line.startsWith("127.0.0.1")) {
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2) {
                return sanitizeDomain(parts[1])
            }
            return null
        }

        return sanitizeDomain(line)
    }

    private fun sanitizeDomain(input: String): String? {
        val cleaned = input.substringBefore('#').substringBefore('/').trim().lowercase()
        if (cleaned.isEmpty() || !cleaned.contains('.')) return null
        if (cleaned == "localhost" || cleaned == "localhost.localdomain" || cleaned == "0.0.0.0") return null
        if (cleaned.contains(' ') || cleaned.contains('*') || cleaned.contains('$')) return null
        return cleaned
    }

    fun isBlocked(domain: String): Boolean {
        val clean = domain.trim().lowercase().trimEnd('.')
        if (clean.isEmpty()) return false

        val whitelist = AdBlockState.whitelist.value
        if (whitelist.isNotEmpty() && matchesDomainOrParent(clean, whitelist)) {
            return false
        }

        return matchesDomainOrParent(clean, blockedDomains)
    }

    private fun matchesDomainOrParent(domain: String, set: Set<String>): Boolean {
        if (set.contains(domain)) return true
        var idx = domain.indexOf('.')
        while (idx != -1) {
            val parent = domain.substring(idx + 1)
            if (parent.contains('.') && set.contains(parent)) {
                return true
            }
            idx = domain.indexOf('.', idx + 1)
        }
        return false
    }
}
