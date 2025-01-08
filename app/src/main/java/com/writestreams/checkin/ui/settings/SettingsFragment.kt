package com.writestreams.checkin.ui.settings

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.writestreams.checkin.R
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.databinding.FragmentSettingsBinding
import com.writestreams.checkin.ui.checkin.CheckinFragment
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private lateinit var deviceAddressEditText: EditText
    private lateinit var labelTextEditText: EditText
    private lateinit var printButton: Button

    private lateinit var repository: Repository

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null

    // Listener for potential communication with host Activity
    interface ThermalPrinterListener {
        fun onPrintSuccess()
        fun onPrintError(message: String)
    }
    private var printerListener: ThermalPrinterListener? = null

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Factory method for fragment creation
        fun newInstance(): CheckinFragment {
            return CheckinFragment()
        }
    }

    // Permission request launcher
    private val multiplePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            connectAndPrint()
        } else {
            Toast.makeText(requireContext(), "Bluetooth Permissions Denied", Toast.LENGTH_SHORT).show()
        }
    }

    // This property is only valid between onCreateView and onDestroyView
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settingsViewModel =
            ViewModelProvider(this).get(SettingsViewModel::class.java)

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textSettings
        settingsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Find views
        deviceAddressEditText = view.findViewById(R.id.deviceAddressEditText)
        labelTextEditText = view.findViewById(R.id.labelTextEditText)
        printButton = view.findViewById(R.id.printButton)

        // Set up print button click listener
        printButton.setOnClickListener {
//            checkBluetoothPermissions()
            requestPermissions()
        }

        repository = Repository(requireContext())

        val getUpdatesButton: Button = view.findViewById(R.id.getUpdatesButton)
        getUpdatesButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    repository.fetchAndCachePersons()
                    val cachedPersons = repository.getCachedPersons()
                    Toast.makeText(requireContext(), "Fetched ${cachedPersons.size} persons", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("SettingsFragment", "Error fetching updates", e)
                    Toast.makeText(requireContext(), "Error fetching updates", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkBluetoothPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        val unGrantedPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (unGrantedPermissions.isNotEmpty()) {
            multiplePermissionLauncher.launch(unGrantedPermissions.toTypedArray())
        } else {
            connectAndPrint()
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            multiplePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            val missingPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
            }
            if (missingPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(requireActivity(), missingPermissions.toTypedArray(),
                    100)
            } else {
                connectAndPrint()
            }
        }
    }

    // Handle permission result for older Android versions
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED
                }) {
                connectAndPrint()
            } else {
                // TODO: Handle denied permissions
            }
        }
    }

    private fun connectAndPrint() {
        val deviceAddress = deviceAddressEditText.text.toString()
        val labelText = labelTextEditText.text.toString()

        if (deviceAddress.isEmpty() || labelText.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter device address and label text", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the user granting permission.
                // See the ActivityCompat#requestPermissions doc
                return
            }
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothSocket?.connect()

            val fpslCommand = generateFPSLCommand("Dec 8, 2024 9:55 am", "15", "Emma Parham", labelText, "1234", "")
            bluetoothSocket?.outputStream?.write(fpslCommand.toByteArray())

            Toast.makeText(requireContext(), "Label Printed Successfully!", Toast.LENGTH_SHORT).show()
            printerListener?.onPrintSuccess()
        } catch (e: Exception) {
            val errorMessage = "Printing Error: ${e.localizedMessage}"
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            printerListener?.onPrintError(errorMessage)
        } finally {
            bluetoothSocket?.close()
        }
    }

    private fun generateFPSLCommand(time: String, number: String, name: String, phone: String, id: String, image: String): String {
        // FPSL doc:
        //   - https://www.scribd.com/document/520866936/Thermal-Label-Printer-Programming-Manual-V1-0-2
        //   - https://hackernoon.com/how-to-print-labels-with-tspl-and-javascript
        // The Mvgges PL925U printer doesn't honor DIRECTION
        // 0,0 is bottom right with rotation 90

        return """
            SIZE 59 mm,102 mm
            GAP 5mm,0
            CLS
            TEXT 45,100,"1",90,2,2,"$number"
            TEXT 45,800,"1",90,2,2,"$time"
            TEXT 200,850,"1",90,2,2,"$image"   
            TEXT 200,630,"2",90,3,3,"$name"  
            TEXT 275,630,"1",90,2,2,"$phone"  
            TEXT 400,150,"2",90,2,2,"$id"          
            PRINT 1
            END
        """.trimIndent()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ThermalPrinterListener) {
            printerListener = context
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        printerListener = null
    }
}