package com.writestreams.checkin.ui.checkin

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.databinding.DialogFamilyCheckinBinding

class FamilyCheckinDialogFragment(private val person: Person) : DialogFragment() {

    private var _binding: DialogFamilyCheckinBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FamilyMemberAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFamilyCheckinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.personNameTextView.text = "${person.first_name} ${person.last_name}"
//        binding.personDetailsTextView.text = person.toString()

        val familyMembers = person.family
        adapter = FamilyMemberAdapter(familyMembers)
        binding.familyMembersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.familyMembersRecyclerView.adapter = adapter

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.doneButton.setOnClickListener {
            val checkedFamilyMembers = adapter.getCheckedFamilyMembers()
            for (member in checkedFamilyMembers) {
                Log.d("FamilyCheckin", "Checked family member: ${member.details.first_name} ${member.details.last_name}")
            }
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}