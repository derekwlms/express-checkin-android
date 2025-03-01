package com.writestreams.checkin.ui.checkin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.writestreams.checkin.data.local.Guest
import com.writestreams.checkin.databinding.DialogGuestCheckinBinding
import com.writestreams.checkin.service.CheckinService

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

        binding.doneButton.setOnClickListener {
            val guest = createGuest()
            checkinService.checkInGuest(guest)
            Toast.makeText(requireContext(), "Guest added: ${guest.firstName} ${guest.lastName}", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.addChildButton.setOnClickListener {
            if (childCount < 10) {
                childCount++
                val editText = EditText(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    hint = "Child Name $childCount"
                    minHeight = 48.dpToPx()
                }
                binding.childNamesContainer.addView(editText)
            } else {
                Toast.makeText(requireContext(), "Please add again for more than 10 child names",
                    Toast.LENGTH_SHORT).show()
            }
        }
        binding.doneButton.isEnabled = false
    }

    private fun createGuest(): Guest {
        val childNames = mutableListOf<String>()
        binding.childNameEditText1.text.toString()
            .takeIf { it.isNotEmpty() }?.let { childNames.add(it) }
        // maybe time for ktx
        for (i in 0 until binding.childNamesContainer.childCount) {
            val childNameEditText = binding.childNamesContainer.getChildAt(i) as EditText
            childNames.add(childNameEditText.text.toString())
        }
        return Guest(
            firstName = binding.firstNameEditText.text.toString(),
            lastName = binding.lastNameEditText.text.toString(),
            phoneNumber = binding.phoneNumberEditText.text.toString(),
            emailAddress = binding.emailAddressEditText.text.toString(),
            addToDirectory = binding.addToDirectoryCheckBox.isChecked,
            childNames = childNames
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

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}