package com.writestreams.checkin.ui.checkin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.writestreams.checkin.R
import com.writestreams.checkin.databinding.FragmentCheckinBinding

class CheckinFragment : Fragment() {
    private var _binding: FragmentCheckinBinding? = null
    private lateinit var searchTextEditText: EditText
    private lateinit var searchButton: Button

    // This property is only valid between onCreateView and onDestroyView
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val checkinViewModel =
            ViewModelProvider(this)[CheckinViewModel::class.java]

        _binding = FragmentCheckinBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textCheckin
        checkinViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchTextEditText = view.findViewById(R.id.searchTextEditText)
        searchButton = view.findViewById(R.id.searchButton)

        searchButton.setOnClickListener {
            Toast.makeText(requireContext(),
                "Searching for ${searchTextEditText.text}",
                Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}