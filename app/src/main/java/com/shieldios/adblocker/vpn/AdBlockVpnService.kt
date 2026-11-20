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

class AdBlockVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.shieldios.adblocker.START"
        const val ACTION_STOP = "com.shieldios.adblocker.STOP"
        private const val CHANNEL_ID = "shield_ios_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_DNS = "10.0.0.1"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceScope: CoroutineScope? = null
    private val writeLock = Any()

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
            .setContentText("Bloqueando anuncios y rastreadores localmente")
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

    private fun startVpn() {
        stopTunnelOnly()
        AdBlockState.init(applicationContext)

        val builder = Builder()
            .setSession("Shield iOS")
            .setMtu(1500)
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_DNS)
            .addRoute(VPN_DNS, 32)

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

        scope.launch {
            BlocklistManager.loadInitialList(applicationContext)
            runPacketLoop(tun, scope)
        }
    }

    private fun runPacketLoop(tun: ParcelFileDescriptor, scope: CoroutineScope) {
        val inputStream = FileInputStream(tun.fileDescriptor)
        val outputStream = FileOutputStream(tun.fileDescriptor)
        val buffer = ByteArray(4096)

        try {
            while (scope.isActive) {
                val length = inputStream.read(buffer)
                if (length <= 0) continue

                val parsed = DnsPacketHelper.parseIpv4UdpDns(buffer, length) ?: continue
                val domain = DnsPacketHelper.extractQueryDomain(parsed.dnsPayload) ?: continue

                if (BlocklistManager.isBlocked(domain)) {
                    AdBlockState.recordQuery(applicationContext, domain, blocked = true)
                    val nxPayload = DnsPacketHelper.buildNxDomainResponse(parsed.dnsPayload)
                    val responsePacket = DnsPacketHelper.buildIpv4UdpResponse(parsed, nxPayload)
                    synchronized(writeLock) {
                        try {
                            outputStream.write(responsePacket)
                        } catch (_: Exception) {
                        }
                    }
                } else {
                    AdBlockState.recordQuery(applicationContext, domain, blocked = false)
                    scope.launch {
                        forwardDnsQuery(parsed, outputStream)
                    }
                }
            }
        } catch (_: Exception) {
            // Túnel cerrado
        }
    }

    private fun forwardDnsQuery(
        parsed: DnsPacketHelper.ParsedUdpPacket,
        outputStream: FileOutputStream
    ) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protect(socket)
            socket.soTimeout = 3000

            val upstreamIp = AdBlockState.upstreamDnsIp.value
            val upstreamAddress = InetAddress.getByName(upstreamIp)
            val requestPacket = DatagramPacket(
                parsed.dnsPayload,
                parsed.dnsPayload.size,
                upstreamAddress,
                53
            )
            socket.send(requestPacket)

            val responseBuffer = ByteArray(2048)
            val responseDatagram = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responseDatagram)

            val dnsResponse = responseBuffer.copyOfRange(0, responseDatagram.length)
            val ipUdpPacket = DnsPacketHelper.buildIpv4UdpResponse(parsed, dnsResponse)

            synchronized(writeLock) {
                try {
                    outputStream.write(ipUdpPacket)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
            // Tiempo de espera agotado o red no disponible
        } finally {
            socket?.close()
        }
    }

    private fun stopTunnelOnly() {
        serviceScope?.cancel()
        serviceScope = null
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
