package com.sstools.anticheat.scanner

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/**
 * JAR File Inspector for Android
 * Reads .jar/.zip files, extracts .class names,
 * reads string constants from Java bytecode constant pool,
 * and runs cheat detection.
 */
object JarInspector {

    data class JarScanResult(
        val path: String,
        val filename: String,
        val sizeMb: Float,
        val classFiles: List<String>,
        val textFiles: List<String>,
        val totalEntries: Int,
        val scannedClasses: Int,
        val whitelisted: Boolean,
        val isDisguised: Boolean,
        val flagged: Boolean,
        val safe: Boolean,
        val maxSeverity: String,
        val detections: List<DetectionResult>,
        val authenticity: AuthenticityResult?,
        val fabricModJson: String?,
        val modInfo: String?,
        val manifest: String?,
        val error: String? = null
    )

    fun extractStringsFromClass(classBytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        try {
            if (classBytes.size < 10) return strings
            val buf = ByteBuffer.wrap(classBytes).order(ByteOrder.BIG_ENDIAN)

            // Check magic number 0xCAFEBABE
            val magic = buf.int.toLong() and 0xFFFFFFFFL
            if (magic != 0xCAFEBABEL) return strings

            // Skip version
            buf.short // minor
            buf.short // major
            val cpCount = buf.short.toInt() and 0xFFFF

            var i = 1
            while (i < cpCount && buf.hasRemaining()) {
                val tag = buf.get().toInt() and 0xFF
                when (tag) {
                    1 -> { // CONSTANT_Utf8
                        if (buf.remaining() < 2) break
                        val length = buf.short.toInt() and 0xFFFF
                        if (buf.remaining() < length) break
                        val bytes = ByteArray(length)
                        buf.get(bytes)
                        try {
                            val s = String(bytes, Charsets.UTF_8)
                            if (s.length >= 3 && !s.all { it in " \t\n\r" }) {
                                strings.add(s)
                            }
                        } catch (_: Exception) {}
                    }
                    7, 8, 16, 19, 20 -> { if (buf.remaining() >= 2) buf.short else break }
                    3, 4, 9, 10, 11, 12, 17, 18 -> { if (buf.remaining() >= 4) buf.int else break }
                    5, 6 -> { if (buf.remaining() >= 8) buf.long else break; i++ }
                    15 -> { if (buf.remaining() >= 3) { buf.get(); buf.short } else break }
                    else -> break
                }
                i++
            }
        } catch (_: Exception) {}
        return strings
    }

    fun inspectJar(jarFile: File): JarScanResult {
        val filename = jarFile.name
        val sizeMb = jarFile.length().toFloat() / (1024 * 1024)
        val claimsWhitelisted = CheatDetector.isWhitelisted(filename)

        val classFiles = mutableListOf<String>()
        val textFiles = mutableListOf<String>()
        var totalEntries = 0
        var scannedClasses = 0
        var fabricModJson: String? = null
        var modInfo: String? = null
        var manifest: String? = null
        val allDetections = mutableListOf<DetectionResult>()
        val allStrings = mutableListOf<String>()

        try {
            val zf = ZipFile(jarFile)
            totalEntries = zf.size()

            for (entry in zf.entries()) {
                val entryName = entry.name
                val entryLower = entryName.lowercase()

                when {
                    entryLower.endsWith(".class") -> classFiles.add(entryName)
                    entryLower.endsWith(".json") || entryLower.endsWith(".toml") ||
                    entryLower.endsWith(".txt") || entryLower.endsWith(".cfg") ||
                    entryLower.endsWith(".properties") || entryLower.endsWith(".yml") ||
                    entryLower.endsWith(".yaml") || entryLower.endsWith(".xml") -> textFiles.add(entryName)
                }

                // Read metadata
                when {
                    entryName == "META-INF/MANIFEST.MF" -> manifest = readEntry(zf.getInputStream(entry))
                    entryName == "fabric.mod.json" -> fabricModJson = readEntry(zf.getInputStream(entry))
                    entryLower in listOf("mcmod.info", "mods.toml") -> modInfo = readEntry(zf.getInputStream(entry))
                }
            }

            // Scan class files
            for (classFile in classFiles) {
                try {
                    val entry = zf.getEntry(classFile) ?: continue
                    val classBytes = zf.getInputStream(entry).readBytes()
                    val strings = extractStringsFromClass(classBytes)
                    scannedClasses++
                    allStrings.addAll(strings)

                    val content = strings.joinToString(" ") + " " + classFile
                    val detections = CheatDetector.detectCheats(content, classFile, "${jarFile.path}!/$classFile")
                    allDetections.addAll(detections)
                } catch (_: Exception) { continue }
            }

            // Scan text files
            for (textFile in textFiles) {
                try {
                    val entry = zf.getEntry(textFile) ?: continue
                    val textContent = readEntry(zf.getInputStream(entry))
                    val detections = CheatDetector.detectCheats(textContent, textFile, "${jarFile.path}!/$textFile")
                    allDetections.addAll(detections)
                } catch (_: Exception) { continue }
            }

            // Scan metadata
            manifest?.let { allDetections.addAll(CheatDetector.detectCheats(it, "MANIFEST.MF", "${jarFile.path}!/META-INF/MANIFEST.MF")) }
            fabricModJson?.let { allDetections.addAll(CheatDetector.detectCheats(it, "fabric.mod.json", "${jarFile.path}!/fabric.mod.json")) }
            modInfo?.let { allDetections.addAll(CheatDetector.detectCheats(it, "mod_info", "${jarFile.path}!/mod_info")) }

            // Combined string check
            val combined = allStrings.joinToString(" ")
            val combinedDetections = CheatDetector.detectCheats(combined, filename, jarFile.path)
            val existingSigs = allDetections.map { it.signatureName }.toSet()
            for (d in combinedDetections) {
                if (d.signatureName !in existingSigs) allDetections.add(d)
            }

            zf.close()
        } catch (e: Exception) {
            return JarScanResult(jarFile.path, filename, sizeMb, emptyList(), emptyList(), 0, 0,
                false, false, false, false, "none", emptyList(), null, null, null, null, e.message)
        }

        // Deduplicate
        val uniqueDetections = allDetections.distinctBy { it.signatureName to it.filePath }

        // Authenticity verification
        val authenticity = CheatDetector.verifyModAuthenticity(filename, classFiles)
        var isDisguised = false

        if (claimsWhitelisted && !authenticity.isAuthentic) {
            isDisguised = true
            val disguiseDetection = DetectionResult(
                signatureName = "Disguised Cheat (Fake Whitelisted Mod)",
                category = "Evasion",
                severity = "critical",
                description = "This JAR is named like '${authenticity.claimedMod}' but does NOT contain expected package structure. Expected: ${authenticity.expectedPackages.joinToString(", ")}. Found: ${authenticity.foundMatching.joinToString(", ").ifEmpty { "NONE" }}. Likely a disguised cheat.",
                matchedPatterns = listOf("fake_name:${authenticity.claimedMod}"),
                matchCount = 1,
                filePath = jarFile.path,
                confidence = 0.95f
            )
            (uniqueDetections as MutableList).add(0, disguiseDetection)
        }

        val flagged = uniqueDetections.isNotEmpty()
        val severityOrder = mapOf("critical" to 4, "high" to 3, "medium" to 2, "low" to 1)
        val maxSeverity = if (flagged) uniqueDetections.maxByOrNull { severityOrder[it.severity] ?: 0 }?.severity ?: "none" else "none"
        val verifiedWhitelisted = claimsWhitelisted && authenticity.isAuthentic && !flagged

        return JarScanResult(
            path = jarFile.path,
            filename = filename,
            sizeMb = roundMb(sizeMb),
            classFiles = classFiles.take(200),
            textFiles = textFiles,
            totalEntries = totalEntries,
            scannedClasses = scannedClasses,
            whitelisted = verifiedWhitelisted,
            isDisguised = isDisguised,
            flagged = flagged,
            safe = verifiedWhitelisted || !flagged,
            maxSeverity = maxSeverity,
            detections = uniqueDetections,
            authenticity = if (claimsWhitelisted) authenticity else null,
            fabricModJson = fabricModJson,
            modInfo = modInfo,
            manifest = manifest
        )
    }

    private fun readEntry(inputStream: InputStream): String {
        return inputStream.bufferedReader().use { it.readText() }
    }

    fun scanModsDirectory(modsDir: File): List<JarScanResult> {
        if (!modsDir.isDirectory) return emptyList()
        return modsDir.walkTopDown()
            .filter { it.isFile && (it.extension.lowercase() in listOf("jar", "zip")) }
            .map { inspectJar(it) }
            .toList()
    }
}
