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
import com.writestreams.checkin.util.LabelFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class BluetoothPrintService(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    suspend fun printLabel(labelText: String, deviceAddress: String = "66:32:D7:D6:ED:10") {
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

                val fpslCommand = LabelFormatter.generateFPSLCommand("Dec 8, 2024 9:55 am", "15", "Emma Parham", labelText, "1234", "")
                bluetoothSocket?.outputStream?.write(fpslCommand.toByteArray())

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
}