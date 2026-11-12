package com.example.service

import com.example.data.VehicleExpenseEntity
import com.example.data.TripEntity
import java.util.TimeZone
import kotlin.math.min

/**
 * T2125 preparation worksheet, not a tax return. Capital allowances require a
 * separately verified class/UCC calculation (including acquisitions/disposals).
 */
data class CanadianTaxAdjustments(
    val operatingItcCad: Double = 0.0,
    val directExpenseItcCad: Double = 0.0,
    val verifiedCcaClaimCad: Double = 0.0,
    val ccaClassAndBasis: String = "",
    val ownershipPeriodReviewed: Boolean = false
)

data class CanadianTaxWorksheetResult(
    val base: CanadianExpenseResult,
    val netOperatingCosts: Double,
    val netDirectCosts: Double,
    val t2125VehicleExpenses: Double,
    val t2125CcaSeparate: Double,
    val combinedEstimate: Double,
    val warnings: List<String>
)

object CanadianTaxWorksheet {
    const val CRA_MOTOR = "https://www.canada.ca/en/revenue-agency/services/tax/businesses/small-businesses-self-employed-income/business-income-tax-reporting/business-expenses/motor-vehicle-expenses.html"
    const val CRA_CCA = "https://www.canada.ca/en/revenue-agency/services/forms-publications/publications/t4002/t4002-6.html"
    const val CRA_ITC = "https://www.canada.ca/en/revenue-agency/services/tax/businesses/topics/gst-hst-businesses/calculate-prepare-report/input-tax-credit.html"

    /** 2026 new passenger-car loan ceiling; acquisition-year rules must be checked for earlier loans. */
    fun passengerInterestLimit2026(interestPaidCad: Double, eligibleDays: Int): Double {
        require(interestPaidCad.isFinite() && interestPaidCad >= 0 && eligibleDays in 0..366)
        return min(interestPaidCad, eligibleDays * 11.66)
    }

    fun calculate(
        expenses: VehicleExpenseEntity, trips: List<TripEntity>, adjustments: CanadianTaxAdjustments,
        zone: TimeZone = TimeZone.getDefault()
    ): CanadianTaxWorksheetResult {
        val base = CanadianVehicleExpenses.calculate(expenses, trips, zone)
        val amounts = listOf(adjustments.operatingItcCad, adjustments.directExpenseItcCad, adjustments.verifiedCcaClaimCad)
        require(amounts.all { it.isFinite() && it >= 0 }) { "Tax adjustments must be non-negative CAD amounts" }
        require(adjustments.operatingItcCad <= base.operatingCosts) { "Operating ITCs exceed operating costs" }
        require(adjustments.directExpenseItcCad <= base.directCosts) { "Direct ITCs exceed direct costs" }
        require(adjustments.verifiedCcaClaimCad == 0.0 || adjustments.ccaClassAndBasis.isNotBlank()) {
            "Enter the CCA class and reviewed UCC/claim basis"
        }
        val netOperating = base.operatingCosts - adjustments.operatingItcCad
        val netDirect = base.directCosts - adjustments.directExpenseItcCad
        val vehicleExpenses = netOperating * base.businessShare + netDirect
        // Verified CCA is entered as the business-use amount from a separate CRA class worksheet.
        val cca = adjustments.verifiedCcaClaimCad
        val warnings = buildList {
            if (!adjustments.ownershipPeriodReviewed) add("Confirm odometer readings cover the tax year or actual ownership/use period.")
            if (trips.any { it.vehicle == expenses.vehicle && it.type == null }) add("Unclassified journeys need review; they are not business kilometres.")
            if (expenses.allowableInterest > 0) add("Verify acquisition-date interest ceiling and Chart B.")
            if (expenses.allowableLease > 0) add("Verify Chart C including vehicle list-price proration and lease dates; monthly ceiling alone is insufficient.")
            if (cca > 0) add("CCA is a separate T2125 line. Verify class, acquisition year, opening UCC, additions, dispositions, available incentives, recapture and ITC treatment.")
            add("Reconcile receipts, GST/HST ITCs and reimbursements before filing; do not deduct the same amount twice.")
        }
        return CanadianTaxWorksheetResult(base, netOperating, netDirect, vehicleExpenses, cca, vehicleExpenses + cca, warnings)
    }
}
