package com.sstools.anticheat.scanner

import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Deleted File Scanner — works WITHOUT root on Android 12-16.
 *
 * Non-root strategy:
 *  - Scans /storage/emulated/0/Download (public, always accessible)
 *  - Scans Android/obb and other world-readable dirs
 *  - Scans app external cache dirs (if passed)
 *  - Uses Shizuku for /data/local/tmp and protected dirs if available
 *
 * Root/Shizuku bonus:
 *  - Scans /data/local/tmp
 *  - Scans launcher cache dirs inside /Android/data/
 *  - Uses `find` to locate recently modified cheat files
 */
object DeletedFileScanner {

    private const val TAG = "DeletedFileScanner"

    data class DeletedFileScanResult(
        val tempFiles: List<SuspiciousFile>,
        val downloadFiles: List<SuspiciousFile>,
        val cacheFiles: List<SuspiciousFile>,
        val recentApks: List<SuspiciousFile>,
        val flaggedItems: List<SuspiciousFile>,
        val trashItems: List<SuspiciousFile>,
        val recentlyDeleted: List<SuspiciousFile>,
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
    private val SUSPICIOUS_EXTENSIONS = setOf(".jar", ".apk", ".zip", ".dex", ".so")

    private val CHEAT_FILENAMES = listOf(
        "meteor", "wurst", "impact", "aristois", "liquidbounce",
        "sigma", "rusherhack", "future", "phobos", "konas",
        "gamesense", "earthhack", "salhack", "forgehax", "bleachhack",
        "thunderhack", "coffeeclient", "198macro", "zenithmacro",
        "crystalmacro", "autoclicker", "killaura", "aimassist",
        "triggerbot", "cheatengine", "gameguardian", "injector",
        "xray", "nuker", "scaffold", "inertiaclient",
        "vape", "entropy", "whiteout", "skilled", "juul", "astolfo",
        "novoline", "riseclient", "tenacity", "moonclient", "flux",
        "raven-b", "raven-ni", "itami", "antic", "skilled",
    )

    fun scanDeletedFiles(cacheDir: File? = null): DeletedFileScanResult {
        val tempFiles = mutableListOf<SuspiciousFile>()
        val downloadFiles = mutableListOf<SuspiciousFile>()
        val cacheFiles = mutableListOf<SuspiciousFile>()
        val recentApks = mutableListOf<SuspiciousFile>()
        val flaggedItems = mutableListOf<SuspiciousFile>()
        val trashItems = mutableListOf<SuspiciousFile>()
        val recentlyDeleted = mutableListOf<SuspiciousFile>()
        var totalScanned = 0

        val useShizuku = try { ShizukuHelper.isAvailable() } catch (_: Exception) { false }
        Log.i(TAG, "scanDeletedFiles: Shizuku=$useShizuku")

        // ─── 1. Public Downloads — ALWAYS accessible without root ───
        val dlDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
                ?: "/storage/emulated/0/Download",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Downloads",
        ).distinct()
        for (dir in dlDirs) {
            totalScanned += scanDirPublic(dir, "Downloads", downloadFiles, flaggedItems)
        }

        // ─── 2. Public Documents ───
        totalScanned += scanDirPublic(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.absolutePath
                ?: "/storage/emulated/0/Documents",
            "Documents", downloadFiles, flaggedItems
        )

        // ─── 3. App cache (always accessible) ───
        if (cacheDir != null) {
            totalScanned += scanDirPublic(cacheDir.absolutePath, "App Cache", cacheFiles, flaggedItems)
        }

        // ─── 4. Public trash / recently-deleted (standard Android locations) ───
        val trashDirs = listOf(
            "/storage/emulated/0/.Trash",
            "/storage/emulated/0/.trash",
            "/storage/emulated/0/Trash",
            "/storage/emulated/0/.recently-deleted",
        )
        for (dir in trashDirs) {
            totalScanned += scanDirPublic(dir, "Trash", trashItems, flaggedItems)
        }

        // ─── 5. Scan all recent APKs in Downloads ───
        try {
            val dlRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (dlRoot?.exists() == true) {
                dlRoot.walkTopDown().maxDepth(3)
                    .filter { it.isFile && it.extension.lowercase() == "apk" }
                    .forEach { apk ->
                        totalScanned++
                        val sf = createSuspiciousFile(apk, "APK Download")
                        recentApks.add(sf)
                        if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                    }
            }
        } catch (_: Exception) {}

        // ─── 6. Shizuku-only paths ───
        if (useShizuku) {
            // /data/local/tmp
            totalScanned += scanDirShizuku("/data/local/tmp", "Temp (root)", tempFiles, flaggedItems)

            // Launcher cache dirs
            val launcherCacheDirs = listOf(
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/cache",
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/cache",
                "/storage/emulated/0/Android/data/git.artdeell.mojo/cache",
                "/storage/emulated/0/Android/data/net.kdt.pojavlaunch/cache",
                "/storage/emulated/0/Android/data/com.tungsten.fcl/cache",
            )
            for (dir in launcherCacheDirs) {
                totalScanned += scanDirShizuku(dir, "Launcher Cache", cacheFiles, flaggedItems)
            }

            // Recently modified cheat files via `find`
            totalScanned += scanRecentlyDeletedShizuku(recentlyDeleted, flaggedItems)

            // Check Android/data for suspicious packages
            totalScanned += scanSuspiciousPackagesShizuku(flaggedItems)
        }

        return DeletedFileScanResult(
            tempFiles = tempFiles,
            downloadFiles = downloadFiles,
            cacheFiles = cacheFiles,
            recentApks = recentApks,
            flaggedItems = flaggedItems.distinctBy { it.path }.toMutableList(),
            trashItems = trashItems,
            recentlyDeleted = recentlyDeleted,
            totalScanned = totalScanned
        )
    }

    /** Scan a publicly accessible directory without Shizuku */
    private fun scanDirPublic(
        dirPath: String,
        source: String,
        fileList: MutableList<SuspiciousFile>,
        flaggedItems: MutableList<SuspiciousFile>
    ): Int {
        var scanned = 0
        try {
            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) return 0
            // Try canRead; if not, still attempt listFiles (works with ALL_FILES_ACCESS)
            dir.listFiles()?.forEach { file ->
                try {
                    if (!file.isFile) return@forEach
                    scanned++
                    val ext = ".${file.extension.lowercase()}"
                    if (ext in SUSPICIOUS_EXTENSIONS || isCheatName(file.name)) {
                        val sf = createSuspiciousFile(file, source)
                        fileList.add(sf)
                        if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.d(TAG, "scanDirPublic $dirPath: ${e.message}")
        }
        return scanned
    }

    /** Scan a protected directory using Shizuku */
    private fun scanDirShizuku(
        dirPath: String,
        source: String,
        fileList: MutableList<SuspiciousFile>,
        flaggedItems: MutableList<SuspiciousFile>
    ): Int {
        var scanned = 0
        try {
            val files = ShizukuHelper.listFilesDetailed(dirPath)
            for (fi in files) {
                if (fi.isDirectory) continue
                scanned++
                val ext = ".${fi.name.substringAfterLast('.', "").lowercase()}"
                if (ext in SUSPICIOUS_EXTENSIONS || isCheatName(fi.name)) {
                    val fullPath = "$dirPath/${fi.name}"
                    val sf = SuspiciousFile(
                        filename = fi.name,
                        path = fullPath,
                        sizeMb = bytesToMb(fi.size),
                        lastModified = "Unknown",
                        source = source,
                        detections = CheatDetector.detectCheats(
                            content = fi.name.substringBeforeLast('.'),
                            filename = fi.name,
                            filePath = fullPath
                        )
                    )
                    fileList.add(sf)
                    if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "scanDirShizuku $dirPath: ${e.message}")
        }
        return scanned
    }

    private fun scanRecentlyDeletedShizuku(
        recentlyDeleted: MutableList<SuspiciousFile>,
        flaggedItems: MutableList<SuspiciousFile>
    ): Int {
        var scanned = 0
        try {
            val searchPaths = listOf(
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Downloads",
                "/storage/emulated/0/Documents",
                "/storage/emulated/0/tmp",
                "/storage/emulated/0/.tmp",
            ).joinToString(" ")

            val output = ShizukuHelper.execCommand(
                "find $searchPaths -maxdepth 4 -type f " +
                "\\( -name '*.jar' -o -name '*.apk' -o -name '*.zip' -o -name '*.dex' \\) " +
                "-mtime -30 2>/dev/null"
            )
            val files = output?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

            for (filePath in files) {
                scanned++
                val filename = filePath.substringAfterLast('/')
                if (!isCheatName(filename)) continue
                val sizeOut = ShizukuHelper.execCommand("stat -c '%s' '$filePath' 2>/dev/null")
                val dateOut = ShizukuHelper.execCommand("stat -c '%y' '$filePath' 2>/dev/null")
                val size = sizeOut?.trim()?.toLongOrNull() ?: 0L
                val sf = SuspiciousFile(
                    filename = filename,
                    path = filePath,
                    sizeMb = bytesToMb(size),
                    lastModified = dateOut?.trim()?.take(19) ?: "Unknown",
                    source = "Recently Modified",
                    detections = CheatDetector.detectCheats(
                        filename.substringBeforeLast('.'), filename, filePath
                    )
                )
                recentlyDeleted.add(sf)
                if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
            }

            // Also trash via Shizuku
            val trashOut = ShizukuHelper.execCommand(
                "find /storage/emulated/0/.Trash /storage/emulated/0/.trash " +
                "/storage/emulated/0/.recently-deleted -maxdepth 3 -type f 2>/dev/null"
            )
            val trashFiles = trashOut?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            for (filePath in trashFiles) {
                scanned++
                val filename = filePath.substringAfterLast('/')
                val sf = SuspiciousFile(
                    filename = filename, path = filePath,
                    sizeMb = 0f, lastModified = "Deleted",
                    source = "Trash/Deleted",
                    detections = CheatDetector.detectCheats(
                        filename.substringBeforeLast('.'), filename, filePath
                    )
                )
                recentlyDeleted.add(sf)
                if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanRecentlyDeleted: ${e.message}")
        }
        return scanned
    }

    private fun scanSuspiciousPackagesShizuku(flaggedItems: MutableList<SuspiciousFile>): Int {
        var scanned = 0
        try {
            val suspiciousKeywords = listOf(
                "cheatengine", "gameguardian", "xposed", "lsposed",
                "supersu", "magisk", "gamebooster.cheat"
            )
            val packages = ShizukuHelper.listFiles("/storage/emulated/0/Android/data")
            for (pkg in packages) {
                scanned++
                val pkgLower = pkg.lowercase()
                if (suspiciousKeywords.any { it in pkgLower }) {
                    flaggedItems.add(SuspiciousFile(
                        filename = pkg,
                        path = "/storage/emulated/0/Android/data/$pkg",
                        sizeMb = 0f, lastModified = "Installed",
                        source = "Suspicious App",
                        detections = listOf(DetectionResult(
                            signatureName = "Suspicious Package: $pkg",
                            category = "Suspicious App", severity = "high",
                            description = "App data ditemukan untuk package mencurigakan: $pkg",
                            matchedPatterns = listOf("package:$pkg"),
                            matchCount = 1,
                            filePath = "/storage/emulated/0/Android/data/$pkg",
                            confidence = 0.8f
                        ))
                    ))
                }
            }
        } catch (_: Exception) {}
        return scanned
    }

    private fun isCheatName(filename: String): Boolean {
        val lower = filename.lowercase()
        return CHEAT_FILENAMES.any { it in lower }
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
            sizeMb = try { bytesToMb(file.length()) } catch (_: Exception) { 0f },
            lastModified = try { dateFormat.format(Date(file.lastModified())) } catch (_: Exception) { "Unknown" },
            source = source,
            detections = detections
        )
    }
}
