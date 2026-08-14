package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.port.NetworkProbe

actual fun createPlatformNetworkProbe(): NetworkProbe = BrowserNetworkProbe(
    platform = "Browser JS",
    details = "Browser sandboxing blocks raw ICMP, DNS, UDP and arbitrary TCP sockets. HTTP, WebSocket, MQTT over WebSocket and all offline tools remain available subject to CORS and endpoint policy.",
)
