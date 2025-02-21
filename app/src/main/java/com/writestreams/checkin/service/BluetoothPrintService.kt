package com.writestreams.checkin.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.writestreams.checkin.util.BaseLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class BluetoothPrintService(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    suspend fun printLabel(label: BaseLabel) {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val deviceAddress = sharedPreferences.getString("printer_device_address", "66:32:F6:7A:4D:65")
            ?: "66:32:F6:7A:4D:65"
        withContext(Dispatchers.IO) {
            try {
                val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the user granting permission.
                    // See the ActivityCompat#requestPermissions doc
                    return@withContext
                }
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket?.connect()
                bluetoothSocket?.outputStream?.write(label.asFPSLCommand().toByteArray())

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Label Printed Successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val errorMessage = "Printing Error: ${e.localizedMessage}"
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
                Log.e("BluetoothPrintService", errorMessage, e)
            } finally {
                bluetoothSocket?.close()
            }
        }
    }

    // For development:
    suspend fun logPairedDevices() {
        withContext(Dispatchers.IO) {
            try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // Consider calling ActivityCompat#requestPermissions here... and then...
                    Log.e("BluetoothPrintService", "Bluetooth Connect permission not granted")
                } else {
                    for (device in bluetoothAdapter.bondedDevices) {
                        Log.i(
                            "BluetoothPrintService",
                            "Paired device: ${device.name} - ${device.address}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("BluetoothPrintService", "Error getting paired devices", e)
            }
        }
    }
}