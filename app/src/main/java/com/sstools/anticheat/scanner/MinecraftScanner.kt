package com.sstools.anticheat.scanner

import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Minecraft Launcher Auto-Detection for Android
 * All file operations wrapped in try-catch to prevent crashes
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

    // Known Minecraft launchers on Android
    private val LAUNCHERS = listOf(
        LauncherInfo(
            name = "Zalith Launcher",
            packageName = "com.movtery.zalithlauncher",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/files/.minecraft",
                "/storage/emulated/0/games/com.movtery.zalithlauncher/.minecraft",
                "/sdcard/Android/data/com.movtery.zalithlauncher/files/.minecraft",
            )
        ),
        LauncherInfo(
            name = "Zalith Launcher 2",
            packageName = "com.movtery.zalithlauncher2",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/files/.minecraft",
                "/sdcard/Android/data/com.movtery.zalithlauncher2/files/.minecraft",
            )
        ),
        LauncherInfo(
            name = "Mojo Launcher",
            packageName = "git.artdeell.mojo",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/git.artdeell.mojo/files/.minecraft",
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
                "/sdcard/Android/data/net.kdt.pojavlaunch.debug/files/.minecraft",
            )
        ),
        LauncherInfo(
            name = "Fold Craft Launcher",
            packageName = "com.tungsten.fcl",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/com.tungsten.fcl/files/.minecraft",
                "/storage/emulated/0/FCL/.minecraft",
                "/sdcard/Android/data/com.tungsten.fcl/files/.minecraft",
            )
        ),
        LauncherInfo(
            name = "HMCL-PE",
            packageName = "com.tungsten.hmclpe",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/com.tungsten.hmclpe/files/.minecraft",
                "/sdcard/Android/data/com.tungsten.hmclpe/files/.minecraft",
            )
        ),
    )

    // Cheat patterns for log scanning
    private val CHEAT_LOG_PATTERNS = listOf(
        "meteor-client" to "critical", "meteorclient" to "critical", "MeteorClient" to "critical",
        "wurstclient" to "critical", "WurstClient" to "critical",
        "impactclient" to "critical", "ImpactClient" to "critical",
        "aristois" to "critical", "Aristois" to "critical",
        "liquidbounce" to "critical", "LiquidBounce" to "critical",
        "futureclient" to "critical", "FutureClient" to "critical",
        "rusherhack" to "critical", "RusherHack" to "critical",
        "thunderhack" to "critical", "ThunderHack" to "critical",
        "bleachhack" to "critical", "BleachHack" to "critical",
        "coffeeclient" to "critical", "CoffeeClient" to "critical",
        "phobos" to "critical", "konas" to "critical",
        "gamesense" to "critical", "salhack" to "critical",
        "forgehax" to "critical", "3arthh4ck" to "critical",
        "earthhack" to "critical", "inertiaclient" to "critical",
        "sigmaclient" to "critical",
        "Loading module: KillAura" to "critical",
        "Loading module: AutoCrystal" to "critical",
        "Loading module: AimAssist" to "critical",
        "Loading module: Triggerbot" to "critical",
        "Loading module: Scaffold" to "critical",
        "Loading module: Xray" to "critical",
        "Loading module: Nuker" to "critical",
        "Enabled hack:" to "high",
        "Toggled module:" to "high",
        "clickgui" to "high", "ClickGUI" to "high",
        "Injecting into" to "critical",
        "injection successful" to "critical",
    )

    fun detectLaunchers(): List<LauncherScanResult> {
        val results = mutableListOf<LauncherScanResult>()

        for (launcher in LAUNCHERS) {
            try {
                var foundPath: String? = null
                for (basePath in launcher.basePaths) {
                    try {
                        val dir = File(basePath)
                        if (dir.exists() && dir.isDirectory && dir.canRead()) {
                            foundPath = basePath
                            break
                        }
                    } catch (e: SecurityException) {
                        Log.d(TAG, "Cannot access $basePath: ${e.message}")
                        continue
                    } catch (e: Exception) {
                        Log.d(TAG, "Error checking $basePath: ${e.message}")
                        continue
                    }
                }

                if (foundPath != null) {
                    val baseDir = File(foundPath)

                    // Get mod files safely
                    val mods = safeListMods(File(baseDir, launcher.modsSubdir))

                    // Scan logs safely
                    val logFindings = safeScanLogs(File(baseDir, launcher.logsSubdir))

                    // Get versions safely
                    val versions = safeListVersions(File(baseDir, launcher.versionsSubdir))

                    val modsDir = try {
                        val md = File(baseDir, launcher.modsSubdir)
                        if (md.exists() && md.canRead()) md.absolutePath else null
                    } catch (_: Exception) { null }

                    results.add(LauncherScanResult(
                        name = launcher.name,
                        packageName = launcher.packageName,
                        path = foundPath,
                        found = true,
                        mods = mods,
                        logFindings = logFindings,
                        versions = versions,
                        modsDir = modsDir
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting ${launcher.name}: ${e.message}")
                continue
            }
        }

        return results
    }

    private fun safeListMods(modsDir: File): List<ModFileInfo> {
        return try {
            if (!modsDir.exists() || !modsDir.isDirectory || !modsDir.canRead()) return emptyList()
            modsDir.listFiles()
                ?.filter {
                    try { it.isFile && it.canRead() && it.extension.lowercase() in listOf("jar", "zip") }
                    catch (_: Exception) { false }
                }
                ?.map {
                    try {
                        ModFileInfo(
                            it.name,
                            it.absolutePath,
                            "%.2f".format(it.length().toFloat() / (1024 * 1024)).toFloat()
                        )
                    } catch (_: Exception) {
                        ModFileInfo(it.name, it.absolutePath, 0f)
                    }
                }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error listing mods in ${modsDir.path}: ${e.message}")
            emptyList()
        }
    }

    private fun safeListVersions(versionsDir: File): List<String> {
        return try {
            if (!versionsDir.exists() || !versionsDir.isDirectory || !versionsDir.canRead()) return emptyList()
            versionsDir.listFiles()
                ?.filter { try { it.isDirectory } catch (_: Exception) { false } }
                ?.map { it.name }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error listing versions: ${e.message}")
            emptyList()
        }
    }

    private fun safeScanLogs(logsDir: File): List<LogFinding> {
        val findings = mutableListOf<LogFinding>()
        try {
            if (!logsDir.exists() || !logsDir.isDirectory || !logsDir.canRead()) return findings

            val logFiles = mutableListOf<File>()
            try {
                val latestLog = File(logsDir, "latest.log")
                if (latestLog.exists() && latestLog.canRead()) logFiles.add(latestLog)

                logsDir.listFiles()
                    ?.filter {
                        try { it.isFile && it.canRead() && it.extension == "log" && it.name != "latest.log" }
                        catch (_: Exception) { false }
                    }
                    ?.take(9)
                    ?.let { logFiles.addAll(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing log files: ${e.message}")
            }

            for (logFile in logFiles) {
                try {
                    val content = logFile.readText(Charsets.UTF_8).take(5 * 1024 * 1024)
                    val contentLower = content.lowercase()

                    for ((pattern, severity) in CHEAT_LOG_PATTERNS) {
                        if (pattern.lowercase() in contentLower) {
                            val lines = content.split("\n")
                            for ((i, line) in lines.withIndex()) {
                                if (pattern.lowercase() in line.lowercase()) {
                                    findings.add(LogFinding(
                                        logFile = logFile.name,
                                        lineNumber = i + 1,
                                        line = line.trim().take(300),
                                        matchedPattern = pattern,
                                        severity = severity
                                    ))
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading log ${logFile.name}: ${e.message}")
                    continue
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning logs: ${e.message}")
        }

        return findings.distinctBy { it.logFile to it.matchedPattern }
    }

    fun getModFiles(modsDir: File): List<ModFileInfo> = safeListMods(modsDir)
}
