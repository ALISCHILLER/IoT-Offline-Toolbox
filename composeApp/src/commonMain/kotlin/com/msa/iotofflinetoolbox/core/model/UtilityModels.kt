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

enum class PayloadOperation(val title: String) {
    FORMAT_JSON("Format JSON"),
    MINIFY_JSON("Minify JSON"),
    VALIDATE_JSON("Validate JSON"),
    TEXT_TO_HEX("Text → HEX"),
    HEX_TO_TEXT("HEX → Text"),
    TEXT_TO_BASE64("Text → Base64"),
    BASE64_TO_TEXT("Base64 → Text"),
    EXPAND_VARIABLES("Expand variables"),
    CRC32("CRC32"),
}

data class PayloadToolResult(
    val operation: PayloadOperation,
    val output: String,
    val valid: Boolean = true,
    val message: String = "",
)
