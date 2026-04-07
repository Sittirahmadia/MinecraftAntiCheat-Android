package com.sstools.anticheat.scanner

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chrome / Browser History Scanner for Android
 * Uses Shizuku to copy Chrome's History DB from /data/data/ to app cache,
 * then reads it with SQLite. Falls back gracefully if Shizuku unavailable.
 */
object ChromeScanner {

    private const val TAG = "ChromeScanner"

    data class ChromeScanResult(
        val profilesFound: Int,
        val suspiciousUrls: List<SuspiciousUrl>,
        val suspiciousDownloads: List<SuspiciousDownload>,
        val totalUrlsScanned: Int,
        val totalDownloadsScanned: Int,
        val error: String? = null
    )

    data class SuspiciousUrl(
        val url: String,
        val title: String,
        val visitCount: Int,
        val lastVisit: String,
        val matchedPattern: String
    )

    data class SuspiciousDownload(
        val filename: String,
        val filePath: String,
        val sourceUrl: String,
        val sizeMb: Float,
        val downloadTime: String,
        val matchedPattern: String
    )

    private val SUSPICIOUS_URL_PATTERNS = listOf(
        "meteorclient.com", "wurstclient.net", "impactclient.net",
        "aristois.net", "liquidbounce.net", "sigmaclient.info",
        "rusherhack.org", "futureclient.net",
        "198macro", "zenithmacro", "crystalmacro",
        "minecrafthacks", "minecraft-hacks", "hackphoenix",
        "wizardhax.com", "cheating.net", "mc-hacks",
    )

    private val SUSPICIOUS_DOWNLOAD_PATTERNS = listOf(
        "198macro", "zenithmacro", "crystalmacro", "autoclicker",
        "meteor-client", "wurst", "impact", "aristois", "liquidbounce",
        "sigma", "rusherhack", "futureclient", "phobos", "konas",
        "gamesense", "earthhack", "salhack", "forgehax", "bleachhack",
        "thunderhack", "coffeeclient", "injector", "cheatengine",
        "aimassist", "killaura", "triggerbot",
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // Chrome DB locations (in /data/data/ — requires Shizuku or root)
    private val CHROME_DB_PATHS = listOf(
        "/data/data/com.android.chrome/app_chrome/Default/History",
        "/data/data/com.android.chrome/app_chrome/Profile 1/History",
        "/data/data/com.chrome.beta/app_chrome/Default/History",
        "/data/data/com.brave.browser/app_chrome/Default/History",
        "/data/data/com.microsoft.emmx/app_chrome/Default/History",
        "/data/data/com.opera.browser/app_chrome/Default/History",
    )

    fun scanChromeHistory(context: Context): ChromeScanResult {
        val suspiciousUrls = mutableListOf<SuspiciousUrl>()
        val suspiciousDownloads = mutableListOf<SuspiciousDownload>()
        var totalUrls = 0
        var totalDownloads = 0
        var profilesFound = 0
        var errorMsg: String? = null

        val useShizuku = ShizukuHelper.isAvailable()
        Log.i(TAG, "scanChromeHistory: Shizuku=$useShizuku")

        if (!useShizuku) {
            return ChromeScanResult(0, emptyList(), emptyList(), 0, 0,
                "Chrome history scan requires Shizuku (Chrome DB is in /data/data/ protected directory)")
        }

        // Use Shizuku to find and copy Chrome DB files
        for (dbPath in CHROME_DB_PATHS) {
            var tempDb: File? = null
            try {
                // Check if the DB file exists via Shizuku
                if (!ShizukuHelper.fileExists(dbPath)) {
                    continue
                }
                profilesFound++
                Log.i(TAG, "Found Chrome DB: $dbPath")

                // Copy to app cache via Shizuku
                tempDb = File(context.cacheDir, "chrome_${System.currentTimeMillis()}.db")
                val copied = ShizukuHelper.copyFile(dbPath, tempDb.absolutePath)
                if (!copied || !tempDb.exists() || tempDb.length() == 0L) {
                    Log.e(TAG, "Failed to copy Chrome DB from $dbPath")
                    continue
                }

                // Also copy WAL and SHM files if they exist (for consistent reads)
                val walPath = "$dbPath-wal"
                val shmPath = "$dbPath-shm"
                if (ShizukuHelper.fileExists(walPath)) {
                    ShizukuHelper.copyFile(walPath, "${tempDb.absolutePath}-wal")
                }
                if (ShizukuHelper.fileExists(shmPath)) {
                    ShizukuHelper.copyFile(shmPath, "${tempDb.absolutePath}-shm")
                }

                // Open and read the DB
                val db = SQLiteDatabase.openDatabase(
                    tempDb.absolutePath, null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )

                // Scan URLs
                try {
                    val cursor = db.rawQuery(
                        "SELECT url, title, visit_count, last_visit_time FROM urls ORDER BY last_visit_time DESC LIMIT 5000",
                        null
                    )
                    while (cursor.moveToNext()) {
                        totalUrls++
                        val url = cursor.getString(0) ?: ""
                        val title = cursor.getString(1) ?: ""
                        val visitCount = cursor.getInt(2)
                        val lastVisitTime = cursor.getLong(3)
                        val visitStr = chromeTimestampToString(lastVisitTime)
                        val urlLower = url.lowercase()
                        val titleLower = title.lowercase()

                        for (pattern in SUSPICIOUS_URL_PATTERNS) {
                            if (pattern.lowercase() in urlLower || pattern.lowercase() in titleLower) {
                                suspiciousUrls.add(SuspiciousUrl(url, title, visitCount, visitStr, pattern))
                                break
                            }
                        }
                    }
                    cursor.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading URLs: ${e.message}")
                }

                // Scan downloads
                try {
                    val cursor = db.rawQuery(
                        "SELECT target_path, tab_url, total_bytes, start_time FROM downloads ORDER BY start_time DESC LIMIT 2000",
                        null
                    )
                    while (cursor.moveToNext()) {
                        totalDownloads++
                        val targetPath = cursor.getString(0) ?: ""
                        val tabUrl = cursor.getString(1) ?: ""
                        val totalBytes = cursor.getLong(2)
                        val startTime = cursor.getLong(3)
                        val filename = try { File(targetPath).name } catch (_: Exception) { targetPath }
                        val filenameLower = filename.lowercase()
                        val urlLower = tabUrl.lowercase()
                        val timeStr = chromeTimestampToString(startTime)

                        for (pattern in SUSPICIOUS_DOWNLOAD_PATTERNS) {
                            if (pattern.lowercase() in filenameLower || pattern.lowercase() in urlLower) {
                                suspiciousDownloads.add(SuspiciousDownload(
                                    filename, targetPath, tabUrl,
                                    try { bytesToMb(totalBytes) } catch (_: Exception) { 0f },
                                    timeStr, pattern
                                ))
                                break
                            }
                        }
                    }
                    cursor.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading downloads: ${e.message}")
                }

                db.close()
                Log.i(TAG, "Scanned $dbPath: $totalUrls URLs, $totalDownloads downloads")

            } catch (e: Exception) {
                Log.e(TAG, "Error processing Chrome DB $dbPath: ${e.message}")
                if (errorMsg == null) errorMsg = "Error: ${e.message}"
            } finally {
                try {
                    tempDb?.delete()
                    File("${tempDb?.absolutePath}-wal").delete()
                    File("${tempDb?.absolutePath}-shm").delete()
                } catch (_: Exception) {}
            }
        }

        if (profilesFound == 0) {
            errorMsg = "No Chrome/browser databases found (checked ${CHROME_DB_PATHS.size} locations via Shizuku)"
        }

        return ChromeScanResult(
            profilesFound = profilesFound,
            suspiciousUrls = suspiciousUrls,
            suspiciousDownloads = suspiciousDownloads,
            totalUrlsScanned = totalUrls,
            totalDownloadsScanned = totalDownloads,
            error = errorMsg
        )
    }

    private fun chromeTimestampToString(timestamp: Long): String {
        return try {
            val epochOffset = 11644473600000L
            val millis = (timestamp / 1000) - epochOffset
            if (millis > 0) dateFormat.format(Date(millis)) else "Unknown"
        } catch (_: Exception) { "Unknown" }
    }
}
