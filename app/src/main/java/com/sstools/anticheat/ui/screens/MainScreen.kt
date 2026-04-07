package com.sstools.anticheat.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sstools.anticheat.MainViewModel
import com.sstools.anticheat.ScanState
import com.sstools.anticheat.scanner.*
import com.sstools.anticheat.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestShizuku: () -> Unit,
    onRequestStorage: () -> Unit
) {
    val state by viewModel.scanState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Purple, Pink))),
                            contentAlignment = Alignment.Center
                        ) { Text("AC", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        Column {
                            Text("SS Tools", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Minecraft Anti-Cheat Scanner", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                },
                actions = {
                    // Shizuku status
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (state.shizukuGranted) Success.copy(alpha = 0.15f) else Danger.copy(alpha = 0.15f))
                            .clickable { onRequestShizuku() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (state.shizukuGranted) "Shizuku OK" else "Shizuku",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = if (state.shizukuGranted) Success else Danger
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Status cards
            item { StatusSection(state) }

            // Scan buttons
            if (!state.isScanning && !state.scanComplete) {
                item { ScanButtons(viewModel, state) }
            }

            // Progress
            if (state.isScanning) {
                item { ProgressSection(state) }
            }

            // Results
            if (state.scanComplete) {
                item { VerdictBanner(state) }
                item { StatCards(state) }

                // Launcher results
                if (state.launcherResults.isNotEmpty()) {
                    item { SectionTitle("Detected Launchers", Icons.Default.SportsEsports) }
                    items(state.launcherResults) { launcher -> LauncherCard(launcher) }
                }

                // Flagged mods
                val flaggedMods = state.modScanResults.filter { it.flagged }
                if (flaggedMods.isNotEmpty()) {
                    item { SectionTitle("Flagged Mods", Icons.Default.Warning) }
                    items(flaggedMods) { mod -> ModResultCard(mod) }
                }

                // Clean mods (collapsed)
                val cleanMods = state.modScanResults.filter { !it.flagged }
                if (cleanMods.isNotEmpty()) {
                    item { SectionTitle("Clean Mods (${cleanMods.size})", Icons.Default.CheckCircle) }
                    items(cleanMods.take(20)) { mod -> ModResultCard(mod) }
                }

                // Log findings
                val logFindings = state.launcherResults.flatMap { l -> l.logFindings.map { it to l.name } }
                if (logFindings.isNotEmpty()) {
                    item { SectionTitle("Suspicious Log Entries", Icons.Default.Description) }
                    items(logFindings) { (finding, launcher) -> LogFindingCard(finding, launcher) }
                }

                // Deleted files
                state.deletedFileScanResult?.let { deleted ->
                    if (deleted.flaggedItems.isNotEmpty()) {
                        item { SectionTitle("Suspicious Files", Icons.Default.Delete) }
                        items(deleted.flaggedItems) { file -> DeletedFileCard(file) }
                    }
                }

                // Chrome
                state.chromeScanResult?.let { chrome ->
                    if (chrome.suspiciousUrls.isNotEmpty() || chrome.suspiciousDownloads.isNotEmpty()) {
                        item { SectionTitle("Suspicious Browser Activity", Icons.Default.Language) }
                        items(chrome.suspiciousUrls) { url -> ChromeUrlCard(url) }
                        items(chrome.suspiciousDownloads) { dl -> ChromeDownloadCard(dl) }
                    }
                }

                // Reset button
                item {
                    Button(
                        onClick = { viewModel.resetScan() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCard)
                    ) { Text("Reset & Scan Again") }
                }
            }

            // Error
            state.error?.let { error ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = 0.1f))) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Error, null, tint = Danger)
                            Text(error, color = Danger, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusSection(state: ScanState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatusChip("${CheatDetector.SIGNATURES.size} Sigs", Icons.Default.Shield, Purple)
        StatusChip("MC 1.21+", Icons.Default.SportsEsports, Info)
        StatusChip(if (state.shizukuGranted) "Shizuku OK" else "No Shizuku", Icons.Default.Security,
            if (state.shizukuGranted) Success else Warning)
    }
}

@Composable
fun StatusChip(text: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun ScanButtons(viewModel: MainViewModel, state: ScanState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Full scan button
        Button(
            onClick = { viewModel.runFullScan() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Full Auto Scan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Launchers + Mods + Deleted Files + Chrome", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
            }
        }

        // Minecraft only
        OutlinedButton(
            onClick = { viewModel.scanMinecraftOnly() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            Icon(Icons.Default.SportsEsports, null, tint = PurpleLight, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Minecraft Only (Mods + Logs + Classes)", fontSize = 13.sp, color = TextPrimary)
        }

        // Supported launchers info
        Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Supported Launchers", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Zalith", "Zalith 2", "Mojo", "PojavLauncher", "FCL", "HMCL-PE").forEach { name ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Info.copy(alpha = 0.1f))
                                .border(1.dp, Info.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text(name, fontSize = 11.sp, color = Info) }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressSection(state: ScanState) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = Purple)
                Text("Scanning...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = state.progress,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Purple,
                trackColor = DarkBorder,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.currentTask, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${(state.progress * 100).toInt()}%", fontSize = 12.sp, color = PurpleLight, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VerdictBanner(state: ScanState) {
    val isClean = state.verdict == "CLEAN"
    val color = if (isClean) Success else Danger

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (isClean) "CLEAN" else "FLAGGED", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Spacer(Modifier.height(4.dp))
            Text(
                if (isClean) "No cheats detected across all scans"
                else "${state.totalFlags} suspicious item(s) found",
                fontSize = 14.sp, color = TextSecondary
            )
        }
    }
}

@Composable
fun StatCards(state: ScanState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard("Launchers", "${state.launcherResults.size}", Info, Modifier.weight(1f))
        StatCard("Mods", "${state.modScanResults.size}", PurpleLight, Modifier.weight(1f))
        StatCard("Flags", "${state.totalFlags}", if (state.totalFlags > 0) Danger else Success, Modifier.weight(1f))
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
fun SectionTitle(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)) {
        Icon(icon, null, tint = PurpleLight, modifier = Modifier.size(20.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun LauncherCard(launcher: MinecraftScanner.LauncherScanResult) {
    val hasIssues = launcher.logFindings.isNotEmpty()
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (hasIssues) Danger.copy(alpha = 0.3f) else DarkBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (hasIssues) "!" else "OK", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (hasIssues) Danger else Success,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background((if (hasIssues) Danger else Success).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp))
                    Text(launcher.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                SeverityBadge("${launcher.mods.size} mods", Info)
            }
            Spacer(Modifier.height(6.dp))
            Text(launcher.path, fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (launcher.logFindings.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("${launcher.logFindings.size} log warning(s)", fontSize = 12.sp, color = Danger, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ModResultCard(mod: JarInspector.JarScanResult) {
    val color = when {
        mod.isDisguised -> Critical
        mod.flagged -> Danger
        mod.whitelisted -> Success
        else -> Success
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)) {
                    Text(
                        when { mod.isDisguised -> "FAKE"; mod.flagged -> "FLAG"; mod.whitelisted -> "OK"; else -> "OK" },
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(mod.filename, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${mod.sizeMb}MB", fontSize = 11.sp, color = TextSecondary)
            }

            if (mod.isDisguised) {
                Spacer(Modifier.height(6.dp))
                Text("Pretends to be \"${mod.authenticity?.claimedMod}\" but has wrong packages!",
                    fontSize = 12.sp, color = Danger, fontWeight = FontWeight.SemiBold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text("${mod.classFiles.size} classes", fontSize = 11.sp, color = TextSecondary)
                Text("${mod.scannedClasses} scanned", fontSize = 11.sp, color = TextSecondary)
            }

            if (mod.detections.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Divider(color = DarkBorder, thickness = 1.dp)
                Spacer(Modifier.height(8.dp))
                for (d in mod.detections) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                        SeverityBadge(d.severity, severityColor(d.severity))
                        Column {
                            Text("${d.signatureName} - ${d.category}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(d.matchedPatterns.joinToString(", "), fontSize = 10.sp, color = TextSecondary,
                                fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogFindingCard(finding: MinecraftScanner.LogFinding, launcher: String) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, severityColor(finding.severity).copy(alpha = 0.3f))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(finding.matchedPattern, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                SeverityBadge(finding.severity, severityColor(finding.severity))
            }
            Spacer(Modifier.height(6.dp))
            Text("${finding.logFile} : line ${finding.lineNumber} ($launcher)", fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text(finding.line, fontSize = 10.sp, color = TextSecondary.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(DarkBg.copy(alpha = 0.5f)).padding(8.dp).fillMaxWidth())
        }
    }
}

@Composable
fun DeletedFileCard(file: DeletedFileScanner.SuspiciousFile) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Danger.copy(alpha = 0.3f))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(file.filename, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                SeverityBadge(file.source, High)
            }
            Text("${file.sizeMb}MB - ${file.lastModified}", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
fun ChromeUrlCard(url: ChromeScanner.SuspiciousUrl) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, High.copy(alpha = 0.3f))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(url.title.ifEmpty { url.url.take(50) }, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                SeverityBadge(url.matchedPattern, High)
            }
            Text(url.url, fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${url.visitCount} visits - ${url.lastVisit}", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
fun ChromeDownloadCard(dl: ChromeScanner.SuspiciousDownload) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, High.copy(alpha = 0.3f))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(dl.filename, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                SeverityBadge(dl.matchedPattern, High)
            }
            Text("${dl.sizeMb}MB - ${dl.downloadTime}", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
fun SeverityBadge(text: String, color: Color) {
    Text(text.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.5.sp,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
}

fun severityColor(severity: String): Color = when (severity.lowercase()) {
    "critical" -> Critical
    "high" -> High
    "medium" -> Medium
    "low" -> Low
    else -> Info
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}
