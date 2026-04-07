package com.sstools.anticheat.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sstools.anticheat.MainViewModel
import com.sstools.anticheat.ScanState
import com.sstools.anticheat.scanner.*
import com.sstools.anticheat.ui.theme.*

// ─── Color Palette ───
private val BgDeep      = Color(0xFF0A0C12)
private val BgCard      = Color(0xFF12151E)
private val BgCardAlt   = Color(0xFF161923)
private val AccentBlue  = Color(0xFF4E8AFF)
private val AccentPurple= Color(0xFF9B6DFF)
private val AccentPink  = Color(0xFFFF5C8A)
private val AccentGreen = Color(0xFF3DDC84)
private val AccentRed   = Color(0xFFFF4757)
private val AccentOrange= Color(0xFFFF8C42)
private val AccentYellow= Color(0xFFFFD93D)
private val TextPrim    = Color(0xFFEEF0F8)
private val TextSec     = Color(0xFF7E8499)
private val BorderColor = Color(0xFF232636)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSelectFolder: (String?) -> Unit,
    onRequestStorage: () -> Unit
) {
    val state by viewModel.scanState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Logo badge
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Brush.linearGradient(listOf(AccentBlue, AccentPurple))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text("SS AntiCheat", fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, color = TextPrim)
                            Text("Minecraft Cheat Scanner", fontSize = 10.sp, color = TextSec)
                        }
                    }
                },
                navigationIcon = {
                    if (state.currentScreen != "home") {
                        IconButton(onClick = {
                            viewModel.resetScan()
                            viewModel.setScreen("home")
                        }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = TextPrim)
                        }
                    }
                },
                actions = {
                    // Shizuku indicator
                    val shizukuColor = if (state.shizukuAvailable) AccentGreen else AccentOrange
                    Box(
                        Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shizukuColor.copy(alpha = 0.12f))
                            .border(1.dp, shizukuColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (state.shizukuAvailable) "Shizuku ✓" else "No Root",
                            fontSize = 10.sp,
                            color = shizukuColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        },
        containerColor = BgDeep
    ) { padding ->
        when (state.currentScreen) {
            "home"      -> HomeScreen(viewModel, state, onSelectFolder, padding)
            "minecraft" -> MinecraftResultsScreen(viewModel, state, onSelectFolder, padding)
            "deleted"   -> DeletedFilesScreen(viewModel, state, padding)
            "history"   -> HistoryScreen(viewModel, state, padding)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// HOME SCREEN
// ═══════════════════════════════════════════════════════════

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    state: ScanState,
    onSelectFolder: (String?) -> Unit,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Header stats chips
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoChip("${CheatDetector.SIGNATURES.size} Signatures", AccentBlue)
                InfoChip("MC 1.21+", AccentPurple)
                InfoChip("Android 12-16", AccentGreen)
            }
        }

        // ── Primary: Select Minecraft Folder ──
        item {
            PrimaryCard(
                gradient = listOf(AccentBlue.copy(alpha = 0.08f), AccentPurple.copy(alpha = 0.05f)),
                borderColor = AccentBlue.copy(alpha = 0.35f)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradientIcon(Icons.Default.FolderOpen, listOf(AccentBlue, AccentPurple), 44)
                        Column {
                            Text("Scan Folder Minecraft", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrim)
                            Text("Pilih folder .minecraft kamu", fontSize = 12.sp, color = TextSec)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Cara: Tap 'Pilih Folder' → navigasi ke Android/data/[launcher]/files → pilih folder .minecraft. " +
                        "Semua mod, log, dan class file akan di-scan.",
                        fontSize = 12.sp, color = TextSec, lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(14.dp))

                    // Launcher quick-select buttons
                    Text("Quick select launcher:", fontSize = 11.sp, color = TextSec, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))

                    // Launcher shortcuts
                    val launchers = listOf(
                        "Zalith" to "Android/data/com.movtery.zalithlauncher/files",
                        "Zalith 2" to "Android/data/com.movtery.zalithlauncher2/files",
                        "Mojo" to "Android/data/git.artdeell.mojo/files",
                        "Pojav" to "Android/data/net.kdt.pojavlaunch/files",
                        "FCL" to "Android/data/com.tungsten.fcl/files",
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        launchers.forEach { (name, path) ->
                            LauncherShortcutChip(name, path) { onSelectFolder(path) }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onSelectFolder(null) },
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pilih Folder", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.runAutoScan() },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Icon(Icons.Default.Search, null, Modifier.size(16.dp), tint = AccentBlue)
                            Spacer(Modifier.width(8.dp))
                            Text("Auto Detect", fontSize = 13.sp, color = TextPrim)
                        }
                    }
                }
            }
        }

        // ── Full Auto Scan ──
        item {
            ScanActionCard(
                title = "Full Auto Scan",
                description = "Scan semua: mods + file terhapus + riwayat browser",
                icon = Icons.Default.RocketLaunch,
                gradientColors = listOf(AccentPink, AccentPurple),
                onClick = { viewModel.runFullScan() }
            )
        }

        // ── Deleted File Scanner ──
        item {
            ScanActionCard(
                title = "Scanner File Terhapus",
                description = "Scan Downloads, Temp, Trash untuk file cheat. Bekerja tanpa root.",
                icon = Icons.Default.DeleteForever,
                gradientColors = listOf(AccentOrange, AccentYellow),
                onClick = { viewModel.runDeletedFileScan() }
            )
        }

        // ── Browser History ──
        item {
            ScanActionCard(
                title = "Scanner Riwayat Browser",
                description = "Scan Chrome/Edge/Brave untuk URL dan download cheat",
                icon = Icons.Default.Language,
                gradientColors = listOf(AccentBlue, AccentGreen),
                onClick = { viewModel.runHistoryScan() }
            )
        }

        // ── Access info box ──
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BgCard),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("ℹ️ Mode Akses", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrim)
                    Spacer(Modifier.height(8.dp))
                    AccessRow("Tanpa root/Shizuku", "Gunakan 'Pilih Folder' (SAF) untuk scan mod. Deleted + Browser scanner tetap bekerja.", AccentYellow)
                    Spacer(Modifier.height(4.dp))
                    AccessRow("Dengan Shizuku/ADB", "Auto-detect semua launcher. Chrome history scan langsung dari DB.", AccentGreen)
                    Spacer(Modifier.height(4.dp))
                    AccessRow("All Files Access", "Aktifkan 'Manage All Files' di Settings untuk akses lebih baik ke /Android/data/.", AccentBlue)
                }
            }
        }

        // ── Supported Launchers ──
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BgCard),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Launcher yang Didukung", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextSec)
                    Spacer(Modifier.height(8.dp))
                    LauncherGrid()
                }
            }
        }
    }
}

@Composable
private fun LauncherShortcutChip(name: String, path: String, onSelectFolder: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AccentBlue.copy(alpha = 0.1f))
            .border(1.dp, AccentBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable { onSelectFolder() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(name, fontSize = 12.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AccessRow(label: String, desc: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(6.dp).clip(CircleShape).background(color)
                .align(Alignment.Top).padding(top = 4.dp)
        )
        Column {
            Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 11.sp, color = TextSec, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun LauncherGrid() {
    val launchers = listOf("Zalith", "Zalith 2", "Mojo", "PojavLauncher", "FCL", "HMCL-PE")
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        launchers.forEach { name ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentPurple.copy(alpha = 0.08f))
                    .border(1.dp, AccentPurple.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) { Text(name, fontSize = 11.sp, color = AccentPurple) }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// MINECRAFT RESULTS SCREEN
// ═══════════════════════════════════════════════════════════

@Composable
fun MinecraftResultsScreen(
    viewModel: MainViewModel,
    state: ScanState,
    onSelectFolder: (String?) -> Unit,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        if (state.isScanning) {
            item { ProgressCard(state) }
        }

        state.error?.let { error ->
            item {
                ErrorCard(error, showFolderButton = state.verdict == "NO_ACCESS", onSelectFolder = onSelectFolder)
            }
        }

        if (state.scanComplete && !state.isScanning && state.verdict != "NO_ACCESS") {
            item { VerdictBanner(state) }
            item { StatRow(state) }

            state.safResult?.let { saf ->
                item { SafFolderInfoCard(saf) }
                saf.logFindings.takeIf { it.isNotEmpty() }?.let { findings ->
                    item { SectionHeader("Peringatan di Log (${findings.size})", Icons.Default.WarningAmber, AccentRed) }
                    items(findings) { SafLogCard(it) }
                }
            }

            state.launcherResults.takeIf { it.isNotEmpty() }?.let { launchers ->
                item { SectionHeader("Launcher Ditemukan (${launchers.size})", Icons.Default.SportsEsports, AccentBlue) }
                items(launchers) { LauncherCard(it) }
            }

            val flagged = state.modScanResults.filter { it.flagged }
            flagged.takeIf { it.isNotEmpty() }?.let { mods ->
                item { SectionHeader("🚨 Mod Terdeteksi (${mods.size})", Icons.Default.BugReport, AccentRed) }
                items(mods) { ModResultCard(it) }
            }

            val clean = state.modScanResults.filter { !it.flagged }
            clean.takeIf { it.isNotEmpty() }?.let { mods ->
                item { SectionHeader("✅ Mod Bersih (${mods.size})", Icons.Default.CheckCircle, AccentGreen) }
                items(mods.take(30)) { ModResultCard(it) }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onSelectFolder,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Scan Folder Lain", fontSize = 12.sp, color = TextPrim) }
                    OutlinedButton(
                        onClick = { viewModel.resetScan(); viewModel.setScreen("home") },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Kembali", fontSize = 12.sp, color = TextPrim) }
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
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        if (state.isScanning) { item { ProgressCard(state) } }
        state.error?.let { item { ErrorCard(it) } }

        state.deletedFileScanResult?.let { del ->
            item { VerdictBanner(state) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NumberCard("Di-scan", "${del.totalScanned}", AccentBlue, Modifier.weight(1f))
                    NumberCard("Flagged", "${del.flaggedItems.size}",
                        if (del.flaggedItems.isNotEmpty()) AccentRed else AccentGreen, Modifier.weight(1f))
                    NumberCard("Trash", "${del.trashItems.size + del.recentlyDeleted.size}", AccentOrange, Modifier.weight(1f))
                }
            }

            if (del.flaggedItems.isEmpty() && del.downloadFiles.isEmpty() && del.trashItems.isEmpty() && del.recentlyDeleted.isEmpty()) {
                item {
                    CleanResultCard("Tidak ada file mencurigakan ditemukan di Download, Trash, dan folder publik.")
                }
            }

            del.flaggedItems.takeIf { it.isNotEmpty() }?.let { files ->
                item { SectionHeader("🚨 File Flagged (${files.size})", Icons.Default.Warning, AccentRed) }
                items(files) { FileCard(it) }
            }
            del.recentlyDeleted.takeIf { it.isNotEmpty() }?.let { files ->
                item { SectionHeader("File Baru Dihapus (${files.size})", Icons.Default.Delete, AccentOrange) }
                items(files) { FileCard(it) }
            }
            del.downloadFiles.takeIf { it.isNotEmpty() }?.let { files ->
                item { SectionHeader("Download (${files.size})", Icons.Default.Download, AccentBlue) }
                items(files.take(50)) { FileCard(it) }
            }
            del.trashItems.takeIf { it.isNotEmpty() }?.let { files ->
                item { SectionHeader("Trash (${files.size})", Icons.Default.Delete, AccentOrange) }
                items(files.take(20)) { FileCard(it) }
            }
            item {
                OutlinedButton(onClick = { viewModel.resetScan(); viewModel.setScreen("home") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderColor)) { Text("Kembali ke Home", color = TextPrim) }
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
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        if (state.isScanning) { item { ProgressCard(state) } }

        state.chromeScanResult?.let { chrome ->
            if (chrome.error != null) {
                item { ErrorCard(chrome.error) }
            }

            if (state.scanComplete || (!state.isScanning && chrome.totalDownloadsScanned > 0)) {
                item { VerdictBanner(state) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberCard("Profil", "${chrome.profilesFound}", AccentBlue, Modifier.weight(1f))
                        NumberCard("URL Scan", "${chrome.totalUrlsScanned}", AccentPurple, Modifier.weight(1f))
                        NumberCard("Flagged", "${chrome.suspiciousUrls.size + chrome.suspiciousDownloads.size}",
                            if (chrome.suspiciousUrls.isNotEmpty() || chrome.suspiciousDownloads.isNotEmpty()) AccentRed else AccentGreen,
                            Modifier.weight(1f))
                    }
                }

                if (chrome.suspiciousUrls.isEmpty() && chrome.suspiciousDownloads.isEmpty()) {
                    item { CleanResultCard("Tidak ada URL atau download mencurigakan ditemukan.") }
                }

                chrome.suspiciousUrls.takeIf { it.isNotEmpty() }?.let { urls ->
                    item { SectionHeader("URL Mencurigakan (${urls.size})", Icons.Default.Link, AccentRed) }
                    items(urls) { ChromeUrlCard(it) }
                }
                chrome.suspiciousDownloads.takeIf { it.isNotEmpty() }?.let { dls ->
                    item { SectionHeader("Download Mencurigakan (${dls.size})", Icons.Default.Download, AccentOrange) }
                    items(dls) { ChromeDownloadCard(it) }
                }
            }

            item {
                OutlinedButton(onClick = { viewModel.resetScan(); viewModel.setScreen("home") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderColor)) { Text("Kembali ke Home", color = TextPrim) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════

@Composable
fun ProgressCard(state: ScanState) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(10.dp).clip(CircleShape)
                        .background(AccentBlue.copy(alpha = glowAlpha))
                )
                Text("Scanning...", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrim)
            }
            Spacer(Modifier.height(14.dp))
            // Progress bar
            Box(
                Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(BorderColor)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction = state.progress.coerceIn(0f, 1f))
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(listOf(AccentBlue, AccentPurple)))
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.currentTask, fontSize = 12.sp, color = TextSec, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${(state.progress * 100).toInt()}%", fontSize = 12.sp,
                    color = AccentBlue, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VerdictBanner(state: ScanState) {
    val isClean = state.verdict == "CLEAN"
    val color = if (isClean) AccentGreen else AccentRed
    val emoji = if (isClean) "✅" else "🚨"

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$emoji ${if (isClean) "BERSIH" else "TERDETEKSI"}",
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Spacer(Modifier.height(4.dp))
            Text(
                if (isClean) "Tidak ada cheat yang terdeteksi"
                else "${state.totalFlags} item mencurigakan ditemukan",
                fontSize = 13.sp, color = TextSec
            )
        }
    }
}

@Composable
fun StatRow(state: ScanState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        NumberCard("Total Mod", "${state.modScanResults.size}", AccentBlue, Modifier.weight(1f))
        NumberCard("Flagged", "${state.modScanResults.count { it.flagged }}",
            if (state.modScanResults.any { it.flagged }) AccentRed else AccentGreen, Modifier.weight(1f))
        NumberCard("Bersih", "${state.modScanResults.count { !it.flagged }}", AccentGreen, Modifier.weight(1f))
    }
}

@Composable
fun NumberCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, fontSize = 10.sp, color = TextSec, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SafFolderInfoCard(saf: SafScanner.SafScanResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Folder, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                Text("Folder yang Di-scan", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrim)
            }
            Spacer(Modifier.height(4.dp))
            Text(saf.folderPath, fontSize = 11.sp, color = TextSec, fontFamily = FontFamily.Monospace,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TagText("${saf.modsFound.size} mods", AccentBlue)
                TagText("${saf.logFindings.size} log flags",
                    if (saf.logFindings.isNotEmpty()) AccentRed else AccentGreen)
                TagText("${saf.versionsFound.size} versi", AccentPurple)
            }
        }
    }
}

@Composable
fun LauncherCard(launcher: MinecraftScanner.LauncherScanResult) {
    val hasIssues = launcher.logFindings.isNotEmpty()
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (hasIssues) AccentRed.copy(alpha = 0.3f) else BorderColor)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(launcher.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrim)
                TagText("${launcher.mods.size} mods", AccentBlue)
            }
            Text(launcher.path, fontSize = 10.sp, color = TextSec, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (launcher.versions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Versi: ${launcher.versions.take(3).joinToString(", ")}", fontSize = 11.sp, color = TextSec)
            }
            if (launcher.logFindings.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("⚠ ${launcher.logFindings.size} peringatan di log", fontSize = 12.sp,
                    color = AccentRed, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ModResultCard(mod: JarInspector.JarScanResult) {
    val borderColor = when {
        mod.isDisguised -> AccentRed
        mod.flagged     -> AccentOrange
        mod.whitelisted -> AccentGreen
        else            -> AccentGreen
    }
    val statusLabel = when {
        mod.isDisguised -> "FAKE"
        mod.flagged     -> "FLAG"
        else            -> "OK"
    }
    val statusColor = when {
        mod.isDisguised -> AccentRed
        mod.flagged     -> AccentOrange
        else            -> AccentGreen
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Status badge
                Text(statusLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor)
                        .padding(horizontal = 5.dp, vertical = 2.dp))
                Spacer(Modifier.width(8.dp))
                Text(mod.filename, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                    color = TextPrim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Text("${mod.sizeMb}MB", fontSize = 10.sp, color = TextSec)
            }

            if (mod.isDisguised) {
                Spacer(Modifier.height(4.dp))
                Text("⚠ Berpura-pura jadi '${mod.authenticity?.claimedMod}' - package SALAH!",
                    fontSize = 11.sp, color = AccentRed, fontWeight = FontWeight.SemiBold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 2.dp)) {
                Text("${mod.classFiles.size} class", fontSize = 10.sp, color = TextSec)
                Text("${mod.scannedClasses} scanned", fontSize = 10.sp, color = TextSec)
                if (mod.error != null) Text("⚠ ${mod.error}", fontSize = 10.sp, color = AccentOrange, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }

            if (mod.detections.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Divider(color = BorderColor, thickness = 1.dp)
                Spacer(Modifier.height(6.dp))
                for (d in mod.detections.take(4)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        SeverityPill(d.severity)
                        Column(Modifier.weight(1f)) {
                            Text("${d.signatureName}", fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold, color = TextPrim)
                            Text("[${d.category}] ${d.description.take(80)}",
                                fontSize = 10.sp, color = TextSec, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(d.matchedPatterns.take(3).joinToString(" • "),
                                fontSize = 9.sp, color = TextSec.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (mod.detections.size > 4) {
                    Text("...+${mod.detections.size - 4} deteksi lainnya",
                        fontSize = 10.sp, color = TextSec, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
fun SafLogCard(finding: SafScanner.SafLogFinding) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, severityColor(finding.severity).copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(finding.matchedPattern, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrim)
                SeverityPill(finding.severity)
            }
            Text("${finding.logFile} : line ${finding.lineNumber}", fontSize = 10.sp, color = TextSec)
            Spacer(Modifier.height(4.dp))
            Text(finding.line, fontSize = 9.sp, color = TextSec.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(6.dp).fillMaxWidth())
        }
    }
}

@Composable
fun FileCard(file: DeletedFileScanner.SuspiciousFile) {
    val flagged = file.detections.isNotEmpty()
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (flagged) AccentRed.copy(alpha = 0.35f) else BorderColor)
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(file.filename, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    color = TextPrim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                TagText(file.source, if (flagged) AccentRed else AccentBlue)
            }
            Text("${file.sizeMb}MB  •  ${file.lastModified}", fontSize = 10.sp, color = TextSec)
            if (flagged) {
                Spacer(Modifier.height(4.dp))
                Text("🚨 ${file.detections.first().signatureName}", fontSize = 11.sp, color = AccentRed)
            }
        }
    }
}

@Composable
fun ChromeUrlCard(url: ChromeScanner.SuspiciousUrl) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(url.title.ifEmpty { url.url.take(50) }, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = TextPrim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                TagText(url.matchedPattern, AccentOrange)
            }
            Text(url.url, fontSize = 9.sp, color = TextSec, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${url.visitCount}x dikunjungi  •  ${url.lastVisit}", fontSize = 10.sp, color = TextSec)
        }
    }
}

@Composable
fun ChromeDownloadCard(dl: ChromeScanner.SuspiciousDownload) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(dl.filename, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    color = TextPrim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                TagText(dl.matchedPattern, AccentRed)
            }
            Text("${dl.sizeMb}MB  •  ${dl.downloadTime}", fontSize = 10.sp, color = TextSec)
            if (dl.sourceUrl.isNotEmpty() && dl.sourceUrl != "Download folder") {
                Text(dl.sourceUrl.take(60), fontSize = 9.sp, color = TextSec, fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ErrorCard(error: String, showFolderButton: Boolean = false, onSelectFolder: (() -> Unit)? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AccentYellow.copy(alpha = 0.06f)),
        border = BorderStroke(1.dp, AccentYellow.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = AccentYellow)
                Text(error, fontSize = 12.sp, color = AccentYellow, lineHeight = 17.sp)
            }
            if (showFolderButton && onSelectFolder != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onSelectFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pilih Folder Minecraft")
                }
            }
        }
    }
}

@Composable
fun CleanResultCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.25f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(22.dp))
            Text(message, fontSize = 13.sp, color = AccentGreen)
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrim)
    }
}

@Composable
fun SeverityPill(severity: String) {
    val color = severityColor(severity)
    Text(
        severity.uppercase(),
        fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color,
        letterSpacing = 0.4.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

@Composable
fun TagText(text: String, color: Color) {
    Text(
        text,
        fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

@Composable
fun InfoChip(text: String, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun GradientIcon(icon: ImageVector, colors: List<Color>, size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(colors.map { it.copy(alpha = 0.2f) })),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = colors.first(), modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun PrimaryCard(
    gradient: List<Color>,
    borderColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(gradient), RoundedCornerShape(16.dp))
    ) {
        content()
    }
}

@Composable
fun ScanActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, gradientColors.first().copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(gradientColors.map { it.copy(alpha = 0.2f) })),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = gradientColors.first(), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrim)
                Text(description, fontSize = 12.sp, color = TextSec,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            }
            Icon(Icons.Default.ChevronRight, null,
                modifier = Modifier.size(18.dp), tint = TextSec)
        }
    }
}

fun severityColor(severity: String): Color = when (severity.lowercase()) {
    "critical" -> AccentRed
    "high"     -> AccentOrange
    "medium"   -> AccentYellow
    "low"      -> AccentGreen
    else       -> AccentBlue
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
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
