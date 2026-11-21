package com.shieldios.adblocker.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.shieldios.adblocker.MainActivity
import com.shieldios.adblocker.R
import com.shieldios.adblocker.data.AdBlockState
import com.shieldios.adblocker.data.BlocklistManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class AdBlockVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.shieldios.adblocker.START"
        const val ACTION_STOP = "com.shieldios.adblocker.STOP"
        private const val CHANNEL_ID = "shield_ios_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_DNS = "10.0.0.1"
        private const val DNS_CACHE_TTL_MS = 5 * 60 * 1000L
        private const val DNS_CACHE_MAX_SIZE = 1000
    }

    private data class CachedDnsEntry(
        val payload: ByteArray,
        val expiresAtMs: Long
    )

    private data class PendingQuery(
        val originalPacket: DnsPacketHelper.ParsedUdpPacket,
        val origId0: Byte,
        val origId1: Byte,
        val cacheKey: String,
        val createdAtMs: Long
    )

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceScope: CoroutineScope? = null
    private var upstreamSocket: DatagramSocket? = null
    private val writeLock = Any()
    private val socketLock = Any()
    private val cacheLock = Any()

    private val txIdCounter = AtomicInteger(1)
    private val pendingQueries = ConcurrentHashMap<Int, PendingQuery>()

    private val dnsCache = object : LinkedHashMap<String, CachedDnsEntry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDnsEntry>?): Boolean {
            return size > DNS_CACHE_MAX_SIZE
        }
    }

    @Volatile
    private var cachedUpstreamIpStr: String = ""
    @Volatile
    private var cachedUpstreamInet: InetAddress? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        startVpn()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Protección Shield iOS",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene activo el bloqueo local de anuncios"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AdBlockVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shield iOS activo")
            .setContentText("Bloqueo DNS local · Modo ahorro de batería")
            .setSmallIcon(R.drawable.ic_app_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(0, "Desactivar", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun getOrCreateUpstreamSocket(): DatagramSocket {
        synchronized(socketLock) {
            val existing = upstreamSocket
            if (existing != null && !existing.isClosed) {
                return existing
            }
            val created = DatagramSocket()
            protect(created)
            upstreamSocket = created
            return created
        }
    }

    private fun resetUpstreamSocket() {
        synchronized(socketLock) {
            try {
                upstreamSocket?.close()
            } catch (_: Exception) {
            }
            upstreamSocket = null
        }
    }

    private fun resolveUpstreamAddress(): InetAddress {
        val currentIp = AdBlockState.upstreamDnsIp.value
        val existing = cachedUpstreamInet
        if (existing != null && currentIp == cachedUpstreamIpStr) {
            return existing
        }
        val resolved = InetAddress.getByName(currentIp)
        cachedUpstreamIpStr = currentIp
        cachedUpstreamInet = resolved
        return resolved
    }

    private fun startVpn() {
        stopTunnelOnly()
        AdBlockState.init(applicationContext)

        val builder = Builder()
            .setSession("Shield iOS")
            .setMtu(1500)
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_DNS)
            .addRoute(VPN_DNS, 32)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: Exception) {
        }

        for (pkg in AdBlockState.excludedApps.value) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (_: Exception) {
            }
        }

        val tun = builder.establish() ?: run {
            stopVpn()
            return
        }

        vpnInterface = tun
        AdBlockState.setVpnRunning(true)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serviceScope = scope

        val outputStream = FileOutputStream(tun.fileDescriptor)

        // Único hilo receptor compartido para todas las respuestas DNS upstream
        scope.launch {
            runUpstreamReceiverLoop(outputStream, scope)
        }

        // Único hilo lector del túnel local (duerme en el kernel con 0% CPU cuando no hay DNS)
        scope.launch {
            BlocklistManager.loadInitialList(applicationContext)
            runTunReaderLoop(tun, outputStream, scope)
        }
    }

    private fun runTunReaderLoop(
        tun: ParcelFileDescriptor,
        outputStream: FileOutputStream,
        scope: CoroutineScope
    ) {
        val inputStream = FileInputStream(tun.fileDescriptor)
        val buffer = ByteArray(2048)

        try {
            while (scope.isActive) {
                val length = inputStream.read(buffer)
                if (length <= 0) continue

                val parsed = DnsPacketHelper.parseIpv4UdpDns(buffer, length) ?: continue
                val question = DnsPacketHelper.extractQuestion(parsed.dnsPayload) ?: continue
                val domain = question.domain

                // 1. Si está bloqueado -> responder NXDOMAIN localmente (0 radio, 0 sockets)
                if (BlocklistManager.isBlocked(domain)) {
                    AdBlockState.recordQuery(applicationContext, domain, blocked = true)
                    val nxPayload = DnsPacketHelper.buildNxDomainResponse(parsed.dnsPayload)
                    writeToTun(outputStream, DnsPacketHelper.buildIpv4UdpResponse(parsed, nxPayload))
                    continue
                }

                AdBlockState.recordQuery(applicationContext, domain, blocked = false)

                // 2. Buscar en caché DNS local (ahorra hasta 70% de batería y radio móvil)
                val cacheKey = "$domain:${question.qType}"
                val now = System.currentTimeMillis()
                val cachedResponse: ByteArray? = synchronized(cacheLock) {
                    val entry = dnsCache[cacheKey]
                    if (entry != null && entry.expiresAtMs > now) {
                        entry.payload
                    } else {
                        if (entry != null) dnsCache.remove(cacheKey)
                        null
                    }
                }

                if (cachedResponse != null) {
                    AdBlockState.recordCacheHit()
                    val responseCopy = cachedResponse.copyOf()
                    responseCopy[0] = parsed.dnsPayload[0]
                    responseCopy[1] = parsed.dnsPayload[1]
                    writeToTun(outputStream, DnsPacketHelper.buildIpv4UdpResponse(parsed, responseCopy))
                    continue
                }

                // 3. Reenviar por el único socket UDP multiplexado (sin crear hilos ni sockets nuevos)
                sendViaSharedSocket(parsed, cacheKey, now)
            }
        } catch (_: Exception) {
            // Túnel cerrado normalmente
        }
    }

    private fun sendViaSharedSocket(
        parsed: DnsPacketHelper.ParsedUdpPacket,
        cacheKey: String,
        nowMs: Long
    ) {
        if (pendingQueries.size > 96) {
            val cutoff = nowMs - 5_000L
            pendingQueries.entries.removeIf { it.value.createdAtMs < cutoff }
        }

        val mappedId = txIdCounter.getAndIncrement() and 0xFFFF
        val payloadToSend = parsed.dnsPayload.copyOf()
        val orig0 = payloadToSend[0]
        val orig1 = payloadToSend[1]
        payloadToSend[0] = ((mappedId ushr 8) and 0xFF).toByte()
        payloadToSend[1] = (mappedId and 0xFF).toByte()

        pendingQueries[mappedId] = PendingQuery(
            originalPacket = parsed,
            origId0 = orig0,
            origId1 = orig1,
            cacheKey = cacheKey,
            createdAtMs = nowMs
        )

        try {
            val socket = getOrCreateUpstreamSocket()
            val upstreamAddr = resolveUpstreamAddress()
            val packet = DatagramPacket(payloadToSend, payloadToSend.size, upstreamAddr, 53)
            socket.send(packet)
        } catch (_: Exception) {
            // Si cambió la red (Wi-Fi <-> 5G), recrear socket una vez
            resetUpstreamSocket()
            try {
                val socket = getOrCreateUpstreamSocket()
                val upstreamAddr = resolveUpstreamAddress()
                val packet = DatagramPacket(payloadToSend, payloadToSend.size, upstreamAddr, 53)
                socket.send(packet)
            } catch (_: Exception) {
                pendingQueries.remove(mappedId)
            }
        }
    }

    private fun runUpstreamReceiverLoop(
        outputStream: FileOutputStream,
        scope: CoroutineScope
    ) {
        val receiveBuffer = ByteArray(2048)
        while (scope.isActive) {
            try {
                val socket = getOrCreateUpstreamSocket()
                val datagram = DatagramPacket(receiveBuffer, receiveBuffer.size)
                socket.receive(datagram)

                if (datagram.length < 12) continue
                val mappedId = ((receiveBuffer[0].toInt() and 0xFF) shl 8) or
                        (receiveBuffer[1].toInt() and 0xFF)
                val pending = pendingQueries.remove(mappedId) ?: continue

                val responsePayload = receiveBuffer.copyOfRange(0, datagram.length)
                responsePayload[0] = pending.origId0
                responsePayload[1] = pending.origId1

                // Guardar en caché solo respuestas válidas (RCODE == 0)
                val rcode = responsePayload[3].toInt() and 0x0F
                if (rcode == 0) {
                    synchronized(cacheLock) {
                        dnsCache[pending.cacheKey] = CachedDnsEntry(
                            payload = responsePayload.copyOf(),
                            expiresAtMs = System.currentTimeMillis() + DNS_CACHE_TTL_MS
                        )
                    }
                }

                val ipUdp = DnsPacketHelper.buildIpv4UdpResponse(pending.originalPacket, responsePayload)
                writeToTun(outputStream, ipUdp)
            } catch (_: Exception) {
                if (!scope.isActive) break
                try {
                    Thread.sleep(150)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun writeToTun(outputStream: FileOutputStream, packet: ByteArray) {
        synchronized(writeLock) {
            try {
                outputStream.write(packet)
            } catch (_: Exception) {
            }
        }
    }

    private fun stopTunnelOnly() {
        AdBlockState.flushStatsToDisk(applicationContext)
        serviceScope?.cancel()
        serviceScope = null
        pendingQueries.clear()
        resetUpstreamSocket()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
    }

    private fun stopVpn() {
        stopTunnelOnly()
        AdBlockState.setVpnRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopTunnelOnly()
        AdBlockState.setVpnRunning(false)
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }
}
