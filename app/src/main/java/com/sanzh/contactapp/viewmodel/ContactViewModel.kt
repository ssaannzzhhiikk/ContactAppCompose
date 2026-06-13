package com.sanzh.contactapp.viewmodel

import android.app.Application
import android.database.Cursor
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sanzh.contactapp.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel that owns all contact-list state and exposes it via LiveData.
 *
 * Using AndroidViewModel (instead of plain ViewModel) so we can access the
 * ContentResolver from inside the ViewModel without holding a reference to a
 * short-lived Activity or Fragment context.
 *
 * LiveData is consumed in the UI via observeAsState() — the Compose-friendly
 * bridge documented at:
 * https://developer.android.com/develop/ui/compose/state#use-other-types-of-state-in-jetpack-compose
 */
class ContactViewModel(application: Application) : AndroidViewModel(application) {

    // ── State exposed to the UI ───────────────────────────────────────────────

    /** Full contact list loaded from the device. */
    private val _contacts = MutableLiveData<List<Contact>>(emptyList())
    val contacts: LiveData<List<Contact>> = _contacts

    /** Filtered list shown in the LazyColumn after the user types in the search bar. */
    private val _filteredContacts = MutableLiveData<List<Contact>>(emptyList())
    val filteredContacts: LiveData<List<Contact>> = _filteredContacts

    /** Current search query — kept in the ViewModel so it survives recomposition. */
    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    /** True while the contacts are being loaded from the ContentProvider. */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // ── Detail-screen state ───────────────────────────────────────────────────

    /** Full details for the currently-viewed contact (null when on list screen). */
    private val _selectedContactDetail = MutableLiveData<ContactDetail?>(null)
    val selectedContactDetail: LiveData<ContactDetail?> = _selectedContactDetail

    // ── Public actions ────────────────────────────────────────────────────────

    /**
     * Loads all contacts with phone numbers from the device in a background coroutine
     * so the UI thread is never blocked.
     */
    fun loadContacts() {
        viewModelScope.launch {
            _isLoading.value = true
            val loaded = withContext(Dispatchers.IO) {
                queryContacts()
            }
            _contacts.value = loaded
            applyFilter(_searchQuery.value ?: "")
            _isLoading.value = false
        }
    }

    /**
     * Updates the search query LiveData and re-applies the filter.
     * Called from the search TextField's onValueChange callback.
     */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        applyFilter(query)
    }

    /**
     * Loads full detail (phones, emails, addresses, org, birthday) for one contact.
     * Called when the user taps a row in the list.
     */
    fun loadContactDetail(contactId: String) {
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) {
                queryContactDetail(contactId)
            }
            _selectedContactDetail.value = detail
        }
    }

    /** Clears the selected detail when navigating back to the list. */
    fun clearSelectedContact() {
        _selectedContactDetail.value = null
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Applies [query] against the full contact list and updates [_filteredContacts]. */
    private fun applyFilter(query: String) {
        val all = _contacts.value ?: emptyList()
        _filteredContacts.value = if (query.isEmpty()) {
            all
        } else {
            all.filter { c ->
                c.name.contains(query, ignoreCase = true) ||
                        c.phoneNumber.contains(query)
            }
        }
    }

    /**
     * Queries ContactsContract on a background thread and returns a deduplicated
     * list sorted alphabetically — identical logic to the original ContactListFragment.
     */
    private fun queryContacts(): List<Contact> {
        val resolver = getApplication<Application>().contentResolver
        val result = mutableListOf<Contact>()

        val cursor: Cursor? = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val idIdx    = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx  = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val seen     = mutableSetOf<String>()

            while (it.moveToNext()) {
                val id    = it.getString(idIdx)
                val name  = it.getString(nameIdx) ?: "Unknown"
                val phone = it.getString(phoneIdx) ?: ""
                val photo = if (photoIdx >= 0) it.getString(photoIdx) else null

                if (id !in seen) {
                    seen += id
                    result += Contact(id, name, phone, photo)
                }
            }
        }
        return result
    }

    /**
     * Queries all the detail data (multiple phone numbers, emails, addresses,
     * organization, birthday) for a single contact ID.
     * Mirrors the logic from the original ContactDetailsFragment, moved to the ViewModel.
     */
    private fun queryContactDetail(contactId: String): ContactDetail {
        val resolver = getApplication<Application>().contentResolver

        // ── Name & photo ──────────────────────────────────────────────────────
        var name     = "Unknown"
        var photoUri: String? = null

        resolver.query(
            ContactsContract.Contacts.CONTENT_URI, null,
            "${ContactsContract.Contacts._ID} = ?", arrayOf(contactId), null
        )?.use { c ->
            if (c.moveToFirst()) {
                name     = c.safeGet(ContactsContract.Contacts.DISPLAY_NAME) ?: "Unknown"
                photoUri = c.safeGet(ContactsContract.Contacts.PHOTO_URI)
            }
        }

        // ── Phone numbers ─────────────────────────────────────────────────────
        val phones = mutableListOf<String>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(contactId), null
        )?.use { c ->
            while (c.moveToNext()) {
                c.safeGet(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    ?.takeIf { it.isNotEmpty() }?.let { phones += it }
            }
        }

        // ── Emails ────────────────────────────────────────────────────────────
        val emails = mutableListOf<String>()
        resolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI, null,
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?", arrayOf(contactId), null
        )?.use { c ->
            while (c.moveToNext()) {
                c.safeGet(ContactsContract.CommonDataKinds.Email.ADDRESS)
                    ?.takeIf { it.isNotEmpty() }?.let { emails += it }
            }
        }

        // ── Postal addresses ─────────────────────────────────────────────────
        val addresses = mutableListOf<String>()
        resolver.query(
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI, null,
            "${ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID} = ?",
            arrayOf(contactId), null
        )?.use { c ->
            while (c.moveToNext()) {
                c.safeGet(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
                    ?.takeIf { it.isNotEmpty() }?.let { addresses += it }
            }
        }

        // ── Organization ─────────────────────────────────────────────────────
        var organization = ""
        resolver.query(
            ContactsContract.Data.CONTENT_URI, null,
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                organization = c.safeGet(
                    ContactsContract.CommonDataKinds.Organization.COMPANY
                ) ?: ""
            }
        }

        // ── Birthday ─────────────────────────────────────────────────────────
        var birthday = ""
        resolver.query(
            ContactsContract.Data.CONTENT_URI, null,
            "${ContactsContract.Data.CONTACT_ID} = ? AND " +
                    "${ContactsContract.Data.MIMETYPE} = ? AND " +
                    "${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
            arrayOf(
                contactId,
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
            ), null
        )?.use { c ->
            if (c.moveToFirst()) {
                birthday = c.safeGet(ContactsContract.CommonDataKinds.Event.START_DATE) ?: ""
            }
        }

        return ContactDetail(
            id           = contactId,
            name         = name,
            photoUri     = photoUri,
            phones       = phones,
            emails       = emails,
            addresses    = addresses,
            organization = organization,
            birthday     = birthday
        )
    }

    /** Safe cursor column reader — returns null instead of crashing on missing columns. */
    private fun Cursor.safeGet(column: String): String? {
        val idx = getColumnIndex(column)
        return if (idx >= 0) getString(idx) else null
    }
}

/**
 * Holds all detail data for a single contact.
 * Kept in the viewmodel package alongside [ContactViewModel] for cohesion.
 */
data class ContactDetail(
    val id: String,
    val name: String,
    val photoUri: String?,
    val phones: List<String>,
    val emails: List<String>,
    val addresses: List<String>,
    val organization: String,
    val birthday: String
)
