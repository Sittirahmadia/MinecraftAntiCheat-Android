package com.sstools.anticheat.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

/**
 * SAF (Storage Access Framework) Scanner
 * Reads files from user-selected directories via DocumentFile API.
 * This ALWAYS works on Android 12-16 for /Android/data/ directories.
 */
object SafScanner {

    private const val TAG = "SafScanner"

    data class SafScanResult(
        val folderPath: String,
        val modsFound: List<SafModFile>,
        val logFindings: List<SafLogFinding>,
        val versionsFound: List<String>,
        val totalFilesScanned: Int,
        val jarResults: List<JarInspector.JarScanResult>,
        val error: String? = null
    )

    data class SafModFile(
        val name: String,
        val uri: String,
        val sizeMb: Float
    )

    data class SafLogFinding(
        val logFile: String,
        val lineNumber: Int,
        val line: String,
        val matchedPattern: String,
        val severity: String
    )

    // Cheat patterns for log scanning
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
        "Loading module: Nuker" to "critical",
        "Enabled hack:" to "high", "Toggled module:" to "high",
        "clickgui" to "high", "ClickGUI" to "high",
        "Injecting into" to "critical", "injection successful" to "critical",
    )

    /**
     * Scan a user-selected folder (via SAF) for Minecraft mods, logs, and versions.
     * The URI comes from ACTION_OPEN_DOCUMENT_TREE.
     */
    fun scanFolder(context: Context, treeUri: Uri, onProgress: (Float, String) -> Unit): SafScanResult {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: return SafScanResult("", emptyList(), emptyList(), emptyList(), 0, emptyList(), "Cannot open folder")

        val folderName = rootDoc.name ?: treeUri.toString()
        Log.i(TAG, "Scanning folder: $folderName (${rootDoc.uri})")
        onProgress(0.05f, "Opening folder: $folderName")

        var totalScanned = 0
        val modsFound = mutableListOf<SafModFile>()
        val logFindings = mutableListOf<SafLogFinding>()
        val versionsFound = mutableListOf<String>()
        val jarResults = mutableListOf<JarInspector.JarScanResult>()

        // Look for mods directory
        val modsDir = findSubDir(rootDoc, "mods")
        if (modsDir != null) {
            onProgress(0.1f, "Scanning mods folder...")
            val modFiles = modsDir.listFiles()
            val modCount = modFiles.size
            var scanned = 0

            for (modFile in modFiles) {
                if (modFile.isFile) {
                    val name = modFile.name ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in listOf("jar", "zip")) {
                        val sizeMb = modFile.length().toFloat() / (1024 * 1024)
                        modsFound.add(SafModFile(name, modFile.uri.toString(), "%.2f".format(sizeMb).toFloat()))
                        totalScanned++

                        // Copy to cache and inspect
                        scanned++
                        onProgress(0.1f + (0.6f * scanned / maxOf(modCount, 1)), "Inspecting: $name ($scanned/$modCount)")

                        try {
                            val tempFile = copyToCache(context, modFile)
                            if (tempFile != null) {
                                val result = JarInspector.inspectJar(tempFile)
                                jarResults.add(result)
                                tempFile.delete()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error inspecting $name: ${e.message}")
                        }
                    }
                }
            }
            Log.i(TAG, "Found ${modsFound.size} mods")
        } else {
            Log.d(TAG, "No mods directory found")
            // Maybe user selected a parent dir — search recursively
            val subDirs = rootDoc.listFiles().filter { it.isDirectory }
            for (subDir in subDirs) {
                val subMods = findSubDir(subDir, "mods")
                if (subMods != null) {
                    onProgress(0.1f, "Found mods in ${subDir.name}/mods")
                    for (modFile in subMods.listFiles()) {
                        if (modFile.isFile) {
                            val name = modFile.name ?: continue
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext in listOf("jar", "zip")) {
                                val sizeMb = modFile.length().toFloat() / (1024 * 1024)
                                modsFound.add(SafModFile(name, modFile.uri.toString(), "%.2f".format(sizeMb).toFloat()))
                                totalScanned++
                                try {
                                    val tempFile = copyToCache(context, modFile)
                                    if (tempFile != null) {
                                        jarResults.add(JarInspector.inspectJar(tempFile))
                                        tempFile.delete()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error: ${e.message}")
                                }
                            }
                        }
                    }
                    break
                }
            }
        }

        // Look for logs directory
        onProgress(0.75f, "Scanning logs...")
        val logsDir = findSubDir(rootDoc, "logs")
        if (logsDir != null) {
            scanLogsDir(context, logsDir, logFindings)
            totalScanned += logFindings.size
        }

        // Look for versions directory
        onProgress(0.85f, "Checking versions...")
        val versionsDir = findSubDir(rootDoc, "versions")
        if (versionsDir != null) {
            for (versionDir in versionsDir.listFiles()) {
                if (versionDir.isDirectory) {
                    versionsFound.add(versionDir.name ?: "unknown")
                }
            }
            totalScanned += versionsFound.size
        }

        onProgress(0.95f, "Generating report...")
        Log.i(TAG, "Scan complete: ${modsFound.size} mods, ${logFindings.size} log findings, ${versionsFound.size} versions")

        return SafScanResult(
            folderPath = folderName,
            modsFound = modsFound,
            logFindings = logFindings,
            versionsFound = versionsFound,
            totalFilesScanned = totalScanned,
            jarResults = jarResults,
            error = if (modsFound.isEmpty() && logFindings.isEmpty() && versionsFound.isEmpty())
                "No mods/logs/versions found. Make sure you selected the .minecraft folder (or the folder containing mods/logs)."
            else null
        )
    }

    /**
     * Find a subdirectory by name (case-insensitive).
     */
    private fun findSubDir(parent: DocumentFile, name: String): DocumentFile? {
        return parent.listFiles().firstOrNull {
            it.isDirectory && it.name?.lowercase() == name.lowercase()
        }
    }

    /**
     * Copy a DocumentFile to app cache so we can read it with ZipFile.
     */
    private fun copyToCache(context: Context, docFile: DocumentFile): File? {
        return try {
            val name = docFile.name ?: "temp_${System.currentTimeMillis()}.jar"
            val tempFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}_$name")

            context.contentResolver.openInputStream(docFile.uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) tempFile else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "copyToCache failed: ${e.message}")
            null
        }
    }

    /**
     * Scan log files in a logs directory via SAF.
     */
    private fun scanLogsDir(context: Context, logsDir: DocumentFile, findings: MutableList<SafLogFinding>) {
        try {
            val logFiles = logsDir.listFiles()
                .filter { it.isFile && (it.name?.endsWith(".log") == true) }
                .sortedByDescending { it.name == "latest.log" }
                .take(10)

            for (logDoc in logFiles) {
                try {
                    val logName = logDoc.name ?: continue
                    val content = context.contentResolver.openInputStream(logDoc.uri)?.use { input ->
                        input.bufferedReader().use { it.readText().take(5 * 1024 * 1024) }
                    } ?: continue

                    val contentLower = content.lowercase()
                    for ((pattern, severity) in CHEAT_LOG_PATTERNS) {
                        if (pattern.lowercase() in contentLower) {
                            val lines = content.split("\n")
                            for ((i, line) in lines.withIndex()) {
                                if (pattern.lowercase() in line.lowercase()) {
                                    findings.add(SafLogFinding(logName, i + 1, line.trim().take(300), pattern, severity))
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading log ${logDoc.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning logs: ${e.message}")
        }
    }
}
