package com.writestreams.checkin.util

object Utils {
    fun formatPhoneNumber(phone: String): String {
        val digits = phone.replace(Regex("[^\\d]"), "")
        val length = digits.length

        return when {
            length == 0 -> ""
            length <= 3 -> "(${digits.substring(0, length)})"
            length <= 6 -> "(${digits.substring(0, 3)}) ${digits.substring(3, length)}"
            else -> "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6, length)}"
        }
    }
}