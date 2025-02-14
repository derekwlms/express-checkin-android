package com.writestreams.checkin.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.writestreams.checkin.R
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.databinding.FragmentSettingsBinding
import com.writestreams.checkin.service.BluetoothPrintService
import com.writestreams.checkin.util.Label
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private lateinit var deviceAddressEditText: EditText
    private lateinit var labelTextEditText: EditText
    private lateinit var printButton: Button

    private lateinit var repository: Repository

    // TODO - Do we want to restore the ThermalPrinterListener here?

    companion object {
        private lateinit var bluetoothPrintService: BluetoothPrintService
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
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothPrintService = BluetoothPrintService(requireContext())

        deviceAddressEditText = view.findViewById(R.id.deviceAddressEditText)
        labelTextEditText = view.findViewById(R.id.labelTextEditText)
        printButton = view.findViewById(R.id.printButton)

        printButton.setOnClickListener {
            // TODO - Do we want checkBluetoothPermissions() here? - see 2/8/25 ~ 8:50 pm commit
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

    // TODO - Do we want to support older Android versions?
    // If so, see onRequestPermissionsResult in the 2/8/25 ~ 8:50 pm commit

    private fun connectAndPrint() {
        val deviceAddress = deviceAddressEditText.text.toString()
        val labelText = labelTextEditText.text.toString()

        if (deviceAddress.isEmpty() || labelText.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter device address and label text", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val label = Label(labelText, deviceAddress)
            // bluetoothPrintService.logPairedDevices()  // determine deviceAddress (MAC address)
            // Paired device: BlueTooth Printer - 66:32:F6:7A:4D:65
            // Paired device: BlueTooth Printer - 66:32:D7:D6:ED:10
            bluetoothPrintService.printLabel(label, deviceAddress)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}