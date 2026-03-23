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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.mempass.ui.theme.BrandRose
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
        
        SecurityUtils.clearTemporaryFiles(this)
        
        val isScreenSecurityEnabled = securityPrefs.getBoolean(Constants.KEY_SCREEN_SECURITY_ENABLED, true)
        updateScreenSecurity(isScreenSecurityEnabled)
        securityPrefs.registerOnSharedPreferenceChangeListener(securityPrefListener)
        
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        
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
                onResult = { }
            )

            LaunchedEffect(Unit) {
                if (permissionsToRequest.isNotEmpty()) {
                    launcher.launch(permissionsToRequest.toTypedArray())
                }
            }

            MempassTheme(darkTheme = useDarkTheme) {
                val navController = rememberNavController()
                val isUnlocked by viewModel.isUnlocked.collectAsState()
                val lockoutTime by viewModel.lockoutTimeRemaining.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

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
                    Box(Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier.fillMaxSize().padding(padding), 
                            color = MaterialTheme.colorScheme.background
                        ) {
                            NavGraph(navController = navController, vaultViewModel = viewModel)
                        }

                        // Global Lockout Overlay
                        if (lockoutTime > 0) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.Black.copy(alpha = 0.9f)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.LockClock, null, modifier = Modifier.size(64.dp), tint = BrandRose)
                                    Spacer(Modifier.height(24.dp))
                                    Text(
                                        stringResource(R.string.temporary_lockout),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 24.sp,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.lockout_countdown, lockoutTime / 1000),
                                        fontSize = 16.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
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
