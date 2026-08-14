package com.msa.iotofflinetoolbox.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

actual fun createPlatformHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    followRedirects = false
}
