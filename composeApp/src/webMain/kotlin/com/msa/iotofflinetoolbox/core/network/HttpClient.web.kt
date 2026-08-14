package com.msa.iotofflinetoolbox.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun createPlatformHttpClient(): HttpClient = HttpClient(Js) {
    expectSuccess = false
    followRedirects = false
}
