package com.sstools.anticheat.scanner

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chrome / Browser History Scanner for Android
 *
 * Non-root mode (Android 12-16):
 *  - Scans Downloads folder for suspicious filenames (always accessible)
 *  - Scans browser download folders (accessible via public storage)
 *  - Reads any browser history DB that was exported to public storage
 *
 * Shizuku mode (root/ADB):
 *  - Copies Chrome History SQLite DB from /data/data/
 *  - Reads URLs and downloads tables directly
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
        "rusherhack.org", "futureclient.net", "phobosclient",
        "198macro", "zenithmacro", "crystalmacro",
        "minecrafthacks", "minecraft-hacks", "hackphoenix",
        "wizardhax.com", "mc-hacks", "ghostclient",
        "thunderhack", "bleachhack", "coffeeclient",
        "inertiaclient", "forgehax", "salhack",
        "vape.gg", "dream.help", "entropy.club", "itami.io",
        "whiteout.lol", "antic.lol", "skilled.club", "juul.lol",
        "astolfo.lgbt", "novoline.wtf", "riseclient.com",
        "tenacity.dev", "moonclient.xyz", "flux.today",
    )

    private val SUSPICIOUS_DOWNLOAD_PATTERNS = listOf(
        "198macro", "zenithmacro", "crystalmacro", "autoclicker",
        "meteor-client", "meteor_client", "wurst", "impact",
        "aristois", "liquidbounce", "sigma", "rusherhack",
        "futureclient", "phobos", "konas", "gamesense",
        "earthhack", "salhack", "forgehax", "bleachhack",
        "thunderhack", "coffeeclient", "injector", "cheatengine",
        "aimassist", "killaura", "triggerbot", "inertiaclient",
        "xray-client", "scaffold-mod", "vape", "entropy", "whiteout",
        "skilled", "juul", "astolfo", "novoline", "riseclient",
        "tenacity", "moonclient", "flux", "raven-b", "raven-ni",
    )

    // Chrome DB locations — require Shizuku or root
    private val CHROME_DB_PATHS = listOf(
        "/data/data/com.android.chrome/app_chrome/Default/History",
        "/data/data/com.android.chrome/app_chrome/Profile 1/History",
        "/data/data/com.chrome.beta/app_chrome/Default/History",
        "/data/data/com.brave.browser/app_chrome/Default/History",
        "/data/data/com.microsoft.emmx/app_chrome/Default/History",
        "/data/data/com.opera.browser/app_chrome/Default/History",
        "/data/data/org.mozilla.firefox/files/mozilla",
        "/data/data/com.sec.android.app.sbrowser/app_sbrowser/Default/History", // Samsung Browser
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun scanChromeHistory(context: Context): ChromeScanResult {
        val suspiciousUrls = mutableListOf<SuspiciousUrl>()
        val suspiciousDownloads = mutableListOf<SuspiciousDownload>()
        var totalUrls = 0
        var totalDownloads = 0
        var profilesFound = 0
        var errorMsg: String? = null

        val useShizuku = try { ShizukuHelper.isAvailable() } catch (_: Exception) { false }
        Log.i(TAG, "scanChromeHistory: Shizuku=$useShizuku")

        // ─── Non-root fallback: scan Downloads for browser-downloaded cheat files ───
        // This ALWAYS works without root on Android 12-16
        val dlDirs = listOf(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)?.absolutePath
                ?: "/storage/emulated/0/Download",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Downloads",
        ).distinct()

        for (dlDir in dlDirs) {
            try {
                val dir = File(dlDir)
                if (!dir.exists()) continue
                dir.listFiles()?.filter { it.isFile }?.forEach { file ->
                    totalDownloads++
                    val filenameLower = file.name.lowercase()
                    for (pattern in SUSPICIOUS_DOWNLOAD_PATTERNS) {
                        if (pattern.lowercase() in filenameLower) {
                            profilesFound = maxOf(profilesFound, 1)
                            suspiciousDownloads.add(
                                SuspiciousDownload(
                                    filename = file.name,
                                    filePath = file.absolutePath,
                                    sourceUrl = "Download folder",
                                    sizeMb = bytesToMb(file.length()),
                                    downloadTime = try {
                                        dateFormat.format(Date(file.lastModified()))
                                    } catch (_: Exception) { "Unknown" },
                                    matchedPattern = pattern
                                )
                            )
                            break
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // ─── Shizuku path: read actual Chrome SQLite databases ───
        if (useShizuku) {
            for (dbPath in CHROME_DB_PATHS) {
                var tempDb: File? = null
                try {
                    if (!ShizukuHelper.fileExists(dbPath)) continue
                    profilesFound++
                    Log.i(TAG, "Found browser DB: $dbPath")

                    tempDb = File(context.cacheDir, "chrome_${System.currentTimeMillis()}.db")
                    val copied = ShizukuHelper.copyFile(dbPath, tempDb.absolutePath)
                    if (!copied || !tempDb.exists() || tempDb.length() == 0L) {
                        Log.e(TAG, "Failed to copy DB from $dbPath")
                        continue
                    }

                    // Copy WAL/SHM for consistent reads
                    if (ShizukuHelper.fileExists("$dbPath-wal"))
                        ShizukuHelper.copyFile("$dbPath-wal", "${tempDb.absolutePath}-wal")
                    if (ShizukuHelper.fileExists("$dbPath-shm"))
                        ShizukuHelper.copyFile("$dbPath-shm", "${tempDb.absolutePath}-shm")

                    val db = try {
                        SQLiteDatabase.openDatabase(
                            tempDb.absolutePath, null,
                            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Cannot open DB: ${e.message}")
                        continue
                    }

                    // Scan URLs table
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
                            val visitStr = chromeTimestamp(lastVisitTime)
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

                    // Scan downloads table
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
                            for (pattern in SUSPICIOUS_DOWNLOAD_PATTERNS) {
                                if (pattern.lowercase() in filenameLower || pattern.lowercase() in urlLower) {
                                    suspiciousDownloads.add(
                                        SuspiciousDownload(
                                            filename, targetPath, tabUrl,
                                            try { bytesToMb(totalBytes) } catch (_: Exception) { 0f },
                                            chromeTimestamp(startTime), pattern
                                        )
                                    )
                                    break
                                }
                            }
                        }
                        cursor.close()
                    } catch (_: Exception) {}

                    db.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing $dbPath: ${e.message}")
                    if (errorMsg == null) errorMsg = "Error DB: ${e.message}"
                } finally {
                    try {
                        tempDb?.delete()
                        File("${tempDb?.absolutePath}-wal").delete()
                        File("${tempDb?.absolutePath}-shm").delete()
                    } catch (_: Exception) {}
                }
            }
        }

        // Build result message
        if (profilesFound == 0 && !useShizuku) {
            errorMsg = "Scan browser tanpa Shizuku: hanya folder Download yang di-scan.\n" +
                       "Install Shizuku untuk membaca riwayat Chrome secara langsung."
        } else if (profilesFound == 0 && useShizuku) {
            errorMsg = "Tidak ada database browser ditemukan (${CHROME_DB_PATHS.size} lokasi dicek)."
        }

        // Deduplicate downloads
        val uniqueDownloads = suspiciousDownloads.distinctBy { it.filePath + it.matchedPattern }

        return ChromeScanResult(
            profilesFound = profilesFound,
            suspiciousUrls = suspiciousUrls.distinctBy { it.url + it.matchedPattern },
            suspiciousDownloads = uniqueDownloads,
            totalUrlsScanned = totalUrls,
            totalDownloadsScanned = totalDownloads,
            error = errorMsg
        )
    }

    private fun chromeTimestamp(timestamp: Long): String {
        return try {
            val epochOffset = 11644473600000L
            val millis = (timestamp / 1000) - epochOffset
            if (millis > 0) dateFormat.format(Date(millis)) else "Unknown"
        } catch (_: Exception) { "Unknown" }
    }
}
