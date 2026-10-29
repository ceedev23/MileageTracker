package com.example.data

/** Presentation preferences only. Switching never rewrites or hides trip records. */
enum class WorkProfile(
    val id: String, val label: String, val purposeLabel: String,
    val purposeHint: String, val reportTitle: String, val reportHint: String,
    val quickPurposes: List<String>
) {
    GENERAL("general", "General", "Purpose / Notes", "Add the reason for this trip", "Mileage & Expense Report",
        "Trip records, fuel expenses and rate-based estimates for review",
        listOf("Client visit", "Work travel")),
    CARE("care", "Care Work", "Agency / Assignment / Purpose",
        "Example: Agency A - between visits. Use neutral labels; omit patient names and clinical information.",
        "Care Work Mileage Report", "Review travel by purpose for your agency, employer or accountant. Check reimbursement eligibility separately.",
        listOf("Between care visits", "Facility assignment", "Agency meeting")),
    DELIVERY("delivery", "Delivery Work", "Platform / Shift / Purpose",
        "Example: DoorDash - evening shift. Omit customer names, order numbers and delivery details.",
        "Delivery Work Mileage Report", "Review mileage and recorded fuel costs. Earnings syncing and profit calculations are not included.",
        listOf("Uber Eats", "DoorDash", "Multi-app shift"));

    companion object {
        fun fromId(id: String?): WorkProfile = entries.firstOrNull { it.id == id } ?: GENERAL
    }
}
