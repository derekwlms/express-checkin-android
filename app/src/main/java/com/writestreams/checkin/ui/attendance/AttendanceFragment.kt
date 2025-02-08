package com.writestreams.checkin.ui.attendance

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AttendanceFragment : Fragment() {

    private var _binding: FragmentAttendanceBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: Repository
    private lateinit var adapter: CheckedInPersonAdapter
    private lateinit var attendanceService: AttendanceService
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
        adapter = CheckedInPersonAdapter(personsList)
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
            val attendanceList = personsList.map { "${it.first_name} ${it.last_name}" }
            val recipient = "derekwlms@gmail.com"
            attendanceService.emailAttendanceList(attendanceList, recipient)
        }
    }

    private fun fetchCheckedInPersons() {
        lifecycleScope.launch {
            personsList = withContext(Dispatchers.IO) {
                repository.getCheckedInPersons()
            }
            adapter.updateList(personsList)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}