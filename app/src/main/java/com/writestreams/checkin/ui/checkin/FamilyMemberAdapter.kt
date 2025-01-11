package com.writestreams.checkin.ui.checkin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.writestreams.checkin.R
import com.writestreams.checkin.data.local.FamilyMember

class FamilyMemberAdapter(private val familyMembers: List<FamilyMember>) :
    RecyclerView.Adapter<FamilyMemberAdapter.FamilyMemberViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FamilyMemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_family_member, parent, false)
        return FamilyMemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: FamilyMemberViewHolder, position: Int) {
        val familyMember = familyMembers[position]
        holder.bind(familyMember)
    }

    override fun getItemCount(): Int = familyMembers.size

    class FamilyMemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.familyMemberNameTextView)

        fun bind(familyMember: FamilyMember) {
            nameTextView.text = "${familyMember.details.first_name} ${familyMember.details.last_name}"
        }
    }
}