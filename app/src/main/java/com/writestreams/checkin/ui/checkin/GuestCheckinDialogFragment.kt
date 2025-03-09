package com.writestreams.checkin.ui.checkin

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.writestreams.checkin.data.local.Guest
import com.writestreams.checkin.data.local.GuestChild
import com.writestreams.checkin.databinding.DialogGuestCheckinBinding
import com.writestreams.checkin.databinding.ItemChildBinding
import com.writestreams.checkin.service.CheckinService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

class GuestCheckinDialogFragment : DialogFragment() {

    private lateinit var checkinService: CheckinService
    private var _binding: DialogGuestCheckinBinding? = null
    private val binding get() = _binding!!
    private var childCount = 1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogGuestCheckinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkinService = CheckinService(requireContext())

        val requiredFields = listOf(
            binding.firstNameEditText,
            binding.lastNameEditText,
            binding.phoneNumberEditText,
            binding.emailAddressEditText
        )
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.doneButton.isEnabled = requiredFields.all { it.text.isNotEmpty() }
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        requiredFields.forEach { it.addTextChangedListener(textWatcher) }

        binding.dateOfBirthEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                val selectedDate = LocalDateTime.of(selectedYear, selectedMonth + 1, selectedDay, 0, 0)
                val formattedDate = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                binding.dateOfBirthEditText.setText(formattedDate)
            }, year, month, day)
            datePickerDialog.show()
        }

        binding.doneButton.setOnClickListener {
            val guest = createGuest()
            checkinService.checkInGuest(guest)
            Toast.makeText(requireContext(), "Guest added: ${guest.fullName()}", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.addChildButton.setOnClickListener {
            if (childCount < 10) {
                childCount++
                val childViewBinding = ItemChildBinding.inflate(layoutInflater, binding.childNamesContainer, false)
                binding.childNamesContainer.addView(childViewBinding.root)
            } else {
                Toast.makeText(requireContext(), "Please add again for more than 10 child names",
                    Toast.LENGTH_SHORT).show()
            }
        }
        binding.doneButton.isEnabled = false
    }

    private fun createGuest(): Guest {
        val children = mutableListOf<GuestChild>()
        for (i in 0 until binding.childNamesContainer.childCount) {
            val childViewBinding = ItemChildBinding.bind(binding.childNamesContainer.getChildAt(i))
            val childName = childViewBinding.childNameEditText.text.toString()
            val childDob = childViewBinding.childDobEditText.text.toString()
            val childSpecialNeeds = childViewBinding.childSpecialNeedsEditText.text.toString()
            if (childName.isNotEmpty()) {
//                val dateOfBirth = LocalDateTime.parse(childDob, DateTimeFormatter.ofPattern("MM/dd/yyyy"))
                val dateOfBirth = null
                children.add(GuestChild(childName, "", dateOfBirth, childSpecialNeeds))
            }
        }
        return Guest(
            firstName = binding.firstNameEditText.text.toString(),
            lastName = binding.lastNameEditText.text.toString(),
            phoneNumber = binding.phoneNumberEditText.text.toString(),
            emailAddress = binding.emailAddressEditText.text.toString(),
//            dateOfBirth = LocalDateTime.parse(binding.dateOfBirthEditText.text.toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            dateOfBirth = null,
            addToDirectory = binding.addToDirectoryCheckBox.isChecked,
            children = children
        )
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