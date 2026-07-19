package com.writestreams.checkin.ui.attendance

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.databinding.FragmentAttendanceBinding
import com.writestreams.checkin.service.AttendanceService
import com.writestreams.checkin.service.CheckinService
import com.writestreams.checkin.service.SyncEngine
import com.writestreams.checkin.util.ApiKeys
import com.writestreams.checkin.R
import kotlinx.coroutines.launch

class AttendanceFragment : Fragment() {

    private var _binding: FragmentAttendanceBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CheckedInPersonAdapter
    private lateinit var attendanceService: AttendanceService
    private lateinit var checkinService: CheckinService
    private lateinit var viewModel: AttendanceViewModel
    private var personsList: List<Person> = listOf()
    private var searchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAttendanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        attendanceService = AttendanceService(requireContext())
        checkinService = CheckinService(requireContext())
        viewModel = ViewModelProvider(this)[AttendanceViewModel::class.java]
        adapter = CheckedInPersonAdapter(personsList) { person ->
            showCheckOutConfirmationDialog(person)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.persons.collect { persons ->
                    personsList = persons
                    showFilteredList()
                    binding.attendeesBadge.text = persons.size.toString()
                }
            }
        }

        binding.sourceRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            viewModel.setSource(
                when (checkedId) {
                    R.id.localRadioButton -> AttendanceViewModel.Source.LOCAL
                    R.id.pendingRadioButton -> AttendanceViewModel.Source.PENDING
                    else -> AttendanceViewModel.Source.BREEZE
                }
            )
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText ?: ""
                showFilteredList()
                return true
            }
        })

        binding.syncButton.setOnClickListener {
            confirmThenSyncWithBreeze()
        }

        binding.printButton.setOnClickListener {
            val attendanceList = personsList.map { "${it.first_name} ${it.last_name}" }
            attendanceService.printAttendanceList(attendanceList)
        }

        binding.emailButton.setOnClickListener {
            val attendanceList = personsList.map { "${it.nameLastFirst()} - ${it.id} - ${it.getFormattedCheckinTime()}" }
            val recipient = ApiKeys.EMAIL_RECIPIENTS
            attendanceService.emailAttendanceList(attendanceList, recipient)
        }
    }

    private fun showFilteredList() {
        val filteredList = if (searchQuery.isEmpty()) personsList else personsList.filter {
            it.first_name.contains(searchQuery, ignoreCase = true) ||
                    it.last_name.contains(searchQuery, ignoreCase = true)
        }
        adapter.updateList(filteredList)
    }

    private fun showCheckOutConfirmationDialog(person: Person) {
        AlertDialog.Builder(requireContext())
            .setTitle("Check Out")
            .setMessage("Are you sure you want to check out ${person.first_name} ${person.last_name}?")
            .setPositiveButton("Yes") { _, _ ->
                // The observed Flow refreshes the list when the row is removed
                checkinService.checkOutPerson(person)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun confirmThenSyncWithBreeze() {
        AlertDialog.Builder(requireContext())
            .setTitle("Sync with Breeze")
            .setMessage("Do you want to send local data to breeze?")
            .setPositiveButton("Yes") { _, _ ->
                checkinService.sendLocalDataToBreeze()
                SyncEngine.getInstance(requireContext().applicationContext).requestSyncNow()
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
