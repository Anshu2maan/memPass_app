package com.example.mempass.ui.screens

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.mempass.R
import com.example.mempass.VaultViewModel
import com.example.mempass.ui.theme.BrandIndigo
import com.example.mempass.ui.theme.BrandRose

@Composable
fun BiometricSetupScreen(navController: NavHostController, viewModel: VaultViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val biometricStatus = remember { viewModel.biometricHelper.isBiometricAvailable() }
    val pinChars = remember { viewModel.getTempPin() }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = BrandIndigo
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.enable_biometric),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.biometric_desc),
            color = Color.Gray,
            textAlign = TextAlign.Center,
            fontSize = 16.sp
        )
        
        Spacer(Modifier.height(48.dp))

        when (biometricStatus) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Button(
                    onClick = {
                        if (pinChars != null && pinChars.isNotEmpty()) {
                            viewModel.biometricHelper.encryptPinWithBiometric(
                                activity,
                                pinChars,
                                onSuccess = {
                                    viewModel.clearTempPin()
                                    navController.navigate("main") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onError = { error ->
                                    Toast.makeText(context, context.getString(R.string.setup_failed_generic, error), Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            Toast.makeText(context, context.getString(R.string.session_expired_try_again), Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                ) {
                    Text(stringResource(R.string.enable_now), fontWeight = FontWeight.Bold)
                }
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Button(
                    onClick = { viewModel.biometricHelper.openBiometricSettings(activity) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                ) {
                    Text(stringResource(R.string.enroll_in_settings), fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                Text(
                    stringResource(R.string.hardware_not_available),
                    color = BrandRose,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { 
            viewModel.clearTempPin()
            navController.navigate("main") {
                popUpTo(0) { inclusive = true }
            }
        }) {
            Text(stringResource(R.string.skip_for_now), color = Color.Gray)
        }
    }
}
