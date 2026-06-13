package com.sanzh.contactapp.ui

/**
 * Sealed class defining all navigation destinations in the app.
 * Using a sealed class instead of raw strings prevents typos and makes
 * the route graph easy to reason about at a glance.
 */
sealed class Screen(val route: String) {
    /** The contact list screen — the app's start destination. */
    object ContactList : Screen("contact_list")

    /**
     * The contact detail screen.
     * The route template includes the {contactId} argument that
     * NavController will substitute when navigating.
     */
    object ContactDetail : Screen("contact_detail/{contactId}") {
        /** Builds the concrete route string for a specific contact ID. */
        fun createRoute(contactId: String) = "contact_detail/$contactId"
    }
}
