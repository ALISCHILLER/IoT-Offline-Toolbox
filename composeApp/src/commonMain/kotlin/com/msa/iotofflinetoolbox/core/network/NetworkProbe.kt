package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.port.NetworkProbe

/** Platform adapter factory. The application depends only on [NetworkProbe]. */
expect fun createPlatformNetworkProbe(): NetworkProbe
