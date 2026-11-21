package com.shieldios.adblocker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class DnsLogEntry(
    val domain: String,
    val blocked: Boolean,
    val timestamp: String
)

data class FilterListSource(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val enabledByDefault: Boolean
)

data class UpstreamDns(
    val name: String,
    val ip: String,
    val subtitle: String
)

object AdBlockState {
    private const val PREFS_NAME = "shield_ios_prefs"
    private const val PERSIST_INTERVAL_MS = 60_000L
    private const val UI_THROTTLE_MS = 1_000L

    val availableLists = listOf(
        FilterListSource(
            id = "oisd_small",
            name = "OISD Small",
            description = "Equilibrio ideal: bloquea anuncios sin romper apps",
            url = "https://small.oisd.nl/domainswild2",
            enabledByDefault = true
        ),
        FilterListSource(
            id = "adguard_dns",
            name = "AdGuard DNS Filter",
            description = "Anuncios móviles, banners y rastreadores",
            url = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            enabledByDefault = true
        ),
        FilterListSource(
            id = "stevenblack",
            name = "StevenBlack Hosts",
            description = "Lista consolidada de adware y malware",
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            enabledByDefault = false
        ),
        FilterListSource(
            id = "peter_lowe",
            name = "Peter Lowe's Ad Servers",
            description = "Lista ligera de servidores de publicidad",
            url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            enabledByDefault = false
        )
    )

    val upstreamDnsOptions = listOf(
        UpstreamDns("Cloudflare", "1.1.1.1", "Ultra rápido y privado"),
        UpstreamDns("Google DNS", "8.8.8.8", "Alta disponibilidad global"),
        UpstreamDns("Quad9", "9.9.9.9", "Bloqueo extra de malware"),
        UpstreamDns("AdGuard DNS", "94.140.14.14", "Doble capa anti-anuncios")
    )

    private val _isVpnRunning = MutableStateFlow(false)
    val isVpnRunning: StateFlow<Boolean> = _isVpnRunning.asStateFlow()

    private val _blockedCount = MutableStateFlow(0L)
    val blockedCount: StateFlow<Long> = _blockedCount.asStateFlow()

    private val _totalQueries = MutableStateFlow(0L)
    val totalQueries: StateFlow<Long> = _totalQueries.asStateFlow()

    private val _cacheHits = MutableStateFlow(0L)
    val cacheHits: StateFlow<Long> = _cacheHits.asStateFlow()

    private val _domainCount = MutableStateFlow(0)
    val domainCount: StateFlow<Int> = _domainCount.asStateFlow()

    private val _isUpdatingLists = MutableStateFlow(false)
    val isUpdatingLists: StateFlow<Boolean> = _isUpdatingLists.asStateFlow()

    private val _lastUpdated = MutableStateFlow("Lista integrada")
    val lastUpdated: StateFlow<String> = _lastUpdated.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<DnsLogEntry>>(emptyList())
    val recentLogs: StateFlow<List<DnsLogEntry>> = _recentLogs.asStateFlow()

    private val _hourlyBlocked = MutableStateFlow<List<Int>>(List(24) { 0 })
    val hourlyBlocked: StateFlow<List<Int>> = _hourlyBlocked.asStateFlow()

    private val _whitelist = MutableStateFlow<Set<String>>(emptySet())
    val whitelist: StateFlow<Set<String>> = _whitelist.asStateFlow()

    private val _enabledLists = MutableStateFlow<Set<String>>(
        availableLists.filter { it.enabledByDefault }.map { it.id }.toSet()
    )
    val enabledLists: StateFlow<Set<String>> = _enabledLists.asStateFlow()

    private val _excludedApps = MutableStateFlow<Set<String>>(emptySet())
    val excludedApps: StateFlow<Set<String>> = _excludedApps.asStateFlow()

    private val _upstreamDnsIp = MutableStateFlow("1.1.1.1")
    val upstreamDnsIp: StateFlow<String> = _upstreamDnsIp.asStateFlow()

    // Contadores rápidos en RAM (cero escrituras a disco por paquete)
    private val atomicBlocked = AtomicLong(0L)
    private val atomicTotal = AtomicLong(0L)
    private val atomicCacheHits = AtomicLong(0L)
    private val hourlyBuckets = IntArray(24)
    private var lastBucketDay = -1

    private val logLock = Any()
    private val logRing = ArrayDeque<DnsLogEntry>(160)

    @Volatile private var lastPersistMs = 0L
    @Volatile private var lastUiEmitMs = 0L
    @Volatile private var dirtyStats = false
    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedBlocked = prefs.getLong("blocked_count", 0L)
            val savedTotal = prefs.getLong("total_queries", 0L)
            atomicBlocked.set(savedBlocked)
            atomicTotal.set(savedTotal)
            _blockedCount.value = savedBlocked
            _totalQueries.value = savedTotal

            _whitelist.value = prefs.getStringSet("whitelist", emptySet())?.toSet() ?: emptySet()
            _excludedApps.value = prefs.getStringSet("excluded_apps", emptySet())?.toSet() ?: emptySet()
            _upstreamDnsIp.value = prefs.getString("upstream_dns", "1.1.1.1") ?: "1.1.1.1"
            _lastUpdated.value = prefs.getString("last_updated", "Lista integrada") ?: "Lista integrada"

            val savedLists = prefs.getStringSet("enabled_lists", null)
            if (savedLists != null) {
                _enabledLists.value = savedLists.toSet()
            }

            val cal = Calendar.getInstance()
            val today = cal.get(Calendar.DAY_OF_YEAR)
            val savedDay = prefs.getInt("hourly_day", -1)
            if (savedDay == today) {
                val rawHourly = prefs.getString("hourly_blocked", null)
                if (rawHourly != null) {
                    val parts = rawHourly.split(",")
                    for (i in 0 until minOf(24, parts.size)) {
                        hourlyBuckets[i] = parts[i].toIntOrNull() ?: 0
                    }
                }
            }
            lastBucketDay = today
            _hourlyBlocked.value = hourlyBuckets.toList()
            initialized = true
        }
    }

    fun setVpnRunning(running: Boolean) {
        _isVpnRunning.value = running
    }

    fun setDomainCount(count: Int) {
        _domainCount.value = count
    }

    fun setUpdatingLists(updating: Boolean) {
        _isUpdatingLists.value = updating
    }

    fun setLastUpdated(context: Context, label: String) {
        _lastUpdated.value = label
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("last_updated", label).apply()
    }

    fun recordCacheHit() {
        atomicCacheHits.incrementAndGet()
    }

    fun recordQuery(context: Context, domain: String, blocked: Boolean) {
        val now = System.currentTimeMillis()
        atomicTotal.incrementAndGet()
        if (blocked) {
            atomicBlocked.incrementAndGet()
            val cal = Calendar.getInstance()
            val today = cal.get(Calendar.DAY_OF_YEAR)
            val hour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            synchronized(logLock) {
                if (lastBucketDay != today) {
                    hourlyBuckets.fill(0)
                    lastBucketDay = today
                }
                hourlyBuckets[hour]++
            }
        }
        dirtyStats = true

        // Solo guardar registro ligero en memoria
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
        synchronized(logLock) {
            logRing.addFirst(DnsLogEntry(domain = domain, blocked = blocked, timestamp = stamp))
            while (logRing.size > 150) {
                logRing.removeLast()
            }
        }

        // Optimización de batería: si la UI no está escuchando (app en segundo plano), no emitir copias ni recomposiciones
        val hasUiObservers = _blockedCount.subscriptionCount.value > 0 ||
                _recentLogs.subscriptionCount.value > 0 ||
                _hourlyBlocked.subscriptionCount.value > 0

        if (hasUiObservers && (now - lastUiEmitMs >= UI_THROTTLE_MS)) {
            lastUiEmitMs = now
            pushUiSnapshot()
        }

        // Optimización de batería: guardar a disco como máximo 1 vez cada 60 segundos
        if (now - lastPersistMs >= PERSIST_INTERVAL_MS) {
            flushStatsToDisk(context)
        }
    }

    fun pushUiSnapshot() {
        _blockedCount.value = atomicBlocked.get()
        _totalQueries.value = atomicTotal.get()
        _cacheHits.value = atomicCacheHits.get()
        synchronized(logLock) {
            _recentLogs.value = logRing.toList()
            _hourlyBlocked.value = hourlyBuckets.toList()
        }
    }

    fun flushStatsToDisk(context: Context) {
        if (!dirtyStats) return
        dirtyStats = false
        lastPersistMs = System.currentTimeMillis()
        val hourlyStr: String
        val day: Int
        synchronized(logLock) {
            hourlyStr = hourlyBuckets.joinToString(",")
            day = lastBucketDay
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong("blocked_count", atomicBlocked.get())
            .putLong("total_queries", atomicTotal.get())
            .putInt("hourly_day", day)
            .putString("hourly_blocked", hourlyStr)
            .apply()
    }

    fun addToWhitelist(context: Context, domain: String) {
        val clean = domain.trim().lowercase()
        if (clean.isEmpty()) return
        val updated = _whitelist.value + clean
        _whitelist.value = updated
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet("whitelist", updated).apply()
    }

    fun removeFromWhitelist(context: Context, domain: String) {
        val updated = _whitelist.value - domain.lowercase()
        _whitelist.value = updated
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet("whitelist", updated).apply()
    }

    fun toggleFilterList(context: Context, listId: String) {
        val current = _enabledLists.value
        val updated = if (current.contains(listId)) current - listId else current + listId
        _enabledLists.value = updated
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet("enabled_lists", updated).apply()
    }

    fun toggleExcludedApp(context: Context, packageName: String) {
        val current = _excludedApps.value
        val updated = if (current.contains(packageName)) current - packageName else current + packageName
        _excludedApps.value = updated
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet("excluded_apps", updated).apply()
    }

    fun setUpstreamDns(context: Context, ip: String) {
        _upstreamDnsIp.value = ip
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("upstream_dns", ip).apply()
    }

    fun resetStats(context: Context) {
        atomicBlocked.set(0L)
        atomicTotal.set(0L)
        atomicCacheHits.set(0L)
        synchronized(logLock) {
            logRing.clear()
            hourlyBuckets.fill(0)
        }
        dirtyStats = true
        pushUiSnapshot()
        flushStatsToDisk(context)
    }
}
