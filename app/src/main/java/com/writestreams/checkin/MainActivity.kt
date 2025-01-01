package com.writestreams.checkin

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: Repository

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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cachedPersons = repository.getCachedPersons()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity,
                        "Found ${cachedPersons.size} persons", Toast.LENGTH_SHORT).show()
                    Log.i("MainActivity", "Found ${cachedPersons.size} cached persons")
                }
                if (cachedPersons.size < 10) {
                    repository.fetchAndCachePersons()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("MainActivity", "Error fetching and caching persons", e)
                }
            }
        }
    }
}