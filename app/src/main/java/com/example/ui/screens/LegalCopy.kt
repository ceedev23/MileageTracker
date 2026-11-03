package com.example.ui.screens

enum class LegalDoc(val title: String) {
    TERMS("Terms of Service"),
    PRIVACY("Privacy Policy"),
    REFUNDS("Refund Policy"),
    SUBSCRIPTIONS("Subscriptions & Cancellation"),
    DATA("Data Retention & Deletion"),
    LOCATION("Location Tracking & Consent"),
    ACCEPTABLE("Acceptable Use Policy"),
    SECURITY("Security & Data Protection"),
    COOKIES("Cookie Policy"),
    DISCLAIMER("Accuracy & Tax Disclaimer")
}

data class LegalSection(val heading: String, val body: String)

object LegalCopy {
    const val UPDATED = "November 3, 2026"

    fun sections(doc: LegalDoc): List<LegalSection> = when (doc) {
        LegalDoc.TERMS -> terms
        LegalDoc.PRIVACY -> privacy
        LegalDoc.REFUNDS -> refunds
        LegalDoc.SUBSCRIPTIONS -> subscriptions
        LegalDoc.DATA -> data
        LegalDoc.LOCATION -> location
        LegalDoc.ACCEPTABLE -> acceptable
        LegalDoc.SECURITY -> security
        LegalDoc.COOKIES -> cookies
        LegalDoc.DISCLAIMER -> disclaimer
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

    // Draft disclosures: confirm operator identity, contact details, billing configuration,
    // storage practices and jurisdiction-specific consumer rights before publication.
    private val refunds = listOf(
        LegalSection("Current availability", "This version does not offer a paid subscription or in-app purchase. No subscription refund is available for a charge that MileageTracker did not collect."),
        LegalSection("Future paid plans", "Before paid plans launch, the checkout must disclose the price, renewal interval, cancellation method, refund eligibility, and applicable taxes. This policy must be updated before any paid purchase is accepted."),
        LegalSection("App store purchases", "If a future purchase is billed by Apple or Google, request a refund through the applicable store, subject to its rules and your legal rights."),
        LegalSection("Consumer rights", "Nothing here limits refund or cancellation rights that cannot lawfully be waived.")
    )
    private val subscriptions = listOf(
        LegalSection("Plans and billing", "The current app has no active paid subscription checkout. If paid plans are introduced, prices, billing currency, renewal frequency and any free trial conditions will be displayed before purchase."),
        LegalSection("Renewals", "A future automatically renewing plan must clearly identify its renewal terms and provide a cancellation path before the user subscribes."),
        LegalSection("Cancellation", "Uninstalling an app does not necessarily cancel a future store subscription. Store-billed plans must be managed through the store used for purchase. Direct billing will require an accessible cancellation method."),
        LegalSection("Changes", "We will publish applicable billing and refund terms before introducing paid plans.")
    )
    private val data = listOf(
        LegalSection("Local and cloud copies", "Guest records remain on the device, subject to operating-system backup. Signed-in trip and receipt records may also be synced to Supabase. Optional receipt photo backup may upload images when enabled."),
        LegalSection("Deleting records", "Individual deletion may remove the corresponding synced record when signed in. Clear all trips is a local action; it is not a request to erase every cloud copy."),
        LegalSection("Account deletion", "The current app does not provide an in-app account deletion workflow. Signing out or uninstalling does not erase your cloud account or backups. A verified account deletion request process must be added before public launch."),
        LegalSection("Retention and requests", "Cloud records may persist until removed, subject to operational backups and legal retention requirements. Contact details and a documented response process for access, correction, export and deletion requests must be published before launch.")
    )
    private val location = listOf(
        LegalSection("Why location is used", "Precise GPS data is used to record routes, distance, start and end locations and automatic trips."),
        LegalSection("Background collection", "When automatic detection is enabled and the necessary Android permission is granted, location can be collected while the app is not visible. Tracking may continue until stopped or disabled."),
        LegalSection("Your control", "You can turn automatic detection off, stop an active trip or revoke location permissions in Android Settings. Some trip features will then stop working."),
        LegalSection("Sharing", "Signed-in trips may be synced to Supabase. Device geocoding providers may process coordinates to resolve addresses. We do not sell route information.")
    )
    private val acceptable = listOf(
        LegalSection("Permitted use", "Use MileageTracker to maintain records of journeys you are authorized to record."),
        LegalSection("Prohibited conduct", "Do not access other accounts, interfere with the service, submit malicious content, abuse infrastructure or use location tracking to stalk or surveil another person unlawfully."),
        LegalSection("Accuracy and integrity", "You are responsible for reviewing manually entered mileage, receipts, trip categories and submitted reports."),
        LegalSection("Enforcement", "We may restrict access for serious misuse, subject to applicable law.")
    )
    private val security = listOf(
        LegalSection("Account protection", "Authentication uses Supabase. Keep your sign-in credentials and devices secure; do not share your account."),
        LegalSection("Data transfers", "Account and sync requests use encrypted HTTPS connections. Local records are stored in the app's device storage. Security cannot be guaranteed against every threat."),
        LegalSection("Optional photo backup", "Receipt images are uploaded only if the optional photo-backup feature is enabled. Receipt images can contain sensitive payment or merchant details."),
        LegalSection("Incidents", "If a security incident affects personal information, notifications and regulatory reporting will be handled as required by applicable law. An operator contact and incident-response procedure must be finalized before launch.")
    )
    private val cookies = listOf(
        LegalSection("Android application", "This native Android application does not itself use browser cookies for advertising."),
        LegalSection("Website and external services", "A future website, payment page or third-party sign-in flow may use essential cookies or other technologies. Any non-essential tracking and consent choices must be disclosed where applicable."),
        LegalSection("Updates", "This policy will be updated if website analytics, advertising or cookie-based services are introduced.")
    )
    private val disclaimer = listOf(
        LegalSection("Trip accuracy", "GPS signals, background restrictions, battery settings, tunnels and device conditions can cause missing or inaccurate drives. Verify trip records before relying on them."),
        LegalSection("Odometer and receipts", "Camera-based readings and receipt recognition can contain errors. Review and correct extracted values."),
        LegalSection("Taxes and reimbursement", "Mileage rates and totals are estimates, not accounting, legal or tax advice. Eligibility and recordkeeping rules differ by country, employer and circumstances."),
        LegalSection("No guaranteed outcome", "MileageTracker does not guarantee a tax deduction, reimbursement, audit acceptance or uninterrupted availability. Statutory consumer protections remain unaffected.")
    )
}
