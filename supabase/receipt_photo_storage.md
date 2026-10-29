# Receipt photo backup (development)

The confirmed MileageTracker development project is `gszhecjcksyxlptlmwok`, in
Canada Central (`ca-central-1`). GitHub changes belong on `dev` only.

Apply `receipt_photo_storage.sql` after the base schema on a new environment.
It creates the private `receipt-photos` bucket (JPEG, maximum 10 MB), scopes
all object operations to the signed-in user's UUID folder, and removes
anonymous access to receipt metadata. It does not remove tables or receipt rows.
The migration `private_receipt_photo_storage` was applied to the confirmed
development project. Never use a service-role key in the Android app.

`RECEIPT_BACKUP_READY` controls availability. Keep it false for environments
whose storage and region have not been verified. `RECEIPT_STORAGE_REGION` is
the disclosure displayed before opt-in; update it if the environment changes.
Photo backup defaults off and consent is scoped to account and project on this
installation. A new phone must sign in and enable photo backup to restore photos.
A failed upload or restore makes full sync fail rather than certify a backup.

Device filesystem paths are never uploaded. Cloud receipt `image_path` stores
an opaque `receipt-photo:<user UUID>/<receipt ID>.jpg` reference. Existing
path-only backups cannot restore photos that were never uploaded; the original
phone is needed to upload them. Original JPEG bytes are retained without OCR
rewriting. Receipt details continue syncing independently when photos are off.

Turning backup off does not erase existing cloud copies. Deleting a receipt
persists a deletion request before removing its local row and image; the next
successful online sync removes the object and metadata before restoring anything.
Do not uninstall or clear app data before pending deletions have synced.
The app does not enforce a blanket tax retention period or promise that a photo
lets the user destroy paper originals. Tax record export, including receipt
images, remains the separate reporting work item.

Android cloud backup excludes receipt images, Supabase credentials, and the
photo consent/deletion preferences. A direct device transfer may retain images
but does not transfer cloud credentials or photo consent.

Canadian project hosting is not a promise that all processing remains in Canada.
Supabase's DPA includes processing and transfer terms. Before public launch,
review the privacy notice, subprocessors, applicable transfer assessments,
account deletion procedure, and backup purge schedule for the intended markets.
This implementation is not a legal compliance certification.

Verification: Robolectric tests simulate Storage/REST responses, including
cross-account reference rejection, opt-in, failed transfers and retry. These
are separate from live two-account and new-device acceptance tests.
