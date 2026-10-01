package com.example.data

data class LatLngPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val speedKmh: Float = 0f
)
