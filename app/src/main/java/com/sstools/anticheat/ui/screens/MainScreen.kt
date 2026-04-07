package com.sstools.anticheat.ui.screens

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
    onSelectFolder: () -> Unit,
    onRequestStorage: () -> Unit
) {
    val state by viewModel.scanState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Purple, Pink))),
                            contentAlignment = Alignment.Center
                        ) { Text("SS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        Column {
                            Text("SS Tools AntiCheat", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Minecraft Cheat Scanner", fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                },
                navigationIcon = {
                    if (state.currentScreen != "home") {
                        IconButton(onClick = { viewModel.resetScan(); viewModel.setScreen("home") }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { padding ->
        when (state.currentScreen) {
            "home" -> HomeScreen(viewModel, state, onSelectFolder, padding)
            "minecraft" -> MinecraftResultsScreen(viewModel, state, onSelectFolder, padding)
            "deleted" -> DeletedFilesScreen(viewModel, state, padding)
            "history" -> HistoryScreen(viewModel, state, padding)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// HOME SCREEN
// ═══════════════════════════════════════════════════════════
@Composable
fun HomeScreen(viewModel: MainViewModel, state: ScanState, onSelectFolder: () -> Unit, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Status chips
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatusChip("${CheatDetector.SIGNATURES.size} Sigs", Icons.Default.Shield, Purple)
                StatusChip("MC 1.21+", Icons.Default.SportsEsports, Info)
                StatusChip("Android 12-16", Icons.Default.PhoneAndroid, Success)
            }
        }

        // Main action: Select Minecraft Folder
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Purple.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Purple.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Minecraft Scanner", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Select your .minecraft folder to scan all mods, logs, and class files. " +
                        "Works on Android 12-16 with folder picker.",
                        fontSize = 13.sp, color = TextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onSelectFolder,
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Purple),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Select Folder", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.runAutoScan() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DarkBorder)
                        ) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = PurpleLight)
                            Spacer(Modifier.width(8.dp))
                            Text("Auto Detect", fontSize = 13.sp, color = TextPrimary)
                        }
                    }
                }
            }
        }

        // Full Auto Scan
        item {
            ActionCard(
                title = "Full Auto Scan",
                description = "Scans everything: Minecraft mods + Deleted files + Browser history",
                icon = Icons.Default.RocketLaunch,
                color = Pink,
                onClick = { viewModel.runFullScan() }
            )
        }

        // Deleted File Scanner
        item {
            ActionCard(
                title = "Deleted File Scanner",
                description = "Scans Downloads, Temp, Trash, Cache for cheat-related files",
                icon = Icons.Default.Delete,
                color = High,
                onClick = { viewModel.runDeletedFileScan() }
            )
        }

        // History Scanner
        item {
            ActionCard(
                title = "Browser History Scanner",
                description = "Scans Chrome/Edge/Brave for cheat-related URLs and downloads",
                icon = Icons.Default.Language,
                color = Info,
                onClick = { viewModel.runHistoryScan() }
            )
        }

        // Supported launchers
        item {
            Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Supported Launchers", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Zalith", "Zalith 2", "Mojo", "Pojav", "FCL", "HMCL-PE").forEach { name ->
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Info.copy(alpha = 0.1f))
                                .border(1.dp, Info.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text(name, fontSize = 11.sp, color = Info) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("How to scan:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text("1. Tap 'Select Folder'\n2. Navigate to Android > data > your launcher > files\n3. Select the .minecraft folder\n4. App will scan all mods, logs, and class files",
                        fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
fun ActionCard(title: String, description: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(description, fontSize = 12.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp), tint = TextSecondary)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// MINECRAFT RESULTS SCREEN
// ═══════════════════════════════════════════════════════════
@Composable
fun MinecraftResultsScreen(viewModel: MainViewModel, state: ScanState, onSelectFolder: () -> Unit, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Progress
        if (state.isScanning) {
            item { ProgressCard(state) }
        }

        // Error / No access message
        state.error?.let { error ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, Warning.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(error, fontSize = 13.sp, color = Warning)
                        if (state.verdict == "NO_ACCESS") {
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onSelectFolder, colors = ButtonDefaults.buttonColors(containerColor = Purple),
                                shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Select Minecraft Folder")
                            }
                        }
                    }
                }
            }
        }

        // Results
        if (state.scanComplete && state.verdict != "NO_ACCESS") {
            item { VerdictBanner(state) }
            item { StatCards(state) }

            // SAF result info
            state.safResult?.let { saf ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Scanned Folder", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(saf.folderPath, fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("${saf.modsFound.size} mods", fontSize = 12.sp, color = PurpleLight)
                                Text("${saf.logFindings.size} log flags", fontSize = 12.sp, color = if (saf.logFindings.isNotEmpty()) Danger else Success)
                                Text("${saf.versionsFound.size} versions", fontSize = 12.sp, color = Info)
                            }
                        }
                    }
                }
            }

            // Launcher results
            if (state.launcherResults.isNotEmpty()) {
                item { SectionTitle("Detected Launchers", Icons.Default.SportsEsports) }
                items(state.launcherResults) { launcher -> LauncherCard(launcher) }
            }

            // Flagged mods
            val flaggedMods = state.modScanResults.filter { it.flagged }
            if (flaggedMods.isNotEmpty()) {
                item { SectionTitle("Flagged Mods (${flaggedMods.size})", Icons.Default.Warning) }
                items(flaggedMods) { mod -> ModResultCard(mod) }
            }

            // SAF log findings
            state.safResult?.logFindings?.let { findings ->
                if (findings.isNotEmpty()) {
                    item { SectionTitle("Log Warnings (${findings.size})", Icons.Default.Description) }
                    items(findings) { finding -> SafLogCard(finding) }
                }
            }

            // Clean mods
            val cleanMods = state.modScanResults.filter { !it.flagged }
            if (cleanMods.isNotEmpty()) {
                item { SectionTitle("Clean Mods (${cleanMods.size})", Icons.Default.CheckCircle) }
                items(cleanMods.take(30)) { mod -> ModResultCard(mod) }
            }

            // Actions
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onSelectFolder, modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Purple.copy(alpha = 0.5f)), shape = RoundedCornerShape(10.dp)) {
                        Text("Scan Another Folder", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { viewModel.resetScan(); viewModel.setScreen("home") },
                        modifier = Modifier.weight(1f), border = BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(10.dp)) {
                        Text("Back to Home", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// DELETED FILES SCREEN
// ═══════════════════════════════════════════════════════════
@Composable
fun DeletedFilesScreen(viewModel: MainViewModel, state: ScanState, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        if (state.isScanning) { item { ProgressCard(state) } }

        state.error?.let { item { ErrorCard(it) } }

        state.deletedFileScanResult?.let { deleted ->
            item { VerdictBanner(state) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("Scanned", "${deleted.totalScanned}", Info, Modifier.weight(1f))
                    StatCard("Flagged", "${deleted.flaggedItems.size}", if (deleted.flaggedItems.isNotEmpty()) Danger else Success, Modifier.weight(1f))
                    StatCard("Trash", "${deleted.trashItems.size + deleted.recentlyDeleted.size}", Warning, Modifier.weight(1f))
                }
            }

            if (deleted.flaggedItems.isNotEmpty()) {
                item { SectionTitle("Flagged Files (${deleted.flaggedItems.size})", Icons.Default.Warning) }
                items(deleted.flaggedItems) { file -> FileCard(file) }
            }
            if (deleted.recentlyDeleted.isNotEmpty()) {
                item { SectionTitle("Recently Deleted (${deleted.recentlyDeleted.size})", Icons.Default.Delete) }
                items(deleted.recentlyDeleted) { file -> FileCard(file) }
            }
            if (deleted.downloadFiles.isNotEmpty()) {
                item { SectionTitle("Downloads (${deleted.downloadFiles.size})", Icons.Default.Download) }
                items(deleted.downloadFiles.take(30)) { file -> FileCard(file) }
            }
            if (deleted.trashItems.isNotEmpty()) {
                item { SectionTitle("Trash (${deleted.trashItems.size})", Icons.Default.Delete) }
                items(deleted.trashItems) { file -> FileCard(file) }
            }

            item {
                OutlinedButton(onClick = { viewModel.resetScan(); viewModel.setScreen("home") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkBorder)) { Text("Back to Home") }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// HISTORY SCREEN
// ═══════════════════════════════════════════════════════════
@Composable
fun HistoryScreen(viewModel: MainViewModel, state: ScanState, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        if (state.isScanning) { item { ProgressCard(state) } }

        state.error?.let { item { ErrorCard(it) } }

        state.chromeScanResult?.let { chrome ->
            item { VerdictBanner(state) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("Profiles", "${chrome.profilesFound}", Info, Modifier.weight(1f))
                    StatCard("URLs", "${chrome.totalUrlsScanned}", PurpleLight, Modifier.weight(1f))
                    StatCard("Suspicious", "${chrome.suspiciousUrls.size + chrome.suspiciousDownloads.size}",
                        if (chrome.suspiciousUrls.isNotEmpty() || chrome.suspiciousDownloads.isNotEmpty()) Danger else Success,
                        Modifier.weight(1f))
                }
            }

            if (chrome.suspiciousUrls.isNotEmpty()) {
                item { SectionTitle("Suspicious URLs (${chrome.suspiciousUrls.size})", Icons.Default.Link) }
                items(chrome.suspiciousUrls) { url -> ChromeUrlCard(url) }
            }
            if (chrome.suspiciousDownloads.isNotEmpty()) {
                item { SectionTitle("Suspicious Downloads (${chrome.suspiciousDownloads.size})", Icons.Default.Download) }
                items(chrome.suspiciousDownloads) { dl -> ChromeDownloadCard(dl) }
            }

            if (chrome.suspiciousUrls.isEmpty() && chrome.suspiciousDownloads.isEmpty() && chrome.error == null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Success.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Success.copy(alpha = 0.3f))) {
                        Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No suspicious browser activity found", fontSize = 14.sp, color = Success)
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = { viewModel.resetScan(); viewModel.setScreen("home") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkBorder)) { Text("Back to Home") }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════

@Composable
fun ProgressCard(state: ScanState) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(Purple))
                Text("Scanning...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(DarkBorder)) {
                Box(Modifier.fillMaxWidth(fraction = state.progress.coerceIn(0f, 1f)).height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(Purple, Pink))))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.currentTask, fontSize = 12.sp, color = TextSecondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${(state.progress * 100).toInt()}%", fontSize = 12.sp, color = PurpleLight, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VerdictBanner(state: ScanState) {
    val isClean = state.verdict == "CLEAN"
    val color = if (isClean) Success else Danger
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.3f))) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isClean) "CLEAN" else "FLAGGED", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(if (isClean) "No cheats detected" else "${state.totalFlags} suspicious item(s)",
                fontSize = 13.sp, color = TextSecondary)
        }
    }
}

@Composable
fun StatCards(state: ScanState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard("Mods", "${state.modScanResults.size}", PurpleLight, Modifier.weight(1f))
        StatCard("Flagged", "${state.modScanResults.count { it.flagged }}", Danger, Modifier.weight(1f))
        StatCard("Clean", "${state.modScanResults.count { !it.flagged }}", Success, Modifier.weight(1f))
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, fontSize = 10.sp, color = TextSecondary)
        }
    }
}

@Composable
fun ErrorCard(error: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, Warning.copy(alpha = 0.3f)), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp), tint = Warning)
            Text(error, fontSize = 12.sp, color = Warning)
        }
    }
}

@Composable
fun StatusChip(text: String, icon: ImageVector, color: Color) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f))
        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun SectionTitle(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)) {
        Icon(icon, null, tint = PurpleLight, modifier = Modifier.size(18.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun LauncherCard(launcher: MinecraftScanner.LauncherScanResult) {
    val hasIssues = launcher.logFindings.isNotEmpty()
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (hasIssues) Danger.copy(alpha = 0.3f) else DarkBorder)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(launcher.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                SeverityBadge("${launcher.mods.size} mods", Info)
            }
            Text(launcher.path, fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (launcher.logFindings.isNotEmpty()) {
                Text("${launcher.logFindings.size} log warning(s)", fontSize = 12.sp, color = Danger, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ModResultCard(mod: JarInspector.JarScanResult) {
    val color = when { mod.isDisguised -> Critical; mod.flagged -> Danger; mod.whitelisted -> Success; else -> Success }
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text(when { mod.isDisguised -> "FAKE"; mod.flagged -> "FLAG"; else -> "OK" },
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 5.dp, vertical = 2.dp))
                    Text(mod.filename, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${mod.sizeMb}MB", fontSize = 10.sp, color = TextSecondary)
            }
            if (mod.isDisguised) {
                Text("Pretends to be \"${mod.authenticity?.claimedMod}\" - WRONG packages!", fontSize = 11.sp, color = Danger, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 2.dp)) {
                Text("${mod.classFiles.size} classes", fontSize = 10.sp, color = TextSecondary)
                Text("${mod.scannedClasses} scanned", fontSize = 10.sp, color = TextSecondary)
            }
            if (mod.detections.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Divider(color = DarkBorder, thickness = 1.dp)
                Spacer(Modifier.height(6.dp))
                for (d in mod.detections) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                        SeverityBadge(d.severity, severityColor(d.severity))
                        Column {
                            Text("${d.signatureName} - ${d.category}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(d.matchedPatterns.joinToString(", "), fontSize = 9.sp, color = TextSecondary,
                                fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SafLogCard(finding: SafScanner.SafLogFinding) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, severityColor(finding.severity).copy(alpha = 0.3f))) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(finding.matchedPattern, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                SeverityBadge(finding.severity, severityColor(finding.severity))
            }
            Text("${finding.logFile} : line ${finding.lineNumber}", fontSize = 10.sp, color = TextSecondary)
            Text(finding.line, fontSize = 9.sp, color = TextSecondary.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(DarkBg.copy(alpha = 0.5f))
                    .padding(6.dp).fillMaxWidth())
        }
    }
}

@Composable
fun FileCard(file: DeletedFileScanner.SuspiciousFile) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (file.detections.isNotEmpty()) Danger.copy(alpha = 0.3f) else DarkBorder)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(file.filename, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                SeverityBadge(file.source, if (file.detections.isNotEmpty()) High else Info)
            }
            Text("${file.sizeMb}MB - ${file.lastModified}", fontSize = 10.sp, color = TextSecondary)
        }
    }
}

@Composable
fun ChromeUrlCard(url: ChromeScanner.SuspiciousUrl) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, High.copy(alpha = 0.3f))) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(url.title.ifEmpty { url.url.take(50) }, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                SeverityBadge(url.matchedPattern, High)
            }
            Text(url.url, fontSize = 9.sp, color = TextSecondary, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${url.visitCount} visits - ${url.lastVisit}", fontSize = 10.sp, color = TextSecondary)
        }
    }
}

@Composable
fun ChromeDownloadCard(dl: ChromeScanner.SuspiciousDownload) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, High.copy(alpha = 0.3f))) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dl.filename, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                SeverityBadge(dl.matchedPattern, High)
            }
            Text("${dl.sizeMb}MB - ${dl.downloadTime}", fontSize = 10.sp, color = TextSecondary)
        }
    }
}

@Composable
fun SeverityBadge(text: String, color: Color) {
    Text(text.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.5.sp,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
}

fun severityColor(severity: String): Color = when (severity.lowercase()) {
    "critical" -> Critical; "high" -> High; "medium" -> Medium; "low" -> Low; else -> Info
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
        modifier = modifier, horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement, content = { content() }
    )
}
