package com.sstools.anticheat.scanner

import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Deleted File Scanner for Android
 * Uses Shizuku to scan protected directories for traces of deleted cheat files.
 * Scans: Downloads, Temp, Cache, Trash, Recent APKs, Android/data leftovers,
 * and recently modified/deleted files across accessible storage.
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
    private val SUSPICIOUS_EXTENSIONS = setOf(".jar", ".apk", ".zip", ".dex", ".so", ".exe", ".dll")

    // Cheat-related filenames to look for in deleted/recent files
    private val CHEAT_FILENAMES = listOf(
        "meteor", "wurst", "impact", "aristois", "liquidbounce",
        "sigma", "rusherhack", "future", "phobos", "konas",
        "gamesense", "earthhack", "salhack", "forgehax", "bleachhack",
        "thunderhack", "coffeeclient", "198macro", "zenithmacro",
        "crystalmacro", "autoclicker", "killaura", "aimassist",
        "triggerbot", "cheatengine", "gameguardian", "injector",
        "xray", "nuker", "scaffold",
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

        val useShizuku = ShizukuHelper.isAvailable()
        Log.i(TAG, "scanDeletedFiles: Shizuku=$useShizuku")

        // 1. Scan Downloads directory
        totalScanned += scanDirectorySafe(
            "/storage/emulated/0/Download", "Downloads", downloadFiles, flaggedItems, useShizuku
        )
        totalScanned += scanDirectorySafe(
            "/storage/emulated/0/Downloads", "Downloads", downloadFiles, flaggedItems, useShizuku
        )

        // 2. Scan temp directories
        val tempDirs = listOf(
            "/storage/emulated/0/.tmp",
            "/storage/emulated/0/tmp",
            "/data/local/tmp",
        )
        for (tmpDir in tempDirs) {
            totalScanned += scanDirectorySafe(tmpDir, "Temp", tempFiles, flaggedItems, useShizuku)
        }

        // 3. Scan app cache
        if (cacheDir != null) {
            totalScanned += scanDirectorySafe(cacheDir.absolutePath, "Cache", cacheFiles, flaggedItems, false)
        }

        // 4. Scan Trash / .Trash / .recently-deleted (Android file managers)
        val trashDirs = listOf(
            "/storage/emulated/0/.Trash",
            "/storage/emulated/0/.trash",
            "/storage/emulated/0/Trash",
            "/storage/emulated/0/.recently-deleted",
            "/storage/emulated/0/DCIM/.thumbnails",
        )
        for (trashDir in trashDirs) {
            totalScanned += scanDirectorySafe(trashDir, "Trash", trashItems, flaggedItems, useShizuku)
        }

        // 5. Scan for APKs in Downloads (cheat client APKs)
        try {
            val apkFiles = if (useShizuku) {
                val output = ShizukuHelper.execCommand(
                    "find /storage/emulated/0/Download /storage/emulated/0/Downloads -maxdepth 3 -name '*.apk' -type f 2>/dev/null"
                )
                output?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            } else {
                val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (dlDir?.exists() == true) {
                    dlDir.walkTopDown().maxDepth(3)
                        .filter { it.isFile && it.extension.lowercase() == "apk" }
                        .map { it.absolutePath }
                        .toList()
                } else emptyList()
            }

            for (apkPath in apkFiles) {
                totalScanned++
                val sf = createSuspiciousFileFromPath(apkPath, "APK", useShizuku)
                recentApks.add(sf)
                if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning APKs: ${e.message}")
        }

        // 6. Check Android/data for suspicious packages (via Shizuku)
        if (useShizuku) {
            totalScanned += scanSuspiciousPackages(flaggedItems)
        }

        // 7. Use Shizuku to find recently deleted/modified cheat files
        if (useShizuku) {
            totalScanned += scanRecentlyDeletedViaShizuku(recentlyDeleted, flaggedItems)
        }

        // 8. Scan Minecraft launcher temp/cache for deleted cheats (via Shizuku)
        if (useShizuku) {
            val launcherCacheDirs = listOf(
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/cache",
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/cache",
                "/storage/emulated/0/Android/data/git.artdeell.mojo/cache",
                "/storage/emulated/0/Android/data/net.kdt.pojavlaunch/cache",
                "/storage/emulated/0/Android/data/com.tungsten.fcl/cache",
            )
            for (dir in launcherCacheDirs) {
                totalScanned += scanDirectorySafe(dir, "Launcher Cache", cacheFiles, flaggedItems, true)
            }
        }

        return DeletedFileScanResult(
            tempFiles = tempFiles,
            downloadFiles = downloadFiles,
            cacheFiles = cacheFiles,
            recentApks = recentApks,
            flaggedItems = flaggedItems,
            trashItems = trashItems,
            recentlyDeleted = recentlyDeleted,
            totalScanned = totalScanned
        )
    }

    /**
     * Scan a directory for suspicious files. Uses Shizuku if needed.
     */
    private fun scanDirectorySafe(
        dirPath: String,
        source: String,
        fileList: MutableList<SuspiciousFile>,
        flaggedItems: MutableList<SuspiciousFile>,
        useShizuku: Boolean
    ): Int {
        var scanned = 0
        try {
            if (useShizuku) {
                val files = ShizukuHelper.listFilesDetailed(dirPath)
                for (fileInfo in files) {
                    if (fileInfo.isDirectory) continue
                    scanned++
                    val ext = ".${fileInfo.name.substringAfterLast('.', "").lowercase()}"
                    if (ext in SUSPICIOUS_EXTENSIONS || isCheatRelatedName(fileInfo.name)) {
                        val fullPath = "$dirPath/${fileInfo.name}"
                        val sf = SuspiciousFile(
                            filename = fileInfo.name,
                            path = fullPath,
                            sizeMb = try { "%.2f".format(fileInfo.size.toFloat() / (1024 * 1024)).toFloat() } catch (_: Exception) { 0f },
                            lastModified = "Unknown",
                            source = source,
                            detections = CheatDetector.detectCheats(
                                content = fileInfo.name.substringBeforeLast('.'),
                                filename = fileInfo.name,
                                filePath = fullPath
                            )
                        )
                        fileList.add(sf)
                        if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                    }
                }
            } else {
                val dir = File(dirPath)
                if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return 0
                dir.listFiles()?.forEach { file ->
                    try {
                        if (file.isFile && file.canRead()) {
                            scanned++
                            val ext = ".${file.extension.lowercase()}"
                            if (ext in SUSPICIOUS_EXTENSIONS || isCheatRelatedName(file.name)) {
                                val sf = createSuspiciousFile(file, source)
                                fileList.add(sf)
                                if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error scanning $dirPath: ${e.message}")
        }
        return scanned
    }

    /**
     * Check if filename matches known cheat names
     */
    private fun isCheatRelatedName(filename: String): Boolean {
        val lower = filename.lowercase()
        return CHEAT_FILENAMES.any { it in lower }
    }

    /**
     * Use Shizuku `find` to locate recently modified suspicious files
     * that may have been downloaded/used and then deleted.
     * Checks modification time within the last 30 days.
     */
    private fun scanRecentlyDeletedViaShizuku(
        recentlyDeleted: MutableList<SuspiciousFile>,
        flaggedItems: MutableList<SuspiciousFile>
    ): Int {
        var scanned = 0
        try {
            // Find .jar, .apk, .zip files modified in last 30 days in accessible locations
            val searchPaths = "/storage/emulated/0/Download /storage/emulated/0/Downloads /storage/emulated/0/Documents /storage/emulated/0/tmp /storage/emulated/0/.tmp"
            val output = ShizukuHelper.execCommand(
                "find $searchPaths -maxdepth 4 -type f \\( -name '*.jar' -o -name '*.apk' -o -name '*.zip' -o -name '*.dex' \\) -mtime -30 2>/dev/null"
            )
            val files = output?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

            for (filePath in files) {
                scanned++
                val filename = filePath.substringAfterLast('/')
                if (isCheatRelatedName(filename)) {
                    // Get file details
                    val sizeOutput = ShizukuHelper.execCommand("stat -c '%s' '$filePath' 2>/dev/null")
                    val dateOutput = ShizukuHelper.execCommand("stat -c '%y' '$filePath' 2>/dev/null")
                    val size = sizeOutput?.trim()?.toLongOrNull() ?: 0L

                    val sf = SuspiciousFile(
                        filename = filename,
                        path = filePath,
                        sizeMb = try { "%.2f".format(size.toFloat() / (1024 * 1024)).toFloat() } catch (_: Exception) { 0f },
                        lastModified = dateOutput?.trim()?.take(19) ?: "Unknown",
                        source = "Recent File",
                        detections = CheatDetector.detectCheats(
                            content = filename.substringBeforeLast('.'),
                            filename = filename,
                            filePath = filePath
                        )
                    )
                    recentlyDeleted.add(sf)
                    if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
                }
            }

            // Also check /sdcard/.Trash and similar locations via Shizuku
            val trashOutput = ShizukuHelper.execCommand(
                "find /storage/emulated/0/.Trash /storage/emulated/0/.trash /storage/emulated/0/.recently-deleted -maxdepth 3 -type f 2>/dev/null"
            )
            val trashFiles = trashOutput?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            for (filePath in trashFiles) {
                scanned++
                val filename = filePath.substringAfterLast('/')
                val sf = SuspiciousFile(
                    filename = filename,
                    path = filePath,
                    sizeMb = 0f,
                    lastModified = "Deleted",
                    source = "Trash/Deleted",
                    detections = CheatDetector.detectCheats(
                        content = filename.substringBeforeLast('.'),
                        filename = filename,
                        filePath = filePath
                    )
                )
                recentlyDeleted.add(sf)
                if (sf.detections.isNotEmpty()) flaggedItems.add(sf)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in Shizuku deleted file scan: ${e.message}")
        }
        return scanned
    }

    /**
     * Check Android/data for suspicious app packages via Shizuku
     */
    private fun scanSuspiciousPackages(flaggedItems: MutableList<SuspiciousFile>): Int {
        var scanned = 0
        try {
            val suspiciousPackages = listOf(
                "com.cheatengine", "com.gameguardian.xx",
                "eu.chainfire.supersu", "com.noshufou.android.su",
                "com.koushikdutta.superuser",
                "catch_.me.if" // GameGuardian common package
            )

            // List all packages in Android/data via Shizuku
            val packages = ShizukuHelper.listFiles("/storage/emulated/0/Android/data")
            for (pkg in packages) {
                scanned++
                val pkgLower = pkg.lowercase()
                val isSuspicious = suspiciousPackages.any { it in pkgLower } ||
                    pkgLower.contains("gameguard") ||
                    pkgLower.contains("cheatengine") ||
                    pkgLower.contains("xposed") ||
                    pkgLower.contains("lsposed")

                if (isSuspicious) {
                    flaggedItems.add(SuspiciousFile(
                        filename = pkg,
                        path = "/storage/emulated/0/Android/data/$pkg",
                        sizeMb = 0f,
                        lastModified = "Installed",
                        source = "Suspicious App",
                        detections = listOf(DetectionResult(
                            signatureName = "Suspicious Package: $pkg",
                            category = "Suspicious App",
                            severity = "high",
                            description = "Found data for suspicious application: $pkg",
                            matchedPatterns = listOf("package:$pkg"),
                            matchCount = 1,
                            filePath = "/storage/emulated/0/Android/data/$pkg",
                            confidence = 0.8f
                        ))
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning suspicious packages: ${e.message}")
        }
        return scanned
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

    private fun createSuspiciousFileFromPath(path: String, source: String, useShizuku: Boolean): SuspiciousFile {
        val filename = path.substringAfterLast('/')
        return if (!useShizuku) {
            val file = File(path)
            createSuspiciousFile(file, source)
        } else {
            SuspiciousFile(
                filename = filename,
                path = path,
                sizeMb = 0f,
                lastModified = "Unknown",
                source = source,
                detections = CheatDetector.detectCheats(
                    content = filename.substringBeforeLast('.'),
                    filename = filename,
                    filePath = path
                )
            )
        }
    }
}
