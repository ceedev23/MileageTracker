package com.example.data.supabase

import com.example.data.GasReceiptEntity
import com.example.data.TripEntity
import org.json.JSONObject

data class SupabaseUser(
    val id: String,
    val email: String,
    val name: String? = null
)

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val user: SupabaseUser,
    val expiresAt: Long = System.currentTimeMillis() + 3600 * 1000L
) {
    fun isAccessExpired(now: Long = System.currentTimeMillis(), skewMs: Long = 60_000L): Boolean =
        expiresAt <= now + skewMs
}

enum class SyncStatus {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR,
    NOT_CONFIGURED
}

data class SupabaseSyncState(
    val status: SyncStatus = SyncStatus.IDLE,
    val lastSyncTime: Long? = null,
    val message: String? = null,
    val syncedTripsCount: Int = 0,
    val syncedReceiptsCount: Int = 0
)

data class SupabaseTripDto(
    val id: Long? = null,
    val clientId: Long,
    val userId: String? = null,
    val startDate: Long,
    val endDate: Long,
    val distanceKm: Double,
    val type: String? = null,
    val startAddress: String? = null,
    val endAddress: String? = null,
    val purpose: String? = null,
    val vehicle: String? = null,
    val pathJson: String = "[]",
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun toTripEntity(): TripEntity {
        return TripEntity(
            id = clientId,
            startDate = startDate,
            endDate = endDate,
            distanceKm = distanceKm,
            type = type,
            startAddress = startAddress,
            endAddress = endAddress,
            purpose = purpose,
            vehicle = vehicle,
            pathJson = pathJson
        )
    }

    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        if (id != null && id > 0) json.put("id", id)
        json.put("client_id", clientId)
        if (userId != null) json.put("user_id", userId)
        json.put("start_date", startDate)
        json.put("end_date", endDate)
        json.put("distance_km", distanceKm)
        if (type != null) json.put("type", type) else json.put("type", JSONObject.NULL)
        if (startAddress != null) json.put("start_address", startAddress) else json.put("start_address", JSONObject.NULL)
        if (endAddress != null) json.put("end_address", endAddress) else json.put("end_address", JSONObject.NULL)
        if (purpose != null) json.put("purpose", purpose) else json.put("purpose", JSONObject.NULL)
        if (vehicle != null) json.put("vehicle", vehicle) else json.put("vehicle", JSONObject.NULL)
        json.put("path_json", pathJson)
        return json
    }

    companion object {
        fun fromTripEntity(entity: TripEntity, userId: String? = null): SupabaseTripDto {
            return SupabaseTripDto(
                clientId = entity.id,
                userId = userId,
                startDate = entity.startDate,
                endDate = entity.endDate,
                distanceKm = entity.distanceKm,
                type = entity.type,
                startAddress = entity.startAddress,
                endAddress = entity.endAddress,
                purpose = entity.purpose,
                vehicle = entity.vehicle,
                pathJson = entity.pathJson
            )
        }

        fun fromJsonObject(json: JSONObject): SupabaseTripDto {
            val rawClientId = json.optLong("client_id", 0L)
            val id = if (json.has("id")) json.optLong("id") else null
            val effectiveClientId = if (rawClientId > 0) rawClientId else (id ?: 0L)

            return SupabaseTripDto(
                id = id,
                clientId = effectiveClientId,
                userId = json.optString("user_id").takeIf { it.isNotBlank() },
                startDate = json.optLong("start_date", 0L),
                endDate = json.optLong("end_date", 0L),
                distanceKm = json.optDouble("distance_km", 0.0),
                type = json.optString("type").takeIf { it.isNotBlank() && it != "null" },
                startAddress = json.optString("start_address").takeIf { it.isNotBlank() && it != "null" },
                endAddress = json.optString("end_address").takeIf { it.isNotBlank() && it != "null" },
                purpose = json.optString("purpose").takeIf { it.isNotBlank() && it != "null" },
                vehicle = json.optString("vehicle").takeIf { it.isNotBlank() && it != "null" },
                pathJson = json.optString("path_json", "[]"),
                createdAt = json.optString("created_at").takeIf { it.isNotBlank() },
                updatedAt = json.optString("updated_at").takeIf { it.isNotBlank() }
            )
        }
    }
}

data class SupabaseReceiptDto(
    val id: Long? = null,
    val clientId: Long,
    val userId: String? = null,
    val timestamp: Long,
    val imagePath: String,
    val merchant: String? = null,
    val totalAmount: Double = 0.0,
    val currency: String = "$",
    val fuelType: String? = null,
    val gallonsOrLiters: Double? = null,
    val unit: String = "L",
    val pricePerUnit: Double? = null,
    val receiptDate: String? = null,
    val paymentMethod: String? = null,
    val rawExtraction: String? = null,
    val tripId: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun toGasReceiptEntity(): GasReceiptEntity {
        return GasReceiptEntity(
            id = clientId,
            timestamp = timestamp,
            imagePath = imagePath,
            merchant = merchant,
            totalAmount = totalAmount,
            currency = currency,
            fuelType = fuelType,
            gallonsOrLiters = gallonsOrLiters,
            unit = unit,
            pricePerUnit = pricePerUnit,
            receiptDate = receiptDate,
            paymentMethod = paymentMethod,
            rawExtraction = rawExtraction,
            tripId = tripId
        )
    }

    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        if (id != null && id > 0) json.put("id", id)
        json.put("client_id", clientId)
        if (userId != null) json.put("user_id", userId)
        json.put("timestamp", timestamp)
        json.put("image_path", imagePath)
        if (merchant != null) json.put("merchant", merchant) else json.put("merchant", JSONObject.NULL)
        json.put("total_amount", totalAmount)
        json.put("currency", currency)
        if (fuelType != null) json.put("fuel_type", fuelType) else json.put("fuel_type", JSONObject.NULL)
        if (gallonsOrLiters != null) json.put("gallons_or_liters", gallonsOrLiters) else json.put("gallons_or_liters", JSONObject.NULL)
        json.put("unit", unit)
        if (pricePerUnit != null) json.put("price_per_unit", pricePerUnit) else json.put("price_per_unit", JSONObject.NULL)
        if (receiptDate != null) json.put("receipt_date", receiptDate) else json.put("receipt_date", JSONObject.NULL)
        if (paymentMethod != null) json.put("payment_method", paymentMethod) else json.put("payment_method", JSONObject.NULL)
        if (rawExtraction != null) json.put("raw_extraction", rawExtraction) else json.put("raw_extraction", JSONObject.NULL)
        if (tripId != null) json.put("trip_id", tripId) else json.put("trip_id", JSONObject.NULL)
        return json
    }

    companion object {
        fun fromGasReceiptEntity(entity: GasReceiptEntity, userId: String? = null): SupabaseReceiptDto {
            return SupabaseReceiptDto(
                clientId = entity.id,
                userId = userId,
                timestamp = entity.timestamp,
                imagePath = entity.imagePath,
                merchant = entity.merchant,
                totalAmount = entity.totalAmount,
                currency = entity.currency,
                fuelType = entity.fuelType,
                gallonsOrLiters = entity.gallonsOrLiters,
                unit = entity.unit,
                pricePerUnit = entity.pricePerUnit,
                receiptDate = entity.receiptDate,
                paymentMethod = entity.paymentMethod,
                rawExtraction = entity.rawExtraction,
                tripId = entity.tripId
            )
        }

        fun fromJsonObject(json: JSONObject): SupabaseReceiptDto {
            val rawClientId = json.optLong("client_id", 0L)
            val id = if (json.has("id")) json.optLong("id") else null
            val effectiveClientId = if (rawClientId > 0) rawClientId else (id ?: 0L)

            return SupabaseReceiptDto(
                id = id,
                clientId = effectiveClientId,
                userId = json.optString("user_id").takeIf { it.isNotBlank() },
                timestamp = json.optLong("timestamp", 0L),
                imagePath = json.optString("image_path", ""),
                merchant = json.optString("merchant").takeIf { it.isNotBlank() && it != "null" },
                totalAmount = json.optDouble("total_amount", 0.0),
                currency = json.optString("currency", "$"),
                fuelType = json.optString("fuel_type").takeIf { it.isNotBlank() && it != "null" },
                gallonsOrLiters = if (json.has("gallons_or_liters") && !json.isNull("gallons_or_liters")) json.getDouble("gallons_or_liters") else null,
                unit = json.optString("unit", "L").ifBlank { "L" },
                pricePerUnit = if (json.has("price_per_unit") && !json.isNull("price_per_unit")) json.getDouble("price_per_unit") else null,
                receiptDate = json.optString("receipt_date").takeIf { it.isNotBlank() && it != "null" },
                paymentMethod = json.optString("payment_method").takeIf { it.isNotBlank() && it != "null" },
                rawExtraction = json.optString("raw_extraction").takeIf { it.isNotBlank() && it != "null" },
                tripId = if (json.has("trip_id") && !json.isNull("trip_id")) json.getLong("trip_id") else null,
                createdAt = json.optString("created_at").takeIf { it.isNotBlank() },
                updatedAt = json.optString("updated_at").takeIf { it.isNotBlank() }
            )
        }
    }
}
