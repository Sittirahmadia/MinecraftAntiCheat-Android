package com.sstools.anticheat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.sstools.anticheat.ui.theme.AntiCheatTheme
import com.sstools.anticheat.ui.screens.MainScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    // SAF folder picker — opens directly to a convenient location
    val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist permission so we can re-read later
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }
            Log.i("MainActivity", "Folder selected: $uri")
            viewModel.onFolderSelected(this, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermissions()

        setContent {
            AntiCheatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        onSelectFolder = { openFolderPicker() },
                        onRequestStorage = { requestStoragePermissions() }
                    )
                }
            }
        }
    }

    fun openFolderPicker(suggestedPath: String? = null) {
        try {
            // Try to open directly to Android/data for convenience
            // The suggestedPath can hint at a specific launcher's folder
            val initialUri = when {
                suggestedPath != null -> {
                    // e.g. "Android/data/com.movtery.zalithlauncher/files"
                    val encoded = suggestedPath.replace("/", "%2F")
                    Uri.parse("content://com.android.externalstorage.documents/document/primary%3A$encoded")
                }
                else -> Uri.parse("content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata")
            }
            try {
                folderPickerLauncher.launch(initialUri)
            } catch (_: Exception) {
                folderPickerLauncher.launch(null)
            }
        } catch (e: Exception) {
            try {
                folderPickerLauncher.launch(null)
            } catch (_: Exception) {
                Log.e("MainActivity", "Cannot open folder picker")
            }
        }
    }

    private fun requestStoragePermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        } catch (_: Exception) {}
                    }
                }
            } else {
                val perms = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
                val needed = perms.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }.toTypedArray()
                if (needed.isNotEmpty()) storagePermissionLauncher.launch(needed)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Permission error: ${e.message}")
        }
    }
}
