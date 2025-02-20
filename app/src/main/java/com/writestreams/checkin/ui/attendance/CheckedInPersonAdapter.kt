package com.writestreams.checkin.ui.attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.writestreams.checkin.R
import com.writestreams.checkin.data.local.Person

class CheckedInPersonAdapter(
    private var personsList: List<Person>,
    private val onPersonClick: (Person) -> Unit
) : RecyclerView.Adapter<CheckedInPersonAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_person, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val person = personsList[position]
        holder.nameTextView.text = "${person.first_name} ${person.last_name}"
        holder.itemView.setOnClickListener {
            onPersonClick(person)
        }
    }

    override fun getItemCount(): Int = personsList.size

    fun updateList(newList: List<Person>) {
        personsList = newList
        notifyDataSetChanged()
    }
}