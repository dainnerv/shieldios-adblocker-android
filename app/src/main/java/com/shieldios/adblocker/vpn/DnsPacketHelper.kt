package com.shieldios.adblocker.vpn

import java.nio.ByteBuffer

object DnsPacketHelper {

    data class ParsedUdpPacket(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val dnsPayload: ByteArray
    )

    data class DnsQuestion(
        val domain: String,
        val qType: Int
    )

    fun parseIpv4UdpDns(packet: ByteArray, length: Int): ParsedUdpPacket? {
        if (length < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null

        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        if (length < ipHeaderLen + 8) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        val srcPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or
                (packet[ipHeaderLen + 1].toInt() and 0xFF)
        val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                (packet[ipHeaderLen + 3].toInt() and 0xFF)

        if (dstPort != 53) return null

        val udpLength = ((packet[ipHeaderLen + 4].toInt() and 0xFF) shl 8) or
                (packet[ipHeaderLen + 5].toInt() and 0xFF)
        val dnsStart = ipHeaderLen + 8
        val dnsLength = minOf(udpLength - 8, length - dnsStart)
        if (dnsLength < 12) return null

        val dnsPayload = packet.copyOfRange(dnsStart, dnsStart + dnsLength)
        return ParsedUdpPacket(srcIp, dstIp, srcPort, dstPort, dnsPayload)
    }

    fun extractQuestion(dnsPayload: ByteArray): DnsQuestion? {
        if (dnsPayload.size < 13) return null
        val sb = StringBuilder(48)
        var pos = 12
        var jumps = 0

        while (pos < dnsPayload.size && jumps < 64) {
            val len = dnsPayload[pos].toInt() and 0xFF
            if (len == 0) {
                pos++
                break
            }
            if ((len and 0xC0) == 0xC0) return null
            pos++
            if (pos + len > dnsPayload.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) {
                sb.append(dnsPayload[pos + i].toInt().toChar())
            }
            pos += len
            jumps++
        }

        if (sb.isEmpty()) return null
        val qType = if (pos + 1 < dnsPayload.size) {
            ((dnsPayload[pos].toInt() and 0xFF) shl 8) or (dnsPayload[pos + 1].toInt() and 0xFF)
        } else {
            1
        }
        return DnsQuestion(sb.toString().lowercase(), qType)
    }

    fun buildNxDomainResponse(queryPayload: ByteArray): ByteArray {
        val response = queryPayload.copyOf()
        if (response.size >= 12) {
            response[2] = 0x81.toByte()
            response[3] = 0x83.toByte()
            response[6] = 0
            response[7] = 0
            response[8] = 0
            response[9] = 0
            response[10] = 0
            response[11] = 0
        }
        return response
    }

    fun buildIpv4UdpResponse(
        original: ParsedUdpPacket,
        dnsResponsePayload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLength = ipHeaderLen + udpHeaderLen + dnsResponsePayload.size
        val buffer = ByteBuffer.allocate(totalLength)

        buffer.put(0x45.toByte())
        buffer.put(0x00.toByte())
        buffer.putShort(totalLength.toShort())
        buffer.putShort(0x0000.toShort())
        buffer.putShort(0x4000.toShort())
        buffer.put(64.toByte())
        buffer.put(17.toByte())
        buffer.putShort(0x0000.toShort())
        buffer.put(original.dstIp)
        buffer.put(original.srcIp)

        buffer.putShort(original.dstPort.toShort())
        buffer.putShort(original.srcPort.toShort())
        buffer.putShort((udpHeaderLen + dnsResponsePayload.size).toShort())
        buffer.putShort(0x0000.toShort())

        buffer.put(dnsResponsePayload)

        val result = buffer.array()
        val checksum = computeIpv4Checksum(result, ipHeaderLen)
        result[10] = ((checksum ushr 8) and 0xFF).toByte()
        result[11] = (checksum and 0xFF).toByte()
        return result
    }

    private fun computeIpv4Checksum(header: ByteArray, length: Int): Int {
        var sum = 0
        var i = 0
        while (i < length) {
            if (i != 10) {
                val word = ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
                sum += word
            }
            i += 2
        }
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
