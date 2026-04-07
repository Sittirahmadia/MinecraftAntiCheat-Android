package com.sstools.anticheat.scanner

import android.util.Log
import java.io.File

/**
 * Minecraft Launcher Auto-Detection for Android
 * Uses Shizuku shell commands to access /Android/data/ (protected on Android 11+)
 * Falls back to direct File access if Shizuku unavailable
 */
object MinecraftScanner {

    private const val TAG = "MinecraftScanner"

    data class LauncherInfo(
        val name: String,
        val packageName: String,
        val basePaths: List<String>,
        val modsSubdir: String = "mods",
        val logsSubdir: String = "logs",
        val versionsSubdir: String = "versions"
    )

    data class LauncherScanResult(
        val name: String,
        val packageName: String,
        val path: String,
        val found: Boolean,
        val mods: List<ModFileInfo>,
        val logFindings: List<LogFinding>,
        val versions: List<String>,
        val modsDir: String?
    )

    data class ModFileInfo(
        val name: String,
        val path: String,
        val sizeMb: Float
    )

    data class LogFinding(
        val logFile: String,
        val lineNumber: Int,
        val line: String,
        val matchedPattern: String,
        val severity: String
    )

    private val LAUNCHERS = listOf(
        LauncherInfo(
            name = "Zalith Launcher",
            packageName = "com.movtery.zalithlauncher",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/files/.minecraft",
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/files/instance/default/.minecraft",
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/files",
                "/sdcard/Android/data/com.movtery.zalithlauncher/files/.minecraft",
            )
        ),
        LauncherInfo(
            name = "Zalith Launcher 2",
            packageName = "com.movtery.zalithlauncher2",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/files/.minecraft",
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/files/instance/default/.minecraft",
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/files",
                "/sdcard/Android/data/com.movtery.zalithlauncher2/files/.minecraft",
            )
        ),
        LauncherInfo(
            name = "Mojo Launcher",
            packageName = "git.artdeell.mojo",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/git.artdeell.mojo/files/.minecraft",
                "/storage/emulated/0/Android/data/git.artdeell.mojo/files/instance/default/.minecraft",
                "/storage/emulated/0/Android/data/git.artdeell.mojo/files",
                "/sdcard/Android/data/git.artdeell.mojo/files/.minecraft",
            )
        ),
        LauncherInfo(
            name = "PojavLauncher",
            packageName = "net.kdt.pojavlaunch",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/net.kdt.pojavlaunch/files/.minecraft",
                "/storage/emulated/0/games/PojavLauncher/.minecraft",
                "/sdcard/Android/data/net.kdt.pojavlaunch/files/.minecraft",
            )
        ),
        LauncherInfo(
            name = "PojavLauncher (Debug)",
            packageName = "net.kdt.pojavlaunch.debug",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/net.kdt.pojavlaunch.debug/files/.minecraft",
            )
        ),
        LauncherInfo(
            name = "Fold Craft Launcher",
            packageName = "com.tungsten.fcl",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/com.tungsten.fcl/files/.minecraft",
                "/storage/emulated/0/FCL/.minecraft",
            )
        ),
        LauncherInfo(
            name = "HMCL-PE",
            packageName = "com.tungsten.hmclpe",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/com.tungsten.hmclpe/files/.minecraft",
            )
        ),
    )

    private val CHEAT_LOG_PATTERNS = listOf(
        "meteor-client" to "critical", "meteorclient" to "critical", "MeteorClient" to "critical",
        "wurstclient" to "critical", "WurstClient" to "critical",
        "impactclient" to "critical", "ImpactClient" to "critical",
        "aristois" to "critical", "liquidbounce" to "critical", "LiquidBounce" to "critical",
        "futureclient" to "critical", "rusherhack" to "critical", "RusherHack" to "critical",
        "thunderhack" to "critical", "bleachhack" to "critical", "BleachHack" to "critical",
        "coffeeclient" to "critical", "phobos" to "critical", "konas" to "critical",
        "gamesense" to "critical", "salhack" to "critical", "forgehax" to "critical",
        "3arthh4ck" to "critical", "earthhack" to "critical",
        "inertiaclient" to "critical", "sigmaclient" to "critical",
        "Loading module: KillAura" to "critical",
        "Loading module: AutoCrystal" to "critical",
        "Loading module: AimAssist" to "critical",
        "Loading module: Triggerbot" to "critical",
        "Loading module: Scaffold" to "critical",
        "Loading module: Xray" to "critical",
        "Enabled hack:" to "high", "Toggled module:" to "high",
        "clickgui" to "high", "ClickGUI" to "high",
        "Injecting into" to "critical", "injection successful" to "critical",
    )

    /**
     * Detect all installed Minecraft launchers.
     * Uses Shizuku for /Android/data/ access, falls back to direct File.
     */
    fun detectLaunchers(): List<LauncherScanResult> {
        val results = mutableListOf<LauncherScanResult>()
        val useShizuku = ShizukuHelper.isAvailable()
        Log.i(TAG, "detectLaunchers: Shizuku available=$useShizuku")

        for (launcher in LAUNCHERS) {
            try {
                var foundPath: String? = null

                for (basePath in launcher.basePaths) {
                    try {
                        val exists = if (useShizuku) {
                            ShizukuHelper.directoryExists(basePath)
                        } else {
                            val dir = File(basePath)
                            dir.exists() && dir.isDirectory && dir.canRead()
                        }

                        if (exists) {
                            foundPath = basePath
                            Log.i(TAG, "Found ${launcher.name} at $basePath")
                            break
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Error checking $basePath: ${e.message}")
                    }
                }

                // Also check if the package's data dir exists at all (via Shizuku)
                if (foundPath == null && useShizuku) {
                    val dataDir = "/storage/emulated/0/Android/data/${launcher.packageName}"
                    if (ShizukuHelper.directoryExists(dataDir)) {
                        // Search for .minecraft inside
                        val candidates = listOf(
                            "$dataDir/files/.minecraft",
                            "$dataDir/files/instance/default/.minecraft",
                            "$dataDir/files",
                        )
                        for (candidate in candidates) {
                            if (ShizukuHelper.directoryExists(candidate)) {
                                foundPath = candidate
                                Log.i(TAG, "Found ${launcher.name} at $candidate (via Shizuku search)")
                                break
                            }
                        }
                        // If still not found, just use the data dir itself
                        if (foundPath == null) {
                            foundPath = "$dataDir/files"
                            Log.i(TAG, "Using ${launcher.name} base at $foundPath")
                        }
                    }
                }

                if (foundPath != null) {
                    val modsPath = "$foundPath/${launcher.modsSubdir}"
                    val logsPath = "$foundPath/${launcher.logsSubdir}"
                    val versionsPath = "$foundPath/${launcher.versionsSubdir}"

                    val mods = listModFiles(modsPath, useShizuku)
                    val logFindings = scanLogs(logsPath, useShizuku)
                    val versions = listVersions(versionsPath, useShizuku)

                    val modsDirExists = if (useShizuku) {
                        ShizukuHelper.directoryExists(modsPath)
                    } else {
                        try { File(modsPath).exists() } catch (_: Exception) { false }
                    }

                    results.add(LauncherScanResult(
                        name = launcher.name,
                        packageName = launcher.packageName,
                        path = foundPath,
                        found = true,
                        mods = mods,
                        logFindings = logFindings,
                        versions = versions,
                        modsDir = if (modsDirExists) modsPath else null
                    ))
                    Log.i(TAG, "${launcher.name}: ${mods.size} mods, ${logFindings.size} log findings, ${versions.size} versions")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting ${launcher.name}: ${e.message}", e)
            }
        }

        return results
    }

    /**
     * List mod files in a directory — uses Shizuku if available.
     */
    private fun listModFiles(modsPath: String, useShizuku: Boolean): List<ModFileInfo> {
        return try {
            if (useShizuku) {
                val files = ShizukuHelper.listFilesDetailed(modsPath)
                files.filter { !it.isDirectory && (it.name.lowercase().endsWith(".jar") || it.name.lowercase().endsWith(".zip")) }
                    .map { ModFileInfo(it.name, "$modsPath/${it.name}", "%.2f".format(it.size.toFloat() / (1024 * 1024)).toFloat()) }
            } else {
                val dir = File(modsPath)
                if (!dir.exists() || !dir.canRead()) return emptyList()
                dir.listFiles()
                    ?.filter { it.isFile && it.canRead() && it.extension.lowercase() in listOf("jar", "zip") }
                    ?.map { ModFileInfo(it.name, it.absolutePath, "%.2f".format(it.length().toFloat() / (1024 * 1024)).toFloat()) }
                    ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing mods in $modsPath: ${e.message}")
            emptyList()
        }
    }

    /**
     * List version directories — uses Shizuku if available.
     */
    private fun listVersions(versionsPath: String, useShizuku: Boolean): List<String> {
        return try {
            if (useShizuku) {
                val files = ShizukuHelper.listFilesDetailed(versionsPath)
                files.filter { it.isDirectory }.map { it.name }
            } else {
                val dir = File(versionsPath)
                if (!dir.exists() || !dir.canRead()) return emptyList()
                dir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing versions: ${e.message}")
            emptyList()
        }
    }

    /**
     * Scan log files for cheat patterns — uses Shizuku to read logs if needed.
     */
    private fun scanLogs(logsPath: String, useShizuku: Boolean): List<LogFinding> {
        val findings = mutableListOf<LogFinding>()
        try {
            // Get list of log files
            val logFileNames = if (useShizuku) {
                val files = ShizukuHelper.listFiles(logsPath)
                files.filter { it.endsWith(".log") }.take(10)
            } else {
                val dir = File(logsPath)
                if (!dir.exists() || !dir.canRead()) return findings
                dir.listFiles()?.filter { it.isFile && it.extension == "log" }?.map { it.name }?.take(10) ?: return findings
            }

            // Put latest.log first
            val sorted = logFileNames.sortedByDescending { it == "latest.log" }

            for (logName in sorted) {
                try {
                    val logFullPath = "$logsPath/$logName"
                    val content = if (useShizuku) {
                        ShizukuHelper.readTextFile(logFullPath) ?: continue
                    } else {
                        val f = File(logFullPath)
                        if (!f.exists() || !f.canRead()) continue
                        f.readText(Charsets.UTF_8).take(5 * 1024 * 1024)
                    }

                    val contentLower = content.lowercase()
                    for ((pattern, severity) in CHEAT_LOG_PATTERNS) {
                        if (pattern.lowercase() in contentLower) {
                            val lines = content.split("\n")
                            for ((i, line) in lines.withIndex()) {
                                if (pattern.lowercase() in line.lowercase()) {
                                    findings.add(LogFinding(logName, i + 1, line.trim().take(300), pattern, severity))
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading log $logName: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning logs: ${e.message}")
        }
        return findings.distinctBy { it.logFile to it.matchedPattern }
    }

    fun getModFiles(modsDir: File): List<ModFileInfo> {
        return listModFiles(modsDir.absolutePath, ShizukuHelper.isAvailable())
    }
}
