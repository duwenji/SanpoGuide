package com.example.sanpoguide.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sanpoguide.walk.WalkService

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()

    private enum class Screen { MAIN, SETTINGS, HISTORY }
    private var hasLocationPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasLocationPermission = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hasLocationPermission = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (hasLocationPermission) {
            if (savedInstanceState == null) viewModel.refresh()
        } else {
            requestPermissions()
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val mood by viewModel.mood.collectAsStateWithLifecycle()
            MoodTheme(mood.takeIf { settings.moodEnabled }, systemDark = isSystemInDarkTheme()) {
                var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
                if (screen != Screen.MAIN) BackHandler { screen = Screen.MAIN }
                when (screen) {
                    Screen.SETTINGS -> SettingsScreen(settingsViewModel, onClose = { screen = Screen.MAIN })
                    Screen.HISTORY -> HistoryScreen(historyViewModel, onClose = { screen = Screen.MAIN })
                    Screen.MAIN -> MainScreen(
                        viewModel = viewModel,
                        hasLocationPermission = hasLocationPermission,
                        onRequestPermission = ::requestPermissions,
                        onToggleWalk = { walking ->
                            if (walking) WalkService.stop(this) else WalkService.start(this)
                        },
                        onOpenSettings = {
                            settingsViewModel.reset()
                            screen = Screen.SETTINGS
                        },
                        onOpenHistory = { screen = Screen.HISTORY },
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
