package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.MdnsService

object DnsSdCodec {
    const val ADDRESS: String = "224.0.0.251"
    const val PORT: Int = 5353
    const val SERVICE_ENUMERATION: String = "_services._dns-sd._udp.local"

    fun query(name: String, type: Int = TYPE_PTR): ByteArray {
        require(name.isNotBlank()) { "mDNS query name cannot be empty" }
        require(name.length <= 253) { "mDNS query name exceeds 253 characters" }
        require(type in 1..65_535) { "mDNS query type must be between 1 and 65535" }
        val header = byteArrayOf(
            0, 0, // transaction ID
            0, 0, // flags
            0, 1, // one question
            0, 0, // answers
            0, 0, // authorities
            0, 0, // additionals
        )
        return header + encodeName(name) + byteArrayOf(
            ((type shr 8) and 0xFF).toByte(), (type and 0xFF).toByte(),
            0, 1, // IN class
        )
    }

    fun parse(packet: ByteArray): DnsMessage {
        require(packet.size >= 12) { "DNS packet is too short" }
        require(packet.size <= MAX_DNS_PACKET_BYTES) { "DNS packet exceeds the UDP datagram limit" }
        val questionCount = u16(packet, 4)
        val answerCount = u16(packet, 6)
        val authorityCount = u16(packet, 8)
        val additionalCount = u16(packet, 10)
        require(questionCount <= MAX_DNS_QUESTIONS) { "DNS packet contains too many questions" }
        val recordCount = answerCount.toLong() + authorityCount.toLong() + additionalCount.toLong()
        require(recordCount <= MAX_DNS_RECORDS) { "DNS packet contains too many resource records" }
        require(questionCount.toLong() * MIN_DNS_QUESTION_BYTES + recordCount * MIN_DNS_RECORD_BYTES <= packet.size.toLong() * DNS_COUNT_SLACK_FACTOR) {
            "DNS section counts are inconsistent with packet size"
        }
        var cursor = 12
        repeat(questionCount) {
            cursor = readName(packet, cursor).nextIndex
            require(cursor + 4 <= packet.size) { "Incomplete DNS question" }
            cursor += 4
        }
        val records = mutableListOf<DnsRecord>()
        repeat(answerCount + authorityCount + additionalCount) {
            val owner = readName(packet, cursor)
            cursor = owner.nextIndex
            require(cursor + 10 <= packet.size) { "Incomplete DNS resource record" }
            val type = u16(packet, cursor)
            val clazz = u16(packet, cursor + 2) and 0x7FFF
            val ttl = u32(packet, cursor + 4)
            val dataLength = u16(packet, cursor + 8)
            val dataStart = cursor + 10
            val dataEnd = dataStart + dataLength
            require(dataEnd <= packet.size) { "DNS resource record exceeds packet size" }
            records += decodeRecord(packet, owner.name, type, clazz, ttl, dataStart, dataLength)
            cursor = dataEnd
        }
        require(cursor == packet.size) { "DNS packet contains trailing bytes outside declared sections" }
        return DnsMessage(records)
    }

    fun serviceTypes(messages: List<DnsMessage>): List<String> = messages
        .flatMap { it.records }
        .filter { it.clazz == CLASS_IN && it.type == TYPE_PTR && it.ttlSeconds > 0 && it.name.equals(SERVICE_ENUMERATION, ignoreCase = true) }
        .mapNotNull { it.pointer }
        .distinct()
        .sorted()

    fun services(messages: List<DnsMessage>): List<MdnsService> {
        val records = messages.flatMap { it.records }
        val internetRecords = records.filter { it.clazz == CLASS_IN }
        val pointers = internetRecords.filter { it.type == TYPE_PTR && it.ttlSeconds > 0 && !it.name.equals(SERVICE_ENUMERATION, ignoreCase = true) }
        val srvByInstance = internetRecords.filter { it.type == TYPE_SRV && it.ttlSeconds > 0 }.associateBy { it.name.lowercase() }
        val txtByInstance = internetRecords.filter { it.type == TYPE_TXT && it.ttlSeconds > 0 }.associateBy { it.name.lowercase() }
        val addressesByHost = internetRecords.filter { it.ttlSeconds > 0 && (it.type == TYPE_A || it.type == TYPE_AAAA) }
            .groupBy { it.name.lowercase() }
            .mapValues { (_, values) -> values.mapNotNull { it.address }.distinct() }

        return pointers.mapNotNull { pointer ->
            val instance = pointer.pointer ?: return@mapNotNull null
            val srv = srvByInstance[instance.lowercase()]
            val host = srv?.target
            MdnsService(
                serviceType = pointer.name,
                instanceName = instance,
                host = host,
                port = srv?.port,
                addresses = host?.let { addressesByHost[it.lowercase()].orEmpty() }.orEmpty(),
                txt = txtByInstance[instance.lowercase()]?.txt.orEmpty(),
                ttlSeconds = pointer.ttlSeconds,
            )
        }.distinctBy { "${it.serviceType.lowercase()}|${it.instanceName.lowercase()}" }
            .sortedWith(compareBy({ it.serviceType.lowercase() }, { it.instanceName.lowercase() }))
    }

    data class DnsMessage(val records: List<DnsRecord>)

    data class DnsRecord(
        val name: String,
        val type: Int,
        val clazz: Int,
        val ttlSeconds: Long,
        val pointer: String? = null,
        val target: String? = null,
        val port: Int? = null,
        val txt: Map<String, String> = emptyMap(),
        val address: String? = null,
    )

    private fun decodeRecord(
        packet: ByteArray,
        name: String,
        type: Int,
        clazz: Int,
        ttl: Long,
        dataStart: Int,
        dataLength: Int,
    ): DnsRecord {
        val dataEnd = dataStart + dataLength
        require(dataStart >= 0 && dataLength >= 0 && dataEnd <= packet.size) { "DNS record data exceeds packet size" }
        return when (type) {
            TYPE_PTR -> {
                require(dataLength > 0) { "Invalid DNS PTR record" }
                val pointer = readName(packet, dataStart)
                require(pointer.nextIndex == dataEnd) { "DNS PTR record contains trailing or truncated name data" }
                DnsRecord(name, type, clazz, ttl, pointer = pointer.name)
            }
            TYPE_SRV -> {
                require(dataLength >= 7) { "Invalid DNS SRV record" }
                val target = readName(packet, dataStart + 6)
                require(target.nextIndex == dataEnd) { "DNS SRV record contains trailing or truncated target data" }
                DnsRecord(
                    name = name,
                    type = type,
                    clazz = clazz,
                    ttlSeconds = ttl,
                    port = u16(packet, dataStart + 4),
                    target = target.name,
                )
            }
            TYPE_TXT -> DnsRecord(name, type, clazz, ttl, txt = decodeTxt(packet, dataStart, dataLength))
            TYPE_A -> {
                require(dataLength == 4) { "DNS A record must contain exactly four address bytes" }
                DnsRecord(
                    name,
                    type,
                    clazz,
                    ttl,
                    address = (0 until 4).joinToString(".") { (packet[dataStart + it].toInt() and 0xFF).toString() },
                )
            }
            TYPE_AAAA -> {
                require(dataLength == 16) { "DNS AAAA record must contain exactly sixteen address bytes" }
                DnsRecord(
                    name,
                    type,
                    clazz,
                    ttl,
                    address = (0 until 8).joinToString(":") { index -> u16(packet, dataStart + index * 2).toString(16) },
                )
            }
            else -> DnsRecord(name, type, clazz, ttl)
        }
    }

    private fun decodeTxt(packet: ByteArray, start: Int, length: Int): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var cursor = start
        val end = start + length
        while (cursor < end) {
            val itemLength = packet[cursor++].toInt() and 0xFF
            require(cursor + itemLength <= end) { "DNS TXT item exceeds record size" }
            val item = packet.copyOfRange(cursor, cursor + itemLength).decodeToString(throwOnInvalidSequence = true)
            cursor += itemLength
            val delimiter = item.indexOf('=')
            if (delimiter < 0) result[item] = "" else result[item.substring(0, delimiter)] = item.substring(delimiter + 1)
        }
        return result
    }

    private data class DecodedName(val name: String, val nextIndex: Int)

    private fun readName(packet: ByteArray, start: Int): DecodedName {
        val labels = mutableListOf<String>()
        var cursor = start
        var nextIndex = -1
        var jumps = 0
        val visited = mutableSetOf<Int>()
        while (true) {
            require(cursor in packet.indices) { "DNS name points outside packet" }
            require(visited.add(cursor)) { "DNS compression pointer loop" }
            val length = packet[cursor].toInt() and 0xFF
            when {
                length == 0 -> {
                    cursor++
                    if (nextIndex < 0) nextIndex = cursor
                    break
                }
                (length and 0xC0) == 0xC0 -> {
                    require(cursor + 1 < packet.size) { "Incomplete DNS compression pointer" }
                    val pointer = ((length and 0x3F) shl 8) or (packet[cursor + 1].toInt() and 0xFF)
                    if (nextIndex < 0) nextIndex = cursor + 2
                    cursor = pointer
                    jumps++
                    require(jumps <= 32) { "Too many DNS compression pointers" }
                }
                else -> {
                    require(length <= 63 && cursor + 1 + length <= packet.size) { "Invalid DNS label" }
                    labels += packet.copyOfRange(cursor + 1, cursor + 1 + length).decodeToString(throwOnInvalidSequence = true)
                    require(labels.sumOf { it.encodeToByteArray().size + 1 } <= 255) { "DNS name exceeds 255 encoded bytes" }
                    cursor += 1 + length
                }
            }
        }
        return DecodedName(labels.joinToString("."), nextIndex)
    }

    private fun encodeName(name: String): ByteArray {
        val output = mutableListOf<Byte>()
        val normalized = name.trim('.')
        require(normalized.encodeToByteArray().size <= 253) { "DNS name exceeds 253 bytes" }
        normalized.split('.').forEach { label ->
            val bytes = label.encodeToByteArray()
            require(bytes.isNotEmpty() && bytes.size <= 63) { "Invalid DNS label: $label" }
            output += bytes.size.toByte()
            output += bytes.toList()
        }
        output += 0
        return output.toByteArray()
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)

    private const val MAX_DNS_PACKET_BYTES = 65_507
    private const val MAX_DNS_QUESTIONS = 64
    private const val MAX_DNS_RECORDS = 512
    private const val MIN_DNS_QUESTION_BYTES = 5L
    private const val MIN_DNS_RECORD_BYTES = 11L
    private const val DNS_COUNT_SLACK_FACTOR = 2L

    private const val CLASS_IN = 1
    private const val TYPE_A = 1
    private const val TYPE_PTR = 12
    private const val TYPE_TXT = 16
    private const val TYPE_AAAA = 28
    private const val TYPE_SRV = 33
}
