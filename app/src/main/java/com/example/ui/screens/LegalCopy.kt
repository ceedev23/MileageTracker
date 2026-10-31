package com.example.ui.screens

enum class LegalDoc(val title: String) {
    TERMS("Terms of Service"),
    PRIVACY("Privacy Policy")
}

data class LegalSection(val heading: String, val body: String)

object LegalCopy {
    const val UPDATED = "October 28, 2026"

    fun sections(doc: LegalDoc): List<LegalSection> = when (doc) {
        LegalDoc.TERMS -> terms
        LegalDoc.PRIVACY -> privacy
    }

    private val terms = listOf(
        LegalSection(
            "The service",
            "MileageTracker records drives, fuel receipts, and a mileage estimate for your own records. " +
                "These terms cover the app on your phone, whether you continue as a guest or create an account."
        ),
        LegalSection(
            "Agreeing",
            "Creating an account, signing in, or continuing as a guest means you agree to these terms and the Privacy Policy. " +
                "You need to be old enough to enter a contract where you live. The app is not for children."
        ),
        LegalSection(
            "Your account",
            "You are responsible for your password and for what happens under your account. Sign out on a phone you share. " +
                "We can close an account that is used to break these terms or to attack the service. " +
                "The app has no way to delete the account."
        ),
        LegalSection(
            "Your records",
            "Trips, purposes, vehicle names, odometer readings, and receipt details are yours. " +
                "You choose what to enter and whether a trip is business or personal. " +
                "Guest data stays on the phone. If you are signed in and cloud backup is available, trips and receipt details are copied to your account. " +
                "Deleting one trip or receipt removes that item from the phone and from the backup. " +
                "Clear all trips removes trips on this phone only. " +
                "Signing out removes the session from the phone. It does not delete the account or the backup."
        ),
        LegalSection(
            "Location",
            "Live drive and automatic trip detection use the phone's location, including while the app is in the background when automatic detection is on. " +
                "You can turn tracking off and deny location permission. The app cannot measure a GPS drive without it."
        ),
        LegalSection(
            "Estimates, not tax advice",
            "Distances, reimbursement amounts, and report totals are estimates from the trips you keep and the rate you set. " +
                "A preset labeled with a year, such as a CRA or IRS figure, can be wrong for your situation. " +
                "MileageTracker does not file taxes, does not guarantee a deduction, and is not a substitute for your records or a tax professional."
        ),
        LegalSection(
            "Acceptable use",
            "Do not misuse the app, try to break into the backup, or use the app to track someone else unless you have the right to do that."
        ),
        LegalSection(
            "Availability",
            "A feature such as cloud backup can be missing from a given version. We may change or stop the app. " +
                "Trips stored on the phone stay on the phone until you delete them or uninstall."
        ),
        LegalSection(
            "Liability",
            "The app is provided as is. To the extent the law allows, we are not liable for a lost trip, a wrong distance, a missed deduction, or a decision you make from a report. " +
                "Where a limit like this is not allowed, it applies only as far as the law allows."
        ),
        LegalSection(
            "Changes",
            "If these terms change, the date at the top of this screen changes. " +
                "Continuing to use the app after that means you accept the updated terms."
        )
    )

    private val privacy = listOf(
        LegalSection(
            "What this covers",
            "This policy describes what MileageTracker stores on your phone and, if you sign in, what is backed up with your account. " +
                "It describes this version of the app."
        ),
        LegalSection(
            "On this phone",
            "The app stores trips (start and end times, distance, business or personal, addresses, purpose, vehicle, and the route as coordinates), " +
                "receipt photos and the text read from them, odometer readings, and settings such as units, reimbursement rate, vehicle name, annual target, and automatic detection. " +
                "If you sign in, the app keeps your email, name, and sign-in tokens in its private storage. It does not store your password. " +
                "Guest use does not create an account. Android may include this app's data in the device backup tied to your Google account."
        ),
        LegalSection(
            "Account and backup",
            "If you create an account, your name, email, and password are sent over HTTPS to the account host (Supabase) to open a session. " +
                "When you are signed in and backup is available, trips and receipt details are copied there, including route coordinates, addresses, purpose, and the text read from a receipt. " +
                "The receipt photo stays on the phone. The backup stores the photo's local path, not the picture. " +
                "Signing out clears the session on the phone. The backup remains until items are deleted as described below. " +
                "The app has no way to delete the account."
        ),
        LegalSection(
            "Location",
            "With your permission, the app reads precise location to measure a drive, including while the app is in the background when automatic detection is on. " +
                "Coordinates are turned into addresses with the phone's geocoder, which may send a coordinate or a place name to the service that provides geocoding on that device. " +
                "Location is used to build your trips. It is not sold."
        ),
        LegalSection(
            "Receipts",
            "You can photograph a fuel receipt. The photo is saved on the phone. The photo is read on the phone, and it is not uploaded for recognition. " +
                "Fields such as the merchant, total, fuel amount, and payment method are saved with the receipt and included in backup when you are signed in. " +
                "Deleting a receipt deletes that photo from the phone and, when you are signed in, the receipt details from the backup."
        ),
        LegalSection(
            "Bluetooth",
            "If you choose it, the app connects to a paired OBD adapter and reads the odometer. That reading stays in settings on the phone. " +
                "The app does not upload a vehicle diagnostic log."
        ),
        LegalSection(
            "Notifications",
            "The app can show a notification while a trip is being recorded. That notification stays on the device."
        ),
        LegalSection(
            "What we do not do",
            "We do not sell your information. This version does not show ads and does not include an analytics or advertising kit. " +
                "Routes and receipt photos are not used to build an advertising profile."
        ),
        LegalSection(
            "How long it is kept",
            "Trips and receipts stay on the phone until you delete them or uninstall the app. " +
                "Deleting one trip or receipt also removes it from the backup when you are signed in. " +
                "Clear all trips removes trips on this phone only. " +
                "Uninstalling the app does not delete a signed-in backup. " +
                "The app has no way to delete the account."
        ),
        LegalSection(
            "Your choices",
            "You can deny location, camera, notification, or Bluetooth permission, turn off automatic detection, stay a guest so nothing is backed up, " +
                "delete a trip or receipt, clear trips on this phone, and sign out. Both documents stay available in Settings."
        ),
        LegalSection(
            "Children",
            "MileageTracker is not directed at children under 13, and we do not knowingly create accounts for them."
        ),
        LegalSection(
            "Changes",
            "If this policy changes, the date at the top of this screen changes."
        )
    )
}
