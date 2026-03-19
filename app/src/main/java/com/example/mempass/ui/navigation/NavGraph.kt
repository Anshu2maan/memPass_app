package com.example.mempass.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.example.mempass.VaultViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    vaultViewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = if (vaultViewModel.isFirstTime()) "setup" else "unlock",
        modifier = modifier
    ) {
        authGraph(navController, vaultViewModel)
        mainGraph(navController, vaultViewModel)
        passwordGraph(navController)
        documentGraph(navController)
        noteGraph(navController)
    }
}
