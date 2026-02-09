package com.msa.iotofflinetoolbox

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform