package com.sanzh.contactapp.model

/**
 * Represents a single device contact.
 * Migrated from the original fragment-based implementation — same fields, same logic.
 */
data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String?
)
