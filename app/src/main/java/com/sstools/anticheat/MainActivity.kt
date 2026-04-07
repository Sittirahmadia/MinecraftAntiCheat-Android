package com.sstools.anticheat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.content.Intent
import android.net.Uri
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

    // Shizuku listener is initialized lazily to avoid crash if Shizuku not installed
    private var shizukuListenerRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestStoragePermissions()
        registerShizukuListenerSafe()

        setContent {
            AntiCheatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        onRequestShizuku = { viewModel.requestShizukuPermission() },
                        onRequestStorage = { requestStoragePermissions() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkShizuku()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterShizukuListenerSafe()
    }

    private fun registerShizukuListenerSafe() {
        try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            // If we can load the class, try to register
            rikka.shizuku.Shizuku.addRequestPermissionResultListener { _, _ ->
                viewModel.checkShizuku()
            }
            shizukuListenerRegistered = true
        } catch (e: Throwable) {
            // Shizuku not available - that's fine
            Log.d("MainActivity", "Shizuku not available: ${e.message}")
            shizukuListenerRegistered = false
        }
    }

    private fun unregisterShizukuListenerSafe() {
        if (!shizukuListenerRegistered) return
        try {
            // We can't easily remove a lambda listener, so just catch any errors
        } catch (_: Throwable) {}
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
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
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
                if (needed.isNotEmpty()) {
                    storagePermissionLauncher.launch(needed)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Permission request error: ${e.message}")
        }
    }
}
