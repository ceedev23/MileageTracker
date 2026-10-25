package com.example.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.data.InputCheck
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

data class ObdDevice(val name: String, val address: String)

object ObdBluetoothReader {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun unavailableReason(context: Context): String? {
        val adapter = adapter(context) ?: return "This phone has no Bluetooth. Type the odometer instead."
        if (!adapter.isEnabled) return "Turn Bluetooth on, then try again."
        return null
    }

    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<ObdDevice> {
        if (unavailableReason(context) != null) return emptyList()
        val adapter = adapter(context) ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty()
                .map { device -> ObdDevice(name = device.name ?: "OBD adapter", address = device.address) }
                .sortedBy { it.name }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun readKm(context: Context, address: String): Double {
        try {
            return readKmChecked(context, address)
        } catch (error: IOException) {
            throw error
        } catch (error: SecurityException) {
            throw IOException("Allow Bluetooth access, then try again.", error)
        } catch (error: Exception) {
            Log.e("ObdBluetooth", "odometer read failed", error)
            throw IOException("Could not read the odometer.", error)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readKmChecked(context: Context, address: String): Double {
        unavailableReason(context)?.let { throw IOException(it) }
        val adapter = adapter(context) ?: throw IOException("Turn Bluetooth on, then try again.")
        val mac = InputCheck.bluetoothAddress(address)
            ?: throw IOException("That adapter is no longer paired.")
        val device = adapter.bondedDevices?.firstOrNull { it.address.equals(mac, ignoreCase = true) }
            ?: throw IOException("That adapter is no longer paired.")
        runCatching { adapter.cancelDiscovery() }
        val socket = connect(device)
        try {
            val input = socket.inputStream
            val output = socket.outputStream
            command(output, input, "ATZ", 3000)
            command(output, input, "ATE0", 1500)
            command(output, input, "ATL0", 1500)
            command(output, input, "ATS0", 1500)
            command(output, input, "ATH0", 1500)
            command(output, input, "ATSP0", 8000)
            val response = command(output, input, "01A6", 8000)
            return Odometer.parseElmKm(response)
                ?: throw IOException("This car did not report an odometer. Type the dash reading instead.")
        } catch (error: IOException) {
            if (error.message == "This car did not report an odometer. Type the dash reading instead.") throw error
            Log.e("ObdBluetooth", "odometer read failed", error)
            throw IOException("Could not connect. Turn the ignition on and stay next to the adapter.", error)
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun adapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return null
        return manager.adapter
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: android.bluetooth.BluetoothDevice): BluetoothSocket {
        val primary = device.createRfcommSocketToServiceRecord(sppUuid)
        try {
            primary.connect()
            return primary
        } catch (_: IOException) {
            runCatching { primary.close() }
        }
        // shortcut: channel-1 socket for clone ELM327 adapters that reject service discovery
        val fallback = try {
            device.javaClass
                .getMethod("createRfcommSocket", Integer.TYPE)
                .invoke(device, 1) as BluetoothSocket
        } catch (error: Exception) {
            throw IOException("Could not connect. Turn the ignition on and stay next to the adapter.", error)
        }
        try {
            fallback.connect()
            return fallback
        } catch (error: IOException) {
            runCatching { fallback.close() }
            throw IOException("Could not connect. Turn the ignition on and stay next to the adapter.", error)
        }
    }

    private fun command(output: OutputStream, input: InputStream, cmd: String, timeoutMs: Int): String {
        output.write("$cmd\r".toByteArray(Charsets.US_ASCII))
        output.flush()
        return readUntilPrompt(input, timeoutMs)
    }

    // BluetoothSocket has no read timeout, so poll the buffer until the ELM prompt.
    private fun readUntilPrompt(input: InputStream, timeoutMs: Int): String {
        val buf = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val waiting = input.available()
            if (waiting <= 0) {
                Thread.sleep(40)
                continue
            }
            for (ignored in 0 until waiting) {
                val byte = input.read()
                if (byte < 0) return buf.toString()
                val char = byte.toChar()
                buf.append(char)
                if (char == '>') return buf.toString()
            }
        }
        return buf.toString()
    }
}
