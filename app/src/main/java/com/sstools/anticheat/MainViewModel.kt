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

// Max file size to inspect in MB
const val MAX_JAR_SIZE_MB = 20000f

data class ScanState(
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val currentTask: String = "",
    val safResult: SafScanner.SafScanResult? = null,
    val launcherResults: List<MinecraftScanner.LauncherScanResult> = emptyList(),
    val modScanResults: List<JarInspector.JarScanResult> = emptyList(),
    val deletedFileScanResult: DeletedFileScanner.DeletedFileScanResult? = null,
    val chromeScanResult: ChromeScanner.ChromeScanResult? = null,
    val totalFlags: Int = 0,
    val verdict: String = "",
    val scanComplete: Boolean = false,
    val error: String? = null,
    val currentScreen: String = "home",
    val shizukuAvailable: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    init {
        val shizukuAvail = try { ShizukuHelper.isAvailable() } catch (_: Exception) { false }
        _scanState.value = _scanState.value.copy(shizukuAvailable = shizukuAvail)
    }

    fun setScreen(screen: String) {
        _scanState.value = _scanState.value.copy(currentScreen = screen)
    }

    /** SAF folder scan — ALWAYS works on Android 12-16 non-root */
    fun onFolderSelected(context: Context, uri: Uri) {
        if (_scanState.value.isScanning) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _scanState.value = _scanState.value.copy(
                    isScanning = true, progress = 0f,
                    currentTask = "Membuka folder...", currentScreen = "minecraft",
                    safResult = null, modScanResults = emptyList(),
                    error = null, scanComplete = false
                )
                val result = SafScanner.scanFolder(context, uri) { progress, task ->
                    _scanState.value = _scanState.value.copy(progress = progress, currentTask = task)
                }
                val totalFlags = result.jarResults.count { it.flagged } + result.logFindings.size
                _scanState.value = _scanState.value.copy(
                    isScanning = false, progress = 1f, currentTask = "Scan selesai!",
                    safResult = result, modScanResults = result.jarResults,
                    totalFlags = totalFlags,
                    verdict = if (totalFlags == 0) "CLEAN" else "FLAGGED",
                    scanComplete = true, error = result.error
                )
            } catch (e: Exception) {
                _scanState.value = _scanState.value.copy(
                    isScanning = false, error = "Scan error: ${e.message}",
                    currentTask = "Error", scanComplete = true, verdict = "ERROR"
                )
            }
        }
    }

    /**
     * Auto-scan: supports non-root Android 12-16.
     * Tries: Shizuku > MANAGE_EXTERNAL_STORAGE > direct accessible paths.
     */
    fun runAutoScan() {
        if (_scanState.value.isScanning) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val shizuku = try { ShizukuHelper.isAvailable() } catch (_: Exception) { false }
                val allFiles = try { Environment.isExternalStorageManager() } catch (_: Exception) { false }

                _scanState.value = _scanState.value.copy(
                    isScanning = true, progress = 0f,
                    currentTask = "Auto-detecting launcher...", currentScreen = "minecraft",
                    launcherResults = emptyList(), modScanResults = emptyList(),
                    safResult = null, error = null, scanComplete = false,
                    shizukuAvailable = shizuku
                )
                updateProgress(0.05f, "Memeriksa akses storage...")

                val foundPaths = mutableListOf<Pair<String, String>>()

                // Candidate paths
                val candidates = listOf(
                    "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/files/.minecraft" to "Zalith Launcher",
                    "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/files/instance" to "Zalith Launcher",
                    "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/files/.minecraft" to "Zalith Launcher 2",
                    "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/files/instance" to "Zalith Launcher 2",
                    "/storage/emulated/0/Android/data/git.artdeell.mojo/files/.minecraft" to "Mojo Launcher",
                    "/storage/emulated/0/Android/data/git.artdeell.mojo/files/instance" to "Mojo Launcher",
                    "/storage/emulated/0/Android/data/net.kdt.pojavlaunch/files/.minecraft" to "PojavLauncher",
                    "/storage/emulated/0/games/PojavLauncher/.minecraft" to "PojavLauncher",
                    "/storage/emulated/0/Android/data/com.tungsten.fcl/files/.minecraft" to "Fold Craft Launcher",
                    "/storage/emulated/0/FCL/.minecraft" to "Fold Craft Launcher",
                    "/storage/emulated/0/Android/data/com.tungsten.hmclpe/files/.minecraft" to "HMCL-PE",
                )

                for ((path, name) in candidates) {
                    try {
                        val isAndroidData = path.contains("/Android/data/")
                        val exists = when {
                            shizuku && isAndroidData -> ShizukuHelper.directoryExists(path)
                            allFiles -> File(path).exists() && File(path).isDirectory
                            else -> File(path).let { it.exists() && it.isDirectory && it.canRead() }
                        }
                        if (!exists) continue

                        if (path.endsWith("/instance")) {
                            // Enumerate instances
                            val instances: List<String> = when {
                                shizuku && isAndroidData -> ShizukuHelper.listFiles(path)
                                allFiles -> File(path).listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                                else -> File(path).listFiles()?.filter { it.isDirectory && it.canRead() }?.map { it.name } ?: emptyList()
                            }
                            for (inst in instances) {
                                val mcPath = "$path/$inst/.minecraft"
                                val instRoot = "$path/$inst"
                                val mcExists = when {
                                    shizuku && isAndroidData -> ShizukuHelper.directoryExists(mcPath)
                                    allFiles -> File(mcPath).exists()
                                    else -> File(mcPath).let { it.exists() && it.canRead() }
                                }
                                if (mcExists) foundPaths.add(mcPath to "$name [$inst]")
                                else {
                                    val modsExists = when {
                                        shizuku && isAndroidData -> ShizukuHelper.directoryExists("$instRoot/mods")
                                        allFiles -> File("$instRoot/mods").exists()
                                        else -> File("$instRoot/mods").let { it.exists() && it.canRead() }
                                    }
                                    if (modsExists) foundPaths.add(instRoot to "$name [$inst]")
                                }
                            }
                        } else {
                            foundPaths.add(path to name)
                        }
                    } catch (_: Exception) {}
                }

                if (foundPaths.isEmpty()) {
                    val reason = buildString {
                        if (!shizuku && !allFiles) {
                            append("Android 12+ memblokir akses /Android/data/ tanpa root/Shizuku.\n\n")
                            append("Cara scan mods:\n")
                            append("1. Tap 'Pilih Folder Minecraft'\n")
                            append("2. Navigasi: Android > data > [nama_launcher] > files\n")
                            append("3. Pilih folder .minecraft\n\n")
                            append("Atau install Shizuku untuk auto-scan.")
                        } else if (shizuku) {
                            append("Shizuku aktif tapi tidak ada launcher ditemukan di path standar.")
                        } else {
                            append("All Files Access aktif tapi tidak ada launcher ditemukan.")
                        }
                    }
                    _scanState.value = _scanState.value.copy(
                        isScanning = false, progress = 0f, currentTask = "",
                        error = reason, scanComplete = true, verdict = "NO_ACCESS"
                    )
                    return@launch
                }

                updateProgress(0.1f, "Ditemukan ${foundPaths.size} lokasi launcher")

                val allModResults = mutableListOf<JarInspector.JarScanResult>()
                val allLaunchers = mutableListOf<MinecraftScanner.LauncherScanResult>()

                // Count total mods first
                var totalMods = 0
                for ((path, _) in foundPaths.distinctBy { it.first }) {
                    totalMods += listModsAtPath("$path/mods", shizuku, allFiles).size
                }

                var scannedMods = 0
                for ((path, launcherName) in foundPaths.distinctBy { it.first }) {
                    val mods = mutableListOf<MinecraftScanner.ModFileInfo>()
                    val logFindings = mutableListOf<MinecraftScanner.LogFinding>()
                    val versions = mutableListOf<String>()

                    val modFiles = listModsAtPath("$path/mods", shizuku, allFiles)
                    for ((modName, modPath, modSizeMb) in modFiles) {
                        mods.add(MinecraftScanner.ModFileInfo(modName, modPath, modSizeMb))
                        if (modSizeMb > MAX_JAR_SIZE_MB) continue
                        scannedMods++
                        if (totalMods > 0) updateProgress(
                            0.1f + (0.7f * scannedMods / totalMods),
                            "Scanning: $modName ($scannedMods/$totalMods)"
                        )
                        try {
                            val result = scanModFile(modPath, modName, shizuku)
                            if (result != null) allModResults.add(result)
                        } catch (_: Exception) {}
                    }

                    scanLogsAtPath("$path/logs", shizuku, allFiles, logFindings)
                    versions.addAll(listVersionsAtPath("$path/versions", shizuku, allFiles))

                    allLaunchers.add(MinecraftScanner.LauncherScanResult(
                        name = launcherName, packageName = "",
                        path = path, found = true,
                        mods = mods, logFindings = logFindings, versions = versions,
                        modsDir = "$path/mods"
                    ))
                }

                val totalFlags = allModResults.count { it.flagged } + allLaunchers.sumOf { it.logFindings.size }
                _scanState.value = _scanState.value.copy(
                    isScanning = false, progress = 1f, currentTask = "Scan selesai!",
                    launcherResults = allLaunchers, modScanResults = allModResults,
                    totalFlags = totalFlags,
                    verdict = if (totalFlags == 0) "CLEAN" else "FLAGGED",
                    scanComplete = true
                )
            } catch (e: Exception) {
                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    error = "Auto scan error: ${e.message}. Coba 'Pilih Folder'.",
                    scanComplete = true, verdict = "ERROR"
                )
            }
        }
    }

    /** Deleted file scan — works WITHOUT root on Android 12-16 */
    fun runDeletedFileScan() {
        if (_scanState.value.isScanning) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _scanState.value = _scanState.value.copy(
                    isScanning = true, progress = 0f,
                    currentTask = "Scanning file mencurigakan...",
                    currentScreen = "deleted",
                    deletedFileScanResult = null, error = null, scanComplete = false
                )
                updateProgress(0.15f, "Scanning folder Download...")
                val result = DeletedFileScanner.scanDeletedFiles(getApplication<Application>().cacheDir)
                _scanState.value = _scanState.value.copy(
                    isScanning = false, progress = 1f,
                    currentTask = "Scan file terhapus selesai!",
                    deletedFileScanResult = result,
                    totalFlags = result.flaggedItems.size,
                    verdict = if (result.flaggedItems.isEmpty()) "CLEAN" else "FLAGGED",
                    scanComplete = true
                )
            } catch (e: Exception) {
                _scanState.value = _scanState.value.copy(
                    isScanning = false, error = "Error: ${e.message}",
                    scanComplete = true, verdict = "ERROR"
                )
            }
        }
    }

    /** Chrome/browser history scan */
    fun runHistoryScan() {
        if (_scanState.value.isScanning) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val shizuku = try { ShizukuHelper.isAvailable() } catch (_: Exception) { false }
                _scanState.value = _scanState.value.copy(
                    isScanning = true, progress = 0f,
                    currentTask = "Scanning riwayat browser...",
                    currentScreen = "history",
                    chromeScanResult = null, error = null, scanComplete = false,
                    shizukuAvailable = shizuku
                )
                updateProgress(0.3f, "Membaca database browser...")
                val result = ChromeScanner.scanChromeHistory(getApplication())
                val totalFlags = result.suspiciousUrls.size + result.suspiciousDownloads.size
                _scanState.value = _scanState.value.copy(
                    isScanning = false, progress = 1f, currentTask = "Scan history selesai!",
                    chromeScanResult = result, totalFlags = totalFlags,
                    verdict = if (totalFlags == 0) "CLEAN" else "FLAGGED",
                    scanComplete = true, error = result.error
                )
            } catch (e: Exception) {
                _scanState.value = _scanState.value.copy(
                    isScanning = false, error = "Error: ${e.message}",
                    scanComplete = true, verdict = "ERROR"
                )
            }
        }
    }

    /** Full scan: all modules combined */
    fun runFullScan() {
        if (_scanState.value.isScanning) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val shizuku = try { ShizukuHelper.isAvailable() } catch (_: Exception) { false }
                val allFiles = try { Environment.isExternalStorageManager() } catch (_: Exception) { false }

                _scanState.value = _scanState.value.copy(
                    isScanning = true, progress = 0f, currentTask = "Memulai full scan...",
                    currentScreen = "minecraft",
                    launcherResults = emptyList(), modScanResults = emptyList(),
                    deletedFileScanResult = null, chromeScanResult = null,
                    safResult = null, error = null, scanComplete = false,
                    shizukuAvailable = shizuku
                )

                // 1. Deleted files (always works without root)
                updateProgress(0.05f, "Scanning file mencurigakan/terhapus...")
                val deletedResult = try {
                    DeletedFileScanner.scanDeletedFiles(getApplication<Application>().cacheDir)
                } catch (_: Exception) {
                    DeletedFileScanner.DeletedFileScanResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0)
                }
                _scanState.value = _scanState.value.copy(deletedFileScanResult = deletedResult)

                // 2. Chrome history
                updateProgress(0.2f, "Scanning riwayat browser...")
                val chromeResult = try {
                    ChromeScanner.scanChromeHistory(getApplication())
                } catch (e: Exception) {
                    ChromeScanner.ChromeScanResult(0, emptyList(), emptyList(), 0, 0, e.message)
                }
                _scanState.value = _scanState.value.copy(chromeScanResult = chromeResult)

                // 3. Minecraft auto-detect
                updateProgress(0.3f, "Auto-detecting Minecraft launchers...")
                val allModResults = mutableListOf<JarInspector.JarScanResult>()
                val allLaunchers = mutableListOf<MinecraftScanner.LauncherScanResult>()
                var mcNote: String? = null

                val mcPaths = listOf(
                    "/storage/emulated/0/Android/data/com.movtery.zalithlauncher/files/.minecraft" to "Zalith Launcher",
                    "/storage/emulated/0/Android/data/com.movtery.zalithlauncher2/files/.minecraft" to "Zalith Launcher 2",
                    "/storage/emulated/0/Android/data/git.artdeell.mojo/files/.minecraft" to "Mojo Launcher",
                    "/storage/emulated/0/Android/data/net.kdt.pojavlaunch/files/.minecraft" to "PojavLauncher",
                    "/storage/emulated/0/Android/data/com.tungsten.fcl/files/.minecraft" to "Fold Craft Launcher",
                    "/storage/emulated/0/games/PojavLauncher/.minecraft" to "PojavLauncher",
                    "/storage/emulated/0/FCL/.minecraft" to "Fold Craft Launcher",
                )

                var modIdx = 0
                val totalMods = mcPaths.sumOf { (p, _) -> listModsAtPath("$p/mods", shizuku, allFiles).size }

                for ((path, launcherName) in mcPaths) {
                    try {
                        val isAndroidData = path.contains("/Android/data/")
                        val exists = when {
                            shizuku && isAndroidData -> ShizukuHelper.directoryExists(path)
                            allFiles -> File(path).exists()
                            else -> File(path).let { it.exists() && it.canRead() }
                        }
                        if (!exists) continue

                        val mods = mutableListOf<MinecraftScanner.ModFileInfo>()
                        val logFindings = mutableListOf<MinecraftScanner.LogFinding>()
                        val modFiles = listModsAtPath("$path/mods", shizuku, allFiles)
                        for ((modName, modPath, modSizeMb) in modFiles) {
                            mods.add(MinecraftScanner.ModFileInfo(modName, modPath, modSizeMb))
                            if (modSizeMb <= MAX_JAR_SIZE_MB) {
                                modIdx++
                                if (totalMods > 0) updateProgress(
                                    0.3f + (0.5f * modIdx / totalMods),
                                    "Scanning: $modName ($modIdx/$totalMods)"
                                )
                                try {
                                    val result = scanModFile(modPath, modName, shizuku)
                                    if (result != null) allModResults.add(result)
                                } catch (_: Exception) {}
                            }
                        }
                        scanLogsAtPath("$path/logs", shizuku, allFiles, logFindings)
                        val versions = listVersionsAtPath("$path/versions", shizuku, allFiles)
                        allLaunchers.add(MinecraftScanner.LauncherScanResult(
                            name = launcherName, packageName = "",
                            path = path, found = true,
                            mods = mods, logFindings = logFindings, versions = versions,
                            modsDir = "$path/mods"
                        ))
                    } catch (_: Exception) {}
                }

                if (allLaunchers.isEmpty() && !shizuku && !allFiles) {
                    mcNote = "Tidak bisa auto-detect Minecraft (Android 12+ blokir /Android/data/).\nGunakan 'Pilih Folder Minecraft' untuk scan mod."
                }

                _scanState.value = _scanState.value.copy(launcherResults = allLaunchers, modScanResults = allModResults)

                var totalFlags = allModResults.count { it.flagged }
                totalFlags += allLaunchers.sumOf { it.logFindings.size }
                totalFlags += deletedResult.flaggedItems.size
                totalFlags += chromeResult.suspiciousUrls.size + chromeResult.suspiciousDownloads.size

                _scanState.value = _scanState.value.copy(
                    isScanning = false, progress = 1f, currentTask = "Full scan selesai!",
                    totalFlags = totalFlags,
                    verdict = if (totalFlags == 0) "CLEAN" else "FLAGGED",
                    scanComplete = true, error = mcNote
                )
            } catch (e: Exception) {
                _scanState.value = _scanState.value.copy(
                    isScanning = false, error = "Error: ${e.message}",
                    scanComplete = true, verdict = "ERROR"
                )
            }
        }
    }

    fun resetScan() {
        val shizuku = try { ShizukuHelper.isAvailable() } catch (_: Exception) { false }
        _scanState.value = ScanState(shizukuAvailable = shizuku)
    }

    private fun updateProgress(progress: Float, task: String) {
        _scanState.value = _scanState.value.copy(progress = progress, currentTask = task)
    }

    // ── Scan a single mod file (Shizuku copy or direct) ──
    private fun scanModFile(modPath: String, modName: String, useShizuku: Boolean): JarInspector.JarScanResult? {
        val isAndroidData = modPath.contains("/Android/data/")
        return if (useShizuku && isAndroidData) {
            val tmp = ShizukuHelper.copyToCache(modPath, getApplication<Application>().cacheDir)
            if (tmp != null) {
                val r = JarInspector.inspectJar(tmp)
                tmp.delete()
                r.copy(path = modPath, filename = modName)
            } else null
        } else {
            val f = File(modPath)
            if (f.exists() && f.canRead()) JarInspector.inspectJar(f) else null
        }
    }

    // ── List mod files as triple (name, path, sizeMb) ──
    private data class ModInfo(val name: String, val path: String, val sizeMb: Float)

    private fun listModsAtPath(modsPath: String, useShizuku: Boolean, hasAllFiles: Boolean): List<ModInfo> {
        return try {
            val isAndroidData = modsPath.contains("/Android/data/")
            when {
                useShizuku && isAndroidData -> {
                    ShizukuHelper.listFilesDetailed(modsPath)
                        .filter { !it.isDirectory && it.name.lowercase().let { n -> n.endsWith(".jar") || n.endsWith(".zip") } }
                        .map { ModInfo(it.name, "$modsPath/${it.name}", bytesToMb(it.size)) }
                }
                else -> {
                    val dir = File(modsPath)
                    if (!dir.exists()) return emptyList()
                    val readable = if (hasAllFiles) dir.exists() else dir.canRead()
                    if (!readable) return emptyList()
                    dir.listFiles()
                        ?.filter { it.isFile && it.extension.lowercase() in listOf("jar", "zip") }
                        ?.map { ModInfo(it.name, it.absolutePath, bytesToMb(it.length())) }
                        ?: emptyList()
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun listVersionsAtPath(versionsPath: String, useShizuku: Boolean, hasAllFiles: Boolean): List<String> {
        return try {
            val isAndroidData = versionsPath.contains("/Android/data/")
            when {
                useShizuku && isAndroidData -> {
                    ShizukuHelper.listFilesDetailed(versionsPath).filter { it.isDirectory }.map { it.name }
                }
                else -> {
                    val dir = File(versionsPath)
                    if (!dir.exists()) return emptyList()
                    dir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    private val LOG_PATTERNS = listOf(
        "meteor-client" to "critical", "meteorclient" to "critical",
        "wurstclient" to "critical", "impactclient" to "critical",
        "aristois" to "critical", "liquidbounce" to "critical",
        "futureclient" to "critical", "rusherhack" to "critical",
        "thunderhack" to "critical", "bleachhack" to "critical",
        "coffeeclient" to "critical", "phobos" to "critical",
        "konas" to "critical", "gamesense" to "critical",
        "salhack" to "critical", "forgehax" to "critical",
        "3arthh4ck" to "critical", "earthhack" to "critical",
        "sigmaclient" to "critical", "inertiaclient" to "critical",
        "Loading module: KillAura" to "critical",
        "Loading module: AutoCrystal" to "critical",
        "Loading module: AimAssist" to "critical",
        "Loading module: Triggerbot" to "critical",
        "Loading module: Scaffold" to "critical",
        "Loading module: Xray" to "critical",
        "clickgui" to "high", "ClickGUI" to "high",
        "Injecting into" to "critical", "injection successful" to "critical",
    )

    private fun scanLogsAtPath(
        logsPath: String, useShizuku: Boolean, hasAllFiles: Boolean,
        findings: MutableList<MinecraftScanner.LogFinding>
    ) {
        try {
            val isAndroidData = logsPath.contains("/Android/data/")
            val logNames: List<String> = when {
                useShizuku && isAndroidData ->
                    ShizukuHelper.listFiles(logsPath).filter { it.endsWith(".log") }.take(10)
                else -> {
                    val dir = File(logsPath)
                    if (!dir.exists()) return
                    dir.listFiles()?.filter { it.isFile && it.extension == "log" }?.map { it.name }?.take(10) ?: return
                }
            }
            for (logName in logNames.sortedByDescending { it == "latest.log" }) {
                try {
                    val content: String = when {
                        useShizuku && isAndroidData ->
                            ShizukuHelper.readTextFile("$logsPath/$logName") ?: continue
                        else -> {
                            val f = File("$logsPath/$logName")
                            if (!f.exists()) continue
                            f.readText(Charsets.UTF_8).take(5 * 1024 * 1024)
                        }
                    }
                    val lower = content.lowercase()
                    for ((pattern, severity) in LOG_PATTERNS) {
                        if (pattern.lowercase() in lower) {
                            content.split("\n").forEachIndexed { i, line ->
                                if (pattern.lowercase() in line.lowercase()) {
                                    findings.add(MinecraftScanner.LogFinding(logName, i + 1, line.trim().take(300), pattern, severity))
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
