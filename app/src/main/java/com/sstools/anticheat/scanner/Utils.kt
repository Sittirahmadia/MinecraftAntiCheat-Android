package com.sstools.anticheat.scanner

/**
 * Locale-safe utility to convert bytes to MB as Float.
 * Avoids "%.2f".format() which uses system locale (Indonesian = comma separator)
 * and crashes with NumberFormatException when parsed back with .toFloat()
 */
fun bytesToMb(bytes: Long): Float {
    return Math.round(bytes.toFloat() / (1024f * 1024f) * 100f) / 100f
}

fun bytesToMb(bytes: Float): Float {
    return Math.round(bytes / (1024f * 1024f) * 100f) / 100f
}

fun roundMb(mb: Float): Float {
    return Math.round(mb * 100f) / 100f
}
