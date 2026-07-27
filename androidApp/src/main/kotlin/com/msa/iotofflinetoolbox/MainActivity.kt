package com.msa.iotofflinetoolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.msa.iotofflinetoolbox.core.network.initializeAndroidPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeAndroidPlatform(applicationContext)
        enableEdgeToEdge()
        setContent { App() }
    }
}
