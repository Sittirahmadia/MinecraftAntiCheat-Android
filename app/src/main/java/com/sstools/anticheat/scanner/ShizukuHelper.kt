package com.sstools.anticheat.scanner

import android.content.pm.PackageManager
import android.util.Log
import java.io.File

/**
 * Shizuku Helper - Executes shell commands with ADB-level privileges
 * This allows accessing /Android/data/ and /data/data/ directories
 * that are blocked by Android 11+ scoped storage.
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"

    /**
     * Check if Shizuku is available and granted
     */
    fun isAvailable(): Boolean {
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val pingMethod = clazz.getMethod("pingBinder")
            val available = pingMethod.invoke(null) as Boolean
            if (!available) return false

            val checkMethod = clazz.getMethod("checkSelfPermission")
            val perm = checkMethod.invoke(null) as Int
            perm == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            Log.d(TAG, "Shizuku not available: ${e.message}")
            false
        }
    }

    /**
     * Execute a shell command via Shizuku with ADB privileges.
     * Returns the stdout output as a string, or null on failure.
     */
    fun execCommand(command: String): String? {
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = clazz.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )

            val args = arrayOf("sh", "-c", command)
            val process = newProcessMethod.invoke(null, args, null, null) as Process

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val errorOutput = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode != 0 && errorOutput.isNotBlank()) {
                Log.d(TAG, "Command '$command' exit=$exitCode stderr=$errorOutput")
            }

            output.trim().ifEmpty { null }
        } catch (e: Throwable) {
            Log.e(TAG, "execCommand failed for '$command': ${e.message}")
            null
        }
    }

    /**
     * List files in a directory using Shizuku shell.
     * Returns list of filenames, or empty list on failure.
     */
    fun listFiles(dirPath: String): List<String> {
        val output = execCommand("ls -1 '$dirPath' 2>/dev/null") ?: return emptyList()
        return output.split("\n").filter { it.isNotBlank() }
    }

    /**
     * List files with details (name, size) in a directory using Shizuku.
     */
    fun listFilesDetailed(dirPath: String): List<FileInfo> {
        val output = execCommand("ls -la '$dirPath' 2>/dev/null") ?: return emptyList()
        val files = mutableListOf<FileInfo>()
        for (line in output.split("\n")) {
            if (line.isBlank() || line.startsWith("total")) continue
            // Parse ls -la output: permissions links owner group size date time name
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 8) {
                val name = parts.drop(7).joinToString(" ")  // filename may have spaces
                val size = parts.getOrNull(4)?.toLongOrNull() ?: 0L
                val isDir = line.startsWith("d")
                files.add(FileInfo(name, size, isDir))
            }
        }
        return files
    }

    /**
     * Check if a directory exists using Shizuku.
     */
    fun directoryExists(path: String): Boolean {
        val output = execCommand("[ -d '$path' ] && echo 'YES' || echo 'NO'") ?: return false
        return output.trim() == "YES"
    }

    /**
     * Check if a file exists using Shizuku.
     */
    fun fileExists(path: String): Boolean {
        val output = execCommand("[ -f '$path' ] && echo 'YES' || echo 'NO'") ?: return false
        return output.trim() == "YES"
    }

    /**
     * Copy a file from a protected location to a destination using Shizuku.
     * Returns true if successful.
     */
    fun copyFile(source: String, destination: String): Boolean {
        return try {
            val result = execCommand("cp '$source' '$destination' && chmod 644 '$destination' && echo 'OK'")
            result?.trim() == "OK"
        } catch (e: Throwable) {
            Log.e(TAG, "copyFile failed: ${e.message}")
            false
        }
    }

    /**
     * Read a text file from a protected location using Shizuku.
     * Returns file content as string, max 5MB.
     */
    fun readTextFile(path: String, maxBytes: Int = 5 * 1024 * 1024): String? {
        return execCommand("head -c $maxBytes '$path' 2>/dev/null")
    }

    /**
     * List subdirectories in a path using Shizuku.
     */
    fun listDirectories(dirPath: String): List<String> {
        val output = execCommand("ls -1d '$dirPath'/*/ 2>/dev/null") ?: return emptyList()
        return output.split("\n")
            .filter { it.isNotBlank() }
            .map { it.trimEnd('/').substringAfterLast('/') }
    }

    /**
     * Copy a JAR/ZIP file to app cache for inspection.
     * Returns the local File if successful, null otherwise.
     */
    fun copyToCache(sourcePath: String, cacheDir: File): File? {
        return try {
            val filename = sourcePath.substringAfterLast('/')
            val destFile = File(cacheDir, "scan_${System.currentTimeMillis()}_$filename")
            val success = copyFile(sourcePath, destFile.absolutePath)
            if (success && destFile.exists() && destFile.length() > 0) {
                destFile
            } else {
                destFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "copyToCache failed: ${e.message}")
            null
        }
    }

    data class FileInfo(val name: String, val size: Long, val isDirectory: Boolean)
}
