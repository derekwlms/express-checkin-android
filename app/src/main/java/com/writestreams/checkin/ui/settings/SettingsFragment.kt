package com.writestreams.checkin.ui.settings

import android.Manifest
import android.bluetooth.BluetoothAdapter
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
import com.writestreams.checkin.service.BluetoothPrintService
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private lateinit var deviceAddressEditText: EditText
    private lateinit var labelTextEditText: EditText
    private lateinit var printButton: Button

    private lateinit var repository: Repository

    private lateinit var bluetoothAdapter: BluetoothAdapter

    // TODO - A listener might be handy. If I decide not to use it, remove it
//    interface ThermalPrinterListener {
//        fun onPrintSuccess()
//        fun onPrintError(message: String)
//    }
//
//    private var printerListener: ThermalPrinterListener? = null

    companion object {
        private lateinit var bluetoothPrintService: BluetoothPrintService

//        fun newInstance(): CheckinFragment {
//            return CheckinFragment()
//        }
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

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settingsViewModel =
            ViewModelProvider(this).get(SettingsViewModel::class.java)

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        val textView: TextView = binding.textSettings
        settingsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothPrintService = BluetoothPrintService(requireContext())

        // Find views
        deviceAddressEditText = view.findViewById(R.id.deviceAddressEditText)
        labelTextEditText = view.findViewById(R.id.labelTextEditText)
        printButton = view.findViewById(R.id.printButton)

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
                    Toast.makeText(
                        requireContext(),
                        "Fetched ${cachedPersons.size} persons",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Log.e("SettingsFragment", "Error fetching updates", e)
                    Toast.makeText(requireContext(), "Error fetching updates", Toast.LENGTH_SHORT)
                        .show()
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
                ActivityCompat.requestPermissions(requireActivity(), missingPermissions.toTypedArray(), 100)
            } else {
                connectAndPrint()
            }
        }
    }

    // Handle permission result for older Android versions
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            if (grantResults.all {
                    it == PackageManager.PERMISSION_GRANTED
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

        lifecycleScope.launch {
            bluetoothPrintService.printLabel(labelText, deviceAddress)
        }
    }

//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        if (context is ThermalPrinterListener) {
//            printerListener = context
//            lifecycleScope.launch {
//                bluetoothPrintService.printLabel("test label")
//            }
//        }
//    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

//    override fun onDetach() {
//        super.onDetach()
//        printerListener = null
//    }
}