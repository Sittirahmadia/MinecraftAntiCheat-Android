package com.sstools.anticheat.scanner

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Deleted File Scanner for Android (non-root)
 * Scans accessible temp directories, download history, and cache
 * for traces of cheat-related files.
 */
object DeletedFileScanner {

    data class DeletedFileScanResult(
        val tempFiles: List<SuspiciousFile>,
        val downloadFiles: List<SuspiciousFile>,
        val cacheFiles: List<SuspiciousFile>,
        val recentApks: List<SuspiciousFile>,
        val flaggedItems: List<SuspiciousFile>,
        val totalScanned: Int
    )

    data class SuspiciousFile(
        val filename: String,
        val path: String,
        val sizeMb: Float,
        val lastModified: String,
        val source: String,
        val detections: List<DetectionResult> = emptyList()
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val SUSPICIOUS_EXTENSIONS = setOf(".jar", ".apk", ".zip", ".dex", ".so", ".exe", ".dll")

    fun scanDeletedFiles(cacheDir: File? = null): DeletedFileScanResult {
        val tempFiles = mutableListOf<SuspiciousFile>()
        val downloadFiles = mutableListOf<SuspiciousFile>()
        val cacheFiles = mutableListOf<SuspiciousFile>()
        val recentApks = mutableListOf<SuspiciousFile>()
        val flaggedItems = mutableListOf<SuspiciousFile>()
        var totalScanned = 0

        // 1. Scan Downloads directory
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir.exists()) {
            downloadsDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    totalScanned++
                    val ext = ".${file.extension.lowercase()}"
                    if (ext in SUSPICIOUS_EXTENSIONS) {
                        val sf = createSuspiciousFile(file, "Downloads")
                        downloadFiles.add(sf)
                        if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                    }
                }
            }
        }

        // 2. Scan temp directories
        val tempDirs = listOf(
            File("/data/local/tmp"),
            File(Environment.getExternalStorageDirectory(), ".tmp"),
            File(Environment.getExternalStorageDirectory(), "tmp"),
        )
        for (tmpDir in tempDirs) {
            if (tmpDir.exists() && tmpDir.isDirectory) {
                tmpDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        totalScanned++
                        val ext = ".${file.extension.lowercase()}"
                        if (ext in SUSPICIOUS_EXTENSIONS) {
                            val sf = createSuspiciousFile(file, "Temp")
                            tempFiles.add(sf)
                            if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                        }
                    }
                }
            }
        }

        // 3. Scan app cache directory
        if (cacheDir != null && cacheDir.exists()) {
            scanCacheDir(cacheDir, cacheFiles, flaggedItems)
            totalScanned += cacheFiles.size
        }

        // 4. Scan for recently installed APKs (cheat-related)
        val apkDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "APK"),
        )
        for (dir in apkDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().maxDepth(2).filter { it.isFile && it.extension.lowercase() == "apk" }.forEach { file ->
                    totalScanned++
                    val sf = createSuspiciousFile(file, "APK")
                    recentApks.add(sf)
                    if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                }
            }
        }

        // 5. Scan Android/data for leftover cheat launcher data
        val androidDataDir = File(Environment.getExternalStorageDirectory(), "Android/data")
        if (androidDataDir.exists()) {
            val suspiciousPackages = listOf(
                "com.cheatengine", "com.gameguardian", "com.topjohnwu.magisk",
                "eu.chainfire.supersu", "com.noshufou.android.su",
                "com.koushikdutta.superuser", "com.thirdparty.superuser",
            )
            androidDataDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    for (susPkg in suspiciousPackages) {
                        if (dir.name == susPkg) {
                            flaggedItems.add(SuspiciousFile(
                                filename = dir.name,
                                path = dir.absolutePath,
                                sizeMb = 0f,
                                lastModified = dateFormat.format(Date(dir.lastModified())),
                                source = "Android/data (suspicious package)",
                                detections = listOf(DetectionResult(
                                    signatureName = "Suspicious Package: ${dir.name}",
                                    category = "Suspicious App",
                                    severity = "high",
                                    description = "Found data directory for suspicious application: ${dir.name}",
                                    matchedPatterns = listOf("package:${dir.name}"),
                                    matchCount = 1,
                                    filePath = dir.absolutePath,
                                    confidence = 0.8f
                                ))
                            ))
                        }
                    }
                }
            }
        }

        return DeletedFileScanResult(
            tempFiles = tempFiles,
            downloadFiles = downloadFiles,
            cacheFiles = cacheFiles,
            recentApks = recentApks,
            flaggedItems = flaggedItems,
            totalScanned = totalScanned
        )
    }

    private fun createSuspiciousFile(file: File, source: String): SuspiciousFile {
        val detections = CheatDetector.detectCheats(
            content = file.nameWithoutExtension,
            filename = file.name,
            filePath = file.absolutePath
        )
        return SuspiciousFile(
            filename = file.name,
            path = file.absolutePath,
            sizeMb = "%.2f".format(file.length().toFloat() / (1024 * 1024)).toFloat(),
            lastModified = dateFormat.format(Date(file.lastModified())),
            source = source,
            detections = detections
        )
    }

    private fun scanCacheDir(cacheDir: File, cacheFiles: MutableList<SuspiciousFile>, flaggedItems: MutableList<SuspiciousFile>) {
        try {
            cacheDir.walkTopDown().maxDepth(3).filter { it.isFile }.forEach { file ->
                val ext = ".${file.extension.lowercase()}"
                if (ext in SUSPICIOUS_EXTENSIONS) {
                    val sf = createSuspiciousFile(file, "Cache")
                    cacheFiles.add(sf)
                    if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                }
            }
        } catch (_: Exception) {}
    }
}
