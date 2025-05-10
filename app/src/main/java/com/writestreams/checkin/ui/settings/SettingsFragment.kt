package com.writestreams.checkin.ui.settings

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.writestreams.checkin.BuildConfig
import com.writestreams.checkin.R
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.databinding.FragmentSettingsBinding
import com.writestreams.checkin.service.BluetoothPrintService
import com.writestreams.checkin.service.SettingsService
import com.writestreams.checkin.util.Label
import com.writestreams.checkin.util.ReferenceLabel
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private lateinit var printerNamesSpinner: Spinner
    private lateinit var labelTextEditText: EditText
    private lateinit var printButton: Button

    private lateinit var settingsService: SettingsService
    private lateinit var bluetoothPrintService: BluetoothPrintService
    private lateinit var repository: Repository

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
        settingsService = SettingsService(requireContext())

        val versionTextView: TextView = binding.root.findViewById(R.id.versionTextView)
        val versionNumber = BuildConfig.VERSION_NAME
        val buildDate = BuildConfig.BUILD_DATE
        versionTextView.text = "Version $versionNumber\nBuild Date: $buildDate"

        showBreezeInstanceId()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothPrintService = BluetoothPrintService(requireContext())
        repository = Repository(requireContext())

        printerNamesSpinner = view.findViewById(R.id.printerNamesSpinner)
        labelTextEditText = view.findViewById(R.id.labelTextEditText)
        printButton = view.findViewById(R.id.printButton)

        printButton.setOnClickListener {
            // Do we want checkBluetoothPermissions() here? - see 2/8/25 ~ 8:50 pm commit
            requestPermissions()
        }

        val datePicker: DatePicker = view.findViewById(R.id.checkinDatePicker)
        datePicker.init(
            datePicker.year, datePicker.month, datePicker.dayOfMonth
        ) { _, year, monthOfYear, dayOfMonth ->
            settingsService.updateBreezeInstanceId(LocalDate.of(year, monthOfYear + 1, dayOfMonth))
            showBreezeInstanceId()
        }

        val resetCheckinsButton: Button = view.findViewById(R.id.resetCheckinsButton)
        resetCheckinsButton.setOnClickListener {
            confirmThenResetCheckins()
        }
        val getUpdatesButton: Button = view.findViewById(R.id.getUpdatesButton)
        getUpdatesButton.setOnClickListener {
            confirmThenFetchPersons()
        }
    }

    private fun confirmThenResetCheckins() {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Reset")
            .setMessage("Are you sure you want to reset all check-ins?")
            .setPositiveButton("Yes") { _, _ ->
                resetCheckins()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun resetCheckins() {
        lifecycleScope.launch {
            try {
                repository.resetAllCheckins()
                Toast.makeText(
                    requireContext(),
                    "All check-ins have been reset",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Error resetting checkins", e)
                Toast.makeText(requireContext(), "Error resetting checkins", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun confirmThenFetchPersons() {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Update")
            .setMessage("Are you sure you want to update the local members database?")
            .setPositiveButton("Yes") { _, _ ->
                fetchAndCachePersons()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun fetchAndCachePersons() {
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

    // Do we want to support older Android versions?
    // If so, see onRequestPermissionsResult in the 2/8/25 ~ 8:50 pm commit

    private fun connectAndPrint() {
        val printerName = printerNamesSpinner.selectedItem.toString()
        val labelText = labelTextEditText.text.toString()

        if (printerName.isEmpty() || labelText.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a printer and enter label text", Toast.LENGTH_SHORT).show()
            return
        }
        settingsService.updatePrinterDeviceAddress(printerName)

        val labelStrings = ("$labelText,,").split(',')
        lifecycleScope.launch {
            val label = if (labelText.startsWith("@")) {
                ReferenceLabel()
            } else {
                Label(labelStrings[0] ?: "", labelStrings[1] ?: "")
            }
            bluetoothPrintService.printLabel(label)
            // For development - to add a new printer:
            // 1. Uncomment bluetoothPrintService.logPairedDevices() below
            // 2. Pair with the new printer via Android Bluetooth Settings (pin is usually 1234)
            // 3. Run the app and do Settings - Test Print
            // 4. Search logcat for "Paired device:" to get the Mac address
            // 5. Add to the list in SettingsService (deviceAddresses) and strings.xml (printer_names)
            // I'll improve this later (after I drop support for old Android versions)
            // bluetoothPrintService.logPairedDevices()  // determine deviceAddress (MAC address)
            // Paired device: BlueTooth Printer - 66:32:F6:7A:4D:65 - A - 117
            // Paired device: BlueTooth Printer - 66:32:D7:D6:ED:10 - B
            // Paired device: BlueTooth Printer - 66:32:27:5A:91:A4 - C - 514
            // Paired device: BlueTooth Printer - 66:32:AF:39:15:16 - D - 078
            // Paired device: D450 - 10:23:81:47:79:F7 - E - D450
        }
    }

    private fun showBreezeInstanceId() {
        val breezeInstanceIdTextView: TextView = binding.root.findViewById(R.id.breezeInstanceIdTextView)
        breezeInstanceIdTextView.text = settingsService.getBreezeInstanceId()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}