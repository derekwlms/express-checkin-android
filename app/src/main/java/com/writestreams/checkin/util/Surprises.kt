package com.writestreams.checkin.util

import android.app.Activity
import android.app.AlertDialog

object Surprises {

    const val AARON_MESSAGE = "Good morning, Aaron! 🎉\n\n" +
        "We're looking forward to a great, Christ-honoring Sunday!\n\n" +
        "Psalm 118:24"

    fun isAaronAnderson(firstName: String?, lastName: String?): Boolean =
        firstName?.trim().equals("Aaron", ignoreCase = true) &&
            lastName?.trim().equals("Anderson", ignoreCase = true)

    fun showAaronGreeting(activity: Activity) {
        AlertDialog.Builder(activity)
            .setMessage(AARON_MESSAGE)
            .setPositiveButton("Amen!", null)
            .show()
    }
}
