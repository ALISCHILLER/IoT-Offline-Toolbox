package com.msa.iotofflinetoolbox.core.network

import android.content.Context
import android.net.wifi.WifiManager

/**
 * Supplies Android-only services required by multicast discovery.
 *
 * The application context is kept deliberately; no Activity reference is retained.
 */
object AndroidNetworkEnvironment {
    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    internal fun acquireMulticastLock(tag: String): WifiManager.MulticastLock? {
        val manager = applicationContext
            ?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return manager
            ?.createMulticastLock(tag)
            ?.apply {
                setReferenceCounted(false)
                acquire()
            }
    }
}

fun initializeAndroidPlatform(context: Context) {
    AndroidNetworkEnvironment.initialize(context)
}
