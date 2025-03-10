package com.writestreams.checkin.ui.attendance

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.databinding.FragmentAttendanceBinding
import com.writestreams.checkin.service.AttendanceService
import com.writestreams.checkin.service.CheckinService
import com.writestreams.checkin.util.ApiKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AttendanceFragment : Fragment() {

    private var _binding: FragmentAttendanceBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: Repository
    private lateinit var adapter: CheckedInPersonAdapter
    private lateinit var attendanceService: AttendanceService
    private lateinit var checkinService: CheckinService
    private var personsList: List<Person> = listOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAttendanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = Repository(requireContext())
        attendanceService = AttendanceService(requireContext())
        checkinService = CheckinService(requireContext())
        adapter = CheckedInPersonAdapter(personsList) { person ->
            showCheckOutConfirmationDialog(person)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        fetchCheckedInPersons()

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val filteredList = personsList.filter {
                    it.first_name.contains(newText ?: "", ignoreCase = true) ||
                            it.last_name.contains(newText ?: "", ignoreCase = true)
                }
                adapter.updateList(filteredList)
                return true
            }
        })

        binding.printButton.setOnClickListener {
            val attendanceList = personsList.map { "${it.first_name} ${it.last_name}" }
            attendanceService.printAttendanceList(attendanceList)
        }

        binding.emailButton.setOnClickListener {
            val attendanceList = personsList.map { "${it.first_name} ${it.last_name} - ${it.id}" }
            val recipient = ApiKeys.EMAIL_RECIPIENTS
            attendanceService.emailAttendanceList(attendanceList, recipient)
        }
    }

    private fun fetchCheckedInPersons() {
        lifecycleScope.launch {
            personsList = withContext(Dispatchers.IO) {
                repository.getCheckedInPersons()
            }
            adapter.updateList(personsList)
            binding.attendeesBadge.text = personsList.size.toString()
        }
    }

    private fun showCheckOutConfirmationDialog(person: Person) {
        AlertDialog.Builder(requireContext())
            .setTitle("Check Out")
            .setMessage("Are you sure you want to check out ${person.first_name} ${person.last_name}?")
            .setPositiveButton("Yes") { _, _ ->
                checkOutPerson(person)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun checkOutPerson(person: Person) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                checkinService.checkOutPerson(person)
            }
            fetchCheckedInPersons()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}