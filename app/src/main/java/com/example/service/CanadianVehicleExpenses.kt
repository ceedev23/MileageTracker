package com.example.service

import com.example.data.TripEntity
import com.example.data.VehicleExpenseEntity
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class CanadianExpenseResult(val totalKm: Double, val businessKm: Double, val operatingCosts: Double,
    val businessShare: Double, val directCosts: Double, val estimatedDeduction: Double)

object CanadianVehicleExpenses {
    const val SOURCE = "https://www.canada.ca/en/revenue-agency/services/tax/businesses/topics/sole-proprietorships-partnerships/report-business-income-expenses/completing-form-t2125/calculating-motor-vehicle-expenses.html"
    const val LIMITS = "Estimate for one vehicle and calendar year. Enter CAD expenses after applicable interest/lease limits and recoverable GST/HST adjustments. Includes fuel once; saved receipts are not added automatically. Excludes CCA, vehicle purchase price, loan principal and tax savings. Review eligibility before filing."
    fun calculate(e: VehicleExpenseEntity, trips: List<TripEntity>, zone: TimeZone = TimeZone.getDefault()): CanadianExpenseResult {
        require(e.vehicle.isNotBlank() && e.vehicle.length <= 40 && e.year in 1900..9998) { "Choose a vehicle and valid year" }
        val costs = listOf(e.fuel, e.maintenance, e.insurance, e.registration, e.allowableInterest, e.allowableLease, e.businessParking, e.supplementaryInsurance)
        require(costs.all { it.isFinite() && it >= 0 && it <= 100000000 }) { "Expenses must be finite, non-negative CAD amounts" }
        require(e.openingKm.isFinite() && e.closingKm.isFinite() && e.openingKm >= 0 && e.closingKm > e.openingKm) { "Closing odometer must exceed opening odometer (km)" }
        val start = Calendar.getInstance(zone).apply { clear(); set(e.year, Calendar.JANUARY, 1) }.timeInMillis
        val end = Calendar.getInstance(zone).apply { clear(); set(e.year + 1, Calendar.JANUARY, 1) }.timeInMillis
        val vehicleTrips = trips.filter { it.vehicle == e.vehicle && it.startDate < end && it.endDate >= start }
        require(vehicleTrips.none { it.type == "Business" && (it.startDate < start || it.endDate >= end) }) { "Split business trips crossing the year boundary before calculating" }
        require(vehicleTrips.all { it.distanceKm.isFinite() && it.distanceKm >= 0 }) { "Correct invalid trip distances" }
        require(vehicleTrips.all { it.endDate >= it.startDate }) { "Correct invalid trip dates" }
        val business = vehicleTrips.filter { it.type == "Business" && it.startDate >= start && it.startDate < end }.sumOf { it.distanceKm }
        val total = e.closingKm - e.openingKm
        require(business <= total) { "Business kilometres exceed odometer distance. Check readings or duplicate trips" }
        val ratio = business / total
        val operating = costs.take(6).sum()
        val direct = e.businessParking + e.supplementaryInsurance
        return CanadianExpenseResult(total, business, operating, ratio, direct, operating * ratio + direct)
    }
    fun csv(e: VehicleExpenseEntity, result: CanadianExpenseResult): String {
        fun n(v: Double) = String.format(Locale.US, "%.2f", v)
        val vehicle = "'" + e.vehicle.replace("\"", "\"\"")
        return "Vehicle,Year,Opening km,Closing km,Total km,Business km,Business use %,Fuel CAD,Maintenance CAD,Insurance CAD,Registration CAD,Allowable interest CAD,Allowable lease CAD,Business parking CAD,Supplementary insurance CAD,Estimated deduction CAD\r\n" +
            listOf("\"$vehicle\"", e.year.toString(), n(e.openingKm), n(e.closingKm), n(result.totalKm), n(result.businessKm), n(result.businessShare * 100),
                n(e.fuel), n(e.maintenance), n(e.insurance), n(e.registration), n(e.allowableInterest), n(e.allowableLease), n(e.businessParking), n(e.supplementaryInsurance), n(result.estimatedDeduction)).joinToString(",") +
            "\r\n\"$LIMITS\"\r\n$SOURCE\r\n"
    }
}
