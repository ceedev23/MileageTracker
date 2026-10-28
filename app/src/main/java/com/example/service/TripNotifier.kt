package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.TripEntity
import java.util.Locale

data class TripUpdate(
    val newTrip: TripEntity?,
    val pendingCount: Int
)

fun isPendingTrip(trip: TripEntity): Boolean =
    trip.type.isNullOrBlank() || trip.type == "Unclassified"

/** Baseline (`previousIds == null`) never counts existing rows as a new trip. */
fun tripUpdates(previousIds: Set<Long>?, trips: List<TripEntity>): TripUpdate {
    val pending = trips.count(::isPendingTrip)
    // shortcut: a batch of new trips only refreshes Pending trip, post each one if every insert should alert
    val added = if (previousIds == null) emptyList() else trips.filter { it.id != 0L && it.id !in previousIds }
    return TripUpdate(newTrip = added.singleOrNull(), pendingCount = pending)
}

fun currentTripText(distanceKm: Double, speedKmh: Float): Pair<String, String> =
    "Current trip" to String.format(Locale.US, "%.1f km • %d km/h", distanceKm, speedKmh.toInt())

fun newTripText(trip: TripEntity): Pair<String, String> {
    val km = String.format(Locale.US, "%.1f", trip.distanceKm)
    val text = if (isPendingTrip(trip)) "$km km • tap to classify" else "$km km • ${trip.type}"
    return "New trip" to text
}

fun pendingTripText(count: Int): String =
    if (count == 1) "1 trip to classify" else "$count trips to classify"

object TripNotifier {
    private const val CHANNEL_ID = "trip_updates"
    private const val NEW_ID = 1002
    private const val PENDING_ID = 1003

    private var lastPending = -1

    fun post(context: Context, update: TripUpdate) {
        update.newTrip?.let { showNew(context, it) }
        showPending(context, update.pendingCount)
    }

    fun showNew(context: Context, trip: TripEntity) {
        val (title, text) = newTripText(trip)
        notify(context, NEW_ID, title, text, silent = false)
    }

    fun showPending(context: Context, count: Int) {
        if (!canNotify(context.applicationContext)) return
        if (count == lastPending) return
        val alert = (lastPending < 0 && count > 0) || count > lastPending
        lastPending = count
        if (count <= 0) {
            manager(context)?.cancel(PENDING_ID)
            return
        }
        notify(context, PENDING_ID, "Pending trip", pendingTripText(count), silent = !alert)
    }

    private fun notify(context: Context, id: Int, title: String, text: String, silent: Boolean) {
        val appContext = context.applicationContext
        if (!canNotify(appContext)) return
        val manager = manager(appContext) ?: return
        ensureChannel(manager)
        val open = PendingIntent.getActivity(
            appContext,
            id,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(silent)
            .setSilent(silent)
            .build()
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Trip updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New trips, the current drive, and trips waiting to be classified"
            }
        )
    }

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(NotificationManager::class.java)
}
