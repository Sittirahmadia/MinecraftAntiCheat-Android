package com.sstools.anticheat

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sstools.anticheat.scanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ScanState(
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val currentTask: String = "",
    // Minecraft scan results
    val safResult: SafScanner.SafScanResult? = null,
    val launcherResults: List<MinecraftScanner.LauncherScanResult> = emptyList(),
    val modScanResults: List<JarInspector.JarScanResult> = emptyList(),
    // Deleted files
    val deletedFileScanResult: DeletedFileScanner.DeletedFileScanResult? = null,
    // Chrome
    val chromeScanResult: ChromeScanner.ChromeScanResult? = null,
    // Overall
    val totalFlags: Int = 0,
    val verdict: String = "",
    val scanComplete: Boolean = false,
    val error: String? = null,
    // Current screen
    val currentScreen: String = "home" // home, minecraft, deleted, history
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    fun setScreen(screen: String) {
        _scanState.value = _scanState.value.copy(currentScreen = screen)
    }

    /**
     * Called when user selects a folder via SAF folder picker.
     * This is the MAIN scanning method — works on Android 12-16.
     */
    fun onFolderSelected(context: Context, uri: Uri) {
        if (_scanState.value.isScanning) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _scanState.value = _scanState.value.copy(
                    isScanning = true, progress = 0f,
                    currentTask = "Opening selected folder...",
                    currentScreen = "minecraft",
                    safResult = null, modScanResults = emptyList(),
                    error = null, scanComplete = false
                )

                val result = SafScanner.scanFolder(context, uri) { progress, task ->
                    _scanState.value = _scanState.value.copy(progress = progress, currentTask = task)
                }

                val totalFlags = result.jarResults.count { it.flagged } + result.logFindings.size

                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    progress = 1f,
                    currentTask = "Scan complete!",
                    safResult = result,
                    modScanResults = result.jarResults,
                    totalFlags = totalFlags,
                    verdict = if (totalFlags == 0) "CLEAN" else "FLAGGED",
                    scanComplete = true,
                    error = result.error
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "SAF scan error: ${e.message}", e)
                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    error = "Scan error: ${e.message}",
                    currentTask = "Error",
                    scanComplete = true,
                    verdict = "ERROR"
                )
            }
        }
    }

    /**
     * Auto-scan: tries direct file access for known launcher paths.
     * Works if "All Files Access" is granted on Android 11+.
     * Falls back to prompting user for SAF if nothing found.
     */
    fun runAutoScan() {
        if (_scanState.value.isScanning) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _scanState.value = _scanState.value.copy(
                    isScanning = true, progress = 0f,
                    currentTask = "Auto-detecting Minecraft launchers...",
                    currentScreen = "minecraft",
                    launcherResults = emptyList(), modScanResults = emptyList(),
                    safResult = null, error = null, scanComplete = false
                )

                // Try direct file access for known paths
                val knownPaths = listOf(
                    "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/files/.minecraft",
                    "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/files/.minecraft",
                    "/storage/emulated/0/Android/data/git.artdeell.mojo/files/.minecraft",
                    "/storage/emulated/0/Android/data/net.kdt.pojavlaunch/files/.minecraft",
                    "/storage/emulated/0/Android/data/com.tungsten.fcl/files/.minecraft",
                    "/storage/emulated/0/Android/data/com.tungsten.hmclpe/files/.minecraft",
                    "/storage/emulated/0/games/PojavLauncher/.minecraft",
                    "/storage/emulated/0/FCL/.minecraft",
                )

                val foundPaths = mutableListOf<String>()
                for (path in knownPaths) {
                    try {
                        val dir = File(path)
                        if (dir.exists() && dir.isDirectory && dir.canRead()) {
                            val modsDir = File(dir, "mods")
                            if (modsDir.exists() || File(dir, "logs").exists()) {
                                foundPaths.add(path)
                                Log.i("MainViewModel", "Found accessible launcher at: $path")
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Also check instance dirs
                val instanceBaseDirs = listOf(
                    "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/files/instance",
                    "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/files/instance",
                    "/storage/emulated/0/Android/data/git.artdeell.mojo/files/instance",
                )
                for (instanceBase in instanceBaseDirs) {
                    try {
                        val dir = File(instanceBase)
                        if (dir.exists() && dir.isDirectory && dir.canRead()) {
                            dir.listFiles()?.filter { it.isDirectory }?.forEach { instance ->
                                val mcDir = File(instance, ".minecraft")
                                if (mcDir.exists() && mcDir.isDirectory && mcDir.canRead()) {
                                    foundPaths.add(mcDir.absolutePath)
                                    Log.i("MainViewModel", "Found instance: ${mcDir.absolutePath}")
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (foundPaths.isEmpty()) {
                    _scanState.value = _scanState.value.copy(
                        isScanning = false,
                        progress = 0f,
                        currentTask = "",
                        error = "No launchers found via direct access. Android 12+ blocks /Android/data/. " +
                                "Please use 'Select Minecraft Folder' button to manually pick your .minecraft directory.",
                        scanComplete = true,
                        verdict = "NO_ACCESS"
                    )
                    return@launch
                }

                updateProgress(0.1f, "Found ${foundPaths.size} launcher path(s)")

                // Scan all found paths
                val allModResults = mutableListOf<JarInspector.JarScanResult>()
                val allLaunchers = mutableListOf<MinecraftScanner.LauncherScanResult>()
                var totalMods = 0
                var scannedMods = 0

                // Count total mods first
                for (path in foundPaths) {
                    val modsDir = File(path, "mods")
                    if (modsDir.exists() && modsDir.canRead()) {
                        totalMods += modsDir.listFiles()?.count {
                            it.isFile && it.extension.lowercase() in listOf("jar", "zip")
                        } ?: 0
                    }
                }

                for (path in foundPaths) {
                    val launcherName = guessLauncherName(path)
                    val modsDir = File(path, "mods")
                    val logsDir = File(path, "logs")
                    val versionsDir = File(path, "versions")

                    val mods = mutableListOf<MinecraftScanner.ModFileInfo>()
                    val logFindings = mutableListOf<MinecraftScanner.LogFinding>()
                    val versions = mutableListOf<String>()

                    // Scan mods
                    if (modsDir.exists() && modsDir.canRead()) {
                        modsDir.listFiles()?.filter {
                            it.isFile && it.canRead() && it.extension.lowercase() in listOf("jar", "zip")
                        }?.forEach { modFile ->
                            mods.add(MinecraftScanner.ModFileInfo(
                                modFile.name, modFile.absolutePath,
                                "%.2f".format(modFile.length().toFloat() / (1024 * 1024)).toFloat()
                            ))
                            try {
                                val result = JarInspector.inspectJar(modFile)
                                allModResults.add(result)
                            } catch (e: Exception) {
                                Log.e("MainViewModel", "Mod scan error: ${e.message}")
                            }
                            scannedMods++
                            if (totalMods > 0) {
                                updateProgress(0.1f + (0.7f * scannedMods / totalMods),
                                    "Scanning: ${modFile.name} ($scannedMods/$totalMods)")
                            }
                        }
                    }

                    // Scan logs
                    if (logsDir.exists() && logsDir.canRead()) {
                        scanLogsDirectly(logsDir, logFindings)
                    }

                    // Get versions
                    if (versionsDir.exists() && versionsDir.canRead()) {
                        versionsDir.listFiles()?.filter { it.isDirectory }?.forEach {
                            versions.add(it.name)
                        }
                    }

                    allLaunchers.add(MinecraftScanner.LauncherScanResult(
                        name = launcherName, packageName = "",
                        path = path, found = true, mods = mods,
                        logFindings = logFindings, versions = versions,
                        modsDir = if (modsDir.exists()) modsDir.absolutePath else null
                    ))
                }

                val totalFlags = allModResults.count { it.flagged } +
                    allLaunchers.sumOf { it.logFindings.size }

                _scanState.value = _scanState.value.copy(
                    isScanning = false, progress = 1f,
                    currentTask = "Scan complete!",
                    launcherResults = allLaunchers,
                    modScanResults = allModResults,
                    totalFlags = totalFlags,
                    verdict = if (totalFlags == 0) "CLEAN" else "FLAGGED",
                    scanComplete = true
                )

            } catch (e: Exception) {
                Log.e("MainViewModel", "Auto scan error: ${e.message}", e)
                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    error = "Auto scan error: ${e.message}. Try 'Select Minecraft Folder' instead.",
                    scanComplete = true, verdict = "ERROR"
                )
            }
        }
    }

    /**
     * Scan deleted files
     */
    fun runDeletedFileScan() {
        if (_scanState.value.isScanning) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _scanState.value = _scanState.value.copy(
                    isScanning = true, progress = 0f,
                    currentTask = "Scanning for deleted/suspicious files...",
                    currentScreen = "deleted",
                    deletedFileScanResult = null, error = null, scanComplete = false
                )

                updateProgress(0.1f, "Scanning Downloads...")
                val result = DeletedFileScanner.scanDeletedFiles(getApplication<Application>().cacheDir)

                _scanState.value = _scanState.value.copy(
                    isScanning = false, progress = 1f,
                    currentTask = "Deleted file scan complete!",
                    deletedFileScanResult = result,
                    totalFlags = result.flaggedItems.size,
                    verdict = if (result.flaggedItems.isEmpty()) "CLEAN" else "FLAGGED",
                    scanComplete = true
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Deleted scan error: ${e.message}", e)
                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    error = "Error: ${e.message}",
                    scanComplete = true, verdict = "ERROR"
                )
            }
        }
    }

    /**
     * Scan Chrome history
     */
    fun runHistoryScan() {
        if (_scanState.value.isScanning) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _scanState.value = _scanState.value.copy(
                    isScanning = true, progress = 0f,
                    currentTask = "Scanning browser history...",
                    currentScreen = "history",
                    chromeScanResult = null, error = null, scanComplete = false
                )

                updateProgress(0.3f, "Accessing Chrome database...")
                val result = ChromeScanner.scanChromeHistory(getApplication())

                _scanState.value = _scanState.value.copy(
                    isScanning = false, progress = 1f,
                    currentTask = "History scan complete!",
                    chromeScanResult = result,
                    totalFlags = result.suspiciousUrls.size + result.suspiciousDownloads.size,
                    verdict = if (result.suspiciousUrls.isEmpty() && result.suspiciousDownloads.isEmpty()) "CLEAN" else "FLAGGED",
                    scanComplete = true,
                    error = result.error
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "History scan error: ${e.message}", e)
                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    error = "Error: ${e.message}",
                    scanComplete = true, verdict = "ERROR"
                )
            }
        }
    }

    /**
     * Full scan: Minecraft + Deleted + History
     */
    fun runFullScan() {
        if (_scanState.value.isScanning) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _scanState.value = _scanState.value.copy(
                    isScanning = true, progress = 0f,
                    currentTask = "Starting full scan...",
                    currentScreen = "minecraft",
                    launcherResults = emptyList(), modScanResults = emptyList(),
                    deletedFileScanResult = null, chromeScanResult = null,
                    safResult = null, error = null, scanComplete = false
                )

                // 1. Deleted files (always works)
                updateProgress(0.05f, "Scanning deleted/suspicious files...")
                val deletedResult = try {
                    DeletedFileScanner.scanDeletedFiles(getApplication<Application>().cacheDir)
                } catch (e: Exception) {
                    DeletedFileScanner.DeletedFileScanResult(
                        emptyList(), emptyList(), emptyList(), emptyList(),
                        emptyList(), emptyList(), emptyList(), 0
                    )
                }
                _scanState.value = _scanState.value.copy(deletedFileScanResult = deletedResult)

                // 2. Chrome history
                updateProgress(0.2f, "Scanning browser history...")
                val chromeResult = try {
                    ChromeScanner.scanChromeHistory(getApplication())
                } catch (e: Exception) {
                    ChromeScanner.ChromeScanResult(0, emptyList(), emptyList(), 0, 0, e.message)
                }
                _scanState.value = _scanState.value.copy(chromeScanResult = chromeResult)

                // 3. Try auto-detect Minecraft launchers
                updateProgress(0.3f, "Auto-detecting Minecraft launchers...")
                val knownPaths = listOf(
                    "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/files/.minecraft",
                    "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/files/.minecraft",
                    "/storage/emulated/0/Android/data/git.artdeell.mojo/files/.minecraft",
                    "/storage/emulated/0/Android/data/net.kdt.pojavlaunch/files/.minecraft",
                    "/storage/emulated/0/Android/data/com.tungsten.fcl/files/.minecraft",
                    "/storage/emulated/0/games/PojavLauncher/.minecraft",
                    "/storage/emulated/0/FCL/.minecraft",
                )

                val allModResults = mutableListOf<JarInspector.JarScanResult>()
                val allLaunchers = mutableListOf<MinecraftScanner.LauncherScanResult>()
                var mcNote: String? = null

                for (path in knownPaths) {
                    try {
                        val dir = File(path)
                        if (dir.exists() && dir.isDirectory && dir.canRead()) {
                            val launcherName = guessLauncherName(path)
                            val modsDir = File(dir, "mods")
                            val mods = mutableListOf<MinecraftScanner.ModFileInfo>()
                            val logFindings = mutableListOf<MinecraftScanner.LogFinding>()

                            if (modsDir.exists() && modsDir.canRead()) {
                                modsDir.listFiles()?.filter {
                                    it.isFile && it.canRead() && it.extension.lowercase() in listOf("jar", "zip")
                                }?.forEach { modFile ->
                                    mods.add(MinecraftScanner.ModFileInfo(
                                        modFile.name, modFile.absolutePath,
                                        "%.2f".format(modFile.length().toFloat() / (1024*1024)).toFloat()
                                    ))
                                    try { allModResults.add(JarInspector.inspectJar(modFile)) } catch (_: Exception) {}
                                }
                            }

                            val logsDir = File(dir, "logs")
                            if (logsDir.exists() && logsDir.canRead()) {
                                scanLogsDirectly(logsDir, logFindings)
                            }

                            val versions = try {
                                File(dir, "versions").listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                            } catch (_: Exception) { emptyList() }

                            allLaunchers.add(MinecraftScanner.LauncherScanResult(
                                name = launcherName, packageName = "", path = path,
                                found = true, mods = mods, logFindings = logFindings,
                                versions = versions, modsDir = modsDir.absolutePath
                            ))
                        }
                    } catch (_: Exception) {}
                }

                if (allLaunchers.isEmpty()) {
                    mcNote = "No Minecraft launchers found via auto-detect. Use 'Select Minecraft Folder' for /Android/data/ access."
                }

                _scanState.value = _scanState.value.copy(launcherResults = allLaunchers, modScanResults = allModResults)

                // Calculate verdict
                var totalFlags = 0
                totalFlags += allModResults.count { it.flagged }
                totalFlags += allLaunchers.sumOf { it.logFindings.size }
                totalFlags += deletedResult.flaggedItems.size
                totalFlags += chromeResult.suspiciousUrls.size + chromeResult.suspiciousDownloads.size

                _scanState.value = _scanState.value.copy(
                    isScanning = false, progress = 1f,
                    currentTask = "Full scan complete!",
                    totalFlags = totalFlags,
                    verdict = if (totalFlags == 0) "CLEAN" else "FLAGGED",
                    scanComplete = true,
                    error = mcNote
                )

            } catch (e: Exception) {
                Log.e("MainViewModel", "Full scan error: ${e.message}", e)
                _scanState.value = _scanState.value.copy(
                    isScanning = false, error = "Error: ${e.message}",
                    scanComplete = true, verdict = "ERROR"
                )
            }
        }
    }

    fun resetScan() {
        _scanState.value = ScanState()
    }

    private fun updateProgress(progress: Float, task: String) {
        _scanState.value = _scanState.value.copy(progress = progress, currentTask = task)
    }

    private fun guessLauncherName(path: String): String = when {
        "zalithlauncher2" in path -> "Zalith Launcher 2"
        "zalithlauncher" in path -> "Zalith Launcher"
        "artdeell.mojo" in path || "mojo" in path.lowercase() -> "Mojo Launcher"
        "pojavlaunch" in path || "PojavLauncher" in path -> "PojavLauncher"
        "tungsten.fcl" in path || "FCL" in path -> "Fold Craft Launcher"
        "hmclpe" in path -> "HMCL-PE"
        else -> "Minecraft"
    }

    private fun scanLogsDirectly(logsDir: File, findings: MutableList<MinecraftScanner.LogFinding>) {
        val patterns = listOf(
            "meteor-client" to "critical", "meteorclient" to "critical",
            "wurstclient" to "critical", "impactclient" to "critical",
            "aristois" to "critical", "liquidbounce" to "critical",
            "futureclient" to "critical", "rusherhack" to "critical",
            "thunderhack" to "critical", "bleachhack" to "critical",
            "coffeeclient" to "critical", "phobos" to "critical",
            "konas" to "critical", "gamesense" to "critical",
            "salhack" to "critical", "forgehax" to "critical",
            "3arthh4ck" to "critical", "earthhack" to "critical",
            "sigmaclient" to "critical",
            "Loading module: KillAura" to "critical",
            "Loading module: AutoCrystal" to "critical",
            "Loading module: AimAssist" to "critical",
            "clickgui" to "high", "ClickGUI" to "high",
            "Injecting into" to "critical",
        )

        try {
            val logFiles = logsDir.listFiles()?.filter { it.isFile && it.extension == "log" }
                ?.sortedByDescending { it.name == "latest.log" }
                ?.take(10) ?: return

            for (logFile in logFiles) {
                try {
                    val content = logFile.readText(Charsets.UTF_8).take(5 * 1024 * 1024)
                    val contentLower = content.lowercase()
                    for ((pattern, severity) in patterns) {
                        if (pattern.lowercase() in contentLower) {
                            content.split("\n").forEachIndexed { i, line ->
                                if (pattern.lowercase() in line.lowercase()) {
                                    findings.add(MinecraftScanner.LogFinding(
                                        logFile.name, i + 1, line.trim().take(300), pattern, severity
                                    ))
                                    return@forEachIndexed
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
