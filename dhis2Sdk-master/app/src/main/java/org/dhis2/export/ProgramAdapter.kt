package org.dhis2.export

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.dhis2.Program2
import org.dhis2.R

class ProgramAdapter(
        private val programs: List<Program2>,
        private val onItemLongClick: (Program2) -> Unit
) : RecyclerView.Adapter<ProgramAdapter.ProgramViewHolder>() {

    private val selectedPrograms = mutableSetOf<Program2>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgramViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_program, parent, false)
        return ProgramViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProgramViewHolder, position: Int) {
        val program = programs[position]
        holder.bind(program, selectedPrograms.contains(program))
    }

    override fun getItemCount() = programs.size

    fun toggleSelection(program: Program2) {
        if (selectedPrograms.contains(program)) {
            selectedPrograms.remove(program)
        } else {
            selectedPrograms.add(program)
        }
        notifyDataSetChanged()
    }

    fun getSelectedPrograms() = selectedPrograms.toList()

    inner class ProgramViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val programNameTextView: TextView = itemView.findViewById(R.id.programName)

        fun bind(program: Program2, isSelected: Boolean) {
            programNameTextView.text = program.name
            itemView.setBackgroundColor(
                    if (isSelected) Color.LTGRAY else Color.TRANSPARENT
            )
            itemView.setOnClickListener() {
                onItemLongClick(program)
                true
            }
        }
    }
}
