package com.example

import com.example.ui.screens.LegalCopy
import com.example.ui.screens.LegalDoc
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalCopyTest {

    @Test
    fun policiesMatchWhatTheAppStoresAndDeletes() {
        val terms = LegalCopy.sections(LegalDoc.TERMS).joinToString("\n") { it.body }
        val privacy = LegalCopy.sections(LegalDoc.PRIVACY).joinToString("\n") { it.body }

        assertTrue(terms.contains("MileageTracker does not file taxes"))
        assertTrue(terms.contains("The app has no way to delete the account."))
        assertTrue(terms.contains("Clear all trips removes trips on this phone only."))
        assertTrue(privacy.contains("The photo is read on the phone"))
        assertTrue(privacy.contains("including while the app is in the background"))
        assertTrue(privacy.contains("Clear all trips removes trips on this phone only."))
        assertTrue(privacy.contains("The app has no way to delete the account."))
    }
}
