package com.sstools.anticheat.scanner

import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Deleted File Scanner for Android (non-root)
 * All file operations wrapped in try-catch to prevent crashes
 */
object DeletedFileScanner {

    private const val TAG = "DeletedFileScanner"

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
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists() && downloadsDir.canRead()) {
                downloadsDir.listFiles()?.forEach { file ->
                    try {
                        if (file.isFile && file.canRead()) {
                            totalScanned++
                            val ext = ".${file.extension.lowercase()}"
                            if (ext in SUSPICIOUS_EXTENSIONS) {
                                val sf = createSuspiciousFile(file, "Downloads")
                                downloadFiles.add(sf)
                                if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Error scanning download file: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning Downloads: ${e.message}")
        }

        // 2. Scan temp directories (only accessible ones)
        try {
            val tempDirPaths = listOf(
                "/storage/emulated/0/.tmp",
                "/storage/emulated/0/tmp",
            )
            for (tmpPath in tempDirPaths) {
                try {
                    val tmpDir = File(tmpPath)
                    if (tmpDir.exists() && tmpDir.isDirectory && tmpDir.canRead()) {
                        tmpDir.listFiles()?.forEach { file ->
                            try {
                                if (file.isFile && file.canRead()) {
                                    totalScanned++
                                    val ext = ".${file.extension.lowercase()}"
                                    if (ext in SUSPICIOUS_EXTENSIONS) {
                                        val sf = createSuspiciousFile(file, "Temp")
                                        tempFiles.add(sf)
                                        if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning temp dirs: ${e.message}")
        }

        // 3. Scan app cache directory
        try {
            if (cacheDir != null && cacheDir.exists() && cacheDir.canRead()) {
                scanCacheDirSafe(cacheDir, cacheFiles, flaggedItems)
                totalScanned += cacheFiles.size
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning cache: ${e.message}")
        }

        // 4. Scan for APKs in Downloads
        try {
            val downloadDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            )
            for (dir in downloadDirs) {
                try {
                    if (dir != null && dir.exists() && dir.isDirectory && dir.canRead()) {
                        dir.walkTopDown().maxDepth(2)
                            .filter {
                                try { it.isFile && it.canRead() && it.extension.lowercase() == "apk" }
                                catch (_: Exception) { false }
                            }
                            .forEach { file ->
                                try {
                                    totalScanned++
                                    val sf = createSuspiciousFile(file, "APK")
                                    recentApks.add(sf)
                                    if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                                } catch (_: Exception) {}
                            }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning APKs: ${e.message}")
        }

        // 5. Check Android/data for suspicious packages (safely)
        try {
            val androidDataDir = File("/storage/emulated/0/Android/data")
            if (androidDataDir.exists() && androidDataDir.canRead()) {
                val suspiciousPackages = listOf(
                    "com.cheatengine", "com.gameguardian",
                    "eu.chainfire.supersu", "com.noshufou.android.su",
                )
                androidDataDir.listFiles()?.forEach { dir ->
                    try {
                        if (dir.isDirectory && dir.name in suspiciousPackages) {
                            flaggedItems.add(SuspiciousFile(
                                filename = dir.name,
                                path = dir.absolutePath,
                                sizeMb = 0f,
                                lastModified = try { dateFormat.format(Date(dir.lastModified())) } catch (_: Exception) { "Unknown" },
                                source = "Android/data (suspicious)",
                                detections = listOf(DetectionResult(
                                    signatureName = "Suspicious Package: ${dir.name}",
                                    category = "Suspicious App",
                                    severity = "high",
                                    description = "Found data for suspicious app: ${dir.name}",
                                    matchedPatterns = listOf("package:${dir.name}"),
                                    matchCount = 1,
                                    filePath = dir.absolutePath,
                                    confidence = 0.8f
                                ))
                            ))
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            // Android/data not accessible without Shizuku on Android 11+ — expected
            Log.d(TAG, "Android/data not accessible: ${e.message}")
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
        val detections = try {
            CheatDetector.detectCheats(
                content = file.nameWithoutExtension,
                filename = file.name,
                filePath = file.absolutePath
            )
        } catch (_: Exception) { emptyList() }

        return SuspiciousFile(
            filename = file.name,
            path = file.absolutePath,
            sizeMb = try { "%.2f".format(file.length().toFloat() / (1024 * 1024)).toFloat() } catch (_: Exception) { 0f },
            lastModified = try { dateFormat.format(Date(file.lastModified())) } catch (_: Exception) { "Unknown" },
            source = source,
            detections = detections
        )
    }

    private fun scanCacheDirSafe(cacheDir: File, cacheFiles: MutableList<SuspiciousFile>, flaggedItems: MutableList<SuspiciousFile>) {
        try {
            cacheDir.walkTopDown().maxDepth(3)
                .filter {
                    try { it.isFile && it.canRead() }
                    catch (_: Exception) { false }
                }
                .forEach { file ->
                    try {
                        val ext = ".${file.extension.lowercase()}"
                        if (ext in SUSPICIOUS_EXTENSIONS) {
                            val sf = createSuspiciousFile(file, "Cache")
                            cacheFiles.add(sf)
                            if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                        }
                    } catch (_: Exception) {}
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning cache dir: ${e.message}")
        }
    }
}
