package com.writestreams.checkin.ui.checkin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.writestreams.checkin.R
import com.writestreams.checkin.data.local.Person
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
        inflater: LayoutInflater, container: ViewGroup?,
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
        adapter = PersonAdapter { person -> showFamilyCheckinDialog(person) }
        resultsRecyclerView.adapter = adapter

        searchButton.setOnClickListener {
            val searchText = searchTextEditText.text.toString().trim()
            if (searchText.isNotEmpty()) {
                searchPersons(searchText)
            } else {
                Toast.makeText(requireContext(), "Please enter a name to search", Toast.LENGTH_SHORT).show()
            }
        }

        searchTextEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                searchButton.performClick()
                true
            } else {
                false
            }
        }

        searchTextEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val searchText = s.toString().trim()
                if (searchText.length >= 4) {
                    searchPersons(searchText)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
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

    private fun showFamilyCheckinDialog(person: Person) {
        val dialogFragment = FamilyCheckinDialogFragment(person)
        dialogFragment.show(parentFragmentManager, "FamilyCheckinDialogFragment")
        searchTextEditText.text.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}