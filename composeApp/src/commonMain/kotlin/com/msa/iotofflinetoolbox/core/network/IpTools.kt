package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.SubnetResult

object Ipv4 {
    fun parse(value: String): Long {
        val parts = value.trim().split('.')
        require(parts.size == 4) { "IPv4 address must contain four octets" }
        return parts.fold(0L) { result, part ->
            require(part.isNotEmpty() && part.all(Char::isDigit)) { "Invalid IPv4 octet: $part" }
            val octet = part.toInt()
            require(octet in 0..255) { "IPv4 octet out of range: $part" }
            (result shl 8) or octet.toLong()
        }
    }

    fun format(value: Long): String = listOf(24, 16, 8, 0)
        .joinToString(".") { shift -> ((value shr shift) and 0xFF).toString() }
}

object CidrCalculator {
    fun calculate(input: String): SubnetResult {
        val pieces = input.trim().split('/')
        require(pieces.size == 2) { "CIDR must look like 192.168.1.0/24" }
        val address = Ipv4.parse(pieces[0])
        val prefix = pieces[1].toIntOrNull() ?: error("Invalid prefix length")
        require(prefix in 0..32) { "Prefix length must be between 0 and 32" }

        val mask = if (prefix == 0) 0L else (0xFFFF_FFFFL shl (32 - prefix)) and 0xFFFF_FFFFL
        val network = address and mask
        val wildcard = mask xor 0xFFFF_FFFFL
        val broadcast = network or wildcard
        val total = 1L shl (32 - prefix)
        val usable = when (prefix) {
            32 -> 1L
            31 -> 2L
            else -> (total - 2).coerceAtLeast(0)
        }
        val first = when (prefix) {
            32 -> network
            31 -> network
            else -> network + 1
        }
        val last = when (prefix) {
            32 -> network
            31 -> broadcast
            else -> broadcast - 1
        }

        return SubnetResult(
            input = input,
            networkAddress = Ipv4.format(network),
            broadcastAddress = Ipv4.format(broadcast),
            subnetMask = Ipv4.format(mask),
            firstHost = Ipv4.format(first),
            lastHost = Ipv4.format(last),
            totalAddresses = total,
            usableAddresses = usable,
            prefixLength = prefix,
        )
    }

    fun hostAddresses(input: String, limit: Int): List<String> {
        require(limit in 1..65_536) { "Host limit is outside the supported range" }
        val result = calculate(input)
        val first = Ipv4.parse(result.firstHost)
        val last = Ipv4.parse(result.lastHost)
        if (last < first) return emptyList()
        val count = (last - first + 1).coerceAtMost(limit.toLong())
        return List(count.toInt()) { index -> Ipv4.format(first + index) }
    }
}

data class PortPreset(
    val id: String,
    val specification: String,
)

object PortPresets {
    val recommended = PortPreset("recommended", "22,53,80,123,443,502,1883,5353,5683,5684,8080,8443,8883")
    val web = PortPreset("web", "80,443,8000,8008,8080,8081,8443,8888,9000")
    val iot = PortPreset("iot", "53,67,68,123,161,162,443,502,1883,5353,5683,5684,8883,10001")
    val remote = PortPreset("remote", "21,22,23,3389,5900,5985,5986")
    val databases = PortPreset("databases", "1433,1521,27017,3306,5432,6379,9042,9200")
    val messaging = PortPreset("messaging", "1883,5671,5672,61613,8883,9092")
    val mail = PortPreset("mail", "25,110,143,465,587,993,995")
    val wellKnown = PortPreset("well_known", "1-1024")
    val all: List<PortPreset> = listOf(recommended, web, iot, remote, databases, messaging, mail, wellKnown)
}

object PortSpecParser {
    private val aliases: Map<String, List<Int>> = mapOf(
        "http" to listOf(80),
        "https" to listOf(443),
        "ssh" to listOf(22),
        "telnet" to listOf(23),
        "ftp" to listOf(20, 21),
        "dns" to listOf(53),
        "dhcp" to listOf(67, 68),
        "ntp" to listOf(123),
        "snmp" to listOf(161, 162),
        "modbus" to listOf(502),
        "mqtt" to listOf(1883),
        "mqtts" to listOf(8883),
        "mdns" to listOf(5353),
        "coap" to listOf(5683),
        "coaps" to listOf(5684),
        "redis" to listOf(6379),
        "mysql" to listOf(3306),
        "postgres" to listOf(5432),
        "postgresql" to listOf(5432),
        "mssql" to listOf(1433),
        "mongodb" to listOf(27017),
        "rdp" to listOf(3389),
        "smtp" to listOf(25, 465, 587),
        "imap" to listOf(143, 993),
        "pop3" to listOf(110, 995),
        "smb" to listOf(445),
        "ldap" to listOf(389),
        "ldaps" to listOf(636),
        "tftp" to listOf(69),
        "syslog" to listOf(514),
        "opcua" to listOf(4840),
        "amqp" to listOf(5671, 5672),
        "kafka" to listOf(9092),
        "elasticsearch" to listOf(9200),
        "influxdb" to listOf(8086),
        "docker" to listOf(2375, 2376),
        "oracle" to listOf(1521),
        "vnc" to listOf(5900),
        "winrm" to listOf(5985, 5986),
        "well-known" to (1..1024).toList(),
        "well_known" to (1..1024).toList(),
        "wellknown" to (1..1024).toList(),
        "recommended" to PortSpecParserPresetValues.recommended,
        "common" to PortSpecParserPresetValues.recommended,
        "web" to PortSpecParserPresetValues.web,
        "iot" to PortSpecParserPresetValues.iot,
        "remote" to PortSpecParserPresetValues.remote,
        "database" to PortSpecParserPresetValues.databases,
        "databases" to PortSpecParserPresetValues.databases,
        "db" to PortSpecParserPresetValues.databases,
        "messaging" to PortSpecParserPresetValues.messaging,
        "mail" to PortSpecParserPresetValues.mail,
    )

    fun parse(spec: String, maximumPorts: Int = 4096): List<Int> {
        require(maximumPorts in 1..65_535) { "Maximum port count must be between 1 and 65535" }
        require(spec.isNotBlank()) { "Enter at least one port" }
        require(spec.length <= 32_768) { "Port specification exceeds the safety limit" }
        val tokens = spec.split(Regex("[,;\\s]+"))
            .map(String::trim)
            .filter(String::isNotEmpty)
        require(tokens.size <= 8_192) { "Port specification contains too many tokens" }

        val included = linkedSetOf<Int>()
        val excluded = linkedSetOf<Int>()
        val maximumExpansionWork = maxOf(MINIMUM_EXPANSION_WORK, maximumPorts * EXPANSION_MULTIPLIER)
        var expansionWork = 0
        tokens.forEach { rawToken ->
            val exclude = rawToken.startsWith('!')
            val token = rawToken.removePrefix("!").lowercase()
            require(token.isNotBlank()) { "Invalid empty port token" }
            val expanded = expandToken(token)
            val destination = if (exclude) excluded else included
            expanded.forEach { port ->
                expansionWork += 1
                require(expansionWork <= maximumExpansionWork) {
                    "Port specification performs too much repeated expansion work"
                }
                destination += port
                if (!exclude) {
                    require(included.size <= maximumPorts) {
                        "A maximum of $maximumPorts ports can be scanned at once"
                    }
                }
            }
        }
        return included.filterNot(excluded::contains).sorted().also {
            require(it.isNotEmpty()) { "The port specification resolves to an empty set" }
        }
    }

    private fun expandToken(token: String): Iterable<Int> {
        aliases[token]?.let { return it }
        if ('-' in token) {
            val bounds = token.split('-', limit = 2)
            val start = bounds[0].trim().toIntOrNull() ?: error("Invalid port range: $token")
            val end = bounds[1].trim().toIntOrNull() ?: error("Invalid port range: $token")
            require(start in 1..65_535 && end in 1..65_535 && start <= end) {
                "Port range must be between 1 and 65535: $token"
            }
            return start..end
        }
        val port = token.toIntOrNull() ?: error("Unknown service alias or invalid port: $token")
        require(port in 1..65_535) { "Port must be between 1 and 65535: $port" }
        return listOf(port)
    }

    private const val MINIMUM_EXPANSION_WORK = 65_536
    private const val EXPANSION_MULTIPLIER = 16

    private object PortSpecParserPresetValues {
        val recommended = parseLiteral(PortPresets.recommended.specification)
        val web = parseLiteral(PortPresets.web.specification)
        val iot = parseLiteral(PortPresets.iot.specification)
        val remote = parseLiteral(PortPresets.remote.specification)
        val databases = parseLiteral(PortPresets.databases.specification)
        val messaging = parseLiteral(PortPresets.messaging.specification)
        val mail = parseLiteral(PortPresets.mail.specification)

        private fun parseLiteral(value: String): List<Int> = value.split(',').map(String::toInt)
    }
}

fun serviceHint(port: Int): String = when (port) {
    20 -> "FTP data"
    21 -> "FTP control"
    22 -> "SSH / SFTP"
    23 -> "Telnet"
    25 -> "SMTP"
    53 -> "DNS"
    67, 68 -> "DHCP"
    69 -> "TFTP"
    80, 8000, 8008, 8080, 8081, 8888 -> "HTTP"
    110 -> "POP3"
    123 -> "NTP"
    135 -> "MS RPC"
    137, 138, 139 -> "NetBIOS"
    143 -> "IMAP"
    161, 162 -> "SNMP"
    389 -> "LDAP"
    443, 8443 -> "HTTPS"
    445 -> "SMB"
    465, 587 -> "SMTP TLS / Submission"
    502 -> "Modbus TCP"
    514 -> "Syslog"
    636 -> "LDAPS"
    853 -> "DNS over TLS"
    993 -> "IMAPS"
    995 -> "POP3S"
    1433 -> "Microsoft SQL Server"
    1521 -> "Oracle Database"
    1883 -> "MQTT"
    2375, 2376 -> "Docker API"
    3000 -> "Development HTTP"
    3306 -> "MySQL / MariaDB"
    3389 -> "RDP"
    4840 -> "OPC UA"
    5353 -> "mDNS"
    5432 -> "PostgreSQL"
    5683 -> "CoAP"
    5684 -> "CoAP DTLS"
    5671, 5672 -> "AMQP"
    5900 -> "VNC"
    5985 -> "WinRM HTTP"
    5986 -> "WinRM HTTPS"
    6379 -> "Redis"
    8086 -> "InfluxDB"
    8883 -> "MQTT TLS"
    9000 -> "Service console"
    9042 -> "Cassandra"
    9092 -> "Kafka"
    9200 -> "Elasticsearch"
    10001 -> "Ubiquiti discovery"
    27017 -> "MongoDB"
    else -> when (port) {
        in 1..1023 -> "Registered system service"
        in 1024..49151 -> "Registered application service"
        else -> "Dynamic / private service"
    }
}

