package com.writestreams.checkin.ui.checkin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.writestreams.checkin.R
import com.writestreams.checkin.data.repository.Repository
import com.writestreams.checkin.databinding.FragmentCheckinBinding
import kotlinx.coroutines.launch

class CheckinFragment : Fragment() {
    private var _binding: FragmentCheckinBinding? = null
    private lateinit var searchTextEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var repository: Repository
    private lateinit var adapter: PersonAdapter

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCheckinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = Repository(requireContext())
        searchTextEditText = view.findViewById(R.id.searchTextEditText)
        searchButton = view.findViewById(R.id.searchButton)
        resultsRecyclerView = view.findViewById(R.id.resultsRecyclerView)
        resultsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = PersonAdapter()
        resultsRecyclerView.adapter = adapter

        searchButton.setOnClickListener {
            val query = searchTextEditText.text.toString()
            if (query.isNotEmpty()) {
                searchPersons(query)
            } else {
                Toast.makeText(requireContext(), "Please enter a search query", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchPersons(query: String) {
        lifecycleScope.launch {
            try {
                val results = repository.searchPersons(query)
                adapter.submitList(results)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error searching persons", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}