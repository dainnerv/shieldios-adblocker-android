package com.shieldios.adblocker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private val _domainCount = MutableStateFlow(0)
    val domainCount: StateFlow<Int> = _domainCount.asStateFlow()

    private val _isUpdatingLists = MutableStateFlow(false)
    val isUpdatingLists: StateFlow<Boolean> = _isUpdatingLists.asStateFlow()

    private val _lastUpdated = MutableStateFlow("Lista integrada")
    val lastUpdated: StateFlow<String> = _lastUpdated.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<DnsLogEntry>>(emptyList())
    val recentLogs: StateFlow<List<DnsLogEntry>> = _recentLogs.asStateFlow()

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

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _blockedCount.value = prefs.getLong("blocked_count", 0L)
        _totalQueries.value = prefs.getLong("total_queries", 0L)
        _whitelist.value = prefs.getStringSet("whitelist", emptySet())?.toSet() ?: emptySet()
        _excludedApps.value = prefs.getStringSet("excluded_apps", emptySet())?.toSet() ?: emptySet()
        _upstreamDnsIp.value = prefs.getString("upstream_dns", "1.1.1.1") ?: "1.1.1.1"
        _lastUpdated.value = prefs.getString("last_updated", "Lista integrada") ?: "Lista integrada"
        val savedLists = prefs.getStringSet("enabled_lists", null)
        if (savedLists != null) {
            _enabledLists.value = savedLists.toSet()
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

    fun recordQuery(context: Context, domain: String, blocked: Boolean) {
        val newTotal = _totalQueries.value + 1
        _totalQueries.value = newTotal
        val newBlocked = if (blocked) _blockedCount.value + 1 else _blockedCount.value
        if (blocked) {
            _blockedCount.value = newBlocked
        }

        val entry = DnsLogEntry(
            domain = domain,
            blocked = blocked,
            timestamp = timeFormat.format(Date())
        )
        val current = _recentLogs.value.toMutableList()
        current.add(0, entry)
        if (current.size > 150) {
            current.removeAt(current.lastIndex)
        }
        _recentLogs.value = current

        if (newTotal % 10 == 0L || blocked) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong("blocked_count", newBlocked)
                .putLong("total_queries", newTotal)
                .apply()
        }
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
        _blockedCount.value = 0L
        _totalQueries.value = 0L
        _recentLogs.value = emptyList()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong("blocked_count", 0L)
            .putLong("total_queries", 0L)
            .apply()
    }
}
