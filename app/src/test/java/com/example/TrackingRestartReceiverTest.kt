package com.example

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsManager
import com.example.service.TrackingRestartReceiver
import com.example.service.TrackingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TrackingRestartReceiverTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val starts = mutableListOf<Intent>()
    private val context = object : ContextWrapper(application) {
        override fun startForegroundService(service: Intent): ComponentName? {
            starts.add(service)
            return service.component
        }
    }
    private val receiver = TrackingRestartReceiver()

    @Test
    fun bootAndAppUpdateRequestForegroundMonitoring() {
        grantTrackingPermissions()

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        assertEquals(2, starts.size)
        starts.forEach { intent ->
            assertEquals(TrackingService.ACTION_START_MONITORING, intent.action)
            assertEquals(ComponentName(application, TrackingService::class.java), intent.component)
        }
    }

    @Test
    fun bootSkipsMonitoringWhenBackgroundLocationIsMissing() {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertTrue(starts.isEmpty())
    }

    @Test
    fun bootSkipsMonitoringWhenPreciseLocationIsMissing() {
        shadowOf(application).grantPermissions(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertTrue(starts.isEmpty())
    }

    @Test
    fun bootRespectsDisabledAutoTracking() {
        grantTrackingPermissions()
        SettingsManager(application).setAutoDetect(false)

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertTrue(starts.isEmpty())
    }

    private fun grantTrackingPermissions() {
        shadowOf(application).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
    }
}
