package com.example.data

import kotlinx.coroutines.flow.Flow

class TripRepository(private val tripDao: TripDao) {
    val allTrips: Flow<List<TripEntity>> = tripDao.getAllTrips()

    suspend fun getAllTripsList(): List<TripEntity> = tripDao.getAllTripsList()

    suspend fun getTripById(id: Long): TripEntity? = tripDao.getTripById(id)

    suspend fun insertTrip(trip: TripEntity): Long = tripDao.insertTrip(trip)

    suspend fun insertTrips(trips: List<TripEntity>) = tripDao.insertTrips(trips)

    suspend fun updateTrip(trip: TripEntity) = tripDao.updateTrip(trip)

    suspend fun classifyTrips(ids: List<Long>, type: String) = tripDao.classifyTrips(ids, type)

    suspend fun deleteTrip(trip: TripEntity) = tripDao.deleteTrip(trip)

    suspend fun deleteTripById(id: Long) = tripDao.deleteTripById(id)

    suspend fun clearAll() = tripDao.clearAllTrips()
}
