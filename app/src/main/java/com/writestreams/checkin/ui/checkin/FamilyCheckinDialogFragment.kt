package com.writestreams.checkin.ui.checkin

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.writestreams.checkin.data.local.GuestChild
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.databinding.DialogFamilyCheckinBinding
import com.writestreams.checkin.databinding.ItemChildBinding
import com.writestreams.checkin.service.CheckinService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class FamilyCheckinDialogFragment(private val person: Person) : DialogFragment() {

    private var _binding: DialogFamilyCheckinBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FamilyMemberAdapter
    private lateinit var checkinService: CheckinService
    private var childCount = 1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFamilyCheckinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkinService = CheckinService(requireContext())

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
            val invalidFields = getInvalidFieldsForNewChild()
            if (invalidFields.isNotEmpty()) {
                Toast.makeText(requireContext(),
                    "The following fields are required for new children: ${invalidFields.joinToString(", ")}",
                    Toast.LENGTH_SHORT).show()
            } else {
                val checkedFamilyMembers = adapter.getCheckedFamilyMembers()
                checkinService.checkinFamily(familyMembers, checkedFamilyMembers, getNewChildren())
                dismiss()
            }
        }

        binding.addChildButton.setOnClickListener {
            if (childCount < 5) {
                childCount++
                addNewChildView()
            } else {
                Toast.makeText(requireContext(), "Please add again for more than 5 children",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addNewChildView() {
        val childViewBinding = ItemChildBinding.inflate(layoutInflater, binding.childNamesContainer, false)
        childViewBinding.childDobEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.set(2020, Calendar.JANUARY, 1)
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val childDobDatePickerDialog = DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                val selectedDate = LocalDate.of(selectedYear, selectedMonth + 1, selectedDay)
                val formattedDate = selectedDate.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))
                childViewBinding.childDobEditText.setText(formattedDate)
            }, year, month, day)
            childDobDatePickerDialog.show()
        }
        binding.childNamesContainer.addView(childViewBinding.root)
    }

    private fun getNewChildren(): List<GuestChild> {
        val children = mutableListOf<GuestChild>()
        for (i in 0 until binding.childNamesContainer.childCount) {
            val childViewBinding = ItemChildBinding.bind(binding.childNamesContainer.getChildAt(i))
            val childFirstName = childViewBinding.childFirstNameEditText.text.toString()
            val childLastName = childViewBinding.childLastNameEditText.text.toString()
            val childDob = childViewBinding.childDobEditText.text.toString()
            val childSpecialNeeds = childViewBinding.childSpecialNeedsEditText.text.toString()
            if (childFirstName.isNotEmpty()) {
                val dateOfBirth = runCatching {
                    LocalDate.parse(childDob, DateTimeFormatter.ofPattern("MM/dd/yyyy"))
                }.getOrNull()
                children.add(GuestChild(childFirstName, childLastName, dateOfBirth, childSpecialNeeds))
            }
        }
        return children
    }

    private fun getInvalidFieldsForNewChild(): List<String> {
        val invalidFields = mutableListOf<String>()
        for (i in 0 until binding.childNamesContainer.childCount) {
            val childViewBinding = ItemChildBinding.bind(binding.childNamesContainer.getChildAt(i))
            if (childViewBinding.childFirstNameEditText.text.isNullOrEmpty()) {
                invalidFields.add("Child First Name")
            }
            if (childViewBinding.childLastNameEditText.text.isNullOrEmpty()) {
                invalidFields.add("Child Last Name")
            }
            if (childViewBinding.childDobEditText.text.isNullOrEmpty()) {
                invalidFields.add("Child Date of Birth")
            }
        }
        return invalidFields
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