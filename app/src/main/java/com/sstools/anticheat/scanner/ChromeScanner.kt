package com.sstools.anticheat.scanner

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chrome / Browser History Scanner for Android
 * All operations wrapped in try-catch to prevent crashes.
 * Chrome DB access requires Shizuku or root — gracefully fails if unavailable.
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

    fun scanChromeHistory(context: Context): ChromeScanResult {
        val suspiciousUrls = mutableListOf<SuspiciousUrl>()
        val suspiciousDownloads = mutableListOf<SuspiciousDownload>()
        var totalUrls = 0
        var totalDownloads = 0
        var profilesFound = 0
        var errorMsg: String? = null

        val dbPaths = getChromeDbPaths()
        profilesFound = dbPaths.size

        if (dbPaths.isEmpty()) {
            // Chrome DB files are in /data/data/ which requires root or Shizuku
            // On non-rooted devices without proper Shizuku IPC, we can't read them
            errorMsg = "Browser history requires Shizuku with proper IPC or root access. " +
                       "Chrome databases are in protected /data/data/ directory."
            Log.d(TAG, errorMsg)
        }

        for (dbPath in dbPaths) {
            var tempDb: File? = null
            try {
                val sourceFile = File(dbPath)
                if (!sourceFile.exists() || !sourceFile.canRead()) {
                    Log.d(TAG, "Cannot read Chrome DB: $dbPath")
                    continue
                }

                // Copy to temp to avoid locks
                tempDb = File(context.cacheDir, "chrome_history_${System.currentTimeMillis()}.db")
                sourceFile.copyTo(tempDb, overwrite = true)

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
                                    try { "%.2f".format(totalBytes.toFloat() / (1024 * 1024)).toFloat() } catch (_: Exception) { 0f },
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
            } catch (e: Exception) {
                Log.e(TAG, "Error processing Chrome DB $dbPath: ${e.message}")
                if (errorMsg == null) errorMsg = "Error reading browser DB: ${e.message}"
            } finally {
                try { tempDb?.delete() } catch (_: Exception) {}
            }
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

    private fun getChromeDbPaths(): List<String> {
        val paths = mutableListOf<String>()
        val candidates = listOf(
            "/data/data/com.android.chrome/app_chrome/Default/History",
            "/data/data/com.android.chrome/app_chrome/Profile 1/History",
            "/data/data/com.chrome.beta/app_chrome/Default/History",
            "/data/data/com.brave.browser/app_chrome/Default/History",
            "/data/data/com.microsoft.emmx/app_chrome/Default/History",
            "/data/data/com.opera.browser/app_chrome/Default/History",
        )

        for (path in candidates) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    paths.add(path)
                }
            } catch (_: Exception) {
                // Expected on non-rooted devices
            }
        }

        return paths
    }

    private fun chromeTimestampToString(timestamp: Long): String {
        return try {
            val epochOffset = 11644473600000L
            val millis = (timestamp / 1000) - epochOffset
            if (millis > 0) dateFormat.format(Date(millis)) else "Unknown"
        } catch (_: Exception) { "Unknown" }
    }
}
