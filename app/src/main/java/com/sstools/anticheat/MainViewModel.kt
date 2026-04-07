package com.sstools.anticheat

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sstools.anticheat.scanner.*
import com.sstools.anticheat.scanner.ShizukuHelper
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
            // Try to load Shizuku class first
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val pingMethod = clazz.getMethod("pingBinder")
            val available = pingMethod.invoke(null) as Boolean

            val granted = if (available) {
                try {
                    val checkMethod = clazz.getMethod("checkSelfPermission")
                    (checkMethod.invoke(null) as Int) == PackageManager.PERMISSION_GRANTED
                } catch (_: Throwable) { false }
            } else false

            _scanState.value = _scanState.value.copy(
                shizukuAvailable = available,
                shizukuGranted = granted
            )
        } catch (e: Throwable) {
            // Shizuku not installed or not available - totally fine
            Log.d("MainViewModel", "Shizuku check failed (not installed?): ${e.message}")
            _scanState.value = _scanState.value.copy(
                shizukuAvailable = false,
                shizukuGranted = false
            )
        }
    }

    fun requestShizukuPermission() {
        try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val pingMethod = clazz.getMethod("pingBinder")
            val available = pingMethod.invoke(null) as Boolean
            if (available) {
                val checkMethod = clazz.getMethod("checkSelfPermission")
                val perm = checkMethod.invoke(null) as Int
                if (perm != PackageManager.PERMISSION_GRANTED) {
                    val requestMethod = clazz.getMethod("requestPermission", Int::class.java)
                    requestMethod.invoke(null, 0)
                }
            }
        } catch (e: Throwable) {
            Log.d("MainViewModel", "Shizuku permission request failed: ${e.message}")
        }
    }

    fun runFullScan() {
        if (_scanState.value.isScanning) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _scanState.value = ScanState(
                    isScanning = true,
                    progress = 0f,
                    currentTask = "Starting full scan...",
                    shizukuAvailable = _scanState.value.shizukuAvailable,
                    shizukuGranted = _scanState.value.shizukuGranted
                )

                // 1. Detect launchers
                updateProgress(0.05f, "Detecting Minecraft launchers...")
                val launchers = try {
                    MinecraftScanner.detectLaunchers()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Launcher detection error: ${e.message}", e)
                    emptyList()
                }
                _scanState.value = _scanState.value.copy(launcherResults = launchers)
                updateProgress(0.10f, "Found ${launchers.size} launcher(s)")

                // 2. Deep scan all mods
                updateProgress(0.15f, "Deep scanning mods...")
                val allModResults = mutableListOf<JarInspector.JarScanResult>()
                val totalMods = launchers.sumOf { it.mods.size }
                var scannedMods = 0

                val useShizuku = ShizukuHelper.isAvailable()
                val cacheDir = getApplication<Application>().cacheDir

                for (launcher in launchers) {
                    for (mod in launcher.mods) {
                        try {
                            val fileToScan = getModFileForScan(mod.path, useShizuku, cacheDir)
                            if (fileToScan != null) {
                                val result = JarInspector.inspectJar(fileToScan)
                                allModResults.add(result)
                                // Clean up temp copy
                                if (fileToScan.absolutePath.startsWith(cacheDir.absolutePath)) {
                                    fileToScan.delete()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Mod scan error ${mod.name}: ${e.message}")
                        }
                        scannedMods++
                        if (totalMods > 0) {
                            val modProgress = 0.15f + (0.50f * scannedMods / totalMods)
                            updateProgress(modProgress, "Scanning mod $scannedMods/$totalMods: ${mod.name}")
                        }
                    }
                }
                _scanState.value = _scanState.value.copy(modScanResults = allModResults)

                // 3. Deleted file scan
                updateProgress(0.70f, "Scanning for suspicious files...")
                val deletedResult = try {
                    DeletedFileScanner.scanDeletedFiles(getApplication<Application>().cacheDir)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Deleted file scan error: ${e.message}", e)
                    DeletedFileScanner.DeletedFileScanResult(
                        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0
                    )
                }
                _scanState.value = _scanState.value.copy(deletedFileScanResult = deletedResult)

                // 4. Chrome history scan
                updateProgress(0.85f, "Scanning browser history...")
                val chromeResult = try {
                    ChromeScanner.scanChromeHistory(getApplication())
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Chrome scan error: ${e.message}", e)
                    ChromeScanner.ChromeScanResult(0, emptyList(), emptyList(), 0, 0, "Browser scan unavailable: ${e.message}")
                }
                _scanState.value = _scanState.value.copy(chromeScanResult = chromeResult)

                // 5. Calculate verdict
                updateProgress(0.95f, "Generating report...")
                var totalFlags = 0
                totalFlags += allModResults.count { it.flagged }
                totalFlags += launchers.sumOf { it.logFindings.size }
                totalFlags += deletedResult.flaggedItems.size
                totalFlags += chromeResult.suspiciousUrls.size + chromeResult.suspiciousDownloads.size

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
                Log.e("MainViewModel", "Full scan crashed: ${e.message}", e)
                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    error = "Scan error: ${e.message ?: "Unknown error"}",
                    currentTask = "Error occurred",
                    scanComplete = true,
                    verdict = "ERROR"
                )
            }
        }
    }

    fun scanMinecraftOnly() {
        if (_scanState.value.isScanning) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _scanState.value = ScanState(
                    isScanning = true,
                    progress = 0f,
                    currentTask = "Detecting launchers...",
                    shizukuAvailable = _scanState.value.shizukuAvailable,
                    shizukuGranted = _scanState.value.shizukuGranted
                )

                val launchers = try {
                    MinecraftScanner.detectLaunchers()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Launcher detection error: ${e.message}", e)
                    emptyList()
                }
                _scanState.value = _scanState.value.copy(launcherResults = launchers)
                updateProgress(0.1f, "Found ${launchers.size} launcher(s)")

                val allModResults = mutableListOf<JarInspector.JarScanResult>()
                val totalMods = launchers.sumOf { it.mods.size }
                var scannedMods = 0

                val useShizuku2 = ShizukuHelper.isAvailable()
                val cacheDir2 = getApplication<Application>().cacheDir

                for (launcher in launchers) {
                    for (mod in launcher.mods) {
                        try {
                            val fileToScan = getModFileForScan(mod.path, useShizuku2, cacheDir2)
                            if (fileToScan != null) {
                                allModResults.add(JarInspector.inspectJar(fileToScan))
                                if (fileToScan.absolutePath.startsWith(cacheDir2.absolutePath)) {
                                    fileToScan.delete()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Mod scan error: ${e.message}")
                        }
                        scannedMods++
                        if (totalMods > 0) {
                            updateProgress(
                                0.1f + (0.85f * scannedMods / totalMods),
                                "Inspecting ${mod.name} ($scannedMods/$totalMods)"
                            )
                        }
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
                Log.e("MainViewModel", "MC scan crashed: ${e.message}", e)
                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    error = "Scan error: ${e.message ?: "Unknown error"}",
                    currentTask = "Error occurred",
                    scanComplete = true,
                    verdict = "ERROR"
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

    /**
     * Get a readable File for a mod path.
     * If the path is in /Android/data/ (protected), uses Shizuku to copy it to cache.
     * Otherwise uses direct File access.
     */
    private fun getModFileForScan(modPath: String, useShizuku: Boolean, cacheDir: File): File? {
        // Try direct access first
        val directFile = File(modPath)
        if (directFile.exists() && directFile.canRead() && directFile.length() > 0) {
            return directFile
        }

        // If direct access failed and Shizuku is available, copy via Shizuku
        if (useShizuku) {
            Log.d("MainViewModel", "Copying via Shizuku: $modPath")
            val copied = ShizukuHelper.copyToCache(modPath, cacheDir)
            if (copied != null) {
                Log.d("MainViewModel", "Copied to ${copied.absolutePath} (${copied.length()} bytes)")
                return copied
            }
        }

        Log.d("MainViewModel", "Cannot access mod: $modPath")
        return null
    }

    private fun updateProgress(progress: Float, task: String) {
        _scanState.value = _scanState.value.copy(progress = progress, currentTask = task)
    }
}
