package com.example.service

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * CRA prescribed reasonable automobile allowance for employee business travel.
 * Not a self-employed vehicle expense deduction or a guarantee of tax-free treatment.
 * https://www.canada.ca/en/revenue-agency/services/tax/businesses/topics/payroll/benefits-allowances/automobile/automobile-motor-vehicle-allowances.html
 */
object CraMileageAllowance {
    private const val THRESHOLD_KM = 5000.0
    data class Rates(val first: Double, val additional: Double)
    data class Result(
        val year: Int,
        val territory: Boolean,
        val businessKm: Double,
        val firstTierKm: Double,
        val additionalTierKm: Double,
        val amountCad: BigDecimal,
        val rates: Rates
    )

    /** Only published years are supported; do not silently apply 2026 rates to future years. */
    fun rates(year: Int, territory: Boolean): Rates {
        val province = when (year) {
            2022 -> Rates(0.61, 0.55)
            2023 -> Rates(0.68, 0.62)
            2024 -> Rates(0.70, 0.64)
            2025 -> Rates(0.72, 0.66)
            2026 -> Rates(0.73, 0.67)
            else -> throw IllegalArgumentException("CRA allowance rates not configured for year $year")
        }
        return if (territory) Rates(province.first + 0.04, province.additional + 0.04) else province
    }

    /** Accumulated eligible business kilometres in the same calendar year, before this segment. */
    fun calculate(year: Int, territory: Boolean, priorBusinessKm: Double, segmentBusinessKm: Double): Result {
        require(priorBusinessKm.isFinite() && priorBusinessKm >= 0.0)
        require(segmentBusinessKm.isFinite() && segmentBusinessKm >= 0.0)
        require(priorBusinessKm + segmentBusinessKm <= 100_000_000.0) { "Invalid annual distance" }
        val r = rates(year, territory)
        val first = (THRESHOLD_KM - priorBusinessKm).coerceAtLeast(0.0).coerceAtMost(segmentBusinessKm)
        val additional = segmentBusinessKm - first
        val amount = BigDecimal.valueOf(first).multiply(BigDecimal.valueOf(r.first))
            .add(BigDecimal.valueOf(additional).multiply(BigDecimal.valueOf(r.additional)))
            .setScale(2, RoundingMode.HALF_UP)
        return Result(year, territory, segmentBusinessKm, first, additional, amount, r)
    }

    fun annual(year: Int, territory: Boolean, businessKm: Double): Result =
        calculate(year, territory, 0.0, businessKm)
}
