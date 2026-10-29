# Work profiles: first version

Branch: `feature/work-profiles`, based on dev commit `117dcc9`.

Settings now offers General, Care Work and Delivery Work. General preserves the existing experience. The selection survives restart and changes Home shortcuts, trip-purpose prompts, quick purpose suggestions, live-drive note labels and report wording. It does not partition or rewrite stored trips. Units, vehicle, odometer and the custom reference rate remain shared preferences.

Care Work is intended for PSWs, CNAs, visiting nurses and other care workers. Users can put an agency or assignment label in the existing purpose field. Delivery Work offers platform/shift examples. This uses the existing purpose field and its current sync/export path; it is not a separate employer database or automatic connection to any platform.

Reports can select an exact saved purpose. Care and Delivery reports default to Business-only selection; users can change this. With either filter enabled, only fuel receipts linked to selected trips are included. Unlinked receipts are excluded and the UI explains this. Preview and export use a fixed snapshot, including profile and filters, to avoid changing a report when preferences change.

Care prompts ask users to omit patient names and clinical information. Delivery prompts ask users to omit customer/order details. Classifying a trip as Business does not establish reimbursement eligibility or tax deductibility.

The profile layer does not yet add per-agency rates, parking/toll receipt categories, income imports, net-profit calculations, commute classification, split/merge history or U.S. actual-expense tax calculations. Existing mileage, fuel receipts, Canadian expense worksheet and odometer functions remain available. Physical-device verification is still required.

CI runs on both dev and this feature branch. Tests cover selection, restart persistence, unknown-setting fallback, preservation of other settings and records, purpose/classification filtering, receipt exclusion, and profile-aware export snapshots.
