package com.writestreams.checkin.ui.attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.writestreams.checkin.R
import com.writestreams.checkin.data.local.Person

class CheckedInPersonAdapter(private var persons: List<Person>) : RecyclerView.Adapter<CheckedInPersonAdapter.PersonViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_person, parent, false)
        return PersonViewHolder(view)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        val person = persons[position]
        holder.nameTextView.text = "${person.first_name} ${person.last_name}"
    }

    override fun getItemCount(): Int {
        return persons.size
    }

    fun updateList(newPersons: List<Person>) {
        persons = newPersons
        notifyDataSetChanged()
    }

    class PersonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
    }
}