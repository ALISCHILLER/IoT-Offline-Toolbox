#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/msa-iot-browser-network-harness"
KOTLIN_LIB="/root/.sdkman/candidates/kotlin/current/lib"
rm -rf "$WORK"
mkdir -p \
  "$WORK/com/msa/iotofflinetoolbox/core/model" \
  "$WORK/com/msa/iotofflinetoolbox/core/port" \
  "$WORK/com/msa/iotofflinetoolbox/core/policy" \
  "$WORK/com/msa/iotofflinetoolbox/core/network" \
  "$WORK/kotlinx/serialization"

cp "$ROOT"/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/model/*.kt \
  "$WORK/com/msa/iotofflinetoolbox/core/model/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/port/NetworkProbe.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/port/NetworkProbe.kt"
cp "$ROOT/composeApp/src/webMain/kotlin/com/msa/iotofflinetoolbox/core/network/BrowserNetworkProbe.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/network/BrowserNetworkProbe.kt"

cat > "$WORK/kotlinx/serialization/Serializable.kt" <<'KT'
package kotlinx.serialization

@Target(AnnotationTarget.CLASS)
annotation class Serializable
KT

cat > "$WORK/com/msa/iotofflinetoolbox/core/policy/ServiceHint.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.policy

fun serviceHint(port: Int): String = when (port) {
    80 -> "HTTP"
    443 -> "HTTPS"
    else -> "Unknown"
}
KT

cat > "$WORK/Main.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.PortState
import kotlinx.coroutines.runBlocking

private fun verify(condition: Boolean, message: String) = check(condition) { message }

fun main() = runBlocking {
    val probe = BrowserNetworkProbe(
        platform = "Browser Test",
        details = "sandboxed browser",
    )
    verify(probe.capabilities.httpClient, "browser HTTP capability must remain available")
    verify(probe.capabilities.webSocketClient, "browser WebSocket capability must remain available")
    verify(probe.capabilities.mqttWebSocketClient, "browser MQTT-over-WebSocket capability must remain available")
    verify(!probe.capabilities.tcpClient && !probe.capabilities.udpClient, "raw sockets must stay disabled in browsers")
    verify(!probe.ping("example.test", 500).reachable, "browser ping must be capability-gated")
    verify(probe.resolve("example.test").isEmpty(), "browser DNS lookup must be capability-gated")
    val scan = probe.scanPorts("example.test", listOf(80, 443), 500, 2)
    verify(scan.size == 2 && scan.all { it.state == PortState.ERROR }, "browser port scan must return bounded unsupported evidence")
    verify(scan.first().serviceHint == "HTTP", "browser unsupported scan should preserve service hints")
    val tcp = probe.tcpExchange("example.test", 80, byteArrayOf(), 500, 1024)
    verify(tcp.error?.contains("unavailable") == true, "browser TCP exchange must explain unsupported capability")
    val udp = probe.udpExchange("example.test", 53, byteArrayOf(), 500, 1024)
    verify(udp.error?.contains("unavailable") == true, "browser UDP exchange must explain unsupported capability")
    verify(probe.discover("192.168.1.0/24", 500, 2, 8).isEmpty(), "browser host discovery must be disabled")
    verify(probe.discoverSsdp(500, 8).isEmpty(), "browser SSDP must be disabled")
    verify(probe.discoverMdns(500, 8).isEmpty(), "browser mDNS must be disabled")
    println("BROWSER_NETWORK_RUNTIME_HARNESS_PASSED")
}
KT

kotlinc "$WORK" \
  -cp "$KOTLIN_LIB/kotlinx-coroutines-core-jvm.jar" \
  -include-runtime \
  -d "$WORK/browser-network-harness.jar"

java -cp "$WORK/browser-network-harness.jar:$KOTLIN_LIB/kotlinx-coroutines-core-jvm.jar" \
  com.msa.iotofflinetoolbox.core.network.MainKt
