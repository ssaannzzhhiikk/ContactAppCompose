package com.sanzh.contactapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sanzh.contactapp.ui.ContactDetailScreen
import com.sanzh.contactapp.ui.ContactListScreen
import com.sanzh.contactapp.ui.Screen
import com.sanzh.contactapp.ui.theme.ContactAppTheme
import com.sanzh.contactapp.viewmodel.ContactViewModel

/**
 * Single Activity — the entire UI is built with Jetpack Compose.
 * No fragments are used anywhere in the app.
 *
 * Responsibilities:
 *  1. Request READ_CONTACTS permission (using the modern ActivityResult API).
 *  2. Set up the Compose NavHost with two destinations: list → detail.
 *  3. Provide the shared [ContactViewModel] so both screens see the same state.
 */
class MainActivity : ComponentActivity() {

    // Single shared ViewModel instance — both list and detail screens use the same one
    private val contactViewModel: ContactViewModel by viewModels()

    // Modern permission launcher — replaces the deprecated onRequestPermissionsResult
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                contactViewModel.loadContacts()
            } else {
                Toast.makeText(this, "Permission denied — cannot read contacts.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permission or load contacts directly if already granted
        checkAndRequestPermission()

        setContent {
            ContactAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    ContactAppNavGraph(viewModel = contactViewModel)
                }
            }
        }
    }

    private fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED -> {
                contactViewModel.loadContacts()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }
}

/**
 * Defines the Compose Navigation graph.
 *
 * Two destinations:
 *  • [Screen.ContactList]   — LazyColumn of all contacts (start destination)
 *  • [Screen.ContactDetail] — scrollable detail view for a selected contact
 *
 * Navigating from the list to the detail screen passes the contactId as a
 * navigation argument, so the ViewModel can load the correct data.
 */
@Composable
fun ContactAppNavGraph(viewModel: ContactViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = Screen.ContactList.route
    ) {

        // ── List screen ──────────────────────────────────────────────────
        composable(route = Screen.ContactList.route) {
            ContactListScreen(
                viewModel      = viewModel,
                onContactClick = { contactId ->
                    // Navigate to the detail screen, substituting the contactId arg
                    navController.navigate(Screen.ContactDetail.createRoute(contactId))
                }
            )
        }

        // ── Detail screen ────────────────────────────────────────────────
        composable(
            route     = Screen.ContactDetail.route,
            arguments = listOf(
                navArgument("contactId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: return@composable
            ContactDetailScreen(
                contactId = contactId,
                viewModel = viewModel,
                onBack    = { navController.popBackStack() }
            )
        }
    }
}
