package com.example.minimal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class HistoryAdapter(
    private val onNoteChanged: (HistoryEntry, String) -> Unit
) : ListAdapter<HistoryEntry, HistoryAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        val tvWinning: TextView = itemView.findViewById(R.id.tv_winning)
        val tvUser: TextView = itemView.findViewById(R.id.tv_user)
        val tvNote: TextView = itemView.findViewById(R.id.tv_note)
        val btnEditNote: MaterialButton = itemView.findViewById(R.id.btn_edit_note)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)

        holder.tvDate.text = "Draw: ${entry.drawDate}"

        holder.tvWinning.text = buildString {
            append("Winning: ${entry.winningWhite.sorted().joinToString(" ")}")
            entry.winningPowerball?.let { append("  PB $it") }
        }

        holder.tvUser.text = buildString {
            append("Your pick: ${entry.userWhite.sorted().joinToString(" ")}")
            entry.userPowerball?.let { append("  PB $it") }
        }

        holder.tvNote.text = if (entry.note.isBlank()) "(no note)" else entry.note
        holder.tvNote.setTextColor(
            if (entry.note.isBlank()) 0xFF888888.toInt() else 0xFF000000.toInt()
        )

        holder.btnEditNote.setOnClickListener {
            val context = holder.itemView.context
            val input = TextInputEditText(context).apply {
                setText(entry.note)
                hint = "Your note for this draw…"
            }

            MaterialAlertDialogBuilder(context)
                .setTitle("Edit Note")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newNote = input.text?.toString()?.trim() ?: ""
                    onNoteChanged(entry, newNote)

                    // Optimistic UI update
                    val updated = entry.copy(note = newNote)
                    val currentList = currentList.toMutableList()
                    val index = currentList.indexOfFirst { it.drawDate == entry.drawDate }
                    if (index != -1) {
                        currentList[index] = updated
                        submitList(currentList)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HistoryEntry>() {
        override fun areItemsTheSame(old: HistoryEntry, new: HistoryEntry): Boolean =
            old.drawDate == new.drawDate

        override fun areContentsTheSame(old: HistoryEntry, new: HistoryEntry): Boolean =
            old == new
    }
}