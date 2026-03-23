package com.example.mempass.ui.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.mempass.DocumentViewModel
import com.example.mempass.NoteViewModel
import com.example.mempass.PasswordViewModel
import com.example.mempass.VaultViewModel
import com.example.mempass.ui.screens.*

fun NavGraphBuilder.authGraph(navController: NavHostController, vaultViewModel: VaultViewModel) {
    composable("setup") { SetupScreen(navController, vaultViewModel) }
    composable("biometric_setup") {
        BiometricSetupScreen(navController, vaultViewModel)
    }
    composable("unlock") { UnlockScreen(navController, vaultViewModel) }
}

fun NavGraphBuilder.mainGraph(navController: NavHostController, vaultViewModel: VaultViewModel) {
    composable("main") { MainDashboard(navController, vaultViewModel) }
    composable("settings") { 
        SettingsScreen(
            navController = navController, 
            viewModel = vaultViewModel,
            onNavigateToLogs = { navController.navigate("intruder_logs") },
            onNavigateToRecovery = { /* Navigate to recovery if exists */ },
            onNavigateToTheme = { /* Navigate to theme if exists */ }
        ) 
    }
    composable("intruder_logs") { IntruderLogScreen(navController) }
}

fun NavGraphBuilder.passwordGraph(navController: NavHostController) {
    composable("password_list") {
        val pwViewModel: PasswordViewModel = hiltViewModel()
        PasswordListScreen(navController, pwViewModel)
    }
    composable("add_password?id={id}") { backStackEntry ->
        val id = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: 0
        val pwViewModel: PasswordViewModel = hiltViewModel()
        AddPasswordScreen(navController, pwViewModel, id)
    }
}

fun NavGraphBuilder.documentGraph(navController: NavHostController) {
    composable("document_list") {
        val docViewModel: DocumentViewModel = hiltViewModel()
        DocumentListScreen(navController, docViewModel)
    }
    composable("add_document?id={id}") { backStackEntry ->
        val id = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: 0
        val docViewModel: DocumentViewModel = hiltViewModel()
        AddDocumentScreen(navController, docViewModel, id)
    }
}

fun NavGraphBuilder.noteGraph(navController: NavHostController) {
    composable("note_list") {
        val noteViewModel: NoteViewModel = hiltViewModel()
        NoteListScreen(navController, noteViewModel)
    }
    composable("add_note?id={id}") { backStackEntry ->
        val id = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: 0
        val noteViewModel: NoteViewModel = hiltViewModel()
        AddNoteScreen(navController, noteViewModel, id)
    }
}
