package com.writestreams.checkin.ui.attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.writestreams.checkin.R
import com.writestreams.checkin.data.local.Person

/** One line on the Attendance screen; person is set only for rows that can be checked out. */
data class AttendanceRow(
    val key: String,
    val name: String,
    val detail: String,
    val person: Person? = null
)

class AttendanceRowAdapter(
    private val onCheckOut: (Person) -> Unit
) : RecyclerView.Adapter<AttendanceRowAdapter.ViewHolder>() {

    private var rows: List<AttendanceRow> = emptyList()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        val detailTextView: TextView = itemView.findViewById(R.id.detailTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = rows[position]
        holder.nameTextView.text = row.name
        holder.detailTextView.text = row.detail
        holder.detailTextView.visibility = if (row.detail.isEmpty()) View.GONE else View.VISIBLE
        val person = row.person
        if (person != null) {
            holder.itemView.setOnClickListener { onCheckOut(person) }
        } else {
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
        }
    }

    override fun getItemCount(): Int = rows.size

    fun updateList(newRows: List<AttendanceRow>) {
        rows = newRows
        notifyDataSetChanged()
    }
}
