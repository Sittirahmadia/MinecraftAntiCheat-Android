package com.sstools.anticheat.scanner

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Minecraft Launcher Auto-Detection for Android
 * Supports: Zalith Launcher, Mojo Launcher, PojavLauncher, and more
 */
object MinecraftScanner {

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
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/files/instance/default/.minecraft",
                "/storage/emulated/0/games/com.movtery.zalithlauncher/.minecraft",
                "/sdcard/Android/data/com.movtery.zalithlauncher/files/.minecraft",
            )
        ),
        LauncherInfo(
            name = "Zalith Launcher 2",
            packageName = "com.movtery.zalithlauncher2",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/files/.minecraft",
                "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/files/instance/default/.minecraft",
                "/sdcard/Android/data/com.movtery.zalithlauncher2/files/.minecraft",
            )
        ),
        LauncherInfo(
            name = "Mojo Launcher",
            packageName = "git.artdeell.mojo",
            basePaths = listOf(
                "/storage/emulated/0/Android/data/git.artdeell.mojo/files/.minecraft",
                "/storage/emulated/0/Android/data/git.artdeell.mojo/files/instance/default/.minecraft",
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
        "phobos" to "critical", "Phobos" to "critical",
        "konas" to "critical", "Konas" to "critical",
        "gamesense" to "critical", "GameSense" to "critical",
        "salhack" to "critical", "SalHack" to "critical",
        "forgehax" to "critical", "ForgeHax" to "critical",
        "3arthh4ck" to "critical", "earthhack" to "critical",
        "inertiaclient" to "critical", "sigmaclient" to "critical",
        "Loading module: KillAura" to "critical",
        "Loading module: AutoCrystal" to "critical",
        "Loading module: AimAssist" to "critical",
        "Loading module: Triggerbot" to "critical",
        "Loading module: Scaffold" to "critical",
        "Loading module: Speed" to "high",
        "Loading module: Fly" to "high",
        "Loading module: ESP" to "high",
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
            var foundPath: String? = null
            for (basePath in launcher.basePaths) {
                val dir = File(basePath)
                if (dir.exists() && dir.isDirectory) {
                    foundPath = basePath
                    break
                }
            }

            if (foundPath != null) {
                val baseDir = File(foundPath)
                val modsDir = File(baseDir, launcher.modsSubdir)
                val logsDir = File(baseDir, launcher.logsSubdir)
                val versionsDir = File(baseDir, launcher.versionsSubdir)

                // Get mod files
                val mods = if (modsDir.exists() && modsDir.isDirectory) {
                    modsDir.listFiles()
                        ?.filter { it.isFile && it.extension.lowercase() in listOf("jar", "zip") }
                        ?.map { ModFileInfo(it.name, it.absolutePath, "%.2f".format(it.length().toFloat() / (1024 * 1024)).toFloat()) }
                        ?: emptyList()
                } else emptyList()

                // Scan logs
                val logFindings = scanLogs(logsDir)

                // Get versions
                val versions = if (versionsDir.exists() && versionsDir.isDirectory) {
                    versionsDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                } else emptyList()

                results.add(LauncherScanResult(
                    name = launcher.name,
                    packageName = launcher.packageName,
                    path = foundPath,
                    found = true,
                    mods = mods,
                    logFindings = logFindings,
                    versions = versions,
                    modsDir = if (modsDir.exists()) modsDir.absolutePath else null
                ))
            }
        }

        return results
    }

    private fun scanLogs(logsDir: File): List<LogFinding> {
        val findings = mutableListOf<LogFinding>()
        if (!logsDir.exists() || !logsDir.isDirectory) return findings

        val logFiles = mutableListOf<File>()
        val latestLog = File(logsDir, "latest.log")
        if (latestLog.exists()) logFiles.add(latestLog)

        logsDir.listFiles()?.filter { it.isFile && it.extension == "log" && it != latestLog }
            ?.take(9)?.let { logFiles.addAll(it) }

        for (logFile in logFiles) {
            try {
                val content = logFile.readText(Charsets.UTF_8).take(5 * 1024 * 1024) // Max 5MB
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
            } catch (_: Exception) { continue }
        }

        return findings.distinctBy { it.logFile to it.matchedPattern }
    }

    fun getModFiles(modsDir: File): List<ModFileInfo> {
        if (!modsDir.exists() || !modsDir.isDirectory) return emptyList()
        return modsDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in listOf("jar", "zip") }
            ?.map { ModFileInfo(it.name, it.absolutePath, "%.2f".format(it.length().toFloat() / (1024 * 1024)).toFloat()) }
            ?: emptyList()
    }
}
