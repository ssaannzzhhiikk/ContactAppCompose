package com.sanzh.contactapp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sanzh.contactapp.viewmodel.ContactDetail
import com.sanzh.contactapp.viewmodel.ContactViewModel

/**
 * Contact detail screen — replaces ContactDetailsFragment entirely.
 *
 * The screen observes [ContactViewModel.selectedContactDetail] via LiveData.observeAsState(),
 * following the recommended Compose-LiveData integration pattern:
 * https://developer.android.com/develop/ui/compose/state#use-other-types-of-state-in-jetpack-compose
 *
 * @param contactId   Passed as a nav argument; used to trigger [ContactViewModel.loadContactDetail].
 * @param onBack      Navigates up — called when the back-arrow is tapped.
 * @param viewModel   Shared ViewModel instance (same scope as ContactListScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactId: String,
    onBack: () -> Unit,
    viewModel: ContactViewModel = viewModel()
) {
    // Load this contact's details the first time the screen is composed
    LaunchedEffect(contactId) {
        viewModel.loadContactDetail(contactId)
    }

    // Observe LiveData → State<ContactDetail?>
    val detail by viewModel.selectedContactDetail.observeAsState(null)
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.name ?: "Contact") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearSelectedContact()
                        onBack()
                    }) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (detail == null) {
            // Show a spinner while the ViewModel loads the data in the background
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            ContactDetailContent(
                detail          = detail!!,
                paddingValues   = paddingValues,
                onCallClicked   = { phone ->
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$phone")
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

/**
 * Scrollable content area for the detail screen.
 * Separated so the loading-state / scaffold logic above stays clean.
 */
@Composable
private fun ContactDetailContent(
    detail: ContactDetail,
    paddingValues: PaddingValues,
    onCallClicked: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Avatar ─────────────────────────────────────────────────────────
        if (detail.photoUri != null) {
            AsyncImage(
                model              = detail.photoUri,
                contentDescription = "${detail.name} photo",
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            )
        } else {
            ContactAvatar(
                photoUri = null,
                name     = detail.name,
                modifier = Modifier.size(120.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Name ───────────────────────────────────────────────────────────
        Text(
            text       = detail.name,
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold
        )

        // ── Call button (primary action) ───────────────────────────────────
        if (detail.phones.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onCallClicked(detail.phones.first()) },
                shape   = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector        = Icons.Default.Call,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Call")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Info cards ─────────────────────────────────────────────────────
        if (detail.phones.isNotEmpty()) {
            DetailCard(
                emoji = "📞",
                title = "Phones",
                items = detail.phones
            )
        }

        if (detail.emails.isNotEmpty()) {
            DetailCard(
                emoji = "✉️",
                title = "Emails",
                items = detail.emails
            )
        }

        if (detail.addresses.isNotEmpty()) {
            DetailCard(
                emoji = "🏠",
                title = "Addresses",
                items = detail.addresses
            )
        }

        if (detail.organization.isNotEmpty()) {
            DetailCard(
                emoji = "🏢",
                title = "Organization",
                items = listOf(detail.organization)
            )
        }

        if (detail.birthday.isNotEmpty()) {
            DetailCard(
                emoji = "🎂",
                title = "Birthday",
                items = listOf(detail.birthday)
            )
        }
    }
}

/**
 * A Material3 Card that groups related contact fields (phones, emails, etc.)
 */
@Composable
private fun DetailCard(
    emoji: String,
    title: String,
    items: List<String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = "$emoji  $title",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp,
                color      = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            items.forEach { item ->
                Text(
                    text     = item,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}
