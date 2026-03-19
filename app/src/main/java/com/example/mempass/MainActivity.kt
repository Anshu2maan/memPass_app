package com.example.mempass

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.*
import com.example.mempass.common.Constants
import com.example.mempass.ui.navigation.NavGraph
import com.example.mempass.ui.theme.MempassTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var workManager: WorkManager

    @Inject
    lateinit var vaultManager: VaultManager

    @Inject
    @Named("SecurityPrefs")
    lateinit var securityPrefs: SharedPreferences

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // Immediate lock on screen off (Concern #3 Fixed)
                vaultManager.clearKey()
            }
        }
    }

    private val securityPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == Constants.KEY_SCREEN_SECURITY_ENABLED) {
            updateScreenSecurity(prefs.getBoolean(key, true))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Security Recommendation: Clear temporary files on startup
        SecurityUtils.clearTemporaryFiles(this)
        
        // Initial setup of screen security
        val isScreenSecurityEnabled = securityPrefs.getBoolean(Constants.KEY_SCREEN_SECURITY_ENABLED, true)
        updateScreenSecurity(isScreenSecurityEnabled)
        securityPrefs.registerOnSharedPreferenceChangeListener(securityPrefListener)
        
        // Register Screen Off Receiver
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        
        // Schedule workers
        setupWorkers()

        setContent {
            val viewModel: VaultViewModel = hiltViewModel()
            val themePref by viewModel.themePreference.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }
            
            val useDarkTheme = when(themePref) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            // Global Crypto Error Handler
            LaunchedEffect(Unit) {
                viewModel.cryptoError.collectLatest { message ->
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Long,
                        withDismissAction = true
                    )
                }
            }

            // Request Permissions on App Launch
            val permissionsToRequest = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.CAMERA)
            }

            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
                onResult = { permissions ->
                    // Permissions handled
                }
            )

            LaunchedEffect(Unit) {
                if (permissionsToRequest.isNotEmpty()) {
                    launcher.launch(permissionsToRequest.toTypedArray())
                }
            }

            MempassTheme(darkTheme = useDarkTheme) {
                val navController = rememberNavController()
                val isUnlocked by viewModel.isUnlocked.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Auto-lock navigation logic
                LaunchedEffect(isUnlocked) {
                    if (!isUnlocked && currentRoute != "unlock" && currentRoute != "setup" && currentRoute != null && !currentRoute.startsWith("biometric_setup")) {
                        navController.navigate("unlock") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { padding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(padding), 
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NavGraph(navController = navController, vaultViewModel = viewModel)
                    }
                }
            }
        }
    }

    private fun updateScreenSecurity(enabled: Boolean) {
        if (enabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        securityPrefs.unregisterOnSharedPreferenceChangeListener(securityPrefListener)
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) { }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch {
            vaultManager.onAppBackgrounded()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            vaultManager.onAppForegrounded()
        }
    }

    private fun setupWorkers() {
        // Schedule Sync Reminder (Every 24 hours)
        SyncReminderWorker.schedule(this)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<DriveBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            getString(R.string.work_drive_backup),
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )

        val selfDestructRequest = PeriodicWorkRequestBuilder<NoteSelfDestructWorker>(1, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            getString(R.string.work_note_self_destruct),
            ExistingPeriodicWorkPolicy.KEEP,
            selfDestructRequest
        )

        // Schedule Document Expiry Checker (Every 24 hours)
        val expiryRequest = PeriodicWorkRequestBuilder<DocumentExpiryWorker>(24, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            getString(R.string.work_document_expiry),
            ExistingPeriodicWorkPolicy.KEEP,
            expiryRequest
        )
    }
}
