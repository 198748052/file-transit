package com.filetransit.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

private val BYTE_UNITS = arrayOf("KB", "MB", "GB", "TB", "PB")

private val dateTimeFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
}

private val dateShortFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble() / 1024.0
    var index = 0
    while (value >= 1024 && index < BYTE_UNITS.size - 1) {
        value /= 1024.0
        index++
    }
    val scaled = (value * 10).roundToLong()
    return "${scaled / 10}.${scaled % 10} ${BYTE_UNITS[index]}"
}

fun formatDate(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0) return "-"
    return dateTimeFormat.get()!!.format(Date(epochMs))
}

fun formatDateShort(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0) return "-"
    return dateShortFormat.get()!!.format(Date(epochMs))
}

fun expiryLabel(expiresAt: Long?): String {
    if (expiresAt == null) return "永久有效"
    val remaining = expiresAt - System.currentTimeMillis()
    if (remaining <= 0) return "已过期"
    val days = remaining / 86_400_000
    return if (days >= 1) "${days + 1} 天后过期" else "今天过期"
}

fun statusLabel(status: String): String = when (status) {
    "ready" -> "可下载"
    "uploading" -> "上传中"
    "expired" -> "已过期"
    "deleted" -> "已删除"
    else -> status
}

fun truncateMiddle(value: String, max: Int = 48): String {
    if (value.length <= max) return value
    val half = (max - 3) / 2
    return value.take(half) + "..." + value.takeLast(half)
}
