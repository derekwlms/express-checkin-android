package com.writestreams.checkin

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.writestreams.checkin.util.AppScope
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.databinding.ActivityMainBinding
import com.writestreams.checkin.util.AttendanceEmailScheduler
import com.writestreams.checkin.util.StatusBanners
import com.writestreams.checkin.service.CheckinService
import com.writestreams.checkin.service.SettingsService
import com.writestreams.checkin.service.SyncEngine
import com.writestreams.checkin.service.SyncStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: Repository
    private var breezeStatusItem: MenuItem? = null
    private var latestSyncStatus = SyncStatus()

    // Re-check the wrong-event banner whenever the selected event changes (e.g. in Settings)
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SELECTED_EVENT_KEY) updateBanners()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_checkin, R.id.navigation_attendance, R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        repository = Repository(applicationContext)
        AttendanceEmailScheduler.scheduleWeeklyEmail(applicationContext)
        observeSyncStatus()

        val settingsService = SettingsService(applicationContext)
        val currentInstanceId = settingsService.updateBreezeInstanceId(LocalDate.now())
        setUpBanners()

        AppScope.io.launch {
            try {
                // Weekly rollover: push any check-ins/persons still pending from past
                // weeks (to their own event instances), then purge those old rows.
                CheckinService(applicationContext).rolloverCheckins(currentInstanceId)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error rolling over checkins", e)
            }
            try {
                val cachedPersons = repository.getCachedPersons()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity,
                        "Found ${cachedPersons.size} persons", Toast.LENGTH_SHORT).show()
                    Log.i("MainActivity", "Found ${cachedPersons.size} cached persons")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error reading cached persons", e)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        breezeStatusItem = menu.findItem(R.id.action_breeze_status)
        updateBreezeStatusIcon(latestSyncStatus)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_breeze_status -> {
                // Tapping nudges the engine to probe/sync right away
                SyncEngine.getInstance(applicationContext).requestSyncNow()
                showSyncStatusDialog(latestSyncStatus)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun observeSyncStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SyncEngine.getInstance(applicationContext).status.collect { status ->
                    latestSyncStatus = status
                    updateBreezeStatusIcon(status)
                    // Also re-checks the event each sync cycle, catching the day rolling over
                    updateBanners()
                }
            }
        }
    }

    private fun setUpBanners() {
        binding.offlineBanner.setOnClickListener {
            SyncEngine.getInstance(applicationContext).requestSyncNow()
            showSyncStatusDialog(latestSyncStatus)
        }
        binding.wrongEventBanner.setOnClickListener { confirmSwitchToCurrentEvent() }
        updateBanners()
    }

    private fun updateBanners() {
        val selectedInstanceId = SettingsService.currentBreezeInstanceId(applicationContext)
        showBanner(binding.wrongEventBanner, StatusBanners.wrongEventText(selectedInstanceId, LocalDate.now()))
        showBanner(binding.offlineBanner, StatusBanners.offlineText(latestSyncStatus))
    }

    private fun showBanner(banner: TextView, text: String?) {
        banner.text = text ?: ""
        banner.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    private fun confirmSwitchToCurrentEvent() {
        val today = LocalDate.now()
        val currentDate = SettingsService.instanceDate(SettingsService.computeBreezeInstanceId(today))
        AlertDialog.Builder(this)
            .setTitle("Switch event?")
            .setMessage("Check in to the current event (${currentDate?.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}) instead?")
            .setPositiveButton("Switch") { _, _ ->
                SettingsService(applicationContext).updateBreezeInstanceId(today)
                SyncEngine.getInstance(applicationContext).requestSyncNow()
            }
            .setNegativeButton("Keep", null)
            .show()
    }

    private fun updateBreezeStatusIcon(status: SyncStatus) {
        val item = breezeStatusItem ?: return
        when {
            status.breezeReachable != true -> {
                item.setIcon(R.drawable.ic_cloud_off)
                item.icon?.setTint(COLOR_UNREACHABLE)
            }
            status.pendingPushCount > 0 -> {
                item.setIcon(R.drawable.ic_cloud)
                item.icon?.setTint(COLOR_PENDING)
            }
            else -> {
                item.setIcon(R.drawable.ic_cloud)
                item.icon?.setTint(COLOR_REACHABLE)
            }
        }
    }

    private fun showSyncStatusDialog(status: SyncStatus) {
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm:ss a")
        val message = buildString {
            appendLine(when (status.breezeReachable) {
                true -> "Breeze is reachable"
                false -> "Breeze is NOT reachable (offline, no Wi-Fi, or blocked)"
                null -> "Breeze has not been checked yet"
            })
            appendLine()
            appendLine("Last attendance sync: ${status.lastAttendanceSync?.format(timeFormatter) ?: "never"}")
            appendLine("Last member refresh: ${status.lastPeopleSync?.format(timeFormatter) ?: "never"}")
            appendLine("Not yet in Breeze: ${status.pendingPushCount} (see Attendance > Pending)")
            if (status.pushRejections.isNotEmpty()) {
                appendLine()
                appendLine("Rejected by Breeze (fix the data, then use Sync with Breeze on the Attendance screen to retry):")
                status.pushRejections.forEach { appendLine("  • $it") }
            }
            appendLine()
            appendLine("A sync check was just started.")
        }
        AlertDialog.Builder(this)
            .setTitle("Breeze Sync Status")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    // The sync engine runs while the app is visible; its first cycle pulls the full people list
    override fun onStart() {
        super.onStart()
        SyncEngine.getInstance(applicationContext).start()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)
        updateBanners()     // the app may have sat in the background since an earlier week
    }

    override fun onStop() {
        super.onStop()
        SyncEngine.getInstance(applicationContext).stop()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    companion object {
        private const val PREFS_NAME = "app_prefs"                  // as in SettingsService
        private const val SELECTED_EVENT_KEY = "breeze_instance_id"
        private val COLOR_REACHABLE = 0xFF4CAF50.toInt()     // green
        private val COLOR_PENDING = 0xFFFFC107.toInt()       // amber: reachable, pushes queued
        private val COLOR_UNREACHABLE = 0xFFBDBDBD.toInt()   // gray
    }
}