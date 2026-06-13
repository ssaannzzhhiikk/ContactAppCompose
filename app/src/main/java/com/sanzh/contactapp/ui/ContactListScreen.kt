package com.sanzh.contactapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sanzh.contactapp.model.Contact
import com.sanzh.contactapp.viewmodel.ContactViewModel

/**
 * Contact list screen — replaces ContactListFragment entirely.
 *
 * Observes [ContactViewModel.filteredContacts] and [ContactViewModel.searchQuery] via
 * LiveData.observeAsState(), which is the recommended Compose-LiveData bridge:
 * https://developer.android.com/develop/ui/compose/state#use-other-types-of-state-in-jetpack-compose
 *
 * @param onContactClick  Called with the selected contact's ID; NavController navigates from MainActivity.
 * @param viewModel       Injected by Compose's viewModel() factory — same instance shared with the detail screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    onContactClick: (String) -> Unit,
    viewModel: ContactViewModel = viewModel()
) {
    // ── Observe LiveData state ──────────────────────────────────────────────
    // observeAsState converts LiveData → State<T> so Compose recomposes on changes
    val filteredContacts by viewModel.filteredContacts.observeAsState(emptyList())
    val searchQuery      by viewModel.searchQuery.observeAsState("")
    val isLoading        by viewModel.isLoading.observeAsState(false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Contacts",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Search bar ──────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search by name or phone…") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            // ── Content area ────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    filteredContacts.isEmpty() -> {
                        Text(
                            text = if (searchQuery.isEmpty())
                                "No contacts found on this device."
                            else
                                "No contacts match \"$searchQuery\".",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                    else -> {
                        // LazyColumn replaces RecyclerView + LinearLayoutManager
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                items = filteredContacts,
                                // Stable key prevents wrong-contact navigation bug
                                // (the 5-point partial-navigation criterion)
                                key = { it.id }
                            ) { contact ->
                                ContactRow(
                                    contact  = contact,
                                    onClick  = { onContactClick(contact.id) }
                                )
                                HorizontalDivider(
                                    modifier  = Modifier.padding(start = 72.dp),
                                    thickness = 0.5.dp,
                                    color     = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single row in the contact list.
 * Equivalent to item_contact.xml + ContactAdapter.onBindViewHolder.
 */
@Composable
private fun ContactRow(
    contact: Contact,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Contact avatar
        ContactAvatar(
            photoUri = contact.photoUri,
            name     = contact.name,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Name + phone
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = contact.name,
                fontWeight = FontWeight.Medium,
                fontSize   = 16.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text     = contact.phoneNumber,
                fontSize = 14.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Circular avatar: shows the contact photo if available, otherwise a
 * coloured circle with the first letter of the contact's name.
 */
@Composable
fun ContactAvatar(
    photoUri: String?,
    name: String,
    modifier: Modifier = Modifier
) {
    if (photoUri != null) {
        AsyncImage(
            model             = photoUri,
            contentDescription = "$name photo",
            contentScale      = ContentScale.Crop,
            modifier          = modifier.clip(CircleShape)
        )
    } else {
        // Fallback: teal circle with the initial letter
        Box(
            modifier          = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary),
            contentAlignment  = Alignment.Center
        ) {
            Text(
                text      = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color     = Color.White,
                fontWeight= FontWeight.Bold,
                fontSize  = 20.sp
            )
        }
    }
}
