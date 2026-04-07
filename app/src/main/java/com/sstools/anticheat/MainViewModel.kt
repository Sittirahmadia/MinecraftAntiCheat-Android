package com.sstools.anticheat

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sstools.anticheat.scanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.File

data class ScanState(
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val currentTask: String = "",
    val launcherResults: List<MinecraftScanner.LauncherScanResult> = emptyList(),
    val modScanResults: List<JarInspector.JarScanResult> = emptyList(),
    val deletedFileScanResult: DeletedFileScanner.DeletedFileScanResult? = null,
    val chromeScanResult: ChromeScanner.ChromeScanResult? = null,
    val totalFlags: Int = 0,
    val verdict: String = "",
    val scanComplete: Boolean = false,
    val shizukuAvailable: Boolean = false,
    val shizukuGranted: Boolean = false,
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    init {
        checkShizuku()
    }

    fun checkShizuku() {
        try {
            val available = Shizuku.pingBinder()
            val granted = if (available) {
                try {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } catch (_: Exception) { false }
            } else false
            _scanState.value = _scanState.value.copy(shizukuAvailable = available, shizukuGranted = granted)
        } catch (_: Exception) {
            _scanState.value = _scanState.value.copy(shizukuAvailable = false, shizukuGranted = false)
        }
    }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(0)
                }
            }
        } catch (_: Exception) {}
    }

    fun runFullScan() {
        if (_scanState.value.isScanning) return

        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanState(isScanning = true, progress = 0f, currentTask = "Starting scan...")

            try {
                // 1. Detect launchers
                updateProgress(0.05f, "Detecting Minecraft launchers...")
                val launchers = MinecraftScanner.detectLaunchers()
                _scanState.value = _scanState.value.copy(launcherResults = launchers)

                // 2. Deep scan all mods
                updateProgress(0.15f, "Deep scanning mods (JAR + Class inspect)...")
                val allModResults = mutableListOf<JarInspector.JarScanResult>()
                val totalMods = launchers.sumOf { it.mods.size }
                var scannedMods = 0

                for (launcher in launchers) {
                    for (mod in launcher.mods) {
                        try {
                            val file = File(mod.path)
                            if (file.exists()) {
                                val result = JarInspector.inspectJar(file)
                                allModResults.add(result)
                            }
                        } catch (_: Exception) {}
                        scannedMods++
                        val modProgress = 0.15f + (0.50f * scannedMods / maxOf(totalMods, 1))
                        updateProgress(modProgress, "Scanning mod ${scannedMods}/${totalMods}: ${mod.name}")
                    }
                }
                _scanState.value = _scanState.value.copy(modScanResults = allModResults)

                // 3. Deleted file scan
                updateProgress(0.70f, "Scanning for deleted/suspicious files...")
                val deletedResult = DeletedFileScanner.scanDeletedFiles(getApplication<Application>().cacheDir)
                _scanState.value = _scanState.value.copy(deletedFileScanResult = deletedResult)

                // 4. Chrome history scan
                updateProgress(0.85f, "Scanning browser history...")
                val chromeResult = try {
                    ChromeScanner.scanChromeHistory(getApplication())
                } catch (_: Exception) {
                    ChromeScanner.ChromeScanResult(0, emptyList(), emptyList(), 0, 0, "Could not access browser history")
                }
                _scanState.value = _scanState.value.copy(chromeScanResult = chromeResult)

                // 5. Calculate verdict
                updateProgress(0.95f, "Generating report...")
                var totalFlags = 0
                totalFlags += allModResults.count { it.flagged }
                totalFlags += launchers.sumOf { it.logFindings.size }
                totalFlags += (deletedResult.flaggedItems.size)
                totalFlags += (chromeResult.suspiciousUrls.size + chromeResult.suspiciousDownloads.size)

                val verdict = if (totalFlags == 0) "CLEAN" else "FLAGGED"

                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    progress = 1f,
                    currentTask = "Scan complete!",
                    totalFlags = totalFlags,
                    verdict = verdict,
                    scanComplete = true
                )

            } catch (e: Exception) {
                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    error = e.message ?: "Unknown error",
                    currentTask = "Error: ${e.message}"
                )
            }
        }
    }

    fun scanMinecraftOnly() {
        if (_scanState.value.isScanning) return

        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanState(isScanning = true, progress = 0f, currentTask = "Detecting launchers...")

            try {
                val launchers = MinecraftScanner.detectLaunchers()
                _scanState.value = _scanState.value.copy(launcherResults = launchers)
                updateProgress(0.1f, "Found ${launchers.size} launcher(s)")

                val allModResults = mutableListOf<JarInspector.JarScanResult>()
                val totalMods = launchers.sumOf { it.mods.size }
                var scannedMods = 0

                for (launcher in launchers) {
                    for (mod in launcher.mods) {
                        try {
                            val file = File(mod.path)
                            if (file.exists()) {
                                allModResults.add(JarInspector.inspectJar(file))
                            }
                        } catch (_: Exception) {}
                        scannedMods++
                        updateProgress(0.1f + (0.85f * scannedMods / maxOf(totalMods, 1)),
                            "Inspecting ${mod.name} ($scannedMods/$totalMods)")
                    }
                }

                val totalFlags = allModResults.count { it.flagged } + launchers.sumOf { it.logFindings.size }

                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    progress = 1f,
                    currentTask = "Minecraft scan complete!",
                    modScanResults = allModResults,
                    totalFlags = totalFlags,
                    verdict = if (totalFlags == 0) "CLEAN" else "FLAGGED",
                    scanComplete = true
                )
            } catch (e: Exception) {
                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    error = e.message,
                    currentTask = "Error: ${e.message}"
                )
            }
        }
    }

    fun resetScan() {
        _scanState.value = ScanState(
            shizukuAvailable = _scanState.value.shizukuAvailable,
            shizukuGranted = _scanState.value.shizukuGranted
        )
    }

    private fun updateProgress(progress: Float, task: String) {
        _scanState.value = _scanState.value.copy(progress = progress, currentTask = task)
    }
}
