#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/msa-iot-desktop-network-harness"
KOTLIN_HOME="${KOTLIN_HOME:-$(CDPATH= cd -- "$(dirname -- "$(command -v kotlinc)")/.." && pwd)}"
COROUTINES_JAR="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"

if [[ ! -f "$COROUTINES_JAR" ]]; then
  echo "Missing kotlinx-coroutines-core-jvm.jar under $KOTLIN_HOME/lib" >&2
  exit 4
fi

rm -rf "$WORK"
mkdir -p \
  "$WORK/com/msa/iotofflinetoolbox/core/network" \
  "$WORK/com/msa/iotofflinetoolbox/core/model" \
  "$WORK/com/msa/iotofflinetoolbox/core/payload"

for file in IpTools.kt DnsSdCodec.kt SsdpParser.kt HttpResponseBodyTools.kt; do
  cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/$file" \
    "$WORK/com/msa/iotofflinetoolbox/core/network/$file"
done

sed '/^actual fun createPlatformNetworkProbe/d' \
  "$ROOT/composeApp/src/desktopMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.desktop.kt" \
  > "$WORK/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.desktop.kt"

cat > "$WORK/com/msa/iotofflinetoolbox/core/model/ModelStubs.kt" <<'KOTLIN'
package com.msa.iotofflinetoolbox.core.model

data class SubnetResult(
    val input: String,
    val networkAddress: String,
    val broadcastAddress: String,
    val subnetMask: String,
    val firstHost: String,
    val lastHost: String,
    val totalAddresses: Long,
    val usableAddresses: Long,
    val prefixLength: Int,
)

data class PlatformCapabilities(
    val platformName: String,
    val ping: Boolean,
    val tcpPortScan: Boolean,
    val hostDiscovery: Boolean,
    val ssdpDiscovery: Boolean,
    val mdnsDiscovery: Boolean,
    val dnsLookup: Boolean,
    val tcpClient: Boolean,
    val udpClient: Boolean,
    val httpClient: Boolean = true,
    val webSocketClient: Boolean = true,
    val mqttWebSocketClient: Boolean = true,
    val coapClient: Boolean,
    val notes: String,
)

data class PingResult(val host: String, val reachable: Boolean, val latencyMillis: Long?, val resolvedAddress: String?, val message: String)
enum class PortState { OPEN, CLOSED, FILTERED, ERROR }
data class PortScanResult(
    val port: Int,
    val isOpen: Boolean,
    val latencyMillis: Long?,
    val serviceHint: String,
    val error: String? = null,
    val state: PortState = if (isOpen) PortState.OPEN else PortState.CLOSED,
)
data class DiscoveredDevice(val host: String, val resolvedName: String? = null, val latencyMillis: Long? = null)
data class SocketExchangeResult(
    val protocol: String,
    val endpoint: String,
    val responseText: String,
    val responseHex: String,
    val bytesReceived: Int,
    val elapsedMillis: Long,
    val truncated: Boolean = false,
    val error: String? = null,
)
data class SsdpDevice(
    val usn: String,
    val location: String,
    val server: String? = null,
    val searchTarget: String? = null,
    val cacheControl: String? = null,
    val remoteAddress: String? = null,
    val headers: Map<String, String> = emptyMap(),
)
data class MdnsService(
    val serviceType: String,
    val instanceName: String,
    val host: String?,
    val port: Int?,
    val addresses: List<String>,
    val txt: Map<String, String>,
    val ttlSeconds: Long,
)
KOTLIN

cat > "$WORK/com/msa/iotofflinetoolbox/core/network/NetworkProbe.kt" <<'KOTLIN'
package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.*

interface NetworkProbe {
    val capabilities: PlatformCapabilities
    suspend fun ping(host: String, timeoutMillis: Int): PingResult
    suspend fun resolve(host: String): List<String>
    suspend fun tcpExchange(host: String, port: Int, payload: ByteArray, timeoutMillis: Int, maxResponseBytes: Int): SocketExchangeResult
    suspend fun udpExchange(host: String, port: Int, payload: ByteArray, timeoutMillis: Int, maxResponseBytes: Int): SocketExchangeResult
    suspend fun scanPorts(host: String, ports: List<Int>, timeoutMillis: Int, concurrency: Int): List<PortScanResult>
    suspend fun discover(cidr: String, timeoutMillis: Int, concurrency: Int, maxHosts: Int): List<DiscoveredDevice>
    suspend fun discoverSsdp(timeoutMillis: Int, maxResults: Int): List<SsdpDevice>
    suspend fun discoverMdns(timeoutMillis: Int, maxResults: Int): List<MdnsService>
    fun close() = Unit
}
KOTLIN

cat > "$WORK/com/msa/iotofflinetoolbox/core/payload/Hex.kt" <<'KOTLIN'
package com.msa.iotofflinetoolbox.core.payload

fun ByteArray.toDisplayHex(): String = joinToString(" ") { it.toUByte().toString(16).uppercase().padStart(2, '0') }
KOTLIN
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/payload/Base64Codec.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/payload/Base64Codec.kt"

cat > "$WORK/Main.kt" <<'KOTLIN'
package com.msa.iotofflinetoolbox.core.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

private fun verify(condition: Boolean, message: String) = check(condition) { message }

fun main() = runBlocking {
    val probe = JavaNetworkProbe("Harness")

    suspend fun tcp(response: ByteArray, limit: Int): com.msa.iotofflinetoolbox.core.model.SocketExchangeResult {
        val server = ServerSocket(0)
        val worker = thread {
            server.use { listener ->
                listener.accept().use { client ->
                    client.getOutputStream().apply { write(response); flush() }
                }
            }
        }
        val result = probe.tcpExchange("127.0.0.1", server.localPort, byteArrayOf(), 1_500, limit)
        worker.join()
        return result
    }

    val exact = tcp(ByteArray(1_024) { 1 }, 1_024)
    verify(exact.error == null && exact.bytesReceived == 1_024 && !exact.truncated, "exact TCP bound misclassified")
    val overflow = tcp(ByteArray(1_025) { 2 }, 1_024)
    verify(overflow.error == null && overflow.bytesReceived == 1_024 && overflow.truncated, "TCP overflow not truncated")
    val binaryTcp = tcp(byteArrayOf(0x00, 0xFF.toByte(), 0x01), 1_024)
    verify(binaryTcp.responseText.isEmpty(), "binary TCP response was rendered as unsafe text")
    verify(binaryTcp.responseHex == "00 FF 01", "binary TCP response HEX evidence changed")

    val cancellationServer = ServerSocket(0)
    val accepted = CountDownLatch(1)
    val cancellationWorker = thread {
        cancellationServer.use { listener ->
            listener.accept().use { client ->
                accepted.countDown()
                client.getInputStream().read()
            }
        }
    }
    val cancellationJob = launch {
        probe.tcpExchange("127.0.0.1", cancellationServer.localPort, byteArrayOf(), 10_000, 1_024)
    }
    yield()
    verify(accepted.await(2, TimeUnit.SECONDS), "cancellation TCP server was not reached")
    val cancellationMillis = measureTimeMillis { cancellationJob.cancelAndJoin() }
    cancellationWorker.join(2_000)
    verify(cancellationMillis < 1_000, "TCP cancellation waited for the socket timeout: $cancellationMillis ms")
    verify(!cancellationWorker.isAlive, "TCP cancellation did not close the peer socket")

    val udpServer = DatagramSocket(0)
    val udpWorker = thread {
        udpServer.use { socket ->
            val request = DatagramPacket(ByteArray(16), 16)
            socket.receive(request)
            val response = ByteArray(1_025) { 3 }
            socket.send(DatagramPacket(response, response.size, request.address, request.port))
        }
    }
    val udp = probe.udpExchange("127.0.0.1", udpServer.localPort, byteArrayOf(1), 1_500, 1_024)
    udpWorker.join()
    verify(udp.error == null && udp.bytesReceived == 1_024 && udp.truncated, "UDP overflow not truncated")

    probe.close()
    println("DESKTOP_NETWORK_RUNTIME_HARNESS_PASSED")
}
KOTLIN

mapfile -t SOURCES < <(find "$WORK" -name '*.kt' -print | sort)
kotlinc -J-Xms128m -J-Xmx1024m "${SOURCES[@]}" -cp "$COROUTINES_JAR" -include-runtime -d "$WORK/desktop-network-harness.jar"
java -cp "$WORK/desktop-network-harness.jar:$COROUTINES_JAR" com.msa.iotofflinetoolbox.core.network.MainKt
