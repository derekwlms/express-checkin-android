package com.writestreams.checkin.ui.home

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.writestreams.checkin.R
import com.writestreams.checkin.databinding.FragmentHomeBinding
import java.util.UUID

class HomeFragment : Fragment() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null

    private var _binding: FragmentHomeBinding? = null
    private lateinit var deviceAddressEditText: EditText
    private lateinit var labelTextEditText: EditText
    private lateinit var printButton: Button

    // Listener for potential communication with host Activity
    interface ThermalPrinterListener {
        fun onPrintSuccess()
        fun onPrintError(message: String)
    }
    private var printerListener: ThermalPrinterListener? = null

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Factory method for fragment creation
        fun newInstance(): HomeFragment {
            return HomeFragment()
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
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
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

            val fpslCommand = generateFPSLCommand(labelText)
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

    private fun generateFPSLCommand(text: String): String {
        // FPSL doc:
        //   - https://www.scribd.com/document/520866936/Thermal-Label-Printer-Programming-Manual-V1-0-2
        //   - https://hackernoon.com/how-to-print-labels-with-tspl-and-javascript

        /*
        SIZE 4,6
        GAP 0.25,0.25
        DIRECTION 1
        CLS
        TEXT 10,10,"2",0,1,1,"${text}"
        TEXT 0,0,"1",0,5,5,"font 1, rot 0, mult 5"
        TEXT 0,0,"1",90,8,8,"font 1, rot 90, mult 8"
        TEXT 0,0,"1",270,6,6,"font 5, rot 270, mult 6"
        PRINT 1
        END


        TEXT 100,100,"3",0,3,3,"Three"
        TEXT 200,200,"4",0,4,4,"Four"
        TEXT 300,300,"5",90,5,5,"Five"
         */

//        return """
//        SIZE 4,6
//        CLS
//        TEXT 150,150,"2",90,2,2,"Two heads are better than one"
//        TEXT 300,300,"3",90,3,3,"Three score and sixteen"
//        PRINT 1
//        END
//        """.trimIndent()

        /*
SIZE 48 mm,25 mm
CLS
TEXT 10,10,"4",0,1,1,"HackerNoon"
BARCODE 10,60,"128",90,1,0,2,2,"altospos.com"
PRINT 1
END

TEXT 10,10,"4",0,1,1,"HackerNoon"
TEXT 10,60,"4",0,1,1,"altospos.com"
         */

        return """
        SIZE 59 mm,102 mm
        GAP 5mm,0
        DIRECTION 0
        CLS
        TEXT 1,10,"1",0,1,1,"Derek Williams"
        TEXT 5,60,"2",0,2,2,"Line two"
        TEXT 10,110,"3",90,1,1,"Line three"
        TEXT 15,160,"4",0,1,1,"Line four"
        TEXT 20,210,"5",0,1,1,"Line five"
        TEXT 25,260,"6",0,1,1,"Line six"
        TEXT 70,800,"6",90,3,3,"Line seven"
        TEXT 120,600,"6",90,3,3,"Line eight"
        BOX 100,100,200,200,5
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