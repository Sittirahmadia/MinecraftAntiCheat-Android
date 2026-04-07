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

    // Launchers with instance support — we scan both the base .minecraft
    // AND dynamically discover instances inside /files/instance/*/
    data class LauncherConfig(
        val name: String,
        val packageName: String,
        val dataRoot: String, // e.g. /storage/emulated/0/Android/data/{pkg}/files
        val hasInstances: Boolean = false, // if true, scan /instance/*/.minecraft
        val instanceDir: String = "instance", // subdir inside dataRoot for instances
        val extraPaths: List<String> = emptyList() // additional paths to check
    )

    private val LAUNCHER_CONFIGS = listOf(
        LauncherConfig(
            name = "Zalith Launcher",
            packageName = "com.movtery.zalithlauncher",
            dataRoot = "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/files",
            hasInstances = true,
            instanceDir = "instance",
            extraPaths = listOf(
                "/sdcard/Android/data/com.movtery.zalithlauncher/files/.minecraft",
            )
        ),
        LauncherConfig(
            name = "Zalith Launcher 2",
            packageName = "com.movtery.zalithlauncher2",
            dataRoot = "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/files",
            hasInstances = true,
            instanceDir = "instance",
            extraPaths = listOf(
                "/sdcard/Android/data/com.movtery.zalithlauncher2/files/.minecraft",
            )
        ),
        LauncherConfig(
            name = "Mojo Launcher",
            packageName = "git.artdeell.mojo",
            dataRoot = "/storage/emulated/0/Android/data/git.artdeell.mojo/files",
            hasInstances = true,
            instanceDir = "instance",
            extraPaths = listOf(
                "/sdcard/Android/data/git.artdeell.mojo/files/.minecraft",
            )
        ),
        LauncherConfig(
            name = "PojavLauncher",
            packageName = "net.kdt.pojavlaunch",
            dataRoot = "/storage/emulated/0/Android/data/net.kdt.pojavlaunch/files",
            hasInstances = false,
            extraPaths = listOf(
                "/storage/emulated/0/games/PojavLauncher/.minecraft",
                "/sdcard/Android/data/net.kdt.pojavlaunch/files/.minecraft",
            )
        ),
        LauncherConfig(
            name = "PojavLauncher (Debug)",
            packageName = "net.kdt.pojavlaunch.debug",
            dataRoot = "/storage/emulated/0/Android/data/net.kdt.pojavlaunch.debug/files",
            hasInstances = false,
        ),
        LauncherConfig(
            name = "Fold Craft Launcher",
            packageName = "com.tungsten.fcl",
            dataRoot = "/storage/emulated/0/Android/data/com.tungsten.fcl/files",
            hasInstances = true,
            instanceDir = "instance",
            extraPaths = listOf("/storage/emulated/0/FCL/.minecraft"),
        ),
        LauncherConfig(
            name = "HMCL-PE",
            packageName = "com.tungsten.hmclpe",
            dataRoot = "/storage/emulated/0/Android/data/com.tungsten.hmclpe/files",
            hasInstances = false,
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
     * Discovers instances dynamically for launchers that use them.
     */
    fun detectLaunchers(): List<LauncherScanResult> {
        val results = mutableListOf<LauncherScanResult>()
        val useShizuku = ShizukuHelper.isAvailable()
        Log.i(TAG, "detectLaunchers: Shizuku available=$useShizuku")

        for (config in LAUNCHER_CONFIGS) {
            try {
                val dataRoot = config.dataRoot
                val dataRootExists = checkDirExists(dataRoot, useShizuku)

                if (!dataRootExists) {
                    // Also check extra paths
                    var extraFound = false
                    for (extraPath in config.extraPaths) {
                        if (checkDirExists(extraPath, useShizuku)) {
                            // Found via extra path — scan as single .minecraft
                            val result = scanMinecraftDir(config.name, config.packageName, extraPath, useShizuku)
                            if (result != null) results.add(result)
                            extraFound = true
                            break
                        }
                    }
                    if (!extraFound) {
                        Log.d(TAG, "${config.name}: data root not found at $dataRoot")
                    }
                    continue
                }

                Log.i(TAG, "Found ${config.name} data root: $dataRoot")

                // Collect all .minecraft paths to scan
                val minecraftPaths = mutableListOf<Pair<String, String>>() // path to label

                // 1. Check direct .minecraft in dataRoot
                val directMc = "$dataRoot/.minecraft"
                if (checkDirExists(directMc, useShizuku)) {
                    minecraftPaths.add(directMc to "default")
                    Log.i(TAG, "${config.name}: found direct .minecraft at $directMc")
                }

                // 2. If launcher has instances, discover them
                if (config.hasInstances) {
                    val instancesDir = "$dataRoot/${config.instanceDir}"
                    if (checkDirExists(instancesDir, useShizuku)) {
                        val instanceNames = if (useShizuku) {
                            ShizukuHelper.listFiles(instancesDir)
                        } else {
                            try {
                                File(instancesDir).listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                            } catch (_: Exception) { emptyList() }
                        }

                        Log.i(TAG, "${config.name}: found ${instanceNames.size} instance(s)")
                        for (instanceName in instanceNames) {
                            // Each instance may have .minecraft inside
                            val instanceMc = "$instancesDir/$instanceName/.minecraft"
                            val instanceRoot = "$instancesDir/$instanceName"

                            if (checkDirExists(instanceMc, useShizuku)) {
                                minecraftPaths.add(instanceMc to "instance:$instanceName")
                            } else if (checkDirExists(instanceRoot, useShizuku)) {
                                // Some instances put mods directly in the instance folder
                                val modsInInstance = "$instanceRoot/mods"
                                if (checkDirExists(modsInInstance, useShizuku)) {
                                    minecraftPaths.add(instanceRoot to "instance:$instanceName")
                                }
                            }
                        }
                    }
                }

                // 3. Check extra paths
                for (extraPath in config.extraPaths) {
                    if (checkDirExists(extraPath, useShizuku)) {
                        val alreadyAdded = minecraftPaths.any { it.first == extraPath }
                        if (!alreadyAdded) {
                            minecraftPaths.add(extraPath to "extra")
                        }
                    }
                }

                // If no .minecraft found anywhere, try dataRoot itself
                if (minecraftPaths.isEmpty()) {
                    minecraftPaths.add(dataRoot to "root")
                }

                // Scan all discovered paths and merge into one result
                val allMods = mutableListOf<ModFileInfo>()
                val allLogFindings = mutableListOf<LogFinding>()
                val allVersions = mutableListOf<String>()
                var primaryPath = dataRoot
                var modsDir: String? = null

                for ((mcPath, label) in minecraftPaths) {
                    if (label == "default" || primaryPath == dataRoot) {
                        primaryPath = mcPath
                    }

                    val modsPath = "$mcPath/mods"
                    val logsPath = "$mcPath/logs"
                    val versionsPath = "$mcPath/versions"

                    val mods = listModFiles(modsPath, useShizuku)
                    allMods.addAll(mods)
                    if (mods.isNotEmpty() && modsDir == null) modsDir = modsPath

                    allLogFindings.addAll(scanLogs(logsPath, useShizuku))
                    allVersions.addAll(listVersions(versionsPath, useShizuku))

                    Log.i(TAG, "${config.name} [$label]: ${mods.size} mods at $modsPath")
                }

                results.add(LauncherScanResult(
                    name = config.name,
                    packageName = config.packageName,
                    path = primaryPath,
                    found = true,
                    mods = allMods,
                    logFindings = allLogFindings.distinctBy { it.logFile to it.matchedPattern },
                    versions = allVersions.distinct(),
                    modsDir = modsDir
                ))
                Log.i(TAG, "${config.name} TOTAL: ${allMods.size} mods, ${allLogFindings.size} log findings, ${allVersions.size} versions")

            } catch (e: Exception) {
                Log.e(TAG, "Error detecting ${config.name}: ${e.message}", e)
            }
        }

        return results
    }

    /**
     * Scan a single .minecraft directory as a launcher result.
     */
    private fun scanMinecraftDir(name: String, pkg: String, mcPath: String, useShizuku: Boolean): LauncherScanResult? {
        val mods = listModFiles("$mcPath/mods", useShizuku)
        val logFindings = scanLogs("$mcPath/logs", useShizuku)
        val versions = listVersions("$mcPath/versions", useShizuku)
        val modsDir = if (checkDirExists("$mcPath/mods", useShizuku)) "$mcPath/mods" else null

        return LauncherScanResult(
            name = name, packageName = pkg, path = mcPath, found = true,
            mods = mods, logFindings = logFindings, versions = versions, modsDir = modsDir
        )
    }

    /**
     * Check if a directory exists — Shizuku or direct.
     */
    private fun checkDirExists(path: String, useShizuku: Boolean): Boolean {
        return try {
            if (useShizuku) {
                ShizukuHelper.directoryExists(path)
            } else {
                val dir = File(path)
                dir.exists() && dir.isDirectory && dir.canRead()
            }
        } catch (_: Exception) { false }
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
