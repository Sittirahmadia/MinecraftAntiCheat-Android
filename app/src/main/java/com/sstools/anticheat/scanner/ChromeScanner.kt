package com.sstools.anticheat.scanner

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chrome / Browser History Scanner for Android (non-root)
 * Reads Chrome history database via Shizuku or direct access
 * to find cheat-related URLs and downloads.
 */
object ChromeScanner {

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
        "mpgh.net/forum/minecraft",
        "unknowncheats.me/minecraft",
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

    /**
     * Scan Chrome history by directly reading the History database.
     * On Android 11+, this requires Shizuku or the file to be copied first.
     */
    fun scanChromeHistory(context: Context): ChromeScanResult {
        val profiles = getChromeDbPaths()
        val suspiciousUrls = mutableListOf<SuspiciousUrl>()
        val suspiciousDownloads = mutableListOf<SuspiciousDownload>()
        var totalUrls = 0
        var totalDownloads = 0

        for (dbPath in profiles) {
            try {
                // Try to copy the db to a temp location to avoid locks
                val tempDb = File(context.cacheDir, "chrome_history_temp_${System.currentTimeMillis()}.db")
                File(dbPath).copyTo(tempDb, overwrite = true)

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
                } catch (_: Exception) {}

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

                        val filename = File(targetPath).name
                        val filenameLower = filename.lowercase()
                        val urlLower = tabUrl.lowercase()
                        val timeStr = chromeTimestampToString(startTime)

                        for (pattern in SUSPICIOUS_DOWNLOAD_PATTERNS) {
                            if (pattern.lowercase() in filenameLower || pattern.lowercase() in urlLower) {
                                suspiciousDownloads.add(SuspiciousDownload(
                                    filename, targetPath, tabUrl,
                                    "%.2f".format(totalBytes.toFloat() / (1024 * 1024)).toFloat(),
                                    timeStr, pattern
                                ))
                                break
                            }
                        }
                    }
                    cursor.close()
                } catch (_: Exception) {}

                db.close()
                tempDb.delete()
            } catch (_: Exception) { continue }
        }

        return ChromeScanResult(
            profilesFound = profiles.size,
            suspiciousUrls = suspiciousUrls,
            suspiciousDownloads = suspiciousDownloads,
            totalUrlsScanned = totalUrls,
            totalDownloadsScanned = totalDownloads,
            error = if (profiles.isEmpty()) "No Chrome/browser profiles found (may need Shizuku)" else null
        )
    }

    private fun getChromeDbPaths(): List<String> {
        val paths = mutableListOf<String>()
        val baseDirs = listOf(
            // Chrome
            "/data/data/com.android.chrome/app_chrome/Default/History",
            "/data/data/com.android.chrome/app_chrome/Profile 1/History",
            // Chrome Beta
            "/data/data/com.chrome.beta/app_chrome/Default/History",
            // Brave
            "/data/data/com.brave.browser/app_chrome/Default/History",
            // Edge
            "/data/data/com.microsoft.emmx/app_chrome/Default/History",
            // Opera
            "/data/data/com.opera.browser/app_chrome/Default/History",
            // Samsung Internet
            "/data/data/com.sec.android.app.sbrowser/app_sbrowser/Default/History",
        )

        for (path in baseDirs) {
            if (File(path).exists()) paths.add(path)
        }

        return paths
    }

    private fun chromeTimestampToString(timestamp: Long): String {
        return try {
            // Chrome timestamps are microseconds since 1601-01-01
            val epochOffset = 11644473600000L // ms between 1601 and 1970
            val millis = (timestamp / 1000) - epochOffset
            if (millis > 0) dateFormat.format(Date(millis)) else "Unknown"
        } catch (_: Exception) { "Unknown" }
    }
}
